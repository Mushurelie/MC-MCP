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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.phys.AABB;
import net.minecraft.core.BlockPos;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Outils "Entites" : liste/detail/spawn/kill/suivi. follow_entity est une
 * implementation simplifiee (TODO(v2) : vrai pathfinding) plafonnee dans le temps,
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
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            throw new JsonRpcException(JsonRpcException.SERVER_ERROR, "Serveur integre indisponible");
        }
        return server;
    }

    private static ServerPlayer requirePlayer(MinecraftServer server) {
        ServerPlayer player = BlockPosCodec.getSinglePlayer(server);
        if (player == null) {
            throw new JsonRpcException(JsonRpcException.SERVER_ERROR, "Aucun joueur en jeu (partie non chargee ?)");
        }
        return player;
    }

    private static String typeKey(Entity entity) {
        return entity.getType().builtInRegistryHolder().key().location() != null
            ? entity.getType().builtInRegistryHolder().key().location().toString()
            : entity.getClass().getSimpleName();
    }

    private static JsonObject entitySummary(Entity entity) {
        JsonObject json = new JsonObject();
        json.addProperty("id", entity.getId());
        json.addProperty("type", typeKey(entity));
        JsonObject pos = new JsonObject();
        pos.addProperty("x", entity.getX());
        pos.addProperty("y", entity.getY());
        pos.addProperty("z", entity.getZ());
        json.add("pos", pos);
        if (entity instanceof LivingEntity) {
            json.addProperty("health", ((LivingEntity) entity).getHealth());
        }
        if (entity.hasCustomName() && entity.getCustomName() != null) {
            json.addProperty("name", entity.getCustomName().getString());
        }
        json.addProperty("isHostile", entity instanceof Enemy);
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
                ServerPlayer player = requirePlayer(requireServer());
                double radius = Math.min(params.get("radius").getAsDouble(), MAX_LIST_RADIUS);
                String filter = params.has("filter") ? params.get("filter").getAsString().toLowerCase() : null;

                AABB box = new AABB(
                    player.getX() - radius, player.getY() - radius, player.getZ() - radius,
                    player.getX() + radius, player.getY() + radius, player.getZ() + radius);
                List<Entity> entities = player.level().getEntities(player, box, e -> true);

                JsonArray array = new JsonArray();
                for (Entity entity : entities) {
                    if (filter != null && !typeKey(entity).toLowerCase().contains(filter)) {
                        continue;
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
                ServerPlayer player = requirePlayer(requireServer());
                int id = params.get("id").getAsInt();
                Entity entity = player.level().getEntity(id);
                if (entity == null) {
                    throw new JsonRpcException(JsonRpcException.SERVER_ERROR, "Entite introuvable : " + id);
                }
                JsonObject result = entitySummary(entity);
                JsonObject rotation = new JsonObject();
                rotation.addProperty("yaw", entity.getYRot());
                rotation.addProperty("pitch", entity.getXRot());
                result.add("rotation", rotation);
                CompoundTag nbt = new CompoundTag();
                entity.save(nbt);
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
                ServerPlayer player = requirePlayer(requireServer());
                String type = params.get("type").getAsString();
                double x = params.get("x").getAsDouble();
                double y = params.get("y").getAsDouble();
                double z = params.get("z").getAsDouble();

                Optional<EntityType<?>> entityType = EntityType.byString(type);
                if (!entityType.isPresent()) {
                    throw new JsonRpcException(JsonRpcException.INVALID_PARAMS, "Type d'entite inconnu : " + type);
                }
                Entity entity = entityType.get().create(player.level());
                if (entity == null) {
                    throw new JsonRpcException(JsonRpcException.INVALID_PARAMS, "Impossible de creer l'entite : " + type);
                }
                entity.absMoveTo(x, y, z, 0.0f, 0.0f);
                if (params.has("nbt") && !params.get("nbt").isJsonNull()) {
                    CompoundTag existing = new CompoundTag();
                    entity.save(existing);
                    CompoundTag patch = NbtToJson.jsonToCompound(params.get("nbt"));
                    existing.merge(patch);
                    entity.load(existing);
                    entity.setPos(x, y, z);
                }
                boolean success = player.level().addFreshEntity(entity);
                JsonObject result = new JsonObject();
                result.addProperty("success", success);
                result.addProperty("entityId", entity.getId());
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
                ServerPlayer player = requirePlayer(requireServer());
                int id = params.get("id").getAsInt();
                Entity entity = player.level().getEntity(id);
                if (entity == null) {
                    throw new JsonRpcException(JsonRpcException.SERVER_ERROR, "Entite introuvable : " + id);
                }
                entity.kill();
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
            ServerPlayer player = requirePlayer(server);
            int entityId = params.get("id").getAsInt();
            Entity target = player.level().getEntity(entityId);
            if (target == null) {
                throw new JsonRpcException(JsonRpcException.SERVER_ERROR, "Entite introuvable : " + entityId);
            }
            long deadline = System.currentTimeMillis() + FOLLOW_MAX_DURATION_MS;

            CompletableFuture<JsonElement> future = new CompletableFuture<>();
            server.execute(() -> tick(server, player, target, deadline, future));
            return future;
        }

        private void tick(MinecraftServer server, ServerPlayer player, Entity target, long deadline,
                           CompletableFuture<JsonElement> future) {
            try {
                if (!target.isAlive()) {
                    complete(future, false, "target_gone");
                    return;
                }
                double dx = target.getX() - player.getX();
                double dz = target.getZ() - player.getZ();
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
                double nextX = player.getX() + (dx / distance) * step;
                double nextZ = player.getZ() + (dz / distance) * step;
                float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));

                BlockPos feet = BlockPos.containing(nextX, player.getY(), nextZ);
                if (player.level().isEmptyBlock(feet) && player.level().isEmptyBlock(feet.above())) {
                    player.connection.teleport(nextX, player.getY(), nextZ, yaw, player.getXRot());
                }

                server.execute(() -> tick(server, player, target, deadline, future));
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
