package com.virjar.echo.hproxy.handlers.http;

import com.virjar.echo.server.common.CommonConstant;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.util.ReferenceCounted;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class HttpEchoUpstreamChannelHandler extends SimpleChannelInboundHandler<Object> {
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof ByteBuf) {
            handleTuningForward(ctx, (ByteBuf) msg);
        } else if (msg instanceof HttpObject) {
            handleHttpResponse(ctx, (HttpObject) msg);
        } else {
            log.warn("unknown message type: " + msg);
        }
    }

    private void handleTuningForward(ChannelHandlerContext ctx, ByteBuf byteBuf) {
        Channel clientToProxyChannel = ctx.channel().attr(CommonConstant.NEXT_CHANNEL).get();
        if (clientToProxyChannel == null) {
            log.error("not clientToProxyChannel bound for channel: {}", ctx.channel());
            ctx.close();
            return;
        }
        byteBuf.retain();
        clientToProxyChannel.writeAndFlush(byteBuf);
    }

    private void handleHttpResponse(ChannelHandlerContext ctx, HttpObject httpResponse) {
        Channel clientToProxyChannel = ctx.channel().attr(CommonConstant.NEXT_CHANNEL).get();
        if (clientToProxyChannel == null) {
            log.error("not clientToProxyChannel bound for channel: {}", ctx.channel());
            ctx.close();
            return;
        }
        if (httpResponse instanceof ReferenceCounted) {
            //httpContent需要retain，否则会被回收导致无法write
            ((ReferenceCounted) httpResponse).retain();
        }
        if (httpResponse instanceof LastHttpContent) {
            clientToProxyChannel.writeAndFlush(httpResponse);
        } else {
            clientToProxyChannel.write(httpResponse);
        }
    }
}
