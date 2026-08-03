package com.mushurelie.mcmcp.mcp.spi;

/**
 * Abstraction de journalisation independante du loader/version Minecraft.
 * Chaque module de version fournit une implementation qui delegue au logger
 * natif du loader (ex : le Logger Log4j2 fourni par FML). La convention
 * "dernier argument = Throwable optionnel" suit celle de SLF4J/Log4j2.
 */
public interface McpLogger {
    void info(String format, Object... args);

    void warn(String format, Object... args);

    void error(String format, Object... args);
}
