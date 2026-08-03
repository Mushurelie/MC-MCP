package com.mushurelie.mcmcp.tools.entity;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mushurelie.mcmcp.dispatch.MainThreadDispatcher;
import com.mushurelie.mcmcp.mcp.JsonRpcException;
import com.mushurelie.mcmcp.mcp.types.JsonSchema;
import com.mushurelie.mcmcp.mcp.types.ToolDefinition;
import com.mushurelie.mcmcp.tools.Tool;
import com.mushurelie.mcmcp.util.BlockPosCodec;
import com.mushurelie.mcmcp.util.NbtToJson;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.FMLCommonHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Outils "Entites" : liste/detail/spawn/kill/suivi. follow_entity est une
 * implementation simplifiee (TODO(v2) : vrai PathNavigate) plafonnee dans le temps,
 * a l'image de walk_to.
 */
public final class EntityTools {

    private static final int MAX_LIST_RADIUS = 64;
    private static final long FOLLOW_MAX_DURATION_MS = 30_000L;
    private static final double FOLLOW_STOP_DISTANCE = 3.0;
    private static final double STEP_PER_TICK = 0.215;

    private EntityTools() {
    }

    public static List<Tool> createAll() {
        List<Tool> tools = new ArrayList<>();
        tools.add(new ListEntitiesTool());
        tools.add(new GetEntityTool());
        tools.add(new SpawnEntityTool());
        tools.add(new KillEntityTool());
        tools.add(new FollowEntityTool());
        return tools;
    }

    private static MinecraftServer requireServer() {
        MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
        if (server == null) {
            throw new JsonRpcException(JsonRpcException.SERVER_ERROR, "Serveur integre indisponible");
        }
        return server;
    }

    private static EntityPlayerMP requirePlayer(MinecraftServer server) {
        EntityPlayerMP player = BlockPosCodec.getSinglePlayer(server);
        if (player == null) {
            throw new JsonRpcException(JsonRpcException.SERVER_ERROR, "Aucun joueur en jeu (partie non chargee ?)");
        }
        return player;
    }

    private static JsonObject entitySummary(Entity entity) {
        JsonObject json = new JsonObject();
        json.addProperty("id", entity.getEntityId());
        json.addProperty("type", EntityList.getKey(entity) != null ? EntityList.getKey(entity).toString() : entity.getClass().getSimpleName());
        JsonObject pos = new JsonObject();
        pos.addProperty("x", entity.posX);
        pos.addProperty("y", entity.posY);
        pos.addProperty("z", entity.posZ);
        json.add("pos", pos);
        if (entity instanceof EntityLivingBase) {
            json.addProperty("health", ((EntityLivingBase) entity).getHealth());
        }
        if (entity.hasCustomName()) {
            json.addProperty("name", entity.getCustomNameTag());
        }
        json.addProperty("isHostile", entity instanceof IMob);
        return json;
    }

    // ---------------------------------------------------------------- list_entities

    static final class ListEntitiesTool implements Tool {
        @Override
        public ToolDefinition getDefinition() {
            JsonObject schema = JsonSchema.object()
                .prop("radius", JsonSchema.number("Rayon de recherche autour du joueur (max " + MAX_LIST_RADIUS + ")"), true)
                .prop("filter", JsonSchema.string("Sous-chaine (insensible a la casse) filtrant le type d'entite. Optionnel."), false)
                .build();
            return new ToolDefinition("list_entities",
                "Liste les entites presentes autour du joueur dans un rayon donne, avec filtre optionnel par type.",
                schema, true);
        }

        @Override
        public CompletableFuture<JsonElement> execute(JsonObject params) {
            return MainThreadDispatcher.runOnServerThread(() -> {
                EntityPlayerMP player = requirePlayer(requireServer());
                double radius = Math.min(params.get("radius").getAsDouble(), MAX_LIST_RADIUS);
                String filter = params.has("filter") ? params.get("filter").getAsString().toLowerCase() : null;

                AxisAlignedBB box = new AxisAlignedBB(
                    player.posX - radius, player.posY - radius, player.posZ - radius,
                    player.posX + radius, player.posY + radius, player.posZ + radius);
                List<Entity> entities = player.world.getEntitiesWithinAABB(Entity.class, box, e -> e != player);

                JsonArray array = new JsonArray();
                for (Entity entity : entities) {
                    if (filter != null) {
                        String typeName = EntityList.getKey(entity) != null ? EntityList.getKey(entity).toString().toLowerCase() : "";
                        if (!typeName.contains(filter)) {
                            continue;
                        }
                    }
                    array.add(entitySummary(entity));
                }
                JsonObject result = new JsonObject();
                result.add("entities", array);
                return result;
            });
        }
    }

    // ---------------------------------------------------------------- get_entity

    static final class GetEntityTool implements Tool {
        @Override
        public ToolDefinition getDefinition() {
            JsonObject schema = JsonSchema.object()
                .prop("id", JsonSchema.integer("Id numerique de l'entite (voir list_entities)"), true)
                .build();
            return new ToolDefinition("get_entity", "Renvoie les details complets d'une entite (position, sante, NBT tronque).", schema, true);
        }

        @Override
        public CompletableFuture<JsonElement> execute(JsonObject params) {
            return MainThreadDispatcher.runOnServerThread(() -> {
                EntityPlayerMP player = requirePlayer(requireServer());
                int id = params.get("id").getAsInt();
                Entity entity = player.world.getEntityByID(id);
                if (entity == null) {
                    throw new JsonRpcException(JsonRpcException.SERVER_ERROR, "Entite introuvable : " + id);
                }
                JsonObject result = entitySummary(entity);
                JsonObject rotation = new JsonObject();
                rotation.addProperty("yaw", entity.rotationYaw);
                rotation.addProperty("pitch", entity.rotationPitch);
                result.add("rotation", rotation);
                NBTTagCompound nbt = new NBTTagCompound();
                entity.writeToNBT(nbt);
                result.add("nbt", NbtToJson.compoundToJson(nbt));
                return result;
            });
        }
    }

    // ---------------------------------------------------------------- spawn_entity

    static final class SpawnEntityTool implements Tool {
        @Override
        public ToolDefinition getDefinition() {
            JsonObject schema = JsonSchema.object()
                .prop("type", JsonSchema.string("Identifiant de registre de l'entite, ex: 'minecraft:zombie'"), true)
                .prop("x", JsonSchema.number("Coordonnee X"), true)
                .prop("y", JsonSchema.number("Coordonnee Y"), true)
                .prop("z", JsonSchema.number("Coordonnee Z"), true)
                .prop("nbt", JsonSchema.object("NBT optionnel a fusionner apres creation"), false)
                .build();
            return new ToolDefinition("spawn_entity", "Fait apparaitre une entite a une position donnee.", schema, false);
        }

        @Override
        public CompletableFuture<JsonElement> execute(JsonObject params) {
            return MainThreadDispatcher.runOnServerThread(() -> {
                EntityPlayerMP player = requirePlayer(requireServer());
                String type = params.get("type").getAsString();
                double x = params.get("x").getAsDouble();
                double y = params.get("y").getAsDouble();
                double z = params.get("z").getAsDouble();

                net.minecraft.util.ResourceLocation location = new net.minecraft.util.ResourceLocation(type);
                Entity entity = EntityList.createEntityByIDFromName(location, player.world);
                if (entity == null) {
                    throw new JsonRpcException(JsonRpcException.INVALID_PARAMS, "Type d'entite inconnu : " + type);
                }
                entity.setLocationAndAngles(x, y, z, 0.0f, 0.0f);
                if (params.has("nbt") && !params.get("nbt").isJsonNull()) {
                    NBTTagCompound existing = new NBTTagCompound();
                    entity.writeToNBT(existing);
                    NBTTagCompound patch = NbtToJson.jsonToCompound(params.get("nbt"));
                    existing.merge(patch);
                    entity.readFromNBT(existing);
                    entity.setPosition(x, y, z);
                }
                boolean success = player.world.spawnEntity(entity);
                JsonObject result = new JsonObject();
                result.addProperty("success", success);
                result.addProperty("entityId", entity.getEntityId());
                return result;
            });
        }
    }

    // ---------------------------------------------------------------- kill_entity

    static final class KillEntityTool implements Tool {
        @Override
        public ToolDefinition getDefinition() {
            JsonObject schema = JsonSchema.object()
                .prop("id", JsonSchema.integer("Id numerique de l'entite a tuer"), true)
                .build();
            return new ToolDefinition("kill_entity", "Tue instantanement une entite (equivalent de /kill).", schema, false);
        }

        @Override
        public CompletableFuture<JsonElement> execute(JsonObject params) {
            return MainThreadDispatcher.runOnServerThread(() -> {
                EntityPlayerMP player = requirePlayer(requireServer());
                int id = params.get("id").getAsInt();
                Entity entity = player.world.getEntityByID(id);
                if (entity == null) {
                    throw new JsonRpcException(JsonRpcException.SERVER_ERROR, "Entite introuvable : " + id);
                }
                entity.onKillCommand();
                JsonObject result = new JsonObject();
                result.addProperty("success", true);
                return result;
            });
        }
    }

    // ---------------------------------------------------------------- follow_entity

    static final class FollowEntityTool implements Tool {
        @Override
        public ToolDefinition getDefinition() {
            JsonObject schema = JsonSchema.object()
                .prop("id", JsonSchema.integer("Id numerique de l'entite a suivre"), true)
                .build();
            return new ToolDefinition("follow_entity",
                "Fait suivre une entite par le joueur (TODO(v2) : implementation simplifiee, plafonnee a "
                    + (FOLLOW_MAX_DURATION_MS / 1000) + "s, sans evitement d'obstacles avance).",
                schema, false);
        }

        @Override
        public CompletableFuture<JsonElement> execute(JsonObject params) {
            MinecraftServer server = requireServer();
            EntityPlayerMP player = requirePlayer(server);
            int entityId = params.get("id").getAsInt();
            Entity target = player.world.getEntityByID(entityId);
            if (target == null) {
                throw new JsonRpcException(JsonRpcException.SERVER_ERROR, "Entite introuvable : " + entityId);
            }
            long deadline = System.currentTimeMillis() + FOLLOW_MAX_DURATION_MS;

            CompletableFuture<JsonElement> future = new CompletableFuture<>();
            server.addScheduledTask(() -> tick(server, player, target, deadline, future));
            return future;
        }

        private void tick(MinecraftServer server, EntityPlayerMP player, Entity target, long deadline,
                           CompletableFuture<JsonElement> future) {
            try {
                if (target.isDead) {
                    complete(future, false, "target_gone");
                    return;
                }
                double dx = target.posX - player.posX;
                double dz = target.posZ - player.posZ;
                double distance = Math.sqrt(dx * dx + dz * dz);

                if (distance <= FOLLOW_STOP_DISTANCE) {
                    complete(future, true, "close_enough");
                    return;
                }
                if (System.currentTimeMillis() >= deadline) {
                    complete(future, true, "duration_elapsed");
                    return;
                }

                double step = Math.min(STEP_PER_TICK, distance);
                double nextX = player.posX + (dx / distance) * step;
                double nextZ = player.posZ + (dz / distance) * step;
                float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));

                BlockPos feet = new BlockPos(nextX, player.posY, nextZ);
                if (player.world.isAirBlock(feet) && player.world.isAirBlock(feet.up())) {
                    player.connection.setPlayerLocation(nextX, player.posY, nextZ, yaw, player.rotationPitch);
                }

                server.addScheduledTask(() -> tick(server, player, target, deadline, future));
            } catch (Throwable t) {
                future.completeExceptionally(t);
            }
        }

        private static void complete(CompletableFuture<JsonElement> future, boolean success, String reason) {
            JsonObject result = new JsonObject();
            result.addProperty("success", success);
            result.addProperty("reason", reason);
            future.complete(result);
        }
    }
}
