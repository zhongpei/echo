package com.virjar.echo.nat.client;

import com.virjar.echo.nat.log.EchoLogger;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

public class ClientToRealServerChannelHandler extends SimpleChannelInboundHandler<ByteBuf> {
    private final EchoClient echoClient;

    ClientToRealServerChannelHandler(EchoClient echoClient) {
        this.echoClient = echoClient;
    }

    private Long checkConnection(ChannelHandlerContext ctx) {

        Channel channel = ctx.channel();
        Long serial = channel.attr(EchoClientConstant.SERIAL_NUM).get();

        if (serial == null) {
            EchoLogger.getLogger().warn("not serial bound for channel:" + channel);
            channel.close();
            return null;
        }

        if (!echoClient.isAlive()) {
            EchoLogger.getLogger().warn("nat connection closed,close proxyToServer connection");
            channel.close();
            return null;
        }

        return serial;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) {
        Long seq = ctx.channel().attr(EchoClientConstant.SERIAL_NUM).get();
        if (seq == null) {
            EchoLogger.getLogger().warn("no serial bound for clientToRealServer channel!!");
            return;
        }
        // msg.retain();
        echoClient.forwardRealServerResponse(msg, seq);

    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        Long serial = checkConnection(ctx);
        if (serial == null) {
            return;
        }

        echoClient.realServerDisconnect(serial);

    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        EchoLogger.getLogger().error("error occur ", cause);
        super.exceptionCaught(ctx, cause);
    }
}
