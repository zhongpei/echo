package com.virjar.echo.nat.server;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.google.common.base.Preconditions;
import com.virjar.echo.nat.protocol.EchoPacketDecoder;
import com.virjar.echo.nat.protocol.EchoPacketEncoder;
import com.virjar.echo.nat.server.echo.EchoRemoteControlManager;
import com.virjar.echo.nat.server.echo.PortResourceManager;
import com.virjar.echo.nat.server.handlers.MappingChannelHandler;
import com.virjar.echo.nat.server.handlers.NatChannelHandler;
import com.virjar.echo.nat.server.handlers.ServerIdleCheckHandler;
import com.virjar.echo.nat.server.portal.ConfigPortal;
import com.virjar.echo.server.common.eventbus.EventBusManager;
import com.virjar.echo.server.common.safethread.Looper;
import com.virjar.echo.server.common.safethread.ValueCallback;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.util.concurrent.DefaultThreadFactory;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Builder
public class EchoNatServer {

    /**
     * 服务器本身启动的端口
     */
    @Getter
    private final int natPort;

    /**
     * 配置服务器启动端口（simple http server）
     */
    @Getter
    private final int cmdHttpPort;

    /**
     * 客户设备网络在本地映射端口配置。"20000-25000:30000:35000-40000",
     * 可以有具体端口，或者端口范围两种。多个配置使用冒号分割
     */
    private final String mappingSpace;

    /**
     * 服务器id，分布式环境下，本台服务器的业务id
     */
    @Getter
    private final String serverId;

    @Getter
    private String localHost;

    @Getter
    private final String apiEntry;

    @Getter
    private PortResourceManager portResourceManager;

    @Getter
    private ServerBootstrap mappingBootstrap;

    @Getter
    private EchoRemoteControlManager echoRemoteControlManager;

    @Getter
    private EventBusManager eventBusManager;

    private ConfigPortal configPortal;

    private final AtomicBoolean started = new AtomicBoolean(false);

    private final Map<String, EchoTuningExtra> connectionAdditionInfoMap = new ConcurrentHashMap<>();

    private final Object connectionAdditionInfoMapOpLock = new Object();

    @Getter
    private Looper looper;

    @Deprecated
    public void registerConnectionInfo(EchoTuningExtra echoTuningExtra) {
        synchronized (connectionAdditionInfoMapOpLock) {
            connectionAdditionInfoMap.put(echoTuningExtra.getClientId(), echoTuningExtra);
        }
    }

    @Deprecated
    public void unregisterConnectionInfo(EchoTuningExtra echoTuningExtra) {
        synchronized (connectionAdditionInfoMapOpLock) {
            EchoTuningExtra remove = connectionAdditionInfoMap.remove(echoTuningExtra.getClientId());
            if (remove != null) {
                if (!remove.getEchoNatChannel().equals(echoTuningExtra.getEchoNatChannel())
                        && remove.getMappingServerChannel().isActive()) {
                    // 极短时间内，又注册上来了。此时取消remove
                    connectionAdditionInfoMap.put(echoTuningExtra.getClientId(), echoTuningExtra);
                }
            }
        }
    }

    @Deprecated
    public JSONObject generateConnectionInfo() {
        JSONObject ret = new JSONObject();
        JSONArray jsonArray = new JSONArray(connectionAdditionInfoMap.size());
        ret.put("clients", jsonArray);
        for (EchoTuningExtra echoTuningExtra : connectionAdditionInfoMap.values()) {
            jsonArray.add(echoTuningExtra.toVo());
        }
        return ret;
    }

    @Deprecated
    public EchoTuningExtra queryConnectionNode(String clientId) {
        return connectionAdditionInfoMap.get(clientId);
    }

    public void registerConnectionInfoV2(EchoTuningExtra echoTuningExtra) {
        if (!looper.inLooper()) {
            looper.post(() -> registerConnectionInfoV2(echoTuningExtra));
            return;
        }
        connectionAdditionInfoMap.put(echoTuningExtra.getClientId(), echoTuningExtra);
    }

    public void unregisterConnectionInfoV2(EchoTuningExtra echoTuningExtra) {
        if (!looper.inLooper()) {
            looper.post(() -> unregisterConnectionInfoV2(echoTuningExtra));
            return;
        }
        EchoTuningExtra remove = connectionAdditionInfoMap.remove(echoTuningExtra.getClientId());
        log.info("unregisterConnectionInfo successfully,clientId:", echoTuningExtra.getClientId());
        // 使用looper之后，这里貌似可以放心remove
        if (remove != null) {
            if (!remove.getEchoNatChannel().equals(echoTuningExtra.getEchoNatChannel())
                    && remove.getMappingServerChannel().isActive()) {
                // 极短时间内，又注册上来了。此时取消remove
                connectionAdditionInfoMap.put(echoTuningExtra.getClientId(), echoTuningExtra);
            }
        }
    }

    public void generateConnectionInfoV2(ValueCallback<JSONObject> valueCallback) {
        if (!looper.inLooper()) {
            looper.post(() -> generateConnectionInfoV2(valueCallback));
            return;
        }
        JSONObject ret = new JSONObject();
        JSONArray jsonArray = new JSONArray(connectionAdditionInfoMap.size());
        ret.put("clients", jsonArray);
        for (EchoTuningExtra echoTuningExtra : connectionAdditionInfoMap.values()) {
            jsonArray.add(echoTuningExtra.toVo());
        }
        valueCallback.onReceiveValue(ret);
        return;
    }

    public void queryConnectionNodeV2(String clientId, ValueCallback<EchoTuningExtra> valueCallback) {
        if (!looper.inLooper()) {
            looper.post(() -> queryConnectionNodeV2(clientId, valueCallback));
            return;
        }
        EchoTuningExtra echoTuningExtra = connectionAdditionInfoMap.get(clientId);
        valueCallback.onReceiveValue(echoTuningExtra);
    }

    public void startUp() {
        if (started.compareAndSet(false, true)) {
            startUpInternal();
        }
    }

    private void startUpInternal() {

        // first check config
        if (portResourceManager == null) {
            Preconditions.checkNotNull(mappingSpace, "need portSpace parameter");
            portResourceManager = new PortResourceManager(mappingSpace);
        }

        if (natPort < 1000) {
            throw new IllegalStateException("illegal natPort :" + natPort);
        }

        if (StringUtils.isBlank(localHost)) {
            localHost = "127.0.0.1";
        }

        if (StringUtils.isBlank(serverId)) {
            throw new IllegalStateException("serverId can not be empty!!");
        }

        this.looper = new Looper(this.getServerId());
        log.error("looper init successfully");

        // second startup NatServer
        ServerBootstrap natServerBootStrap = new ServerBootstrap();
        NioEventLoopGroup serverBossGroup =
                new NioEventLoopGroup(
                        0,
                        new DefaultThreadFactory(
                                "NatServer-boss-group" + DefaultThreadFactory.toPoolName(NioEventLoopGroup.class)));
        NioEventLoopGroup serverWorkerGroup =
                new NioEventLoopGroup(
                        0,
                        new DefaultThreadFactory(
                                "NatServer-worker-group"
                                        + DefaultThreadFactory.toPoolName(NioEventLoopGroup.class)));
        natServerBootStrap
                .group(serverBossGroup, serverWorkerGroup)
                .option(ChannelOption.SO_BACKLOG, 10)
                .option(ChannelOption.SO_REUSEADDR, true)
                .channel(NioServerSocketChannel.class)
                .childHandler(
                        new ChannelInitializer<SocketChannel>() {
                            @Override
                            public void initChannel(SocketChannel ch) {
                                ch.pipeline().addLast(new EchoPacketDecoder());
                                ch.pipeline().addLast(new EchoPacketEncoder());
                                ch.pipeline().addLast(new ServerIdleCheckHandler());
                                ch.pipeline().addLast(new NatChannelHandler(EchoNatServer.this));
                            }
                        });
        natServerBootStrap
                .bind(natPort)
                .addListener(
                        new GenericFutureListener<Future<? super Void>>() {
                            @Override
                            public void operationComplete(Future<? super Void> future) {
                                if (!future.isSuccess()) {
                                    log.error("NAT proxy server startUp Failed ", future.cause());
                                    started.set(false);
                                } else {
                                    log.info("start echo netty [NAT Proxy Server] server ,port:" + natPort);
                                }
                            }
                        });

        // third build a mapping bootstrap to open local port mapping which is used to accept server's
        // proxy request
        mappingBootstrap = new ServerBootstrap();
        serverBossGroup =
                new NioEventLoopGroup(
                        0,
                        new DefaultThreadFactory(
                                "NatMapping-boss-group"
                                        + DefaultThreadFactory.toPoolName(NioEventLoopGroup.class)));
        serverWorkerGroup =
                new NioEventLoopGroup(
                        0,
                        new DefaultThreadFactory(
                                "NatMapping-worker-group"
                                        + DefaultThreadFactory.toPoolName(NioEventLoopGroup.class)));
        mappingBootstrap
                .group(serverBossGroup, serverWorkerGroup)
                .option(ChannelOption.SO_BACKLOG, 10)
                .option(ChannelOption.SO_REUSEADDR, true)
                .channel(NioServerSocketChannel.class)
                .childHandler(
                        new ChannelInitializer<SocketChannel>() {
                            @Override
                            public void initChannel(SocketChannel ch) {
                                //                        ch.pipeline().addLast("test", new testMsgHandler());
                                ch.pipeline().addLast("decoder", new EchoPacketDecoder());
                                ch.pipeline().addLast("encoder", new EchoPacketEncoder());
                                ch.pipeline().addLast("handler", new MappingChannelHandler());
                            }
                        });

        // and finally start command http server
        configPortal = new ConfigPortal(this).startHttpServer();
        echoRemoteControlManager = new EchoRemoteControlManager(this);

        // event push manager
        eventBusManager =
                new EventBusManager(serverId, apiEntry)
                        .enableEventReceiveService(configPortal.getEchoHttpCommandServer())
                        .startup();
    }

    public ChannelFuture openMapping(Integer port) {
        return mappingBootstrap.bind(localHost, port);
    }
}
