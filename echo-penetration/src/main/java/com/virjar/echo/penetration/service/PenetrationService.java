package com.virjar.echo.penetration.service;

import com.virjar.echo.penetration.EchoPenetrationServer;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.concurrent.DefaultThreadFactory;

public class PenetrationService {
    private final EchoPenetrationServer echoPenetrationServer;

    private ServerBootstrap serverBootstrap;

    public PenetrationService(EchoPenetrationServer echoPenetrationServer) {
        this.echoPenetrationServer = echoPenetrationServer;
        buildNettyBootstrap();
    }

    private void buildNettyBootstrap() {
        serverBootstrap = new ServerBootstrap();
        NioEventLoopGroup serverBossGroup = new NioEventLoopGroup(
                0,
                new DefaultThreadFactory("penetration-boss-group" + DefaultThreadFactory.toPoolName(NioEventLoopGroup.class))
        );
        NioEventLoopGroup serverWorkerGroup = new NioEventLoopGroup(
                0,
                new DefaultThreadFactory("penetration-worker-group" + DefaultThreadFactory.toPoolName(NioEventLoopGroup.class))
        );
        serverBootstrap.group(serverBossGroup, serverWorkerGroup)
                .option(ChannelOption.SO_BACKLOG, 10)
                .option(ChannelOption.SO_REUSEADDR, true)
                .channel(NioServerSocketChannel.class)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    public void initChannel(SocketChannel ch) {
                        ChannelPipeline pipeline = ch.pipeline();

                        pipeline.addLast(
                                "idle",
                                new IdleStateHandler(0, 0, 70)
                        );
                    }
                });
    }


}
