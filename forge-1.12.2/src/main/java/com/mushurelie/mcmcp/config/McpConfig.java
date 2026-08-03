package com.mushurelie.mcmcp.config;

import com.mushurelie.mcmcp.MinecraftMcp;
import com.mushurelie.mcmcp.mcp.spi.McpServerConfig;
import net.minecraftforge.common.config.Config;
import net.minecraftforge.common.config.ConfigManager;
import net.minecraftforge.fml.client.event.ConfigChangedEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Configuration Forge classique (@Config, style 1.12.2) persistee dans
 * config/mcmcp.cfg. A ne pas confondre avec le protocole "MCP" (Model Context Protocol).
 */
@Config(modid = MinecraftMcp.MODID, name = "mcmcp")
@Mod.EventBusSubscriber(modid = MinecraftMcp.MODID)
public class McpConfig implements McpServerConfig {

    @Config.Comment("Port TCP d'ecoute du serveur HTTP MCP.")
    @Config.RangeInt(min = 1, max = 65535)
    public static int port = 25567;

    @Config.Comment({
        "Adresse d'ecoute du serveur HTTP.",
        "JAMAIS 0.0.0.0 par defaut : cela exposerait le controle de la partie au reseau local."
    })
    public static String bindAddress = "127.0.0.1";

    @Config.Comment("Si true (par defaut), bloque tous les outils marques comme ecriture (write=true).")
    public static boolean readOnlyMode = true;

    @Config.Comment("Liste blanche de noms d'outils autorises (snake_case). Vide = tous les outils sont autorises (sous reserve de la blacklist).")
    public static String[] toolsWhitelist = new String[0];

    @Config.Comment("Liste noire de noms d'outils interdits (snake_case). Prioritaire sur la whitelist.")
    public static String[] toolsBlacklist = new String[0];

    @Config.Comment("Si true, affiche une popup en jeu (accepter/refuser/timeout) avant chaque appel d'outil en ecriture.")
    public static boolean requireConfirmation = false;

    @Config.Comment("Delai en secondes avant qu'une confirmation en jeu ne soit automatiquement refusee.")
    @Config.RangeInt(min = 1, max = 300)
    public static int confirmationTimeoutSeconds = 30;

    @Config.Comment("Niveau de log applicatif du mod : DEBUG, INFO, WARN, ERROR.")
    public static String logLevel = "INFO";

    @Config.Comment("Jeton bearer optionnel. Si non vide, chaque requete HTTP doit fournir l'entete 'Authorization: Bearer <token>'.")
    public static String bearerToken = "";

    @Config.Comment("Volume maximum (nombre de blocs) autorise pour fill_area et clone_area, pour eviter de figer le jeu.")
    @Config.RangeInt(min = 1, max = 2_000_000)
    public static int maxVolumeBlocks = 200_000;

    public static void init() {
        ConfigManager.sync(MinecraftMcp.MODID, Config.Type.INSTANCE);
    }

    @SubscribeEvent
    public static void onConfigChanged(ConfigChangedEvent.OnConfigChangedEvent event) {
        if (event.getModID().equals(MinecraftMcp.MODID)) {
            ConfigManager.sync(MinecraftMcp.MODID, Config.Type.INSTANCE);
        }
    }

    /**
     * Determine si un outil (par son nom snake_case) est autorise a s'executer,
     * en tenant compte de la whitelist et de la blacklist.
     */
    public static boolean isToolAllowedStatic(String toolName) {
        Set<String> blacklist = new HashSet<>(Arrays.asList(toolsBlacklist));
        if (blacklist.contains(toolName)) {
            return false;
        }
        Set<String> whitelist = new HashSet<>(Arrays.asList(toolsWhitelist));
        if (whitelist.isEmpty()) {
            return true;
        }
        return whitelist.contains(toolName);
    }

    @Override
    public String getBindAddress() {
        return bindAddress;
    }

    @Override
    public int getPort() {
        return port;
    }

    @Override
    public String getBearerToken() {
        return bearerToken;
    }

    @Override
    public boolean isReadOnlyMode() {
        return readOnlyMode;
    }

    @Override
    public boolean isToolAllowed(String toolName) {
        return isToolAllowedStatic(toolName);
    }

    @Override
    public boolean isRequireConfirmation() {
        return requireConfirmation;
    }

    @Override
    public int getConfirmationTimeoutSeconds() {
        return confirmationTimeoutSeconds;
    }
}
