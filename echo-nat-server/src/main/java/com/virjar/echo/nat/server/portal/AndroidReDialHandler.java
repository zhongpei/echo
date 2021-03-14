package com.virjar.echo.nat.server.portal;

import com.alibaba.fastjson.JSONObject;
import com.virjar.echo.nat.server.EchoNatServer;
import com.virjar.echo.server.common.hserver.HttpActionHandler;
import fi.iki.elonen.NanoHTTPD;

public class AndroidReDialHandler implements HttpActionHandler {
    private final EchoNatServer echoNatServer;

    public AndroidReDialHandler(EchoNatServer echoNatServer) {
        this.echoNatServer = echoNatServer;
    }

    @Override
    public JSONObject handle(NanoHTTPD.IHTTPSession httpSession) {
        return null;
    }
}
