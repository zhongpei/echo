package com.virjar.echo.nat.log;

import android.util.Log;

public class AndroidLogger implements ILogger {

    @Override
    public void info(String msg) {
        Log.i(EchoLogger.tag, msg);
    }

    @Override
    public void info(String msg, Throwable throwable) {
        Log.i(EchoLogger.tag, msg, throwable);
    }

    @Override
    public void warn(String msg) {
        Log.w(EchoLogger.tag, msg);
    }

    @Override
    public void warn(String msg, Throwable throwable) {
        Log.w(EchoLogger.tag, msg, throwable);
    }

    @Override
    public void error(String msg) {
        Log.e(EchoLogger.tag, msg);
    }

    @Override
    public void error(String msg, Throwable throwable) {
        Log.e(EchoLogger.tag, msg, throwable);
    }

    @Override
    public void debug(String msg) {
        Log.d(EchoLogger.tag, msg);
    }

    @Override
    public void debug(String msg, Throwable throwable) {
        Log.d(EchoLogger.tag, msg, throwable);
    }
}
