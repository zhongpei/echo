package com.virjar.echo.nat.log;

import android.util.Log;
import org.slf4j.LoggerFactory;

public class EchoLogger {
    @SuppressWarnings("all")
    public static String tag = "Echo";

    private static ILogger logger = null;

    static {
        genLogger();
    }

    public static void setLogger(ILogger logger) {
        if (logger == null) {
            throw new IllegalArgumentException("input logger can not be null");
        }
        EchoLogger.logger = logger;
    }

    private static void genLogger() {
        try {
            Log.i(tag, "test sekiro log");
            logger = new AndroidLogger();
            return;
        } catch (Throwable throwable) {
            //ignore
        }

        try {
            logger = (ILogger) LoggerFactory.getLogger(tag);
            return;
        } catch (Throwable throwable) {
            //ignore
        }

        try {
            LoggerFactory.getLogger(tag).info("test sekiro log");
            logger = new Slf4jLogger();
            return;
        } catch (Throwable throwable) {
            //ignore
        }
        logger = new SystemOutLogger();
    }

    public static ILogger getLogger() {
        return logger;
    }
}
