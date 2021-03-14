package com.virjar.echo.nat.server.handlers;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.util.CharsetUtil;
import lombok.extern.slf4j.Slf4j;

/**
 * @author lei.X
 * @date 2021/2/3
 * <p>
 * 查看经过的ctx的channel的消息
 */

@Slf4j
public class testMsgHandler extends ChannelInboundHandlerAdapter {


    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {

        ByteBuf byteBuf = ((ByteBuf) msg).copy();
        String receiveMsg = byteBuf.toString(CharsetUtil.UTF_8);

        log.info("RECORD:" + receiveMsg);
        super.channelRead(ctx, msg);
    }
}
