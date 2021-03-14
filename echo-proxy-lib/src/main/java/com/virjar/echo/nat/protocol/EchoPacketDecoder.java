package com.virjar.echo.nat.protocol;

import com.virjar.echo.nat.log.EchoLogger;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;

import java.util.List;

public class EchoPacketDecoder extends ByteToMessageDecoder {

    private static final byte HEADER_SIZE = 12;

    private int frameLength = Integer.MAX_VALUE;
    private boolean consumeHeader = false;

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        EchoLogger.getLogger().info("decode data for: " + ctx.channel());
        while (true) {
            doConsumeHeader(ctx, in);
            if (!consumeHeader) {
                return;
            }
            if (in.readableBytes() < frameLength) {
                EchoLogger.getLogger().info("not enough data received: readableBytes->" + in.readableBytes()
                        + " frameLength->" + frameLength
                );
                return;
            }
            consumeHeader = false;
            try {
                EchoPacket echoPacket = packetOne(ctx, in);
                if (echoPacket != null) {
                    out.add(echoPacket);
                }
            } catch (Exception e) {
                EchoLogger.getLogger().error("message decode failed for channel: " + ctx.channel(), e);
                ctx.channel().close();
            }

        }
    }

    private void doConsumeHeader(ChannelHandlerContext ctx, ByteBuf in) {
        if (consumeHeader) {
            return;
        }
        if (in.readableBytes() < HEADER_SIZE) {
            return;
        }
        long magic = in.readLong();
        if (magic != PacketCommon.magic) {
            ctx.close();
            EchoLogger.getLogger().error("package not start with legal magic: " + magic + " expected:" + PacketCommon.magic);
            return;
        }
        frameLength = in.readInt();
        consumeHeader = true;
    }

    private EchoPacket packetOne(ChannelHandlerContext ctx, ByteBuf in) {
        EchoPacket echoPacket = new EchoPacket();
        byte type = in.readByte();
        long sn = in.readLong();

        echoPacket.setSerialNumber(sn);
        echoPacket.setType(type);

        byte extraByteLength = in.readByte();
        byte[] extraBytes = new byte[extraByteLength];
        in.readBytes(extraBytes);
        echoPacket.setExtra(new String(extraBytes));

        byte traceIdByteLength = in.readByte();
        byte[] traceIdBytes = new byte[traceIdByteLength];
        in.readBytes(traceIdBytes);
        echoPacket.setTraceID(new String(traceIdBytes));


        int dataLength = frameLength - PacketCommon.TYPE_SIZE - PacketCommon.SERIAL_NUMBER_SIZE - PacketCommon.EXTRA_LENGTH_SIZE - PacketCommon.TRACE_ID_LENGTH_SIZE - extraByteLength - traceIdByteLength;
        if (dataLength < 0) {
            EchoLogger.getLogger().error("message protocol error,negative data length:" + dataLength + " for channel: " + ctx.channel()
                    + " frameLength: " + frameLength + " type:" + type + " serial_number:" + sn + " uriLength:" + extraByteLength + " extra:" + echoPacket.getExtra()
            );
            ctx.channel().close();
            return null;
        }
        if (dataLength > 0) {
            byte[] data = new byte[dataLength];
            in.readBytes(data);
            echoPacket.setData(data);
        }
        return echoPacket;
    }


}
