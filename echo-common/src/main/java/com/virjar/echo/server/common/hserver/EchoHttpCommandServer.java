package com.virjar.echo.server.common.hserver;

import com.alibaba.fastjson.JSONObject;
import com.google.common.collect.Maps;
import fi.iki.elonen.NanoHTTPD;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

/**
 * http服务器，使用NanoHttpD实现，由于仅仅提供简单的控制只能和信息交换的能力，所以不能使用tomcat/jetty之类的大型容器
 */
@Slf4j
public class EchoHttpCommandServer extends NanoHTTPD {
    private static final String MIME_JSON = "application/json;charset=UTF-8";

    public EchoHttpCommandServer(int port) {
        super(port);
    }

    private final Map<String, HttpActionHandler> handlerMap = Maps.newHashMap();

    public EchoHttpCommandServer registerHandler(String uri, HttpActionHandler httpActionHandler) {
        handlerMap.put(uri, httpActionHandler);
        return this;
    }

    @Override
    public Response serve(IHTTPSession session) {
        String uri = session.getUri();
        log.info("handle request with uri: {}", uri);
        HttpActionHandler httpActionHandler = handlerMap.get(uri);
        if (httpActionHandler == null) {
            return super.serve(session);
        }
        try {
            return serveWithHandler(httpActionHandler, session);
        } catch (Exception e) {
            log.error("call handler:{} failed", httpActionHandler.getClass(), e);
            return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, NanoHTTPD.MIME_PLAINTEXT, e.getMessage());
        }
    }

    private Response serveWithHandler(HttpActionHandler httpActionHandler, IHTTPSession session) {
        JSONObject jsonObject = httpActionHandler.handle(session);
        return newFixedLengthResponse(Response.Status.OK, MIME_JSON, jsonObject.toJSONString());
    }
}
