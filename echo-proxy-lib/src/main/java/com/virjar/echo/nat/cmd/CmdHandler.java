package com.virjar.echo.nat.cmd;

import com.virjar.echo.nat.client.EchoClient;

public interface CmdHandler {
    String ACTION_SHELL = "shell";
    String ACTION_ANDROID_REDIAL = "androidReDial";

    String action();

    void handle(String param, CmdResponse cmdResponse);

    void handle(String param, CmdResponse cmdResponse, EchoClient echoClient);
}
