package com.mushurelie.mcmcp.mcp.spi;

/**
 * Abstraction de la configuration du serveur MCP, independante du systeme de
 * config du loader (ex : @Config classique en 1.12.2 vs ForgeConfigSpec en 1.13+).
 */
public interface McpServerConfig {
    String getBindAddress();

    int getPort();

    /** Chaine vide = pas de jeton bearer requis. */
    String getBearerToken();

    boolean isReadOnlyMode();

    boolean isToolAllowed(String toolName);

    boolean isRequireConfirmation();

    int getConfirmationTimeoutSeconds();
}
