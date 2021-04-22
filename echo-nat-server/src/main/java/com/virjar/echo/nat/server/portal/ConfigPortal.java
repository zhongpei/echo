package com.virjar.echo.nat.server.portal;

import com.alibaba.fastjson.JSONObject;
import com.virjar.echo.nat.server.EchoNatServer;
import com.virjar.echo.server.common.hserver.EchoHttpCommandServer;
import lombok.Getter;

import java.io.IOException;

public class ConfigPortal {
    private final EchoNatServer echoNatServer;
    @Getter
    private EchoHttpCommandServer echoHttpCommandServer;

    public ConfigPortal(EchoNatServer echoNatServer) {
        this.echoNatServer = echoNatServer;
        setup();
    }

    public ConfigPortal startHttpServer() {
        try {
            echoHttpCommandServer.start();
            return this;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }

    private void setup() {
        echoHttpCommandServer = new EchoHttpCommandServer(echoNatServer.getCmdHttpPort());

        // 获取本台机器所有连接的客户端
        echoHttpCommandServer.registerHandler("/echoNatApi/connectionList",
                new ConnectionInfoListAction(echoNatServer)
        );
        //在特定设备执行一个shell指令
        echoHttpCommandServer.registerHandler("/echoNatApi/exeCmd",
                new SendShellCmdToClientHandler(echoNatServer)
        );

        // 客户端重拨
        echoHttpCommandServer.registerHandler("/echoNatApi/reDail",
                new AndroidReDialHandler(echoNatServer)
        );

        echoHttpCommandServer.registerHandler("/echoNatApi/sendCmd",
                new SendCommandToClientHandler(echoNatServer)
        );


        echoHttpCommandServer.registerHandler(
                "/echoNatApi/serverId", httpSession -> {
                    JSONObject ret = new JSONObject();
                    ret.put("serverId", echoNatServer.getServerId());
                    ret.put("natPort", echoNatServer.getNatPort());
                    return ret;
                });
    }
}
