package com.virjar.echo.nat.cmd;

public interface CmdHandler {
    String ACTION_SHELL = "shell";
    String ACTION_ANDROID_REDIAL = "androidReDial";

    String action();

    void handle(String param, CmdResponse cmdResponse);
}
