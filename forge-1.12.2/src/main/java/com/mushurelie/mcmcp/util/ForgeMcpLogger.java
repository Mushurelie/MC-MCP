package com.mushurelie.mcmcp.util;

import com.mushurelie.mcmcp.MinecraftMcp;
import com.mushurelie.mcmcp.mcp.spi.McpLogger;

/**
 * Adaptateur McpLogger delegant au logger Forge (Log4j2) recupere dans
 * FMLPreInitializationEvent.getModLog(). Meme convention varargs +
 * Throwable final que Log4j2/SLF4J.
 */
public final class ForgeMcpLogger implements McpLogger {

    @Override
    public void info(String format, Object... args) {
        MinecraftMcp.logger.info(format, args);
    }

    @Override
    public void warn(String format, Object... args) {
        MinecraftMcp.logger.warn(format, args);
    }

    @Override
    public void error(String format, Object... args) {
        MinecraftMcp.logger.error(format, args);
    }
}
