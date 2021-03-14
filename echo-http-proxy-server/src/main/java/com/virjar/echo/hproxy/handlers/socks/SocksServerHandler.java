package com.virjar.echo.hproxy.handlers.socks;

import com.virjar.echo.hproxy.EchoHttpProxyServer;
import com.virjar.echo.server.common.upstream.ProxyNode;
import com.virjar.echo.hproxy.util.HProxyConstants;
import com.virjar.echo.server.common.NatUpstreamMeta;
import com.virjar.echo.server.common.NettyUtils;
import com.virjar.echo.server.common.auth.AuthenticateDeviceInfo;
import com.virjar.echo.server.common.auth.IAuthenticator;
import io.netty.channel.*;
import io.netty.handler.codec.socks.*;
import lombok.extern.slf4j.Slf4j;

import java.net.InetSocketAddress;

/**
 * @author lei.X
 * @date 2021/1/30
 */
@ChannelHandler.Sharable
@Slf4j
public final class SocksServerHandler extends SimpleChannelInboundHandler<SocksRequest> {

    private final EchoHttpProxyServer echoHttpProxyServer;
    private AuthenticateDeviceInfo authenticateDeviceInfo;

    public SocksServerHandler(EchoHttpProxyServer echoHttpProxyServer) {
        this.echoHttpProxyServer = echoHttpProxyServer;
    }

    private static final String name = "SOCKS_SERVER_HANDLER";

    public static String getName() {
        return name;
    }

    @Override
    public void channelRead0(ChannelHandlerContext ctx, SocksRequest socksRequest) throws Exception {
        switch (socksRequest.requestType()) {
            case INIT:
                handleInit(ctx);
                break;
            case AUTH:
                handleAuth(ctx, (SocksAuthRequest) socksRequest);
                break;
            case CMD:
                handleCmd(ctx, socksRequest);
                break;
            case UNKNOWN:
                ctx.close();
                break;
        }
    }


    private void handleCmd(ChannelHandlerContext ctx, SocksRequest socksRequest) {
        SocksCmdRequest req = (SocksCmdRequest) socksRequest;
        if (req.cmdType() != SocksCmdType.CONNECT) {
            ctx.close();
            return;
        }

        log.info("Socks Ensuring that hostAndPort are available in host:{}, port:{}", req.host(), req.port());
        // 创建到 NatMapping的连接
        ProxyNode proxyNode = ctx.channel().parent().attr(HProxyConstants.BIND_PROXY_NODE).get();
        // 解析我们需要代理到哪里去
        String serverHostAndPort = req.host() + ":" + req.port();

        String traceId = "socks";

        ChannelFuture natMappingChannelFuture = echoHttpProxyServer.connectToNatMapping(proxyNode, serverHostAndPort, traceId);

        // 建立到真实服务器的请求
        natMappingChannelFuture.addListener((ChannelFutureListener) channelFuture1 -> {
            if (!channelFuture1.isSuccess()) {
                log.warn("connect to proxy server failed:{} ", serverHostAndPort, natMappingChannelFuture.cause());
                ctx.channel().writeAndFlush(
                        new SocksCmdResponse(SocksCmdStatus.FAILURE, req.addressType()));
                NettyUtils.closeChannelIfActive(ctx.channel());
                return;
            }
            log.info("Connect to real server using socks success, hostAndPort:{}", serverHostAndPort);
            onEchoUpstreamEstablish(natMappingChannelFuture.channel(),
                    ctx.channel(), req, traceId);

        });
    }


    private void onEchoUpstreamEstablish(Channel natMappingChannel, Channel proxyRequestChanel, SocksCmdRequest request, String traceId) {

        natMappingChannel.attr(HProxyConstants.ECHO_TRACE_ID).set(traceId);
        NettyUtils.makePair(natMappingChannel, proxyRequestChanel);
        NettyUtils.loveOther(natMappingChannel, proxyRequestChanel);

        proxyRequestChanel
                .writeAndFlush(new SocksCmdResponse(SocksCmdStatus.SUCCESS, request.addressType()))
                .addListener(channelFuture -> {
                    proxyRequestChanel.pipeline().remove(SocksMessageEncoder.class);
                    proxyRequestChanel.pipeline().remove(SocksServerHandler.this);
                    proxyRequestChanel.pipeline().addAfter("idle", "replay", new RelayHandler(natMappingChannel));

                    natMappingChannel.pipeline().addAfter("idle", "replay", new RelayHandler(proxyRequestChanel));

                });
    }


    private void handleAuth(ChannelHandlerContext ctx, SocksAuthRequest socksAuthRequest) {
        authenticateDeviceInfo.setUserName(socksAuthRequest.username());
        authenticateDeviceInfo.setPassword(socksAuthRequest.password());
        IAuthenticator authenticator = echoHttpProxyServer.getAuthConfigManager().getAuthenticator();
        if (authenticator.authenticate(authenticateDeviceInfo)) {
            ctx.pipeline().addFirst(new SocksCmdRequestDecoder());
            ctx.writeAndFlush(new SocksAuthResponse(SocksAuthStatus.SUCCESS));
        } else {
            ctx.pipeline().addFirst(new SocksAuthRequestDecoder());
            ctx.writeAndFlush(new SocksAuthResponse(SocksAuthStatus.FAILURE));
        }
    }


    private void handleInit(ChannelHandlerContext ctx) {

        IAuthenticator authenticator = echoHttpProxyServer.getAuthConfigManager().getAuthenticator();
        //未设置代理验证
        if (authenticator == null) {
            ctx.pipeline().addFirst(new SocksCmdRequestDecoder());
            ctx.writeAndFlush(new SocksInitResponse(SocksAuthScheme.NO_AUTH));
            return;
        }

        String remoteHost = ((InetSocketAddress) ctx.channel().remoteAddress()).getHostName();

        ProxyNode proxyNode = ctx.channel().parent().attr(HProxyConstants.BIND_PROXY_NODE).get();
        NatUpstreamMeta natUpstreamMeta = proxyNode.getNatUpstreamMeta();
        authenticateDeviceInfo =
                AuthenticateDeviceInfo.create(natUpstreamMeta, remoteHost, proxyNode.getClientId());
        if (authenticator.authenticateWithIp(authenticateDeviceInfo)) {
            ctx.pipeline().addFirst(new SocksCmdRequestDecoder());
            ctx.writeAndFlush(new SocksInitResponse(SocksAuthScheme.NO_AUTH));
            return;
        }
//        if (authenticateDeviceInfo.getRateLimited()) {
//            // 被限流说明其实授权通过，但是由于流量控制导致运行访问
////            ctx.writeAndFlush(new SocksAuthResponse(SocksAuthStatus.FAILURE))
////                    .addListener(future -> ctx.close());
//            ctx.close();
//            return;
//        }
        ctx.pipeline().addFirst(new SocksAuthRequestDecoder());
        ctx.write(new SocksInitResponse(SocksAuthScheme.AUTH_PASSWORD));
    }


    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) {
        ctx.flush();
    }

}

