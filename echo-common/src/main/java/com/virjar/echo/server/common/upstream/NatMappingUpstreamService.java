package com.virjar.echo.server.common.upstream;


import com.virjar.echo.nat.client.ClientIdleCheckHandler;
import com.virjar.echo.nat.log.EchoLogger;
import com.virjar.echo.nat.protocol.EchoPacket;
import com.virjar.echo.nat.protocol.EchoPacketDecoder;
import com.virjar.echo.nat.protocol.EchoPacketEncoder;
import com.virjar.echo.nat.protocol.PacketCommon;
import com.virjar.echo.server.common.CommonConstant;
import com.virjar.echo.server.common.NatUpstreamMeta;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.util.Attribute;
import io.netty.util.concurrent.DefaultThreadFactory;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class NatMappingUpstreamService {

    public NatMappingUpstreamService() {
        buildNatMappingConnectBootstrap();
    }

    /**
     * NatMapping链接端，链接端口映射资源，代理流量转接到这里。代理upstream
     */
    private Bootstrap natMappingBootstrap;
    private static final int connectTimeout = 40000;

    private void buildNatMappingConnectBootstrap() {
        NioEventLoopGroup natMappingConnectionGroup = new NioEventLoopGroup(
                0,
                new DefaultThreadFactory("nat-mapping-group" + DefaultThreadFactory.toPoolName(NioEventLoopGroup.class))
                //, NioUdtProvider.BYTE_PROVIDER
        );
        natMappingBootstrap = new Bootstrap()
                .group(natMappingConnectionGroup)
                .channelFactory(NioSocketChannel::new)
                .handler(new ChannelInitializer<Channel>() {
                    protected void initChannel(Channel ch) {
                        ChannelPipeline pipeline = ch.pipeline();

                        pipeline.addLast("encode", new EchoPacketEncoder());
                        pipeline.addLast("decode", new EchoPacketDecoder());
                        pipeline.addLast("handler", new NatMappingClientChannelHandler());
                    }
                });

        natMappingBootstrap.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectTimeout);
    }

    public void createNatMappingHealthCheckConnection(ProxyNode proxyNode, HeathCheckConnectionCallback heathCheckConnectionCallback) {
        natMappingBootstrap.connect(proxyNode.getNatUpstreamMeta().getListenIp(), proxyNode.getNatUpstreamMeta().getPort())
                .addListener((ChannelFutureListener) channelFuture -> {
                    if (!channelFuture.isSuccess()) {
                        log.warn("create health channel failed", channelFuture.cause());
                        heathCheckConnectionCallback.onHeathCheckConnectionCreateFailed();
                        return;
                    }
                    Channel healthCheckChannel = channelFuture.channel();
                    // health channel 是需要存在心跳检测的
                    healthCheckChannel.pipeline()
                            .addAfter("decode", "idle-check",
                                    new ClientIdleCheckHandler()
                            );
                    EchoPacket echoPacket = new EchoPacket();
                    echoPacket.setType(PacketCommon.TYPE_HEARTBEAT);
                    healthCheckChannel.writeAndFlush(echoPacket);

                    proxyNode.setNatMappingHealthChannel(healthCheckChannel);
                    heathCheckConnectionCallback.onHeathCheckConnectionCreateSuccess(channelFuture.channel());
                });

    }

    public interface HeathCheckConnectionCallback {
        void onHeathCheckConnectionCreateSuccess(Channel healthCheckChannel);

        void onHeathCheckConnectionCreateFailed();
    }

    public ChannelFuture connectToNatMapping(ProxyNode proxyNode, String hostAndPort, String echoTrace) {
        NatUpstreamMeta natUpstreamMeta = proxyNode.getNatUpstreamMeta();
        ChannelFuture channelFuture = natMappingBootstrap
                .connect(natUpstreamMeta.getListenIp(), natUpstreamMeta.getPort());
        DefaultChannelPromise connectReadyFuture = new DefaultChannelPromise(channelFuture.channel());
        channelFuture.channel().closeFuture().addListener((ChannelFutureListener) channelFuture12 -> {

            Attribute<DefaultChannelPromise> attr = channelFuture12.channel().attr(CommonConstant.connectReadyPromiseKey);
            DefaultChannelPromise defaultChannelPromise = attr.get();
            if (defaultChannelPromise != null) {
                if (attr.compareAndSet(defaultChannelPromise, null)) {
                    defaultChannelPromise.setFailure(new RuntimeException("channel closed before connect ready message", channelFuture12.cause()));
                }
            }
        });

        channelFuture.addListener((ChannelFutureListener) channelFuture1 -> {
            if (!channelFuture1.isSuccess()) {
                connectReadyFuture.setFailure(new RuntimeException("connect to NatMapping failed:" + natUpstreamMeta.getListenIp()
                        + ":" + natUpstreamMeta.getPort(), channelFuture1.cause())
                );
                return;
            }
            Channel natMappingChannel = channelFuture.channel();
            // not write connect request to hostAndPort
            EchoPacket echoPacket = new EchoPacket();
            echoPacket.setType(PacketCommon.TYPE_CONNECT);
            echoPacket.setExtra(hostAndPort);
            echoPacket.setTraceID(echoTrace);
            natMappingChannel.writeAndFlush(echoPacket);
            //
            EchoLogger.getLogger().info("send msg type: TYPE_CONNECT " + " to echo client "
                    + " for request id: " + echoPacket.getSerialNumber() + " traceId: " + echoPacket.getTraceID());
            natMappingChannel.attr(CommonConstant.connectReadyPromiseKey).set(connectReadyFuture);
        });

        return connectReadyFuture;
    }
}
