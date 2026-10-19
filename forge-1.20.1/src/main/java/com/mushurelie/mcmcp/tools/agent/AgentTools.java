package com.mushurelie.mcmcp.tools.agent;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mushurelie.mcmcp.dispatch.MainThreadDispatcher;
import com.mushurelie.mcmcp.mcp.JsonRpcException;
import com.mushurelie.mcmcp.mcp.types.JsonSchema;
import com.mushurelie.mcmcp.mcp.types.ToolDefinition;
import com.mushurelie.mcmcp.tools.Tool;
import com.mushurelie.mcmcp.util.BlockPosCodec;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.entity.Entity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.InteractionResult;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Outils "Agent" : font jouer le joueur a sa place (deplacement, regard, minage,
 * placement, utilisation d'item, attaque). walk_to est une implementation
 * simplifiee (TODO(v2) : vrai pathfinding avec evitement d'obstacles/sauts/portes) ;
 * ici, deplacement en ligne droite pas a pas, tick par tick, avec detection
 * d'obstacle basique.
 */
public final class AgentTools {

    private AgentTools() {
    }

    public static List<Tool> createAll() {
        List<Tool> tools = new ArrayList<>();
        tools.add(new WalkToTool());
        tools.add(new LookAtTool());
        tools.add(new JumpTool());
        tools.add(new StartSneakingTool());
        tools.add(new MineBlockTool());
        tools.add(new PlaceBlockAtTool());
        tools.add(new UseItemTool());
        tools.add(new AttackEntityTool());
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

    private static Direction parseFacing(String face) {
        Direction direction = Direction.byName(face);
        if (direction == null) {
            throw new JsonRpcException(JsonRpcException.INVALID_PARAMS,
                "Face invalide : " + face + " (attendu : up, down, north, south, east, west)");
        }
        return direction;
    }

    // ---------------------------------------------------------------- walk_to

    static final class WalkToTool implements Tool {
        /** Distance parcourue par tick (~ vitesse de marche vanilla, approximative). */
        private static final double STEP_PER_TICK = 0.215;
        private static final double ARRIVAL_DISTANCE = 0.6;
        private static final long DEFAULT_TIMEOUT_MS = 10_000L;

        @Override
        public ToolDefinition getDefinition() {
            JsonObject schema = JsonSchema.object()
                .prop("x", JsonSchema.number("Coordonnee X cible"), true)
                .prop("y", JsonSchema.number("Coordonnee Y cible (utilisee pour detecter l'arrivee, pas pour le pathfinding vertical)"), true)
                .prop("z", JsonSchema.number("Coordonnee Z cible"), true)
                .prop("timeoutMs", JsonSchema.integer("Delai maximum en millisecondes avant abandon. Defaut : 10000"), false)
                .build();
            return new ToolDefinition("walk_to",
                "Fait marcher le joueur vers une position (TODO(v2) : implementation simplifiee en ligne droite, "
                    + "sans evitement d'obstacles avance ; s'arrete si bloque ou en timeout).",
                schema, false);
        }

        @Override
        public CompletableFuture<JsonElement> execute(JsonObject params) {
            MinecraftServer server = requireServer();
            ServerPlayer player = requirePlayer(server);
            double targetX = params.get("x").getAsDouble();
            double targetZ = params.get("z").getAsDouble();
            long timeoutMs = params.has("timeoutMs") ? params.get("timeoutMs").getAsLong() : DEFAULT_TIMEOUT_MS;
            long deadline = System.currentTimeMillis() + timeoutMs;

            CompletableFuture<JsonElement> future = new CompletableFuture<>();
            server.execute(() -> tick(server, player, targetX, targetZ, deadline, future));
            return future;
        }

        private void tick(MinecraftServer server, ServerPlayer player, double targetX, double targetZ,
                           long deadline, CompletableFuture<JsonElement> future) {
            try {
                double dx = targetX - player.getX();
                double dz = targetZ - player.getZ();
                double distance = Math.sqrt(dx * dx + dz * dz);

                if (distance <= ARRIVAL_DISTANCE) {
                    complete(future, true, "arrived");
                    return;
                }
                if (System.currentTimeMillis() >= deadline) {
                    complete(future, false, "timeout");
                    return;
                }

                double step = Math.min(STEP_PER_TICK, distance);
                double nextX = player.getX() + (dx / distance) * step;
                double nextZ = player.getZ() + (dz / distance) * step;
                float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));

                BlockPos feet = BlockPos.containing(nextX, player.getY(), nextZ);
                boolean blocked = !player.level().isEmptyBlock(feet) && !player.level().isEmptyBlock(feet.above());
                if (blocked) {
                    if (player.level().isEmptyBlock(feet.above()) && player.level().isEmptyBlock(feet.above(2))) {
                        player.connection.teleport(nextX, player.getY() + 1.0, nextZ, yaw, player.getXRot());
                    } else {
                        complete(future, false, "blocked");
                        return;
                    }
                } else {
                    player.connection.teleport(nextX, player.getY(), nextZ, yaw, player.getXRot());
                }

                server.execute(() -> tick(server, player, targetX, targetZ, deadline, future));
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

    // ---------------------------------------------------------------- look_at

    static final class LookAtTool implements Tool {
        @Override
        public ToolDefinition getDefinition() {
            JsonObject schema = JsonSchema.object()
                .prop("x", JsonSchema.number("Coordonnee X du point regarde"), true)
                .prop("y", JsonSchema.number("Coordonnee Y du point regarde"), true)
                .prop("z", JsonSchema.number("Coordonnee Z du point regarde"), true)
                .build();
            return new ToolDefinition("look_at", "Oriente le regard du joueur vers un point donne.", schema, false);
        }

        @Override
        public CompletableFuture<JsonElement> execute(JsonObject params) {
            return MainThreadDispatcher.runOnServerThread(() -> {
                MinecraftServer server = requireServer();
                ServerPlayer player = requirePlayer(server);
                double x = params.get("x").getAsDouble();
                double y = params.get("y").getAsDouble();
                double z = params.get("z").getAsDouble();

                double dx = x - player.getX();
                double dy = y - (player.getY() + player.getEyeHeight());
                double dz = z - player.getZ();
                double distXZ = Math.sqrt(dx * dx + dz * dz);
                float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
                float pitch = (float) -Math.toDegrees(Math.atan2(dy, distXZ));

                player.connection.teleport(player.getX(), player.getY(), player.getZ(), yaw, pitch);
                JsonObject result = new JsonObject();
                result.addProperty("success", true);
                return result;
            });
        }
    }

    // ---------------------------------------------------------------- jump

    static final class JumpTool implements Tool {
        @Override
        public ToolDefinition getDefinition() {
            return new ToolDefinition("jump", "Fait sauter le joueur (une seule impulsion).", JsonSchema.object().build(), false);
        }

        @Override
        public CompletableFuture<JsonElement> execute(JsonObject params) {
            return MainThreadDispatcher.runOnServerThread(() -> {
                ServerPlayer player = requirePlayer(requireServer());
                // jumpFromGround() est protected ; on applique directement l'impulsion verticale vanilla (0.42).
                net.minecraft.world.phys.Vec3 motion = player.getDeltaMovement();
                player.setDeltaMovement(motion.x, 0.42, motion.z);
                JsonObject result = new JsonObject();
                result.addProperty("success", true);
                return result;
            });
        }
    }

    // ---------------------------------------------------------------- start_sneaking

    static final class StartSneakingTool implements Tool {
        @Override
        public ToolDefinition getDefinition() {
            JsonObject schema = JsonSchema.object()
                .prop("bool", JsonSchema.bool("true pour s'accroupir, false pour se relever"), true)
                .build();
            return new ToolDefinition("start_sneaking", "Active ou desactive la position accroupie du joueur.", schema, false);
        }

        @Override
        public CompletableFuture<JsonElement> execute(JsonObject params) {
            return MainThreadDispatcher.runOnServerThread(() -> {
                ServerPlayer player = requirePlayer(requireServer());
                boolean sneaking = params.get("bool").getAsBoolean();
                player.setShiftKeyDown(sneaking);
                JsonObject result = new JsonObject();
                result.addProperty("success", true);
                return result;
            });
        }
    }

    // ---------------------------------------------------------------- mine_block

    static final class MineBlockTool implements Tool {
        @Override
        public ToolDefinition getDefinition() {
            JsonObject schema = JsonSchema.object()
                .prop("x", JsonSchema.integer("Coordonnee X du bloc a miner"), true)
                .prop("y", JsonSchema.integer("Coordonnee Y du bloc a miner"), true)
                .prop("z", JsonSchema.integer("Coordonnee Z du bloc a miner"), true)
                .build();
            return new ToolDefinition("mine_block",
                "Mine (casse) un bloc en respectant l'outil actuellement equipe (drops et durabilite corrects).",
                schema, false);
        }

        @Override
        public CompletableFuture<JsonElement> execute(JsonObject params) {
            return MainThreadDispatcher.runOnServerThread(() -> {
                ServerPlayer player = requirePlayer(requireServer());
                BlockPos pos = BlockPosCodec.fromJson(params);
                if (player.level().isEmptyBlock(pos)) {
                    JsonObject result = new JsonObject();
                    result.addProperty("success", false);
                    result.addProperty("reason", "already_air");
                    return result;
                }
                Block block = player.level().getBlockState(pos).getBlock();
                boolean mined = player.gameMode.destroyBlock(pos);
                JsonObject result = new JsonObject();
                result.addProperty("success", mined);
                result.addProperty("block", block.builtInRegistryHolder().key().location() == null ? "unknown" : block.builtInRegistryHolder().key().location().toString());
                return result;
            });
        }
    }

    // ---------------------------------------------------------------- place_block_at

    static final class PlaceBlockAtTool implements Tool {
        @Override
        public ToolDefinition getDefinition() {
            JsonObject schema = JsonSchema.object()
                .prop("x", JsonSchema.integer("Coordonnee X du bloc contre lequel placer"), true)
                .prop("y", JsonSchema.integer("Coordonnee Y du bloc contre lequel placer"), true)
                .prop("z", JsonSchema.integer("Coordonnee Z du bloc contre lequel placer"), true)
                .prop("face", JsonSchema.stringEnum("Face du bloc sur laquelle placer", "up", "down", "north", "south", "east", "west"), true)
                .build();
            return new ToolDefinition("place_block_at",
                "Place le bloc actuellement tenu en main principale contre la face indiquee d'un bloc existant.",
                schema, false);
        }

        @Override
        public CompletableFuture<JsonElement> execute(JsonObject params) {
            return MainThreadDispatcher.runOnServerThread(() -> {
                ServerPlayer player = requirePlayer(requireServer());
                BlockPos anchor = BlockPosCodec.fromJson(params);
                Direction facing = parseFacing(params.get("face").getAsString());
                BlockPos placeAt = anchor.relative(facing);

                ItemStack held = player.getMainHandItem();
                if (held.isEmpty() || !(held.getItem() instanceof BlockItem)) {
                    throw new JsonRpcException(JsonRpcException.SERVER_ERROR, "Aucun bloc tenu en main principale");
                }
                BlockItem blockItem = (BlockItem) held.getItem();

                if (!player.level().isEmptyBlock(placeAt)) {
                    JsonObject result = new JsonObject();
                    result.addProperty("success", false);
                    result.addProperty("reason", "occupied");
                    return result;
                }

                // Vrai placement joueur (BlockItem.place) : orientation selon le regard du joueur,
                // setPlacedBy (ecrans CC qui s'assemblent, proprietaire SecurityCraft...), consommation de l'item.
                Vec3 hitLocation = Vec3.atCenterOf(anchor).add(
                    facing.getStepX() * 0.5, facing.getStepY() * 0.5, facing.getStepZ() * 0.5);
                BlockHitResult hit = new BlockHitResult(hitLocation, facing, anchor, false);
                InteractionResult placement = blockItem.place(new BlockPlaceContext(player, InteractionHand.MAIN_HAND, held, hit));

                JsonObject result = new JsonObject();
                result.addProperty("success", placement.consumesAction());
                result.add("pos", BlockPosCodec.toJson(placeAt));
                result.addProperty("state", player.level().getBlockState(placeAt).toString());
                return result;
            });
        }
    }

    // ---------------------------------------------------------------- use_item

    static final class UseItemTool implements Tool {
        @Override
        public ToolDefinition getDefinition() {
            JsonObject schema = JsonSchema.object()
                .prop("hand", JsonSchema.stringEnum("Main utilisee. Defaut : main", "main", "offhand"), false)
                .build();
            return new ToolDefinition("use_item", "Simule un clic droit avec l'item tenu (manger, boire, activer, etc.).", schema, false);
        }

        @Override
        public CompletableFuture<JsonElement> execute(JsonObject params) {
            return MainThreadDispatcher.runOnServerThread(() -> {
                ServerPlayer player = requirePlayer(requireServer());
                String hand = params.has("hand") ? params.get("hand").getAsString() : "main";
                InteractionHand handEnum = "offhand".equals(hand) ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;

                ItemStack before = player.getItemInHand(handEnum);
                InteractionResultHolder<ItemStack> actionResult = before.getItem().use(player.level(), player, handEnum);
                player.setItemInHand(handEnum, actionResult.getObject());

                JsonObject result = new JsonObject();
                result.addProperty("success", true);
                result.addProperty("actionResultType", actionResult.getResult().name());
                return result;
            });
        }
    }

    // ---------------------------------------------------------------- attack_entity

    static final class AttackEntityTool implements Tool {
        @Override
        public ToolDefinition getDefinition() {
            JsonObject schema = JsonSchema.object()
                .prop("entityId", JsonSchema.integer("Id numerique de l'entite (voir list_entities)"), true)
                .build();
            return new ToolDefinition("attack_entity",
                "Attaque une entite avec l'item actuellement en main (calcul de degats, recul et durabilite vanilla).",
                schema, false);
        }

        @Override
        public CompletableFuture<JsonElement> execute(JsonObject params) {
            return MainThreadDispatcher.runOnServerThread(() -> {
                ServerPlayer player = requirePlayer(requireServer());
                int entityId = params.get("entityId").getAsInt();
                Entity target = player.level().getEntity(entityId);
                if (target == null) {
                    throw new JsonRpcException(JsonRpcException.SERVER_ERROR, "Entite introuvable : " + entityId);
                }
                player.attack(target);
                JsonObject result = new JsonObject();
                result.addProperty("success", true);
                return result;
            });
        }
    }
}
