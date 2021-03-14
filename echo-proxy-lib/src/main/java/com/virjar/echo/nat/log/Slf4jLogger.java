package com.virjar.echo.nat.log;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Slf4jLogger implements ILogger {
    private static final Logger logger = LoggerFactory.getLogger(EchoLogger.tag);

    @Override
    public void info(String msg) {
        logger.info(msg);
    }

    @Override
    public void info(String msg, Throwable throwable) {
        logger.info(msg, throwable);
    }

    @Override
    public void warn(String msg) {
        logger.warn(msg);
    }

    @Override
    public void warn(String msg, Throwable throwable) {
        logger.warn(msg, throwable);
    }

    @Override
    public void error(String msg) {
        logger.error(msg);
    }


    @Override
    public void error(String msg, Throwable throwable) {
        logger.error(msg, throwable);
    }

    @Override
    public void debug(String msg) {
        logger.debug(msg);
    }

    @Override
    public void debug(String msg, Throwable throwable) {
        logger.debug(msg, throwable);
    }
}
