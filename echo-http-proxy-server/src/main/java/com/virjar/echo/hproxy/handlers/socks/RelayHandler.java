package com.virjar.echo.hproxy.handlers.socks;

import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.ReferenceCounted;

/**
 * @author lei.X
 * @date 2021/1/30
 */

public final class RelayHandler extends ChannelInboundHandlerAdapter {

    private final Channel relayChannel;

    public RelayHandler(Channel relayChannel) {
        this.relayChannel = relayChannel;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        ctx.writeAndFlush(Unpooled.EMPTY_BUFFER);
    }



    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (relayChannel.isActive()) {
            if (msg instanceof ReferenceCounted) {
                ((ReferenceCounted) msg).retain();
            }
            relayChannel.writeAndFlush(msg);
        } else {
            ReferenceCountUtil.release(msg);
        }
    }


    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        cause.printStackTrace();
        ctx.close();
    }
}
