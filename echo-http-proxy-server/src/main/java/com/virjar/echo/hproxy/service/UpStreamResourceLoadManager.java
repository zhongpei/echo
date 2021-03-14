package com.virjar.echo.hproxy.service;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONException;
import com.alibaba.fastjson.JSONObject;
import com.google.common.base.Function;
import com.google.common.base.Splitter;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.virjar.echo.server.common.NatUpstreamMeta;
import com.virjar.echo.server.common.SimpleHttpInvoker;
import com.virjar.echo.server.common.eventbus.ComponentEvent;
import com.virjar.echo.server.common.eventbus.EventHandler;
import io.netty.util.concurrent.DefaultThreadFactory;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.net.URLEncoder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

@Slf4j
public class UpStreamResourceLoadManager implements EventHandler {
    /**
     * meta服务器链接地址，可以指向多个。代理服务向meta请求NatMapping的资源数据，
     * 然后在本地开启代理服务
     */
    private final String mappingServerUrls;

    private final Set<UpstreamLoadEvent> upstreamLoadCallbacks = Sets.newCopyOnWriteArraySet();

    private final ScheduledExecutorService proxyUpstreamScanScheduler = Executors.newScheduledThreadPool(1, new DefaultThreadFactory("upstream-scan"));

    private String[] parsedMappingServerUrls;

    private final String serverId;

    public UpStreamResourceLoadManager(String mappingServerUrls, String serverId) {
        this.mappingServerUrls = mappingServerUrls;
        this.serverId = serverId;
        startUpstreamLoadTask();
    }

    @Override
    public void handleEvent(ComponentEvent componentEvent) {
        if (!ComponentEvent.TYPE_NAT_CLIENT_ONLINE.equals(componentEvent.getEventType())) {
            return;
        }

        String onlineClientId = componentEvent.getData().getString("clientId");
        if (loadedResources.contains(onlineClientId)) {
            // 曾经下线过的设备资源现在重新上线，此时立即进行资源刷新，成功之后可以马上上线代理ip
            // 而非等待定时任务完成刷新操作
            doUpstreamLoad();
        }

    }

    public interface UpstreamLoadEvent {
        void onUpStreamResourceLoad(NatUpstreamMeta natUpstreamMeta);
    }

    public UpStreamResourceLoadManager addLoadEventCallback(UpstreamLoadEvent upstreamLoadEvent) {
        upstreamLoadCallbacks.add(upstreamLoadEvent);
        return this;
    }

    public UpStreamResourceLoadManager removeLoadEventCallback(UpstreamLoadEvent upstreamLoadEvent) {
        upstreamLoadCallbacks.remove(upstreamLoadEvent);
        return this;
    }

    public void startUpstreamLoadTask() {
        parsedMappingServerUrls = Lists.newArrayList(
                Splitter.on('|').omitEmptyStrings().trimResults().split(mappingServerUrls)
        ).stream().map((Function<String, String>) input -> {
            if (input.endsWith("/")) {
                return input + "?serverId=" + URLEncoder.encode(serverId);
            }
            if (input.contains("?")) {
                return input + "&serverId=" + URLEncoder.encode(serverId);
            }
            return input + "?serverId=" + URLEncoder.encode(serverId);
        }).collect(Collectors.toList())
                .toArray(new String[]{});
        proxyUpstreamScanScheduler.scheduleAtFixedRate(() -> {
            try {
                doUpstreamLoad();
            } catch (Exception e) {
                log.error("doUpstreamConnect error", e);
            }
        }, 2, 30, TimeUnit.SECONDS);
    }

    private void doUpstreamLoad() {
        if (!resourceLoadLock.tryLock()) {
            return;
        }
        try {
            Map<String, NatUpstreamMeta> mappingNodeMap = loadConnectionMap();
            for (NatUpstreamMeta natUpstreamMeta : mappingNodeMap.values()) {
                log.info("handle natUpstreamMeta load event for client:{}", natUpstreamMeta.getClientId());
                for (UpstreamLoadEvent upstreamLoadEvent : upstreamLoadCallbacks) {
                    upstreamLoadEvent.onUpStreamResourceLoad(natUpstreamMeta);
                    loadedResources.add(natUpstreamMeta.getClientId());
                }
            }
        } finally {
            resourceLoadLock.unlock();
        }

    }

    private final Set<String> loadedResources = Sets.newConcurrentHashSet();

    private final ReentrantLock resourceLoadLock = new ReentrantLock();


    private Map<String, NatUpstreamMeta> loadConnectionMap() {
        // load resource
        ArrayListMultimap<String, NatUpstreamMeta> natMappingInfo = ArrayListMultimap.create();
        for (String metaURL : parsedMappingServerUrls) {
            log.info("get NatMapping Node resource from url:{}", metaURL);
            String jsonContent = SimpleHttpInvoker.get(metaURL);
            log.info("node resource: {}", jsonContent);
            if (StringUtils.isBlank(jsonContent)) {
                log.warn("download nat mapping info failed: {}", metaURL);
                continue;
            }
            //{"clients":[{"listenIp":"127.0.0.1","clientId":"clientId-virjar","port":24577},{"listenIp":"127.0.0.1","clientId":"clientId-virjar-test","port":24576}]}
            JSONObject jsonObject;
            try {
                jsonObject = JSONObject.parseObject(jsonContent);
            } catch (JSONException e) {
                log.warn("download nat mapping info failed: {} response: {}", metaURL, jsonContent);
                continue;
            }
            if (jsonObject == null) {
                log.warn("download nat mapping info failed: {} response: {}", metaURL, jsonContent);
                continue;
            }

            JSONArray clients = jsonObject.getJSONArray("clients");

            for (int i = 0; i < clients.size(); i++) {
                JSONObject clientJsonNode = clients.getJSONObject(i);
                NatUpstreamMeta natUpstreamMeta = clientJsonNode.toJavaObject(NatUpstreamMeta.class);
                natMappingInfo.put(natUpstreamMeta.getClientId(), natUpstreamMeta);
            }
        }
        //for every connect info array, choose one randomly
        Map<String, NatUpstreamMeta> ret = new HashMap<>();
        for (String clientId : natMappingInfo.keys()) {
            List<NatUpstreamMeta> natUpstreamMetas = natMappingInfo.get(clientId);
            if (natUpstreamMetas.isEmpty()) {
                continue;
            }
            if (natMappingInfo.size() == 1) {
                ret.put(clientId, natUpstreamMetas.iterator().next());
                continue;
            }
            ret.put(clientId, natUpstreamMetas.get(ThreadLocalRandom.current().nextInt(
                    natUpstreamMetas.size()
            )));
        }
        return ret;
    }
}
