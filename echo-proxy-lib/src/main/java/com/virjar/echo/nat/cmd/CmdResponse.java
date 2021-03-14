package com.virjar.echo.nat.cmd;


import com.virjar.echo.nat.protocol.EchoPacket;
import com.virjar.echo.nat.protocol.PacketCommon;

import java.nio.charset.StandardCharsets;

import io.netty.channel.Channel;

public class CmdResponse {
    private final long req;
    private final Channel natChannel;
    private boolean respond = false;

    public CmdResponse(long req, Channel natChannel) {
        this.req = req;
        this.natChannel = natChannel;
    }

    public static final String STATUS_OK = "OK";
    public static final String STATUS_FAILED = "FAILED";

    public void success(String response) {
        ack(STATUS_OK, response);
    }

    public void failed(String errorMessage) {
        ack(STATUS_FAILED, errorMessage);
    }

    public synchronized void ack(String status, String response) {
        if (respond) {
            return;
        }
        respond = true;
        EchoPacket ackMessage = new EchoPacket();
        ackMessage.setSerialNumber(req);
        ackMessage.setType(PacketCommon.C_TYPE_CONTROL);
        String totalResponse = status;
        if (response != null) {
            totalResponse = totalResponse + ":" + response;
        }
        ackMessage.setData(totalResponse.getBytes(StandardCharsets.UTF_8));
        natChannel.writeAndFlush(ackMessage);
    }

}
