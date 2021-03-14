package com.virjar.echo.hproxy.service;

import com.virjar.echo.hproxy.EchoHttpProxyServer;
import com.virjar.echo.server.common.upstream.ProxyNode;
import com.virjar.echo.hproxy.handlers.UserProxyProtocolRouterHandler;
import com.virjar.echo.hproxy.util.HProxyConstants;
import com.virjar.echo.server.common.eventbus.ComponentEvent;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.concurrent.DefaultThreadFactory;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class HttpProxyService {
    private static final int MAX_INITIAL_LINE_LENGTH_DEFAULT = 8192;
    private static final int MAX_HEADER_SIZE_DEFAULT = 8192 * 2;
    private static final int MAX_CHUNK_SIZE_DEFAULT = 8192 * 2;
    /**
     * 代理服务端，用来开启代理服务
     */

    private ServerBootstrap httpProxyBootstrap;

    private final EchoHttpProxyServer echoHttpProxyServer;


    public HttpProxyService(EchoHttpProxyServer echoHttpProxyServer) {
        this.echoHttpProxyServer = echoHttpProxyServer;
        buildHttpProxyServerBootstrap();
    }


    private void buildHttpProxyServerBootstrap() {
        httpProxyBootstrap = new ServerBootstrap();
        NioEventLoopGroup serverBossGroup = new NioEventLoopGroup(
                0,
                new DefaultThreadFactory("HttpProxy-boss-group" + DefaultThreadFactory.toPoolName(NioEventLoopGroup.class))
        );
        NioEventLoopGroup serverWorkerGroup = new NioEventLoopGroup(
                0,
                new DefaultThreadFactory("HttpProxy-worker-group" + DefaultThreadFactory.toPoolName(NioEventLoopGroup.class))
        );
        httpProxyBootstrap.group(serverBossGroup, serverWorkerGroup)
                .option(ChannelOption.SO_BACKLOG, 10)
                .option(ChannelOption.SO_REUSEADDR, true)
                .channel(NioServerSocketChannel.class)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    public void initChannel(SocketChannel ch) {
                        ChannelPipeline pipeline = ch.pipeline();
                        pipeline.addLast(UserProxyProtocolRouterHandler.getName(),
                                new UserProxyProtocolRouterHandler(echoHttpProxyServer)
                        );
                        pipeline.addLast(
                                "idle",
                                new IdleStateHandler(0, 0, 70)
                        );
                    }
                });
    }

    public interface ProxyServerCloseCallback {
        void onProxyServerCloseEvent();
    }

    public void startHttpProxyServer(EchoHttpProxyServer echoHttpProxyServer, ProxyNode proxyNode, ProxyServerCloseCallback proxyServerCloseCallback) {
        httpProxyBootstrap.bind(proxyNode.getProxyPort())
                .addListener((ChannelFutureListener) channelFuture -> {
                    if (!channelFuture.isSuccess()) {
                        proxyServerCloseCallback.onProxyServerCloseEvent();
                        return;
                    }
                    log.info("open proxy server success for client:{} with port:{}", proxyNode.getClientId(), proxyNode.getProxyPort());
                    proxyNode.setProxyServerChannel(channelFuture.channel());

                    ComponentEvent hProxyOnlineEvent = ComponentEvent.createHProxyOnlineEvent(proxyNode.toVo());
                    echoHttpProxyServer.getEventBusManager().pushEvent(hProxyOnlineEvent);


                    channelFuture.channel().closeFuture()
                            .addListener(future -> proxyServerCloseCallback.onProxyServerCloseEvent());

                    channelFuture.channel().attr(HProxyConstants.BIND_PROXY_NODE).set(proxyNode);
                });
    }
}
