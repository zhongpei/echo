package com.virjar.echo.penetration.service;

import com.virjar.echo.server.common.SimpleHttpInvoker;
import io.netty.util.concurrent.DefaultThreadFactory;
import lombok.extern.slf4j.Slf4j;

import java.net.URLEncoder;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
public class PenetrationTaskLoadManager {
    private final String serverId;
    private final String apiEntry;

    private final ScheduledExecutorService proxyUpstreamScanScheduler =
            Executors.newScheduledThreadPool(1, new DefaultThreadFactory("penetration-task-download"));

    public PenetrationTaskLoadManager(String serverId, String apiEntry) {
        this.serverId = serverId;
        this.apiEntry = apiEntry;
        startPenetrationLoadTask();
    }

    private void startPenetrationLoadTask() {
        proxyUpstreamScanScheduler.scheduleAtFixedRate(() -> {
            try {
                doPenetrationLoad();
            } catch (Exception e) {
                log.error("doPenetrationLoad error", e);
            }
        }, 2, 30, TimeUnit.SECONDS);
    }

    private void doPenetrationLoad() {
        String url = apiEntry + "/echo-api/penetration/allocatePenetrationMappingTask?" +
                "penetrationServerId=" + URLEncoder.encode(serverId) + "&size=30";
        log.info("penetration task load url:{}", url);
        String response = SimpleHttpInvoker.get(url);
        log.info("penetration task load response:{}", response);


    }
}
