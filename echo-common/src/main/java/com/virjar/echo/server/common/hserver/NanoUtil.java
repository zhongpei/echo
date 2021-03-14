package com.virjar.echo.server.common.hserver;

import com.alibaba.fastjson.JSONObject;
import fi.iki.elonen.NanoHTTPD;

import java.util.List;

public class NanoUtil {
    public static String getParam(String key, NanoHTTPD.IHTTPSession httpSession) {
        List<String> strings = httpSession.getParameters().get(key);
        if (strings == null || strings.isEmpty()) {
            return null;
        }
        return strings.get(0);
    }

    public static JSONObject failed(int code, String errorMessage) {
        JSONObject ret = new JSONObject();
        ret.put("status", code);
        ret.put("msg", errorMessage);
        return ret;
    }

    public static JSONObject success(Object data) {
        JSONObject ret = new JSONObject();
        ret.put("status", 0);
        ret.put("data", data);
        return ret;
    }
}
