package com.virjar.echo.nat.log;

public class SystemOutLogger implements ILogger {
    @Override
    public void info(String msg) {
        System.out.println(msg);
    }

    @Override
    public void info(String msg, Throwable throwable) {
        System.out.println(msg);
        throwable.printStackTrace(System.out);
    }

    @Override
    public void warn(String msg) {
        System.out.println(msg);
    }

    @Override
    public void warn(String msg, Throwable throwable) {
        System.out.println(msg);
        throwable.printStackTrace(System.out);
    }

    @Override
    public void error(String msg) {
        System.err.println(msg);
    }

    @Override
    public void error(String msg, Throwable throwable) {
        System.err.println(msg);
        throwable.printStackTrace(System.err);
    }

    @Override
    public void debug(String msg) {
        System.err.println(msg);
    }

    @Override
    public void debug(String msg, Throwable throwable) {
        System.err.println(msg);
        throwable.printStackTrace(System.err);
    }
}
