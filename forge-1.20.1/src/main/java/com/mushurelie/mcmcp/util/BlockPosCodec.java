package com.mushurelie.mcmcp.util;

import com.google.gson.JsonObject;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.server.ServerLifecycleHooks;

/**
 * Helpers pour lire/ecrire des BlockPos et resoudre des ServerLevel (dimension) a partir
 * de parametres JSON-RPC. A utiliser uniquement depuis le thread principal
 * (voir dispatch/MainThreadDispatcher) : ServerLevel n'est pas thread-safe.
 */
public final class BlockPosCodec {

    private BlockPosCodec() {
    }

    public static BlockPos fromJson(JsonObject params) {
        int x = params.get("x").getAsInt();
        int y = params.get("y").getAsInt();
        int z = params.get("z").getAsInt();
        return new BlockPos(x, y, z);
    }

    /** Lit x{suffix}/y{suffix}/z{suffix}, ex: fromJson(params, "1") lit x1/y1/z1 (voir fill_area). */
    public static BlockPos fromJson(JsonObject params, String suffix) {
        int x = params.get("x" + suffix).getAsInt();
        int y = params.get("y" + suffix).getAsInt();
        int z = params.get("z" + suffix).getAsInt();
        return new BlockPos(x, y, z);
    }

    public static JsonObject toJson(BlockPos pos) {
        JsonObject json = new JsonObject();
        json.addProperty("x", pos.getX());
        json.addProperty("y", pos.getY());
        json.addProperty("z", pos.getZ());
        return json;
    }

    /**
     * Resout le ServerLevel correspondant au parametre optionnel "dimension"
     * ("overworld", "the_nether" ou "the_end"). Si absent, retourne la dimension
     * de l'unique joueur en solo (overworld par defaut). DOIT etre appele depuis
     * le thread serveur.
     */
    public static ServerLevel resolveWorld(JsonObject params) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            throw new IllegalStateException("Serveur integre indisponible");
        }
        if (params != null && params.has("dimension") && !params.get("dimension").isJsonNull()) {
            String dimensionId = params.get("dimension").getAsString();
            ServerLevel world = server.getLevel(dimensionKey(dimensionId));
            if (world == null) {
                throw new IllegalArgumentException("Dimension inconnue : " + dimensionId);
            }
            return world;
        }
        ServerPlayer player = getSinglePlayer(server);
        if (player != null) {
            return player.serverLevel();
        }
        return server.getLevel(Level.OVERWORLD);
    }

    private static net.minecraft.resources.ResourceKey<Level> dimensionKey(String dimensionId) {
        switch (dimensionId) {
            case "overworld":
            case "minecraft:overworld":
                return Level.OVERWORLD;
            case "the_nether":
            case "nether":
            case "minecraft:the_nether":
                return Level.NETHER;
            case "the_end":
            case "end":
            case "minecraft:the_end":
                return Level.END;
            default:
                // Dimensions de mods (ex : 'ad_astra:moon') : identifiant de registre complet.
                return net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,
                    new net.minecraft.resources.ResourceLocation(dimensionId));
        }
    }

    /** Retourne le joueur unique de la partie solo, ou null si aucune partie n'est chargee. */
    public static ServerPlayer getSinglePlayer(MinecraftServer server) {
        if (server == null || server.getPlayerList() == null) {
            return null;
        }
        java.util.List<ServerPlayer> players = server.getPlayerList().getPlayers();
        return players.isEmpty() ? null : players.get(0);
    }
}
