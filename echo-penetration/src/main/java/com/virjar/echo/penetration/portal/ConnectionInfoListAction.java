package com.virjar.echo.penetration.portal;

import com.alibaba.fastjson.JSONObject;
import com.virjar.echo.penetration.EchoPenetrationServer;
import com.virjar.echo.server.common.hserver.HttpActionHandler;
import fi.iki.elonen.NanoHTTPD;

public class ConnectionInfoListAction implements HttpActionHandler {
    public ConnectionInfoListAction(EchoPenetrationServer echoPenetrationServer) {
    }

    @Override
    public JSONObject handle(NanoHTTPD.IHTTPSession httpSession) {
        return null;
    }
}
