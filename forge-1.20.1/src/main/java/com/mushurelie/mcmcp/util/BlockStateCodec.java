package com.mushurelie.mcmcp.util;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mushurelie.mcmcp.mcp.JsonRpcException;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Lecture d'un etat de bloc complet au format des commandes vanilla
 * ("minecraft:oak_stairs[facing=north,half=top]{...nbt...}"), pour que les outils
 * d'ecriture puissent poser des blocs orientes (escaliers, pistons, portes...)
 * et pas seulement l'etat par defaut. A appeler depuis le thread serveur.
 */
public final class BlockStateCodec {

    private BlockStateCodec() {
    }

    public static BlockStateParser.BlockResult parse(ServerLevel world, String blockSpec) {
        try {
            return BlockStateParser.parseForBlock(world.holderLookup(Registries.BLOCK), blockSpec, true);
        } catch (CommandSyntaxException e) {
            throw new JsonRpcException(JsonRpcException.INVALID_PARAMS, "Bloc invalide '" + blockSpec + "' : " + e.getMessage());
        }
    }

    public static BlockState parseState(ServerLevel world, String blockSpec) {
        return parse(world, blockSpec).blockState();
    }

    /** Pose l'etat et applique le NBT eventuel (ex : {Items:[...]} d'un coffre). */
    public static void place(ServerLevel world, BlockPos pos, BlockStateParser.BlockResult parsed, int flags) {
        world.setBlock(pos, parsed.blockState(), flags);
        CompoundTag nbt = parsed.nbt();
        if (nbt != null) {
            BlockEntity blockEntity = world.getBlockEntity(pos);
            if (blockEntity != null) {
                CompoundTag merged = blockEntity.saveWithoutMetadata();
                merged.merge(nbt);
                blockEntity.load(merged);
                blockEntity.setChanged();
            }
        }
    }
}
