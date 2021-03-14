package com.virjar.echo.nat.server.handlers;

import com.google.common.base.Preconditions;
import com.virjar.echo.nat.log.EchoLogger;
import com.virjar.echo.nat.log.EchoTraceLogger;
import com.virjar.echo.nat.protocol.EchoPacket;
import com.virjar.echo.nat.protocol.PacketCommon;
import com.virjar.echo.nat.server.ChannelStateManager;
import com.virjar.echo.nat.server.EchoServerConstant;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 接受从HttpProxy or  代理客户端(mobile clinet)发送来的请求
 */
@Slf4j
public class MappingChannelHandler extends SimpleChannelInboundHandler<Object> {
    private static final int MAPPING_CHANNEL_TYPE_INIT = 0;
    private static final int MAPPING_CHANNEL_TYPE_HEATH_CHECK = 1;
    private static final int MAPPING_CHANNEL_TYPE_USER = 2;
    private int mappingChannelType = MAPPING_CHANNEL_TYPE_INIT;

    @Override
    public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
        super.channelRegistered(ctx);
        ChannelStateManager.setup(ctx.channel(), ChannelStateManager.CHANNEL_TYPE.CHANNEL_TYPE_MAPPING_CHILD);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Object msg) {
        if (msg instanceof EchoPacket) {
            handleEchoMessage(ctx, (EchoPacket) msg);
        } else if (msg instanceof ByteBuf) {
            handleTransferMessage(ctx, (ByteBuf) msg);
        } else {
            log.error("unknown message type:{} channel:{}", msg.getClass(), ctx.channel());
            ctx.close();
        }
    }

    private void setupMappingChannelType(int targetType) {
        if (mappingChannelType == MAPPING_CHANNEL_TYPE_INIT) {
            mappingChannelType = targetType;
            return;
        }
        Preconditions.checkArgument(targetType == mappingChannelType,
                "error channel type,expect :" + mappingChannelType + " target:" + targetType
        );
    }

    private void handleEchoMessage(ChannelHandlerContext ctx, EchoPacket echoPacket) {
        log.info("received message type:{}  for request:{} channel:{}",
                PacketCommon.getReadableType(echoPacket.getType()), echoPacket.getSerialNumber(), ctx.channel());
        switch (echoPacket.getType()) {
            case PacketCommon.TYPE_CONNECT:
                setupMappingChannelType(MAPPING_CHANNEL_TYPE_USER);
                handleHandSharkEchoMessage(ctx, echoPacket);
                break;
            case PacketCommon.TYPE_HEARTBEAT:
                setupMappingChannelType(MAPPING_CHANNEL_TYPE_HEATH_CHECK);
                handleHeartBeatEchoMessage(ctx);
                break;
            default:
                log.warn("unknown message type:{}", PacketCommon.getReadableType(echoPacket.getType()));
                ctx.close();
        }
    }

    private final AtomicBoolean hasSetupServerIdleHandler = new AtomicBoolean(false);


    private void handleHeartBeatEchoMessage(ChannelHandlerContext ctx) {
        // 只有 heathCheck channel  存在 heartbeat消息
        // make sure server idle handler
        log.info("receive heartbeat message from  downstream :{}", ctx.channel());
        if (hasSetupServerIdleHandler.compareAndSet(false, true)) {
            ctx.channel().parent().attr(EchoServerConstant.HEART_BEAT_CHANNEL)
                    .get().add(ctx.channel());
            ctx.pipeline().addAfter("encoder", "idle-check", new ServerIdleCheckHandler());
        }
    }

    private void handleHandSharkEchoMessage(ChannelHandlerContext ctx, EchoPacket echoPacket) {
        String hostAndPort = echoPacket.getExtra();
        if (StringUtils.isBlank(hostAndPort)
                || !hostAndPort.contains(":")) {
            log.warn("error hostAndPort config:{} disconnect channel", hostAndPort);
            ctx.close();
            return;
        }

        Long seq = ChannelStateManager.userConnectionEstablish(ctx.channel());
        log.info("create connection to :{} with sequence:{} traceId:{}", echoPacket.getExtra(), seq, echoPacket.getTraceID());
        echoPacket.setSerialNumber(seq);

        //让手机端创建一个socket链接
        Channel echoNatChannel = ChannelStateManager.getBindNatChannel(ctx.channel());
        // 发送到echo client的channel 绑定traceId
        echoNatChannel.attr(EchoServerConstant.ECHO_TRACE_ID).set(echoPacket.getTraceID());
        echoNatChannel.writeAndFlush(echoPacket);
    }


    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        super.channelInactive(ctx);
        if (mappingChannelType == MAPPING_CHANNEL_TYPE_USER) {
            // 用户端请求断开连接，此时关闭上游连接资源
            ChannelStateManager.userConnectionDisconnect(ctx.channel());
        } else if (mappingChannelType == MAPPING_CHANNEL_TYPE_HEATH_CHECK) {
            //如果是healthCheck的连接断开，那么销毁这个代理资源
            log.info("lost connection for healthCheck,shutdown mapping service:{}",
                    ChannelStateManager.getClientId(ctx.channel()));
            ctx.channel().parent().close();
        }
    }

    private void handleTransferMessage(ChannelHandlerContext ctx, ByteBuf byteBuf) {
        Channel echoNatChannel = ChannelStateManager.getBindNatChannel(ctx.channel());
        Long seq = ctx.channel().attr(EchoServerConstant.NAT_CHANNEL_SERIAL).get();
        String traceId = echoNatChannel.attr(EchoServerConstant.ECHO_TRACE_ID).get();
        int maxPackSize = PacketCommon.MAX_FRAME_LENGTH - 128;
        while (byteBuf.readableBytes() > maxPackSize) {
            byte[] bytes = new byte[maxPackSize];
            byteBuf.readBytes(bytes);
            EchoPacket natMessage = new EchoPacket();
            natMessage.setData(bytes);
            natMessage.setSerialNumber(seq);
            natMessage.setTraceID(traceId);
            natMessage.setType(PacketCommon.TYPE_TRANSFER);
            //write,但是不需要 flush
            echoNatChannel.write(natMessage);
            EchoLogger.getLogger().info("receive data from user endpoint with big packet, forward to natChannel");
        }
        if (byteBuf.readableBytes() > 0) {
            EchoPacket natMessage = new EchoPacket();
            byte[] bytes = new byte[byteBuf.readableBytes()];
            byteBuf.readBytes(bytes);
            natMessage.setData(bytes);
            natMessage.setSerialNumber(seq);
            natMessage.setTraceID(traceId);
            natMessage.setType(PacketCommon.TYPE_TRANSFER);

            echoNatChannel.writeAndFlush(natMessage);
            EchoLogger.getLogger().info("receive data from user endpoint, forward to natChannel");
        } else {
            echoNatChannel.flush();
        }
        EchoLogger.getLogger().info("receive transfer data from user and forward to natChannel, traceId: " + traceId);
    }


}
