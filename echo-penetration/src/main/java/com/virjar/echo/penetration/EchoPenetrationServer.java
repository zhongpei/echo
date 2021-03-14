package com.virjar.echo.penetration;

import com.virjar.echo.penetration.portal.PortalServer;
import com.virjar.echo.penetration.service.PenetrationService;
import com.virjar.echo.penetration.service.PenetrationTaskLoadManager;
import com.virjar.echo.penetration.service.PortResourceManager;
import com.virjar.echo.server.common.eventbus.EventBusManager;
import com.virjar.echo.server.common.upstream.NatMappingUpstreamService;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Builder
public class EchoPenetrationServer {

    /**
     * 配置服务器启动端口（simple http server）
     */
    @Getter
    private final int cmdHttpPort;

    /**
     * 代理端用映射配置。"20000-25000:30000:35000-40000",
     * 可以有具体端口，或者端口范围两种。多个配置使用冒号分割。
     * 最终代理服务会暴露在这些端口上
     */
    private final String mappingSpace;

    /**
     * 服务器id，分布式环境下，本台服务器的业务id
     */
    @Getter
    private final String serverId;

    private final boolean debugMode;

    @Getter
    private String apiEntry;


    private final AtomicBoolean started = new AtomicBoolean(false);


    private NatMappingUpstreamService natMappingUpstreamService;

    private PenetrationService penetrationService;


    private PenetrationTaskLoadManager penetrationTaskLoadManager;

    private EventBusManager eventBusManager;

    private PortResourceManager portResourceManager;
    private PortalServer portalServer;

    private String taskDownloadUrl;


    public void startUp() {
        if (started.compareAndSet(false, true)) {
            startUpInternal();
        }
    }


    private void trimApiEntry() {
        apiEntry = apiEntry.trim();
        if (apiEntry.endsWith("/")) {
            apiEntry = apiEntry.substring(0, apiEntry.length() - 1);
        }
    }

    private void startUpInternal() {
        if (StringUtils.isBlank(apiEntry)) {
            throw new IllegalStateException("apiEntry url can not be empty!!");
        }
        trimApiEntry();


        if (StringUtils.isBlank(serverId)) {
            throw new IllegalStateException("serverId can not be empty!!");
        }

        // 端口资源分发器
        portResourceManager = new PortResourceManager(mappingSpace);


        //nat链接端
        natMappingUpstreamService = new NatMappingUpstreamService();

        //连接meta server，启动代理服务
//        penetrationService = new PenetrationTaskLoadManager(apiEntry, serverId)
//                .addLoadEventCallback(this::doConnectUpstream);

        //http服务，运维管理功能和代理资源拉取接口
        portalServer = new PortalServer(this).startHttpServer();

        // 事件总线管理器，通过他进行各个节点的准时时事通信，这个模块使得分布式环境下各个资源的状态同步更加快速
        // 而不是第一版的每个组件都依靠 30s的定时任务
        eventBusManager = new EventBusManager(serverId, apiEntry)
                .enableEventReceiveService(portalServer.getEchoHttpCommandServer())
                // .registerEventHandler(upStreamResourceLoadManager)
                .startup();

    }
}
