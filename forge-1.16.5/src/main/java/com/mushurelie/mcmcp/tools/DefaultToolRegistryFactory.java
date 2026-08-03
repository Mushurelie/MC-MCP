package com.mushurelie.mcmcp.tools;

import com.mushurelie.mcmcp.mcp.spi.McpLogger;
import com.mushurelie.mcmcp.mcp.spi.McpServerConfig;

/**
 * Cablage des outils de cette version Minecraft dans un ToolRegistry.
 * TODO(portage 1.16.5) : brancher les 49 outils une fois portes (voir forge-1.12.2
 * pour le modele de reference).
 */
public final class DefaultToolRegistryFactory {

    private DefaultToolRegistryFactory() {
    }

    public static ToolRegistry createDefault(McpLogger logger, McpServerConfig config) {
        return new ToolRegistry(logger, config, new ConfirmationManager());
    }
}
