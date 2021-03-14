package com.virjar.echo.penetration.portal;

import com.alibaba.fastjson.JSONObject;
import com.virjar.echo.penetration.EchoPenetrationServer;
import com.virjar.echo.server.common.hserver.EchoHttpCommandServer;
import lombok.Getter;

import java.io.IOException;

public class PortalServer {
    private EchoPenetrationServer echoPenetrationServer;

    public PortalServer(EchoPenetrationServer echoPenetrationServer) {
        this.echoPenetrationServer = echoPenetrationServer;
        setup();
    }

    @Getter
    private EchoHttpCommandServer echoHttpCommandServer;


    public PortalServer startHttpServer() {
        try {
            echoHttpCommandServer.start();
            return this;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }

    private void setup() {
        echoHttpCommandServer = new EchoHttpCommandServer(echoPenetrationServer.getCmdHttpPort());

        // 获取本台机器所有连接的客户端
        echoHttpCommandServer.registerHandler("/echoNatApi/connectionList",
                new ConnectionInfoListAction(echoPenetrationServer)
        );

        echoHttpCommandServer.registerHandler(
                "/echoNatApi/serverId", httpSession -> {
                    JSONObject ret = new JSONObject();
                    ret.put("serverId", echoPenetrationServer.getServerId());
                    return ret;
                });
    }
}
