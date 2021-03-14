package com.virjar.echo.nat.server.portal;

import com.alibaba.fastjson.JSONObject;
import com.virjar.echo.nat.cmd.CmdHandler;
import com.virjar.echo.nat.server.EchoNatServer;
import com.virjar.echo.server.common.hserver.HttpActionHandler;
import com.virjar.echo.server.common.hserver.NanoUtil;
import fi.iki.elonen.NanoHTTPD;
import org.apache.commons.lang3.StringUtils;

/**
 * 在客户端上面执行shell指令
 */
public class SendShellCmdToClientHandler implements HttpActionHandler {
    private final EchoNatServer echoNatServer;

    SendShellCmdToClientHandler(EchoNatServer echoNatServer) {
        this.echoNatServer = echoNatServer;
    }

    @Override
    public JSONObject handle(NanoHTTPD.IHTTPSession httpSession) {
        String clientId = NanoUtil.getParam("clientId", httpSession);
        if (StringUtils.isBlank(clientId)) {
            return NanoUtil.failed(-1, "need param:{clientId}");
        }
        String cmd = NanoUtil.getParam("cmd", httpSession);
        if (StringUtils.isBlank(cmd)) {
            return NanoUtil.failed(-1, "need param:{cmd}");
        }

        return echoNatServer.getEchoRemoteControlManager()
                .sendRemoteControlMessage(clientId, CmdHandler.ACTION_SHELL, cmd);

    }
}
