package com.mushurelie.mcmcp.proxy;

import com.mushurelie.mcmcp.MinecraftMcp;
import com.mushurelie.mcmcp.config.McpConfig;
import com.mushurelie.mcmcp.mcp.spi.McpLogger;
import com.mushurelie.mcmcp.server.McpHttpServer;
import com.mushurelie.mcmcp.tools.DefaultToolRegistryFactory;
import com.mushurelie.mcmcp.tools.ToolRegistry;
import com.mushurelie.mcmcp.util.ForgeMcpLogger;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStoppingEvent;

/**
 * Proxy cote client. Le serveur HTTP MCP est demarre ici (FMLInitializationEvent),
 * jamais cote serveur dedie : ce mod est concu pour le solo (client integre).
 */
public class ClientProxy extends CommonProxy {

    private static McpHttpServer httpServer;

    @Override
    public void preInit(FMLPreInitializationEvent event) {
        super.preInit(event);
    }

    @Override
    public void init(FMLInitializationEvent event) {
        super.init(event);

        McpLogger logger = new ForgeMcpLogger();
        McpConfig config = new McpConfig();
        ToolRegistry registry = DefaultToolRegistryFactory.createDefault(logger, config);
        httpServer = new McpHttpServer(registry, MinecraftMcp.VERSION, logger, config);
        httpServer.start();

        // Garde-fou supplementaire : si la JVM se ferme brutalement (crash, Ctrl+C),
        // on tente quand meme de liberer le port proprement.
        Runtime.getRuntime().addShutdownHook(new Thread(ClientProxy::stopHttpServer, "mcmcp-shutdown-hook"));
    }

    @Override
    public void serverStopping(FMLServerStoppingEvent event) {
        super.serverStopping(event);
        stopHttpServer();
    }

    private static void stopHttpServer() {
        if (httpServer != null) {
            try {
                httpServer.stop();
            } catch (Exception e) {
                MinecraftMcp.logger.error("Erreur pendant l'arret du serveur MCP", e);
            } finally {
                httpServer = null;
            }
        }
    }
}
