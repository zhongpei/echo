package com.virjar.echo.server.common.upstream;


import com.virjar.echo.nat.log.EchoLogger;
import com.virjar.echo.nat.protocol.EchoPacket;
import com.virjar.echo.nat.protocol.EchoPacketDecoder;
import com.virjar.echo.nat.protocol.EchoPacketEncoder;
import com.virjar.echo.nat.protocol.PacketCommon;
import com.virjar.echo.server.common.CommonConstant;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.DefaultChannelPromise;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.Attribute;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class NatMappingClientChannelHandler extends SimpleChannelInboundHandler<EchoPacket> {
    private void fireEchoConnectSuccess(ChannelHandlerContext ctx, EchoPacket msg) {
        // 在socks协议里traceId暂时用来判断传输的是否为traceId协议
        // String traceId = msg.getTraceID();
        EchoLogger.getLogger().info("receive type: TYPE_CONNECT_READY" + " from http natMappingHandle "
                + " for request id: " + msg.getSerialNumber() + " traceId: " + msg.getTraceID());
        Attribute<DefaultChannelPromise> attr = ctx.channel().attr(CommonConstant.connectReadyPromiseKey);
        DefaultChannelPromise defaultChannelPromise = attr.get();
        if (defaultChannelPromise == null) {
            log.warn("no channel promise bound for this connection");
            ctx.close();
            return;
        }
        if (attr.compareAndSet(defaultChannelPromise, null)) {
            ChannelPipeline pipeline = ctx.pipeline();
            pipeline.remove(EchoPacketEncoder.class);
            pipeline.remove(EchoPacketDecoder.class);
            pipeline.remove(this);
            pipeline.addFirst("idle", new IdleStateHandler(0, 0, 70));
            defaultChannelPromise.setSuccess();
        }

    }


    @Override
    protected void channelRead0(ChannelHandlerContext ctx, EchoPacket msg) throws Exception {
        switch (msg.getType()) {
            case PacketCommon.TYPE_HEARTBEAT:
                log.info("receive heartbeat message from heartbeat connection");
                break;
            case PacketCommon.TYPE_CONNECT_READY:
                fireEchoConnectSuccess(ctx, msg);
                break;
            default:
                log.warn("unknown message type:{}", PacketCommon.getReadableType(msg.getType()));
        }
    }


    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        super.exceptionCaught(ctx, cause);
        cause.printStackTrace();
    }
}
