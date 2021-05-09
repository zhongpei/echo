package com.virjar.echo.server.common.safethread;

public interface ValueCallback<T> {
    void onReceiveValue(T value);
}
