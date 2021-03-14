package com.virjar.echo.meta.server.service;

import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.google.common.collect.Lists;
import com.virjar.echo.meta.server.entity.DownstreamServer;
import com.virjar.echo.meta.server.entity.NatMappingServer;
import com.virjar.echo.meta.server.mapper.DownstreamServerMapper;
import com.virjar.echo.meta.server.mapper.NatMappingServerMapper;
import com.virjar.echo.meta.server.utils.IPUtils;
import com.virjar.echo.server.common.NatUpstreamMeta;
import com.virjar.echo.server.common.SimpleHttpInvoker;
import com.virjar.echo.server.common.eventbus.ComponentEvent;
import lombok.extern.slf4j.Slf4j;
import org.joda.time.DateTime;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;

@Service
@Slf4j
public class EventBusService {
    @Resource
    private ProxyResourceService proxyResourceService;

    @Resource
    private ClientInfoService clientInfoService;

    private final LinkedBlockingQueue<ComponentEvent> blockingQueue = new LinkedBlockingQueue<>();

    private final Thread broadcastThread = new Thread("broadcast") {
        @Override
        public void run() {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    doBroadcast(blockingQueue.take());
                } catch (Exception e) {
                    log.error("error", e);
                }
            }
        }
    };

    @PostConstruct
    public void init() {
        broadcastThread.setDaemon(false);
        broadcastThread.start();
    }

    public void receiveEvent(String remoteHost, ComponentEvent componentEvent) {
        switch (componentEvent.getEventType()) {
            case ComponentEvent.TYPE_NAT_CLIENT_ONLINE:
                clientInfoService.onOneNatUpstreamResourceOnline(componentEvent.getFromServerId(), extractEchoNatUpStreamMessage(remoteHost, componentEvent));
                break;
            case ComponentEvent.TYPE_NAT_CLIENT_OFFLINE:
                clientInfoService.onOneNatUpstreamResourceOffline(componentEvent.getFromServerId(), extractEchoNatUpStreamMessage(remoteHost, componentEvent));
                break;
            case ComponentEvent.TYPE_HTTP_PROXY_ONLINE:
                proxyResourceService.flushProxyResource(componentEvent.getData(), componentEvent.getFromServerId(), remoteHost);
                break;
            case ComponentEvent.TYPE_HTTP_PROXY_OFFLINE:
                proxyResourceService.offlineProxyResource(componentEvent.getData(), componentEvent.getFromServerId());
                break;
            default:
                log.warn("unknown event type:{} data:{}", componentEvent.getEventType(), JSONObject.toJSONString(componentEvent));


        }
        if (componentEvent.isBroadcast()) {
            broadcast(componentEvent);
        }
    }

    @Resource
    private NatMappingServerMapper natMappingServerMapper;

    @Resource
    private DownstreamServerMapper downstreamServerMapper;

    public void broadcast(ComponentEvent componentEvent) {
        blockingQueue.offer(componentEvent);
    }

    private void doBroadcast(ComponentEvent componentEvent) {
        List<String> notifyUrls = Lists.newArrayList();
        List<NatMappingServer> natMappingServers = natMappingServerMapper.selectList(new QueryWrapper<NatMappingServer>().eq(NatMappingServer.ENABLED, true)
                .isNotNull(NatMappingServer.SERVER_ID)
                .gt(NatMappingServer.ALIVE_TIME, DateTime.now().minusMinutes(10)
                        .toDate())
        );
        for (NatMappingServer natMappingServer : natMappingServers) {
            if (componentEvent.getFromServerId().equals(natMappingServer.getServerId())) {
                continue;
            }
            notifyUrls.add(natMappingServer.getApiBaseUrl());
        }

        List<DownstreamServer> downstreamServers = downstreamServerMapper.selectList(new QueryWrapper<DownstreamServer>().eq(DownstreamServer.ENABLED, true)
                .isNotNull(DownstreamServer.SERVER_ID)
                .gt(DownstreamServer.ALIVE_TIME, DateTime.now().minusMinutes(10)
                        .toDate()));
        for (DownstreamServer downstreamServer : downstreamServers) {
            if (componentEvent.getFromServerId().equals(downstreamServer.getServerId())) {
                continue;
            }
            notifyUrls.add(downstreamServer.getApiBaseUrl());
        }

        JSONObject body = (JSONObject) JSONObject.toJSON(componentEvent);
        String logBody = body.toJSONString();
        for (String baseUrl : notifyUrls) {
            String url = baseUrl.trim();
            if (url.endsWith("/")) {
                url = url + "broadcastEvent";
            } else {
                url = url + "/broadcastEvent";
            }
            log.info("do event push with url:{} event:{}", url, logBody);
            String response = SimpleHttpInvoker.post(url, body);
            log.info("event push response:{}", response);
        }
    }


    private NatUpstreamMeta extractEchoNatUpStreamMessage(String remoteHost, ComponentEvent componentEvent) {
        NatUpstreamMeta natUpstreamMeta = componentEvent.getData().toJavaObject(NatUpstreamMeta.class);
        if (IPUtils.isLocalHost(natUpstreamMeta.getListenIp())) {
            natUpstreamMeta.setListenIp(remoteHost);
        }
        return natUpstreamMeta;
    }

}
