package com.virjar.echo.hproxy.portal;

import com.alibaba.fastjson.JSONObject;
import com.virjar.echo.hproxy.EchoHttpProxyServer;
import com.virjar.echo.server.common.DownstreamServerType;
import com.virjar.echo.server.common.hserver.EchoHttpCommandServer;
import lombok.Getter;

import java.io.IOException;

public class PortalServer {
    @Getter
    private EchoHttpCommandServer echoHttpCommandServer;
    private final EchoHttpProxyServer echoHttpProxyServer;

    public PortalServer(EchoHttpProxyServer echoHttpProxyServer) {
        this.echoHttpProxyServer = echoHttpProxyServer;
        setup();
    }

    public PortalServer startHttpServer() {
        try {
            echoHttpCommandServer.start();
            return this;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }

    private void setup() {
        echoHttpCommandServer = new EchoHttpCommandServer(echoHttpProxyServer.getCmdHttpPort());

        echoHttpCommandServer
                .registerHandler(
                        "/echoNatApi/proxyList", new HttpProxyListHandler(echoHttpProxyServer)
                ).registerHandler(
                "/echoNatApi/serverId", httpSession -> {
                    JSONObject ret = new JSONObject();
                    ret.put("serverId", echoHttpProxyServer.getServerId());
                    ret.put("serverType", DownstreamServerType.http.name());
                    return ret;
                }
        );
    }
}
