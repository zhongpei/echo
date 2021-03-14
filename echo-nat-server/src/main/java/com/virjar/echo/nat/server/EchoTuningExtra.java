package com.virjar.echo.nat.server;

import com.alibaba.fastjson.JSONObject;
import io.netty.channel.Channel;
import lombok.Data;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.concurrent.atomic.AtomicLong;

@Data
public class EchoTuningExtra {
    private String additionAccount;
    private String clientId;
    private Integer port;
    private Channel echoNatChannel;
    private Channel mappingServerChannel;
    private String listenIp;
    /**
     * 客户端出口ip
     */
    private String clientOutIp;

    public EchoTuningExtra(String additionAccount, String clientId, Integer port, Channel echoNatChannel, Channel mappingServerChannel, String listenIp) {
        this.additionAccount = additionAccount;
        this.clientId = clientId;
        this.port = port;
        this.echoNatChannel = echoNatChannel;
        this.mappingServerChannel = mappingServerChannel;
        this.listenIp = listenIp;
        resolveClientOutIp();
    }

    private void resolveClientOutIp() {
        SocketAddress socketAddress = echoNatChannel.remoteAddress();
        if (!(socketAddress instanceof InetSocketAddress)) {
            // will not happen
            return;
        }
        InetSocketAddress inetSocketAddress = (InetSocketAddress) socketAddress;
        clientOutIp = inetSocketAddress.getHostName();
    }

    public Long nextSeq() {
        if (mappingServerChannel == null) {
            return null;
        }
        AtomicLong seq = mappingServerChannel.attr(EchoServerConstant.SEQ).get();
        if (seq == null) {
            return null;
        }
        return seq.incrementAndGet();
    }

    @Override
    public String toString() {
        return "EchoTuningExtra{" +
                "additionAccount='" + additionAccount + '\'' +
                ", clientId='" + clientId + '\'' +
                ", port=" + port +
                ", listenIp='" + listenIp + '\'' +
                '}';
    }

    public JSONObject toVo() {
        JSONObject additionInfoJson = new JSONObject();
        additionInfoJson.put("additionAccount", getAdditionAccount());
        additionInfoJson.put("clientId", getClientId());
        additionInfoJson.put("port", getPort());
        additionInfoJson.put("listenIp", getListenIp());
        additionInfoJson.put("clientOutIp", getClientOutIp());
        return additionInfoJson;
    }
}
