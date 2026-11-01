package com.mushurelie.mcmcp.tools;

import com.mushurelie.mcmcp.mcp.spi.McpLogger;
import com.mushurelie.mcmcp.mcp.spi.McpServerConfig;
import com.mushurelie.mcmcp.tools.agent.AgentTools;
import com.mushurelie.mcmcp.tools.build.BuildTools;
import com.mushurelie.mcmcp.tools.chat.ChatTools;
import com.mushurelie.mcmcp.tools.craft.CraftTools;
import com.mushurelie.mcmcp.tools.entity.EntityTools;
import com.mushurelie.mcmcp.tools.meta.MetaTools;
import com.mushurelie.mcmcp.tools.observe.ObserveTools;
import com.mushurelie.mcmcp.tools.player.PlayerTools;
import com.mushurelie.mcmcp.tools.redstone.RedstoneTools;
import com.mushurelie.mcmcp.tools.world.WorldTools;
import com.mushurelie.mcmcp.tools.worldglobal.WorldGlobalTools;

/**
 * Cablage des 49 outils de cette version Minecraft dans un ToolRegistry.
 * Point unique a adapter lors du portage vers une nouvelle version.
 */
public final class DefaultToolRegistryFactory {

    private DefaultToolRegistryFactory() {
    }

    public static ToolRegistry createDefault(McpLogger logger, McpServerConfig config) {
        ToolRegistry registry = new ToolRegistry(logger, config, new ConfirmationManager());
        registry.registerAll(WorldTools.createAll());
        registry.registerAll(PlayerTools.createAll());
        registry.registerAll(AgentTools.createAll());
        registry.registerAll(EntityTools.createAll());
        registry.registerAll(ChatTools.createAll());
        registry.registerAll(WorldGlobalTools.createAll());
        registry.registerAll(CraftTools.createAll());
        registry.registerAll(BuildTools.createAll());
        registry.registerAll(ObserveTools.createAll());
        registry.registerAll(RedstoneTools.createAll());
        registry.registerAll(MetaTools.createAll(registry));
        return registry;
    }
}
