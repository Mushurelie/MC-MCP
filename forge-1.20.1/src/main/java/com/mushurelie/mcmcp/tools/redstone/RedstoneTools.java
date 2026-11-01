package com.mushurelie.mcmcp.tools.redstone;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mushurelie.mcmcp.dispatch.MainThreadDispatcher;
import com.mushurelie.mcmcp.mcp.JsonRpcException;
import com.mushurelie.mcmcp.mcp.types.JsonSchema;
import com.mushurelie.mcmcp.mcp.types.ToolDefinition;
import com.mushurelie.mcmcp.tools.Tool;
import com.mushurelie.mcmcp.util.BlockPosCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Outils "Redstone" : interaction generique avec un bloc (levier, bouton, porte,
 * coffre...) et lecture du niveau de signal redstone.
 */
public final class RedstoneTools {

    private RedstoneTools() {
    }

    public static List<Tool> createAll() {
        List<Tool> tools = new ArrayList<>();
        tools.add(new InteractBlockTool());
        tools.add(new GetRedstonePowerTool());
        return tools;
    }

    private static ServerPlayer requirePlayer() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        ServerPlayer player = BlockPosCodec.getSinglePlayer(server);
        if (player == null) {
            throw new JsonRpcException(JsonRpcException.SERVER_ERROR, "Aucun joueur en jeu (partie non chargee ?)");
        }
        return player;
    }

    // ---------------------------------------------------------------- interact_block

    static final class InteractBlockTool implements Tool {
        @Override
        public ToolDefinition getDefinition() {
            JsonObject schema = JsonSchema.object()
                .prop("x", JsonSchema.integer("Coordonnee X du bloc"), true)
                .prop("y", JsonSchema.integer("Coordonnee Y du bloc"), true)
                .prop("z", JsonSchema.integer("Coordonnee Z du bloc"), true)
                .prop("dimension", JsonSchema.string("Dimension. Defaut : dimension du joueur"), false)
                .build();
            return new ToolDefinition("interact_block",
                "Simule un clic droit du joueur sur un bloc (levier, bouton, porte, tremie, coffre...).",
                schema, false);
        }

        @Override
        public CompletableFuture<JsonElement> execute(JsonObject params) {
            return MainThreadDispatcher.runOnServerThread(() -> {
                ServerLevel world = BlockPosCodec.resolveWorld(params);
                BlockPos pos = BlockPosCodec.fromJson(params);
                ServerPlayer player = requirePlayer();
                BlockState state = world.getBlockState(pos);

                BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
                InteractionResult interaction = state.use(world, player, InteractionHand.MAIN_HAND, hit);

                JsonObject result = new JsonObject();
                result.addProperty("success", interaction.consumesAction());
                result.addProperty("result", interaction.name());
                return result;
            });
        }
    }

    // ---------------------------------------------------------------- get_redstone_power

    static final class GetRedstonePowerTool implements Tool {
        @Override
        public ToolDefinition getDefinition() {
            JsonObject schema = JsonSchema.object()
                .prop("x", JsonSchema.integer("Coordonnee X"), true)
                .prop("y", JsonSchema.integer("Coordonnee Y"), true)
                .prop("z", JsonSchema.integer("Coordonnee Z"), true)
                .prop("dimension", JsonSchema.string("Dimension. Defaut : dimension du joueur"), false)
                .build();
            return new ToolDefinition("get_redstone_power", "Renvoie le niveau de signal redstone (0-15) recu par un bloc.", schema, true);
        }

        @Override
        public CompletableFuture<JsonElement> execute(JsonObject params) {
            return MainThreadDispatcher.runOnServerThread(() -> {
                ServerLevel world = BlockPosCodec.resolveWorld(params);
                BlockPos pos = BlockPosCodec.fromJson(params);
                JsonObject result = new JsonObject();
                result.addProperty("power", world.getBestNeighborSignal(pos));
                return result;
            });
        }
    }
}
