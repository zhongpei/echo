package com.virjar.echo.server.common.hserver;

import com.alibaba.fastjson.JSONObject;
import fi.iki.elonen.NanoHTTPD;

public interface HttpActionHandler {
    JSONObject handle(NanoHTTPD.IHTTPSession httpSession);
}
