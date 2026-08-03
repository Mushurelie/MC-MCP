package com.mushurelie.mcmcp.proxy;

import com.mushurelie.mcmcp.MinecraftMcp;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;

/**
 * Proxy cote serveur dedie. Ce mod est concu pour du single-player uniquement
 * (client integre + serveur integre dans la meme JVM) : sur un serveur dedie,
 * on se contente de logguer un avertissement, sans jamais ouvrir de port HTTP.
 */
public class ServerProxy extends CommonProxy {

    @Override
    public void init(FMLInitializationEvent event) {
        super.init(event);
        MinecraftMcp.logger.warn(
            "Minecraft MCP est concu pour le mode solo (client integre). "
                + "Aucun serveur HTTP MCP ne sera demarre sur un serveur dedie."
        );
    }
}
