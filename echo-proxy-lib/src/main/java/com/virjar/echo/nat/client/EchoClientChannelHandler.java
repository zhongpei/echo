package com.virjar.echo.nat.client;

import com.virjar.echo.nat.cmd.CmdHandler;
import com.virjar.echo.nat.cmd.CmdResponse;
import com.virjar.echo.nat.log.EchoLogger;
import com.virjar.echo.nat.protocol.EchoPacket;
import com.virjar.echo.nat.protocol.PacketCommon;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

import java.util.concurrent.TimeUnit;

public class EchoClientChannelHandler extends SimpleChannelInboundHandler<EchoPacket> {

    private EchoClient echoClient;

    EchoClientChannelHandler(EchoClient echoClient) {
        this.echoClient = echoClient;
    }


    @Override
    protected void channelRead0(ChannelHandlerContext ctx, EchoPacket msg) {
        EchoLogger.getLogger().info("received message from server: " + PacketCommon.getReadableType(msg.getType()));
        switch (msg.getType()) {
            case PacketCommon.TYPE_HEARTBEAT:
                handleHeartbeatMessage();
                break;
            case PacketCommon.TYPE_CONNECT:
                handleConnect(ctx, msg);
                break;
            case PacketCommon.TYPE_TRANSFER:
                handleTransfer(ctx, msg);
                break;
            case PacketCommon.TYPE_DISCONNECT:
                handleDisconnectMessage(msg);
                break;
            case PacketCommon.C_TYPE_CONTROL:
                handleControlMessage(ctx, msg);
                break;
            case PacketCommon.TYPE_DESTROY:
                handleDestroyMessage(ctx);
                break;

            default:
                EchoLogger.getLogger().warn("Unknown message type:" + msg.getType());
                break;
        }

    }

    private void handleDestroyMessage(ChannelHandlerContext ctx) {
        ctx.close();
    }

    private void handleControlMessage(ChannelHandlerContext ctx, EchoPacket msg) {
        long seq = msg.getSerialNumber();
        String action = msg.getExtra();
        String cmdParam = "";
        byte[] data = msg.getData();
        if (data != null) {
            cmdParam = new String(data);
        }
        CmdResponse cmdResponse = new CmdResponse(seq, ctx.channel());
        CmdHandler cmdHandler = echoClient.queryCmdHandler(action);
        if (cmdHandler == null) {
            cmdResponse.failed("NO_ACTION_" + action);
            return;
        }
        String finalCmdParam = cmdParam;
        new Thread("cmd-thread") {
            @Override
            public void run() {
                try {
                    cmdHandler.handle(finalCmdParam, cmdResponse, echoClient);
                } catch (Exception e) {
                    EchoLogger.getLogger().error("failed to call cmdHandler: " + action
                            + " param->" + finalCmdParam, e);
                    cmdResponse.failed("error action:" + action + " param:" + finalCmdParam + " e:" + e.getMessage());
                }
            }
        }.start();

    }

    private void handleDisconnectMessage(EchoPacket msg) {
        // 关闭真实服务器的链接，比如baidu.com:443
        echoClient.closeRealServerConnection(msg.getSerialNumber());
    }


    private void handleConnect(final ChannelHandlerContext ctx, final EchoPacket msg) {
        String hostAndPort = msg.getExtra();
        if (hostAndPort == null) {
            EchoLogger.getLogger().error("empty Host:Port config for connect command from nat server");
            disconnect(ctx, msg, "empty Host:Port config");
            return;
        }

        hostAndPort = hostAndPort.trim();
        if (hostAndPort.isEmpty()) {
            EchoLogger.getLogger().error("empty Host:Port config for connect command from nat server");
            disconnect(ctx, msg, "empty Host:Port config ");
            return;
        }
        String[] split = hostAndPort.split(":");
        if (split.length != 2) {
            EchoLogger.getLogger().error("error Host:Port config for connect command from nat server: " + hostAndPort);
            disconnect(ctx, msg, "error Host:Port config");
            return;
        }
        String host = split[0];
        int port;
        try {
            port = Integer.parseInt(split[1]);
        } catch (NumberFormatException e) {
            EchoLogger.getLogger().error("error Host:Port config for connect command from nat server: " + hostAndPort
                    , e);
            disconnect(ctx, msg, "error Host:Port config");
            return;
        }
        ChannelFuture proxyToServerConnection;

        EchoLogger.getLogger().info("echo client create a new connection for->  "
                + host + ":" + port + " for request id: " + msg.getSerialNumber() + " traceId: " + msg.getTraceID());
        // create a connect
        try {
            proxyToServerConnection = echoClient.connectToRealServer(msg.getSerialNumber(), host, port, msg.getTraceID());
        } catch (Exception e) {
            //这里是IP：port端口配置不合法
            EchoLogger.getLogger().error("error Host:Port config", e);
            disconnect(ctx, msg, "error Host:Port config:" + hostAndPort + " error:" + e.getMessage());
            return;
        }


        proxyToServerConnection.addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(final ChannelFuture proxyToServerFuture) {
                if (!proxyToServerFuture.isSuccess()) {
                    EchoLogger.getLogger().warn("connect to real server failed", proxyToServerFuture.cause());
                    disconnect(ctx, msg, proxyToServerFuture.cause().getMessage());
                    return;
                }

                if (!echoClient.isAlive()) {
                    EchoLogger.getLogger().warn("nat channel closed after create server connection completed");
                    disconnect(ctx, msg, "nat channel closed after create server connection completed");
                    proxyToServerFuture.channel().close();

                }

            }
        });
    }

    private void handleTransfer(ChannelHandlerContext ctx, EchoPacket msg) {
        echoClient.forwardNatServerRequest(ctx, msg);
    }

    private void disconnect(ChannelHandlerContext ctx, EchoPacket msg, String errorMsg) {
        EchoPacket disconnectCmd = new EchoPacket();
        disconnectCmd.setSerialNumber(msg.getSerialNumber());
        disconnectCmd.setType(PacketCommon.TYPE_DISCONNECT);
        if (errorMsg != null) {
            disconnectCmd.setData(errorMsg.getBytes());
        }
        ctx.writeAndFlush(disconnectCmd);
    }

    private void handleHeartbeatMessage() {
        EchoLogger.getLogger().info("receive heartbeat message from nat server");
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        super.channelInactive(ctx);
        if (echoClient.isAlive()) {
            return;
        }
        EchoLogger.getLogger().warn("client disconnected: " + ctx.channel() + "  prepare to reconnect");
        ctx.executor().schedule(new Runnable() {
            @Override
            public void run() {
                echoClient.connectNatServer();
            }
        }, 1500, TimeUnit.MILLISECONDS);

    }
}
