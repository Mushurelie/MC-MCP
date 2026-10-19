package com.mushurelie.mcmcp.config;

import com.mushurelie.mcmcp.mcp.spi.McpServerConfig;
import net.minecraftforge.common.ForgeConfigSpec;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Configuration Forge moderne (ForgeConfigSpec) persistee dans config/mcmcp-common.toml.
 * A ne pas confondre avec le protocole "MCP" (Model Context Protocol).
 */
public final class McpConfig implements McpServerConfig {

    public static final ForgeConfigSpec SPEC;

    private static final ForgeConfigSpec.IntValue PORT;
    private static final ForgeConfigSpec.ConfigValue<String> BIND_ADDRESS;
    private static final ForgeConfigSpec.BooleanValue READ_ONLY_MODE;
    private static final ForgeConfigSpec.ConfigValue<List<? extends String>> TOOLS_WHITELIST;
    private static final ForgeConfigSpec.ConfigValue<List<? extends String>> TOOLS_BLACKLIST;
    private static final ForgeConfigSpec.BooleanValue REQUIRE_CONFIRMATION;
    private static final ForgeConfigSpec.IntValue CONFIRMATION_TIMEOUT_SECONDS;
    private static final ForgeConfigSpec.ConfigValue<String> LOG_LEVEL;
    private static final ForgeConfigSpec.ConfigValue<String> BEARER_TOKEN;
    private static final ForgeConfigSpec.IntValue MAX_VOLUME_BLOCKS;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

        PORT = builder.comment("Port TCP d'ecoute du serveur HTTP MCP.")
            .defineInRange("port", 25567, 1, 65535);
        BIND_ADDRESS = builder.comment(
                "Adresse d'ecoute du serveur HTTP.",
                "JAMAIS 0.0.0.0 par defaut : cela exposerait le controle de la partie au reseau local.")
            .define("bindAddress", "127.0.0.1");
        READ_ONLY_MODE = builder.comment("Si true (par defaut), bloque tous les outils marques comme ecriture (write=true).")
            .define("readOnlyMode", true);
        TOOLS_WHITELIST = builder.comment("Liste blanche de noms d'outils autorises (snake_case). Vide = tous les outils sont autorises (sous reserve de la blacklist).")
            .defineList("toolsWhitelist", java.util.Collections.emptyList(), o -> o instanceof String);
        TOOLS_BLACKLIST = builder.comment("Liste noire de noms d'outils interdits (snake_case). Prioritaire sur la whitelist.")
            .defineList("toolsBlacklist", java.util.Collections.emptyList(), o -> o instanceof String);
        REQUIRE_CONFIRMATION = builder.comment("Si true, affiche une popup en jeu (accepter/refuser/timeout) avant chaque appel d'outil en ecriture.")
            .define("requireConfirmation", false);
        CONFIRMATION_TIMEOUT_SECONDS = builder.comment("Delai en secondes avant qu'une confirmation en jeu ne soit automatiquement refusee.")
            .defineInRange("confirmationTimeoutSeconds", 30, 1, 300);
        LOG_LEVEL = builder.comment("Niveau de log applicatif du mod : DEBUG, INFO, WARN, ERROR.")
            .define("logLevel", "INFO");
        BEARER_TOKEN = builder.comment("Jeton bearer optionnel. Si non vide, chaque requete HTTP doit fournir l'entete 'Authorization: Bearer <token>'.")
            .define("bearerToken", "");
        MAX_VOLUME_BLOCKS = builder.comment("Volume maximum (nombre de blocs) autorise pour fill_area et clone_area, pour eviter de figer le jeu.")
            .defineInRange("maxVolumeBlocks", 200_000, 1, 2_000_000);

        SPEC = builder.build();
    }

    @Override
    public String getBindAddress() {
        return BIND_ADDRESS.get();
    }

    @Override
    public int getPort() {
        return PORT.get();
    }

    @Override
    public String getBearerToken() {
        return BEARER_TOKEN.get();
    }

    @Override
    public boolean isReadOnlyMode() {
        return READ_ONLY_MODE.get();
    }

    @Override
    public boolean isToolAllowed(String toolName) {
        Set<String> blacklist = new HashSet<>(TOOLS_BLACKLIST.get());
        if (blacklist.contains(toolName)) {
            return false;
        }
        Set<String> whitelist = new HashSet<>(TOOLS_WHITELIST.get());
        if (whitelist.isEmpty()) {
            return true;
        }
        return whitelist.contains(toolName);
    }

    @Override
    public boolean isRequireConfirmation() {
        return REQUIRE_CONFIRMATION.get();
    }

    @Override
    public int getConfirmationTimeoutSeconds() {
        return CONFIRMATION_TIMEOUT_SECONDS.get();
    }

    public static String getLogLevel() {
        return LOG_LEVEL.get();
    }

    public static int getMaxVolumeBlocks() {
        return MAX_VOLUME_BLOCKS.get();
    }
}
