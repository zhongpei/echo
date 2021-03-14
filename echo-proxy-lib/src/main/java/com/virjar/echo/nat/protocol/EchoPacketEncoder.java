package com.virjar.echo.nat.protocol;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;

public class EchoPacketEncoder extends MessageToByteEncoder<EchoPacket> {


    @Override
    protected void encode(ChannelHandlerContext ctx, EchoPacket msg, ByteBuf out) {

        int bodyLength = PacketCommon.TYPE_SIZE + PacketCommon.SERIAL_NUMBER_SIZE + PacketCommon.EXTRA_LENGTH_SIZE + PacketCommon.TRACE_ID_LENGTH_SIZE;

        byte[] extraBytes = null;
        if (msg.getExtra() != null) {
            extraBytes = msg.getExtra().getBytes();
            if (extraBytes.length > 127) {
                throw new IllegalStateException("too length extra data: " + msg.getExtra());
            }
            bodyLength += extraBytes.length;
        }

        byte[] traceIDBytes = null;
        if (msg.getTraceID() != null) {
            traceIDBytes = msg.getTraceID().getBytes();
            if (traceIDBytes.length > 127) {
                throw new IllegalStateException("too long traceID data: " + msg.getTraceID());
            }
            bodyLength += traceIDBytes.length;
        }

        if (msg.getData() != null) {
            bodyLength += msg.getData().length;
        }

        //避免无效的攻击
        out.writeLong(PacketCommon.magic);
        out.writeInt(bodyLength);

        out.writeByte(msg.getType());
        out.writeLong(msg.getSerialNumber());

        if (extraBytes != null) {
            out.writeByte((byte) extraBytes.length);
            out.writeBytes(extraBytes);
        } else {
            out.writeByte((byte) 0x00);
        }

        if (traceIDBytes != null) {
            out.writeByte((byte) traceIDBytes.length);
            out.writeBytes(traceIDBytes);
        } else {
            out.writeByte((byte) 0x00);
        }

        if (msg.getData() != null) {
            out.writeBytes(msg.getData());
        }

    }
}
