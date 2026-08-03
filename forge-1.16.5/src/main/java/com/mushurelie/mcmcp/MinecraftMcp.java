package com.mushurelie.mcmcp;

import com.mushurelie.mcmcp.config.McpConfig;
import com.mushurelie.mcmcp.mcp.spi.McpLogger;
import com.mushurelie.mcmcp.server.McpHttpServer;
import com.mushurelie.mcmcp.tools.DefaultToolRegistryFactory;
import com.mushurelie.mcmcp.tools.ToolRegistry;
import com.mushurelie.mcmcp.util.ForgeMcpLogger;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.server.FMLServerStoppingEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Point d'entree du mod. Le serveur HTTP MCP est demarre dans FMLClientSetupEvent
 * (qui ne se declenche que cote client) : ce mod ne fonctionne qu'en solo.
 */
@Mod(MinecraftMcp.MODID)
public class MinecraftMcp {

    public static final String MODID = "mcmcp";
    public static final String VERSION = "0.1.0";

    private static McpHttpServer httpServer;

    public MinecraftMcp() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, McpConfig.SPEC);
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::onClientSetup);
        MinecraftForge.EVENT_BUS.register(this);
    }

    private void onClientSetup(final FMLClientSetupEvent event) {
        McpLogger logger = new ForgeMcpLogger();
        McpConfig config = new McpConfig();
        ToolRegistry registry = DefaultToolRegistryFactory.createDefault(logger, config);
        httpServer = new McpHttpServer(registry, VERSION, logger, config);
        httpServer.start();

        Runtime.getRuntime().addShutdownHook(new Thread(MinecraftMcp::stopHttpServer, "mcmcp-shutdown-hook"));
    }

    @SubscribeEvent
    public void onServerStopping(final FMLServerStoppingEvent event) {
        stopHttpServer();
    }

    private static void stopHttpServer() {
        if (httpServer != null) {
            httpServer.stop();
            httpServer = null;
        }
    }
}
