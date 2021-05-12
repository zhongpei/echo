package com.virjar.echo.hproxy;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.google.common.collect.Maps;
import com.virjar.echo.hproxy.portal.PortalServer;
import com.virjar.echo.hproxy.service.AuthConfigManager;
import com.virjar.echo.hproxy.service.HttpProxyService;
import com.virjar.echo.hproxy.service.ProxyPortAllocator;
import com.virjar.echo.hproxy.service.UpStreamResourceLoadManager;
import com.virjar.echo.server.common.NatUpstreamMeta;
import com.virjar.echo.server.common.NettyUtils;
import com.virjar.echo.server.common.auth.BasicAuthenticator;
import com.virjar.echo.server.common.auth.IAuthenticator;
import com.virjar.echo.server.common.auth.NoneAuthenticator;
import com.virjar.echo.server.common.eventbus.ComponentEvent;
import com.virjar.echo.server.common.eventbus.EventBusManager;
import com.virjar.echo.server.common.upstream.NatMappingUpstreamService;
import com.virjar.echo.server.common.upstream.ProxyNode;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Builder
public class EchoHttpProxyServer {

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

    /**
     * meta服务器链接地址，可以指向多个。代理服务向meta请求NatMapping的资源数据，
     * 然后在本地开启代理服务
     */
    private final String mappingServerUrls;

    /**
     * 授权配置下载接口
     */
    private final String authInfoLoadUrl;

    private final boolean debugMode;

    @Getter
    private final String apiEntry;

    private final AtomicBoolean started = new AtomicBoolean(false);

    private ProxyPortAllocator proxyPortAllocator;

    private UpStreamResourceLoadManager upStreamResourceLoadManager;

    private HttpProxyService httpProxyService;

    private NatMappingUpstreamService natMappingUpstreamService;

    private PortalServer portalServer;

    @Getter
    private AuthConfigManager authConfigManager;

    @Getter
    private EventBusManager eventBusManager;

    public void startUp() {
        if (started.compareAndSet(false, true)) {
            startUpInternal();
        }
    }

    private void startUpInternal() {
        if (StringUtils.isBlank(mappingServerUrls)) {
            throw new IllegalStateException("meta server url can not be empty!!");
        }

        if (StringUtils.isBlank(serverId)) {
            throw new IllegalStateException("serverId can not be empty!!");
        }

        // 端口资源分发器
        proxyPortAllocator = new ProxyPortAllocator(mappingSpace);

        // 代理 bootstrap构建
        httpProxyService = new HttpProxyService(this);

        //nat链接端
        natMappingUpstreamService = new NatMappingUpstreamService();

        //连接meta server，启动代理服务
        upStreamResourceLoadManager = new UpStreamResourceLoadManager(mappingServerUrls, serverId)
                .addLoadEventCallback(this::doConnectUpstream);

        //http服务，运维管理功能和代理资源拉取接口
        portalServer = new PortalServer(this).startHttpServer();

        // 事件总线管理器，通过他进行各个节点的准时时事通信，这个模块使得分布式环境下各个资源的状态同步更加快速
        // 而不是第一版的每个组件都依靠 30s的定时任务
        eventBusManager = new EventBusManager(serverId, apiEntry)
                .enableEventReceiveService(portalServer.getEchoHttpCommandServer())
                .registerEventHandler(upStreamResourceLoadManager)
                .startup();

        //鉴权配置同步
        config4Auth();
    }


    private void config4Auth() {
        IAuthenticator authenticator;
        if (StringUtils.isBlank(authInfoLoadUrl)) {
            authenticator = new NoneAuthenticator();
        } else {
            authenticator = new BasicAuthenticator();
        }
        authConfigManager = new AuthConfigManager(authenticator, authInfoLoadUrl);
        authConfigManager.schedulerRefreshAuthConfig();
    }


    private void doConnectUpstream(NatUpstreamMeta natUpstreamMeta) {
        final ProxyNode proxyNode = createProxyNodeFromMappingNode(natUpstreamMeta);
        if (proxyNode == null) {
            //exist already or port allocate failed
            return;
        }
        log.info("begin to startup http proxy server on upstream ->{} with clientId:{}  with httpProxyPort:{}",
                natUpstreamMeta.getListenIp() + ":" + natUpstreamMeta.getPort()
                , proxyNode.getClientId(), proxyNode.getProxyPort()
        );
        // create heath check connection to upstream, we can auto close proxy server when upstream lose connection
        natMappingUpstreamService.createNatMappingHealthCheckConnection(proxyNode, new NatMappingUpstreamService.HeathCheckConnectionCallback() {
            @Override
            public void onHeathCheckConnectionCreateFailed() {
                onProxyNodeDisconnect(proxyNode);
            }

            @Override
            public void onHeathCheckConnectionCreateSuccess(Channel healthCheckChannel) {
                //shutdown proxy server if HeathCheck channel lose connection
                healthCheckChannel.closeFuture().addListener(future -> {
                    onProxyNodeDisconnect(proxyNode);
                });
                httpProxyService.startHttpProxyServer(EchoHttpProxyServer.this, proxyNode, () -> onProxyNodeDisconnect(proxyNode));
            }
        });

    }

    private final Map<String, ProxyNode> allProxyNode = Maps.newConcurrentMap();

    private ProxyNode createProxyNodeFromMappingNode(NatUpstreamMeta natUpstreamMeta) {
        String clientId = natUpstreamMeta.getClientId();
        ProxyNode proxyNode = allProxyNode.get(clientId);
        if (proxyNode != null) {
            if (proxyNode.getNatUpstreamMeta().getPort() == natUpstreamMeta.getPort()) {
                log.info("client: {} online already,proxyNode:{}", proxyNode.getClientId(), proxyNode);
                return null;
            } else {
                log.info("originPoxyNode not active, disconnect it,proxyNode:{}", proxyNode);
                onProxyNodeDisconnect(proxyNode);
            }
        }
        proxyNode = new ProxyNode();
        proxyNode.setClientId(clientId);
        proxyNode.setNatUpstreamMeta(natUpstreamMeta);

        int port = proxyPortAllocator.allocateOne(clientId);
        if (port < 100) {
            log.error("can not allocate http proxy port resource for client:{}", natUpstreamMeta.getClientId());
            return null;
        }
        proxyNode.setProxyPort(port);
        proxyNode.setCreateTimestamp(System.currentTimeMillis());
        allProxyNode.put(clientId, proxyNode);
        return proxyNode;
    }

    private void onProxyNodeDisconnect(ProxyNode proxyNode) {
        NettyUtils.closeChannelIfActive(proxyNode.getProxyServerChannel());
        NettyUtils.closeChannelIfActive(proxyNode.getNatMappingHealthChannel());
        allProxyNode.remove(proxyNode.getClientId());
        proxyPortAllocator.returnResource(proxyNode.getClientId());

        ComponentEvent hProxyOfflineEvent = ComponentEvent.createHProxyOfflineEvent(proxyNode.toVo());
        eventBusManager.pushEvent(hProxyOfflineEvent);

    }


    public ChannelFuture connectToNatMapping(ProxyNode proxyNode, String hostAndPort, String echoTrace) {
        return natMappingUpstreamService.connectToNatMapping(proxyNode, hostAndPort, echoTrace);
    }

    public JSONObject generateProxyListJson() {
        JSONObject ret = new JSONObject();
        JSONArray jsonArray = new JSONArray(allProxyNode.values().size());
        ret.put("proxies", jsonArray);
        for (ProxyNode proxyNode : allProxyNode.values()) {
            jsonArray.add(proxyNode.toVo());
        }
        ret.put("status", 0);
        return ret;
    }
}
