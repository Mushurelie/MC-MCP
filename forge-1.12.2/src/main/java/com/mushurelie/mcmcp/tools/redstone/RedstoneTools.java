package com.mushurelie.mcmcp.tools.redstone;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mushurelie.mcmcp.dispatch.MainThreadDispatcher;
import com.mushurelie.mcmcp.mcp.JsonRpcException;
import com.mushurelie.mcmcp.mcp.types.JsonSchema;
import com.mushurelie.mcmcp.mcp.types.ToolDefinition;
import com.mushurelie.mcmcp.tools.Tool;
import com.mushurelie.mcmcp.util.BlockPosCodec;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraft.world.WorldServer;

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

    private static EntityPlayerMP requirePlayer() {
        MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
        EntityPlayerMP player = BlockPosCodec.getSinglePlayer(server);
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
                .prop("dimension", JsonSchema.integer("Id de dimension. Defaut : dimension du joueur"), false)
                .build();
            return new ToolDefinition("interact_block",
                "Simule un clic droit du joueur sur un bloc (levier, bouton, porte, tremie, coffre...).",
                schema, false);
        }

        @Override
        public CompletableFuture<JsonElement> execute(JsonObject params) {
            return MainThreadDispatcher.runOnServerThread(() -> {
                WorldServer world = BlockPosCodec.resolveWorld(params);
                BlockPos pos = BlockPosCodec.fromJson(params);
                EntityPlayerMP player = requirePlayer();
                IBlockState state = world.getBlockState(pos);

                boolean handled = state.getBlock().onBlockActivated(
                    world, pos, state, player, EnumHand.MAIN_HAND, EnumFacing.UP, 0.5f, 0.5f, 0.5f);

                JsonObject result = new JsonObject();
                result.addProperty("success", handled);
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
                .prop("dimension", JsonSchema.integer("Id de dimension. Defaut : dimension du joueur"), false)
                .build();
            return new ToolDefinition("get_redstone_power", "Renvoie le niveau de signal redstone (0-15) recu par un bloc.", schema, true);
        }

        @Override
        public CompletableFuture<JsonElement> execute(JsonObject params) {
            return MainThreadDispatcher.runOnServerThread(() -> {
                WorldServer world = BlockPosCodec.resolveWorld(params);
                BlockPos pos = BlockPosCodec.fromJson(params);
                int power = world.getRedstonePowerFromNeighbors(pos);
                JsonObject result = new JsonObject();
                result.addProperty("power", power);
                return result;
            });
        }
    }
}
