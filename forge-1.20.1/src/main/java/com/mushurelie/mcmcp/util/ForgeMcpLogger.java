package com.mushurelie.mcmcp.util;

import com.mushurelie.mcmcp.mcp.spi.McpLogger;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Adaptateur McpLogger delegant a un logger Log4j2 standard. Meme convention
 * varargs + Throwable final que Log4j2/SLF4J.
 */
public final class ForgeMcpLogger implements McpLogger {

    private static final Logger LOGGER = LogManager.getLogger("mcmcp");

    @Override
    public void info(String format, Object... args) {
        LOGGER.info(format, args);
    }

    @Override
    public void warn(String format, Object... args) {
        LOGGER.warn(format, args);
    }

    @Override
    public void error(String format, Object... args) {
        LOGGER.error(format, args);
    }
}
