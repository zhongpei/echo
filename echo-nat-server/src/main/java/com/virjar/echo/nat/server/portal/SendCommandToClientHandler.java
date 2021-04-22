package com.virjar.echo.nat.server.portal;

import com.alibaba.fastjson.JSONObject;
import com.virjar.echo.nat.server.EchoNatServer;
import com.virjar.echo.server.common.hserver.HttpActionHandler;
import com.virjar.echo.server.common.hserver.NanoUtil;
import fi.iki.elonen.NanoHTTPD;
import org.apache.commons.lang3.StringUtils;

public class SendCommandToClientHandler implements HttpActionHandler {
    private final EchoNatServer echoNatServer;

    SendCommandToClientHandler(EchoNatServer echoNatServer) {
        this.echoNatServer = echoNatServer;
    }

    @Override
    public JSONObject handle(NanoHTTPD.IHTTPSession httpSession) {
        String clientId = NanoUtil.getParam("clientId", httpSession);
        if (StringUtils.isBlank(clientId)) {
            return NanoUtil.failed(-1, "need param:{clientId}");
        }
        String action = NanoUtil.getParam("action", httpSession);
        if (StringUtils.isBlank(action)) {
            return NanoUtil.failed(-1, "need param:{action}");
        }

        String addition = NanoUtil.getParam("addition", httpSession);


        return echoNatServer.getEchoRemoteControlManager()
                .sendRemoteControlMessage(clientId, action, addition);
    }
}
