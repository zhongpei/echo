package com.virjar.echo.hproxy.portal;

import com.alibaba.fastjson.JSONObject;
import com.virjar.echo.hproxy.EchoHttpProxyServer;
import com.virjar.echo.server.common.hserver.HttpActionHandler;
import fi.iki.elonen.NanoHTTPD;

public class HttpProxyListHandler implements HttpActionHandler {
    private final EchoHttpProxyServer echoHttpProxyServer;

    public HttpProxyListHandler(EchoHttpProxyServer echoHttpProxyServer) {
        this.echoHttpProxyServer = echoHttpProxyServer;
    }

    @Override
    public JSONObject handle(NanoHTTPD.IHTTPSession httpSession) {
        return echoHttpProxyServer.generateProxyListJson();
    }
}
