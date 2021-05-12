package com.virjar.echo.nat.server.portal;

import com.alibaba.fastjson.JSONObject;
import com.virjar.echo.nat.server.EchoNatServer;
import com.virjar.echo.server.common.hserver.HttpActionHandler;
import fi.iki.elonen.NanoHTTPD;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public class ConnectionInfoListAction implements HttpActionHandler {
    private final EchoNatServer echoNatServer;

    public ConnectionInfoListAction(EchoNatServer echoNatServer) {
        this.echoNatServer = echoNatServer;
    }

    @Override
    public JSONObject handle(NanoHTTPD.IHTTPSession httpSession) {
        CompletableFuture<JSONObject> completableFuture = new CompletableFuture<>();
        echoNatServer.generateConnectionInfoV2(value -> {
            completableFuture.complete(value);
        });
        try {
            JSONObject jsonObject = completableFuture.get();
            return jsonObject;
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (ExecutionException e) {
            e.printStackTrace();
        }
        return null;
    }
}
