package com.virjar.echo.nat.server.handlers;

import com.virjar.echo.nat.log.EchoLogger;
import com.virjar.echo.nat.log.EchoTraceLogger;
import com.virjar.echo.nat.protocol.EchoPacket;
import com.virjar.echo.nat.protocol.EchoPacketDecoder;
import com.virjar.echo.nat.protocol.EchoPacketEncoder;
import com.virjar.echo.nat.protocol.PacketCommon;
import com.virjar.echo.nat.server.ChannelStateManager;
import com.virjar.echo.nat.server.EchoServerConstant;
import com.virjar.echo.nat.server.EchoTuningExtra;
import com.virjar.echo.nat.server.EchoNatServer;
import com.virjar.echo.server.common.NettyUtils;
import com.virjar.echo.server.common.eventbus.ComponentEvent;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

@Slf4j
@ChannelHandler.Sharable
public class NatChannelHandler extends SimpleChannelInboundHandler<EchoPacket> {
    private final EchoNatServer echoNatServer;

    public NatChannelHandler(EchoNatServer echoNatServer) {
        this.echoNatServer = echoNatServer;
    }

    @Override
    public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
        super.channelRegistered(ctx);
        ChannelStateManager.setup(ctx.channel(), ChannelStateManager.CHANNEL_TYPE.CHANNEL_TYPE_NAT);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        super.channelInactive(ctx);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, EchoPacket msg) {
        String clientId = ChannelStateManager.getClientId(ctx.channel());
        log.info("receive message :{} from channel:{} clientId:{}", PacketCommon.getReadableType(msg.getType()), ctx.channel(), clientId);
        switch (msg.getType()) {
            case PacketCommon.C_TYPE_REGISTER:
                handleRegister(ctx, msg);
                break;
            case PacketCommon.TYPE_HEARTBEAT:
                handleHeartbeatMessage(ctx);
                break;
            case PacketCommon.TYPE_CONNECT_READY:
                handleConnectReady(ctx, msg);
                break;
            case PacketCommon.TYPE_TRANSFER:
                handleTransfer(ctx, msg);
                break;
            case PacketCommon.TYPE_DISCONNECT:
                handleDisConnect(ctx, msg);
                break;
            case PacketCommon.C_TYPE_CONTROL:
                handleControlResponseMessage(ctx, msg);
                break;
            default:
                log.warn("unKnown message type:{}", PacketCommon.getReadableType(msg.getType()));
                break;
        }
    }

    private void handleControlResponseMessage(ChannelHandlerContext ctx, EchoPacket msg) {
        echoNatServer.getEchoRemoteControlManager().handleReceiveRemoteControlMessage(ctx.channel(), msg);
    }

    private void handleTransfer(ChannelHandlerContext ctx, EchoPacket msg) {
        Channel userEndpointChannel = ChannelStateManager.getMappingChildChannel(ctx.channel(), msg.getSerialNumber());
        String traceId = msg.getTraceID();
        if (userEndpointChannel == null) {
            log.warn("can not find out mappingUserChannel when handleTransfer for channel:{} msg seq:{}", ctx.channel(), msg.getSerialNumber());
            return;
        }
        EchoTraceLogger.getLogger().info("write response data from mappingUserChannel to userEndpointChannel, traceId:" + traceId);
        byte[] data = msg.getData();
        ByteBuf buf = ctx.alloc().buffer(data.length);
        buf.writeBytes(data);
        userEndpointChannel.writeAndFlush(buf);
    }

    private void handleDisConnect(ChannelHandlerContext ctx, EchoPacket msg) {
        final Channel userEndpointChannel = ChannelStateManager.getMappingChildChannel(ctx.channel(), msg.getSerialNumber());
        if (userEndpointChannel != null) {
            userEndpointChannel.writeAndFlush(Unpooled.EMPTY_BUFFER)
                    .addListener(future -> NettyUtils.closeChannelIfActive(userEndpointChannel));
        }
    }

    private void handleConnectReady(ChannelHandlerContext ctx, EchoPacket msg) {
        EchoLogger.getLogger().info("receive type: TYPE_CONNECT_READY" + " from echo client "
                + " for request id: " + msg.getSerialNumber() + " traceId: " + msg.getTraceID());
        Channel mappingUserChannel = ChannelStateManager.getMappingChildChannel(ctx.channel(), msg.getSerialNumber());
        if (mappingUserChannel == null) {
            log.warn("can not find out mappingUserChannel when handleConnectReady for channel:{} msg seq:{}", ctx.channel(), msg.getSerialNumber());
            return;
        }
        mappingUserChannel.writeAndFlush(msg).addListener((ChannelFutureListener) channelFuture -> {
            if (!channelFuture.isSuccess()) {
                log.warn("write connection ready message failed", channelFuture.cause());
                mappingUserChannel.close();
                return;
            }
            //now handShark success,we need start tuning
            mappingUserChannel.pipeline().remove(EchoPacketEncoder.class);
            mappingUserChannel.pipeline().remove(EchoPacketDecoder.class);
        });

    }

    private void handleHeartbeatMessage(ChannelHandlerContext ctx) {
        String clientId = ChannelStateManager.getClientId(ctx.channel());
        if (clientId == null) {
            log.error("no client bound for channel:{}", ctx.channel());
            return;
        }
        log.info("receive heartbeat message from client:{} for channel:{}", clientId, ctx.channel());
    }

    private void onLoseConnect(EchoTuningExtra echoTuningExtra) {
        log.info("mapping service shutdown:{}", echoTuningExtra);
        NettyUtils.closeChannelIfActive(echoTuningExtra.getEchoNatChannel());
        NettyUtils.closeChannelIfActive(echoTuningExtra.getMappingServerChannel());
        echoNatServer.getPortResourceManager().returnPort(echoTuningExtra.getPort());
        echoNatServer.unregisterConnectionInfo(echoTuningExtra);

        //所有的代理服务端到NatMapping端的连接
        NettyUtils.closeAll(ChannelStateManager.connectedDownStreams(echoTuningExtra.getMappingServerChannel()));
        NettyUtils.closeAll(ChannelStateManager.heartBeatChildChannel(echoTuningExtra.getMappingServerChannel()));

        // notify meta server that this resource has been shutdown
        ComponentEvent componentEvent
                = ComponentEvent.createNatClientOfflineEvent(echoTuningExtra.toVo());
        echoNatServer.getEventBusManager()
                .pushEvent(componentEvent);
    }

    private void handleRegister(final ChannelHandlerContext ctx, EchoPacket msg) {
        String clientId = msg.getExtra();
        if (StringUtils.isBlank(clientId)) {
            log.error("the message register with empty clientId");
            ctx.close();
            return;
        }
        byte[] accountExtra = msg.getData();
        if (accountExtra != null && accountExtra.length > 0) {
            String account = new String(accountExtra, StandardCharsets.UTF_8);
            clientId = account + "|@--@--@|" + clientId;
        }

        ChannelStateManager.setupClientId(ctx.channel(), clientId);

        final Integer port = echoNatServer.getPortResourceManager().allocate();

        if (port == null) {
            log.error("no available port resource for http proxy ,now clientId is:{}", clientId);

            //等一会儿再关闭，避免客户端一直重连
            ctx.executor().schedule((Runnable) ctx::close, 40, TimeUnit.SECONDS);
            return;
        }
        String additionAccount = null;
        byte[] data = msg.getData();
        if (data != null && data.length > 0) {
            additionAccount = new String(data);
        }

        final String finalAdditionAccount = additionAccount;
        String finalClientId = clientId;
        log.info("prepare open mapping service for client:{} local port:{}", clientId, port);
        echoNatServer.openMapping(port)
                .addListener((ChannelFutureListener) future -> {
                    if (!future.isSuccess()) {
                        log.error("can not open port mapping:", future.cause());
                        echoNatServer.getPortResourceManager().returnPort(port);
                        ctx.close();
                        return;
                    }
                    onEchoProxyServiceEstablish(future.channel(), finalAdditionAccount, finalClientId,
                            port, ctx.channel()
                    );
                });

    }

    void onEchoProxyServiceEstablish(Channel mappingServerChannel, final String finalAdditionAccount,
                                     String finalClientId, Integer port, Channel echoNatChannel) {

        ChannelStateManager.setup(mappingServerChannel, ChannelStateManager.CHANNEL_TYPE.CHANNEL_TYPE_MAPPING_SERVER);
        EchoTuningExtra echoTuningExtra = new EchoTuningExtra(
                finalAdditionAccount, finalClientId, port, echoNatChannel, mappingServerChannel, echoNatServer.getLocalHost()
        );
        log.info("mapping service open successfully:{}", echoTuningExtra);
        ChannelStateManager.onEchoProxyEstablish(echoTuningExtra);
        echoNatServer.registerConnectionInfo(echoTuningExtra);

        NettyUtils.loveOther(mappingServerChannel, echoNatChannel);

        mappingServerChannel.closeFuture().addListener(
                (ChannelFutureListener) channelFuture -> onLoseConnect(echoTuningExtra)
        );

        // sync notify meta server this client has online
        // and all downstream server will get this message immediately
        ComponentEvent componentEvent
                = ComponentEvent.createNatClientOnlineEvent(echoTuningExtra.toVo());

        echoNatServer.getEventBusManager()
                .pushEvent(componentEvent);
    }

}
