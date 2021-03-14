package com.virjar.echo.nat.client;

import com.virjar.echo.nat.log.EchoLogger;
import com.virjar.echo.nat.protocol.EchoPacket;
import com.virjar.echo.nat.protocol.PacketCommon;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;

public class ClientIdleCheckHandler extends IdleStateHandler {

    public ClientIdleCheckHandler() {
        super(PacketCommon.READ_IDLE_TIME, PacketCommon.WRITE_IDLE_TIME, 0);
    }

    @Override
    protected void channelIdle(ChannelHandlerContext ctx, IdleStateEvent evt) throws Exception {
        if (IdleStateEvent.FIRST_WRITER_IDLE_STATE_EVENT == evt) {
            EchoLogger.getLogger().info("write idle, write a heartbeat message to server");
            EchoPacket proxyMessage = new EchoPacket();
            proxyMessage.setType(PacketCommon.TYPE_HEARTBEAT);
            ctx.channel().writeAndFlush(proxyMessage);
        } else if (IdleStateEvent.FIRST_READER_IDLE_STATE_EVENT == evt) {
            //不能在readTimeout的时候，就判定超时。比如在下载大文件的时候，只有数据写。没有写idle发生。也就没有heartbeat的ack。不会产生heartbeat的响应包
            EchoLogger.getLogger().info("first read  idle, write a heartbeat message to server");
            EchoPacket proxyMessage = new EchoPacket();
            proxyMessage.setType(PacketCommon.TYPE_HEARTBEAT);
            ctx.channel().writeAndFlush(proxyMessage);
        } else if (IdleStateEvent.READER_IDLE_STATE_EVENT == evt) {
            EchoLogger.getLogger().info("read timeout,close channel");
            ctx.channel().close();
            EchoLogger.getLogger().info("the cmd channel lost,restart client");
        }

        super.channelIdle(ctx, evt);

    }
}
