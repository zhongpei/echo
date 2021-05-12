package com.virjar.echo.nat.server.echo;

import com.alibaba.fastjson.JSONObject;
import com.google.common.collect.Maps;
import com.virjar.echo.nat.protocol.EchoPacket;
import com.virjar.echo.nat.protocol.PacketCommon;
import com.virjar.echo.nat.server.EchoNatServer;
import com.virjar.echo.nat.server.EchoServerConstant;
import com.virjar.echo.nat.server.EchoTuningExtra;
import com.virjar.echo.server.common.hserver.NanoUtil;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.util.AttributeKey;
import io.netty.util.concurrent.DefaultPromise;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * 到终端的远端控制命令管理器
 */
@Slf4j
public class EchoRemoteControlManager {

    private EchoNatServer echoNatServer;

    public EchoRemoteControlManager(EchoNatServer echoNatServer) {
        this.echoNatServer = echoNatServer;
    }

    private static final AttributeKey<Map<Long, DefaultPromise<EchoPacket>>> resultPromise = AttributeKey.newInstance("control_ack_promise");

    public void handleReceiveRemoteControlMessage(Channel echoNatChannel, EchoPacket echoPacket) {
        if (echoPacket == null || echoPacket.getType() != PacketCommon.C_TYPE_CONTROL) {
            log.error("not control response message");
            return;
        }
        Map<Long, DefaultPromise<EchoPacket>> promiseMap = echoNatChannel.attr(resultPromise).get();
        if (promiseMap == null) {
            log.warn("not promiseMap bind for handleReceiveRemoteControlMessage,error client message???");
            return;
        }
        DefaultPromise<EchoPacket> future = promiseMap.remove(echoPacket.getSerialNumber());
        if (future == null) {
            String clientId = echoNatChannel.attr(EchoServerConstant.NAT_CHANNEL_CLIENT_KEY).get();
            log.warn("no future bound for control message ,the invoke session maybe timeout clientId:{} seq:{} response:{}",
                    clientId, echoPacket.getSerialNumber(), new String(echoPacket.getData()));
            return;
        }

        future.setSuccess(echoPacket);
    }

    public JSONObject sendRemoteControlMessage(String clientId, String controlAction, String additionParam) {
        EchoTuningExtra echoTuningExtra = getEchoTuningExtra(clientId);
        if (echoTuningExtra == null) {
            log.info("client offline");
            return NanoUtil.failed(-1, "client offline");
        }
        Channel echoNatChannel = echoTuningExtra.getEchoNatChannel();
        if (!echoNatChannel.isActive()) {
            log.info("client offline");
            return NanoUtil.failed(-1, "client offline");
        }
        Long seq = echoTuningExtra.nextSeq();

        EchoPacket echoPacket = new EchoPacket();
        echoPacket.setType(PacketCommon.C_TYPE_CONTROL);
        echoPacket.setExtra(controlAction);
        if (StringUtils.isNotBlank(additionParam)) {
            echoPacket.setData(additionParam.getBytes(StandardCharsets.UTF_8));
        }
        echoPacket.setSerialNumber(seq);
        echoNatChannel.writeAndFlush(echoPacket).addListener((ChannelFutureListener) channelFuture -> {
            if (!channelFuture.isSuccess()) {
                Map<Long, DefaultPromise<EchoPacket>> promiseMap = echoNatChannel.attr(resultPromise).get();
                DefaultPromise<EchoPacket> future = promiseMap.remove(echoPacket.getSerialNumber());
                if (future != null) {
                    future.setFailure(channelFuture.cause());
                } else {
                    log.error("can not get invoke record:{}", seq);
                }

            }
        });


        Map<Long, DefaultPromise<EchoPacket>> promiseMap = echoNatChannel.attr(resultPromise).get();
        if (promiseMap == null) {
            synchronized (this) {
                promiseMap = echoNatChannel.attr(resultPromise).get();
                if (promiseMap == null) {
                    promiseMap = Maps.newConcurrentMap();
                    echoNatChannel.attr(resultPromise).set(promiseMap);
                }
            }
        }
        int timeout = 15;

        DefaultPromise<EchoPacket> future = new DefaultPromise<>(echoNatChannel.eventLoop());
        // make sure the future return after timeout
        Map<Long, DefaultPromise<EchoPacket>> finalPromiseMap = promiseMap;
        echoNatChannel.eventLoop().schedule(() -> {
            if (future.isDone()) {
                return;
            }
            finalPromiseMap.remove(echoPacket.getSerialNumber());
            future.setFailure(new RuntimeException("invoke timeout"));
        }, timeout, TimeUnit.SECONDS);

        promiseMap.put(echoPacket.getSerialNumber(), future);
        EchoPacket echoPacketResponse;
        try {
            echoPacketResponse = future.get(timeout + 1, TimeUnit.SECONDS);
        } catch (ExecutionException exe) {
            Throwable cause = exe.getCause();
            log.warn("wait future failed", exe);
            return NanoUtil.failed(-1, cause.getMessage());
        } catch (Exception e) {
            log.warn("wait future failed", e);
            return NanoUtil.failed(-1, e.getMessage());
        }

        if (echoPacketResponse == null) {
            return NanoUtil.failed(-1, "system error,empty response from client");
        }


        String statusAndResponse = new String(echoPacketResponse.getData());
        JSONObject ret = new JSONObject();
        int i = statusAndResponse.indexOf(":");
        if (i <= 0) {
            ret.put("data", statusAndResponse);
        } else {
            String status = statusAndResponse.substring(0, i);
            String response = statusAndResponse.substring(i + 1);
            if (StringUtils.equalsIgnoreCase(status, "OK")) {
                ret.put("status", 0);
                ret.put("data", response);
            } else {
                ret.put("status", -1);
                ret.put("msg", response);
            }
        }
        return ret;
    }

    private EchoTuningExtra getEchoTuningExtra(String clientId) {
        CompletableFuture<EchoTuningExtra> completableFuture = new CompletableFuture<>();
        echoNatServer.queryConnectionNodeV2(clientId, value -> {
            completableFuture.complete(value);
        });

        EchoTuningExtra echoTuningExtra = null;
        try {
            echoTuningExtra = completableFuture.get();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (ExecutionException e) {
            e.printStackTrace();
        }
        return echoTuningExtra;
    }


}
