package com.virjar.echo.server.common.eventbus;

public interface EventHandler {
    void handleEvent(ComponentEvent componentEvent);
}
