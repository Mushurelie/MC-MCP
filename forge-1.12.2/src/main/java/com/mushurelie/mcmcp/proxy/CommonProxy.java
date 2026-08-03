package com.mushurelie.mcmcp.proxy;

import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStoppingEvent;

/**
 * Proxy commun. Ne contient aucune logique de serveur HTTP : celle-ci est
 * exclusivement cote client (voir ClientProxy), conformement a la contrainte
 * "single-player uniquement, client integre + serveur integre".
 */
public class CommonProxy {

    public void preInit(FMLPreInitializationEvent event) {
        // Rien a faire cote commun pour l'instant.
    }

    public void init(FMLInitializationEvent event) {
        // Rien a faire cote commun pour l'instant.
    }

    public void serverStopping(FMLServerStoppingEvent event) {
        // Rien a faire cote commun pour l'instant.
    }
}
