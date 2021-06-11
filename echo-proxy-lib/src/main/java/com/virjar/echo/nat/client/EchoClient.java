package com.virjar.echo.nat.client;

import com.virjar.echo.nat.cmd.CmdHandler;
import com.virjar.echo.nat.log.EchoLogger;
import com.virjar.echo.nat.protocol.EchoPacket;
import com.virjar.echo.nat.protocol.EchoPacketDecoder;
import com.virjar.echo.nat.protocol.EchoPacketEncoder;
import com.virjar.echo.nat.protocol.PacketCommon;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.util.concurrent.DefaultThreadFactory;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class EchoClient {

    private String natServerHost;
    private int natServerPort;
    private String clientId;
    private String adminAccount;

    private Map<String, CmdHandler> cmdHandlerMap = new HashMap<>();

    public EchoClient(String natServerHost, int natServerPort, String clientId) {
        this.natServerHost = natServerHost;
        this.natServerPort = natServerPort;
        this.clientId = clientId;
    }

    public EchoClient setAdminAccount(String adminAccount) {
        this.adminAccount = adminAccount;
        return this;
    }

    public EchoClient registerCmdHandler(CmdHandler cmdHandler) {
        cmdHandlerMap.put(cmdHandler.action().toUpperCase(), cmdHandler);
        return this;
    }

    public CmdHandler queryCmdHandler(String action) {
        return cmdHandlerMap.get(action.toUpperCase());
    }

    private Bootstrap echoClientBootstrap;

    private Bootstrap echoClientToRealServerBootstrap;

    private ChannelFuture natServerChannelFuture = null;

    private Map<Long, Channel> allRealServerChannels = new ConcurrentHashMap<>();

    ChannelFuture connectToRealServer(final Long seq, final String host, final Integer port, String traceId) {
        ChannelFuture connect = echoClientToRealServerBootstrap.connect(host, port);

        connect.addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture future) {
                if (!future.isSuccess()) {
                    return;
                }
                allRealServerChannels.put(seq, future.channel());
                future.channel().attr(EchoClientConstant.SERIAL_NUM).set(seq);
                future.channel().attr(EchoClientConstant.ECHO_TRACE_ID).set(traceId);

                EchoLogger.getLogger().info("create tcp connection success for host->"
                        + host + ":" + port + " for request: " + seq + " traceId: " + traceId);
                // now notify nat server the connection create ready!!
                EchoPacket echoPacket = new EchoPacket();
                echoPacket.setType(PacketCommon.TYPE_CONNECT_READY);
                echoPacket.setSerialNumber(seq);
                echoPacket.setExtra(getClientId());
                echoPacket.setTraceID(traceId);
                EchoClient.this.natChannel.writeAndFlush(echoPacket);
            }
        });
        return connect;
    }


    void closeRealServerConnection(Long seq) {
        final Channel realServerChannel = allRealServerChannels.remove(seq);
        if (realServerChannel == null) {
            EchoLogger.getLogger().warn("can not find real server connection for request: " + seq);
            return;
        }
        realServerChannel
                .writeAndFlush(Unpooled.EMPTY_BUFFER)// 目的是保证所有的数据，都正确写入到server。否则可能还么没有写入到server，就关闭了请求
                .addListener(new GenericFutureListener<Future<? super Void>>() {
                    @Override
                    public void operationComplete(Future<? super Void> future) {
                        realServerChannel.close();
                    }
                });
    }

    void forwardNatServerRequest(ChannelHandlerContext ctx, EchoPacket echoPacket) {
        Channel channel = allRealServerChannels.get(echoPacket.getSerialNumber());
        if (channel == null) {
            EchoLogger.getLogger().warn("can not find real server connection for request: " + echoPacket.getSerialNumber());
            return;
        }
        EchoLogger.getLogger().info("transfer data from request to  real server ,traceId: " + echoPacket.getTraceID());
        byte[] data = echoPacket.getData();
        ByteBuf buf = ctx.alloc().buffer(data.length);
        buf.writeBytes(data);

        channel.writeAndFlush(buf);

    }


    void forwardRealServerResponse(ByteBuf msg, Long seq) {
        int maxPackSize = PacketCommon.MAX_FRAME_LENGTH - 128;
        Channel realServerChannel = allRealServerChannels.get(seq);
        String traceId = realServerChannel.attr(EchoClientConstant.ECHO_TRACE_ID).get();
        while (msg.readableBytes() > maxPackSize) {
            byte[] bytes = new byte[maxPackSize];
            msg.readBytes(bytes);

            EchoPacket natMessage = new EchoPacket();
            natMessage.setData(bytes);
            natMessage.setSerialNumber(seq);
            natMessage.setTraceID(traceId);
            natMessage.setType(PacketCommon.TYPE_TRANSFER);
            //write,但是不需要 flush
            natChannel.write(natMessage);
            EchoLogger.getLogger().info("receive data from real server endpoint with big packet," +
                    " forward to natChannel with segment packet");
        }
        if (msg.readableBytes() > 0) {
            EchoPacket natMessage = new EchoPacket();
            byte[] bytes = new byte[msg.readableBytes()];
            msg.readBytes(bytes);
            natMessage.setData(bytes);
            natMessage.setSerialNumber(seq);
            natMessage.setTraceID(traceId);
            natMessage.setType(PacketCommon.TYPE_TRANSFER);

            natChannel.writeAndFlush(natMessage);
            EchoLogger.getLogger().info("receive data from real server endpoint with big packet," +
                    " forward to natChannel for final packet");
        } else {
            natChannel.flush();
        }

        EchoLogger.getLogger().info("receive data from real server and transfer to natChannel, traceId:" + traceId);

    }

    void realServerDisconnect(Long seq) {
        EchoPacket echoPacket = new EchoPacket();
        String traceId = allRealServerChannels.get(seq).attr(EchoClientConstant.ECHO_TRACE_ID).get();
        echoPacket.setTraceID(traceId);
        echoPacket.setType(PacketCommon.TYPE_DISCONNECT);
        echoPacket.setSerialNumber(seq);
        natChannel.writeAndFlush(echoPacket);
        allRealServerChannels.remove(seq);
    }


    public void startUp() {
        NioEventLoopGroup workerGroup = new NioEventLoopGroup(
                0,
                new DefaultThreadFactory("nat-endpoint-group" + DefaultThreadFactory.toPoolName(NioEventLoopGroup.class))
                //, NioUdtProvider.BYTE_PROVIDER
        );

        echoClientBootstrap = new Bootstrap();
        echoClientBootstrap.group(workerGroup);
        echoClientBootstrap.channel(NioSocketChannel.class);
        echoClientBootstrap.handler(new ChannelInitializer<SocketChannel>() {

            @Override
            public void initChannel(SocketChannel ch) {
                ch.pipeline().addLast(EchoClientConstant.ECHO_DECODER, new EchoPacketDecoder());
                ch.pipeline().addLast(EchoClientConstant.ECHO_ENCODER, new EchoPacketEncoder());
                ch.pipeline().addLast(EchoClientConstant.ECHO_IDLE, new ClientIdleCheckHandler());
                ch.pipeline().addLast(EchoClientConstant.ECHO_NAT, new EchoClientChannelHandler(EchoClient.this));
            }
        });

        workerGroup = new NioEventLoopGroup(
                1,
                new DefaultThreadFactory("proxy-endpoint-group" + DefaultThreadFactory.toPoolName(NioEventLoopGroup.class))
                //, NioUdtProvider.BYTE_PROVIDER
        );
        echoClientToRealServerBootstrap = new Bootstrap();
        echoClientToRealServerBootstrap.group(workerGroup);
        echoClientToRealServerBootstrap.channel(NioSocketChannel.class);
        echoClientToRealServerBootstrap.handler(new ChannelInitializer<SocketChannel>() {
            @Override
            protected void initChannel(SocketChannel ch) {
                ch.pipeline().addLast(new ClientToRealServerChannelHandler(EchoClient.this));
            }
        });

        connectNatServer();
    }


    private volatile boolean isConnecting = false;

    private Channel natChannel;

    // 主动关闭掉nat channel
    public void closeNatChannel() {
        EchoPacket proxyMessage = new EchoPacket();
        proxyMessage.setType(PacketCommon.TYPE_DISCONNECT);
        proxyMessage.setExtra(clientId);
        if (adminAccount != null && !adminAccount.trim().isEmpty()) {
            //注册的时候，添加账户身份。这样后台标记这个客户端属于那个客户端
            //如果没有登录，或者不设置。那么成为无用户组资源
            proxyMessage.setData(adminAccount.getBytes(StandardCharsets.UTF_8));
        }
        natChannel.writeAndFlush(proxyMessage);
        natChannel.close();
        this.isConnecting = false;
        this.allRealServerChannels.clear();
//        for (Long seq : this.allRealServerChannels.keySet()) {
//            this.realServerDisconnect(seq);
//        }
        EchoLogger.getLogger().info("closeNatChannel finish");
    }


    public boolean isAlive() {
        return natChannel != null && natChannel.isActive();
    }

    public void connectNatServer() {
        Channel cmdChannelCopy = natChannel;
        if (cmdChannelCopy != null && cmdChannelCopy.isActive()) {
            EchoLogger.getLogger().info("cmd channel active, and close channel,heartbeat timeout ?");
            return;
        }
        if (isConnecting) {
            EchoLogger.getLogger().warn("connect event fire already");
            return;
        }
        isConnecting = true;
        EchoLogger.getLogger().info("connect to nat server... -> " + natServerHost + ":" + natServerPort);
        natServerChannelFuture = echoClientBootstrap.connect(natServerHost, natServerPort);

        natServerChannelFuture.addListener((ChannelFutureListener) channelFuture1 -> {
            isConnecting = false;
            if (!channelFuture1.isSuccess()) {
                EchoLogger.getLogger().warn("connect to nat server failed", channelFuture1.cause());
                echoClientBootstrap.group().schedule(new Runnable() {
                    @Override
                    public void run() {
                        EchoLogger.getLogger().info("connect to nat server failed, reconnect by scheduler task start");
                        connectNatServer();
                    }
                }, reconnectWait(), TimeUnit.MILLISECONDS);

            } else {
                sleepTimeMill = 1000;
                natChannel = channelFuture1.channel();
                EchoLogger.getLogger().info("connect to nat server success:" + natChannel);

                EchoPacket proxyMessage = new EchoPacket();
                proxyMessage.setType(PacketCommon.C_TYPE_REGISTER);
                proxyMessage.setExtra(clientId);
                if (adminAccount != null && !adminAccount.trim().isEmpty()) {
                    //注册的时候，添加账户身份。这样后台标记这个客户端属于那个客户端
                    //如果没有登录，或者不设置。那么成为无用户组资源
                    proxyMessage.setData(adminAccount.getBytes(StandardCharsets.UTF_8));
                }
                natChannel.writeAndFlush(proxyMessage);
            }
        });


    }

    private static long sleepTimeMill = 1000;

    private static long reconnectWait() {
        if (sleepTimeMill > 120000) {
            sleepTimeMill = 120000;
        }
        synchronized (EchoClient.class) {
            sleepTimeMill = sleepTimeMill + 1000;
            return sleepTimeMill;
        }
    }

    public String getClientId() {
        return clientId;
    }

    public void sync() {
        if (natServerChannelFuture == null) {
            return;
        }

        try {
            natServerChannelFuture.sync();
        } catch (InterruptedException interruptedException) {
            EchoLogger.getLogger().error("sync interrupted", interruptedException);
        }

    }
}
