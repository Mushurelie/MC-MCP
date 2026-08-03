package com.mushurelie.mcmcp.util;

import com.google.gson.JsonObject;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.common.FMLCommonHandler;

/**
 * Helpers pour lire/ecrire des BlockPos et resoudre des World (dimension) a partir
 * de parametres JSON-RPC. A utiliser uniquement depuis le thread principal
 * (voir dispatch/MainThreadDispatcher) : WorldServer n'est pas thread-safe.
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
     * Resout le WorldServer correspondant au parametre optionnel "dimension".
     * Si absent, retourne la dimension de l'unique joueur en solo (overworld par defaut).
     * DOIT etre appele depuis le thread serveur.
     */
    public static WorldServer resolveWorld(JsonObject params) {
        MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
        if (server == null) {
            throw new IllegalStateException("Serveur integre indisponible");
        }
        int dimension;
        if (params != null && params.has("dimension") && !params.get("dimension").isJsonNull()) {
            dimension = params.get("dimension").getAsInt();
        } else {
            EntityPlayerMP player = getSinglePlayer(server);
            dimension = player != null ? player.dimension : 0;
        }
        WorldServer world = server.getWorld(dimension);
        if (world == null) {
            throw new IllegalArgumentException("Dimension inconnue : " + dimension);
        }
        return world;
    }

    /** Retourne le joueur unique de la partie solo, ou null si aucune partie n'est chargee. */
    public static EntityPlayerMP getSinglePlayer(MinecraftServer server) {
        if (server == null || server.getPlayerList() == null) {
            return null;
        }
        java.util.List<EntityPlayerMP> players = server.getPlayerList().getPlayers();
        return players.isEmpty() ? null : players.get(0);
    }
}
