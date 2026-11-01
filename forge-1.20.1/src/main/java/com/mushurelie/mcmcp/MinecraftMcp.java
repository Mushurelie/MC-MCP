package com.mushurelie.mcmcp;

import com.mushurelie.mcmcp.config.McpConfig;
import com.mushurelie.mcmcp.mcp.spi.McpLogger;
import com.mushurelie.mcmcp.server.McpHttpServer;
import com.mushurelie.mcmcp.tools.DefaultToolRegistryFactory;
import com.mushurelie.mcmcp.tools.ToolRegistry;
import com.mushurelie.mcmcp.util.ForgeMcpLogger;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

/**
 * Point d'entree du mod. Le serveur HTTP MCP est demarre dans FMLClientSetupEvent
 * (qui ne se declenche que cote client) : ce mod ne fonctionne qu'en solo.
 * Le serveur HTTP vit aussi longtemps que le client (il n'est pas arrete en quittant
 * une partie) : les outils renvoient simplement une erreur tant qu'aucun monde n'est charge.
 */
@Mod(MinecraftMcp.MODID)
public class MinecraftMcp {

    public static final String MODID = "mcmcp";
    public static final String VERSION = "0.1.0";

    private static McpHttpServer httpServer;

    public MinecraftMcp() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, McpConfig.SPEC);
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::onClientSetup);
    }

    private void onClientSetup(final FMLClientSetupEvent event) {
        McpLogger logger = new ForgeMcpLogger();
        McpConfig config = new McpConfig();
        ToolRegistry registry = DefaultToolRegistryFactory.createDefault(logger, config);
        httpServer = new McpHttpServer(registry, VERSION, logger, config);
        httpServer.start();

        Runtime.getRuntime().addShutdownHook(new Thread(MinecraftMcp::stopHttpServer, "mcmcp-shutdown-hook"));
    }

    private static void stopHttpServer() {
        if (httpServer != null) {
            httpServer.stop();
            httpServer = null;
        }
    }
}
