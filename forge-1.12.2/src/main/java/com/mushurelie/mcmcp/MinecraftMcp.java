package com.mushurelie.mcmcp;

import com.mushurelie.mcmcp.config.McpConfig;
import com.mushurelie.mcmcp.proxy.CommonProxy;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStoppingEvent;
import org.apache.logging.log4j.Logger;

/**
 * Point d'entree du mod. Cote client uniquement, le serveur HTTP MCP est demarre
 * dans FMLInitializationEvent (voir ClientProxy). Le proxy serveur (dedie) ne fait rien :
 * ce mod ne fonctionne qu'en solo (client + serveur integre dans la meme JVM).
 */
@Mod(
    modid = MinecraftMcp.MODID,
    name = MinecraftMcp.NAME,
    version = MinecraftMcp.VERSION,
    acceptedMinecraftVersions = "[1.12.2]"
)
public class MinecraftMcp {

    public static final String MODID = "mcmcp";
    public static final String NAME = "Minecraft MCP";
    public static final String VERSION = "0.1.0";

    @Mod.Instance(MODID)
    public static MinecraftMcp instance;

    @SidedProxy(
        clientSide = "com.mushurelie.mcmcp.proxy.ClientProxy",
        serverSide = "com.mushurelie.mcmcp.proxy.ServerProxy"
    )
    public static CommonProxy proxy;

    public static Logger logger;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        logger = event.getModLog();
        McpConfig.init();
        proxy.preInit(event);
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        proxy.init(event);
    }

    @Mod.EventHandler
    public void serverStopping(FMLServerStoppingEvent event) {
        proxy.serverStopping(event);
    }
}
