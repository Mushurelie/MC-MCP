package com.mushurelie.mcmcp.tools.world;

import com.google.gson.JsonObject;
import com.mushurelie.mcmcp.config.McpConfig;
import com.mushurelie.mcmcp.dispatch.MainThreadDispatcher;
import com.mushurelie.mcmcp.mcp.JsonRpcException;
import com.mushurelie.mcmcp.mcp.types.JsonSchema;
import com.mushurelie.mcmcp.mcp.types.ToolDefinition;
import com.mushurelie.mcmcp.tools.Tool;
import com.mushurelie.mcmcp.util.BlockPosCodec;
import com.mushurelie.mcmcp.util.BlockStateCodec;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import com.mushurelie.mcmcp.util.NbtToJson;
import com.mushurelie.mcmcp.util.RaycastHelper;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.entity.Entity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Outils "Monde / blocs" : lecture et ecriture directe du monde via le ServerLevel
 * integre. Toujours execute sur le thread serveur (MainThreadDispatcher.runOnServerThread).
 */
public final class WorldTools {

    private static final int MAX_SCAN_RADIUS = 48;

    private WorldTools() {
    }

    public static List<Tool> createAll() {
        List<Tool> tools = new ArrayList<>();
        tools.add(new GetBlockTool());
        tools.add(new SetBlockTool());
        tools.add(new FillAreaTool());
        tools.add(new CloneAreaTool());
        tools.add(new FindNearestBlockTool());
        tools.add(new ScanAreaTool());
        tools.add(new GetBiomeTool());
        tools.add(new GetLightLevelTool());
        tools.add(new RaycastTool());
        return tools;
    }

    private static Block resolveBlock(String id) {
        Block block = ForgeRegistries.BLOCKS.getValue(new ResourceLocation(id));
        if (block == null || block == Blocks.AIR && !"minecraft:air".equals(id)) {
            throw new IllegalArgumentException("Bloc inconnu : " + id);
        }
        return block;
    }

    private static JsonObject blockStateToJson(ServerLevel world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        JsonObject json = new JsonObject();
        json.add("pos", BlockPosCodec.toJson(pos));
        json.addProperty("id", state.getBlock().builtInRegistryHolder().key().location() == null
            ? "unknown" : state.getBlock().builtInRegistryHolder().key().location().toString());
        json.addProperty("state", state.toString());
        BlockEntity tileEntity = world.getBlockEntity(pos);
        if (tileEntity != null) {
            CompoundTag nbt = tileEntity.saveWithFullMetadata();
            json.add("nbt", NbtToJson.compoundToJson(nbt));
        }
        return json;
    }

    private static long volumeOf(BlockPos a, BlockPos b) {
        long dx = Math.abs(a.getX() - b.getX()) + 1L;
        long dy = Math.abs(a.getY() - b.getY()) + 1L;
        long dz = Math.abs(a.getZ() - b.getZ()) + 1L;
        return dx * dy * dz;
    }

    // ---------------------------------------------------------------- get_block

    static final class GetBlockTool implements Tool {
        @Override
        public ToolDefinition getDefinition() {
            JsonObject schema = JsonSchema.object()
                .prop("x", JsonSchema.integer("Coordonnee X du bloc"), true)
                .prop("y", JsonSchema.integer("Coordonnee Y du bloc"), true)
                .prop("z", JsonSchema.integer("Coordonnee Z du bloc"), true)
                .prop("dimension", JsonSchema.string("Dimension : 'overworld', 'the_nether' ou 'the_end'. Defaut : dimension du joueur"), false)
                .build();
            return new ToolDefinition("get_block",
                "Lit le bloc a une position donnee : identifiant, etat (variant) et NBT du tile entity le cas echeant.",
                schema, true);
        }

        @Override
        public CompletableFuture<com.google.gson.JsonElement> execute(JsonObject params) {
            return MainThreadDispatcher.runOnServerThread(() -> {
                ServerLevel world = BlockPosCodec.resolveWorld(params);
                BlockPos pos = BlockPosCodec.fromJson(params);
                return blockStateToJson(world, pos);
            });
        }
    }

    // ---------------------------------------------------------------- set_block

    static final class SetBlockTool implements Tool {
        @Override
        public ToolDefinition getDefinition() {
            JsonObject schema = JsonSchema.object()
                .prop("x", JsonSchema.integer("Coordonnee X"), true)
                .prop("y", JsonSchema.integer("Coordonnee Y"), true)
                .prop("z", JsonSchema.integer("Coordonnee Z"), true)
                .prop("block", JsonSchema.string("Bloc au format commande, proprietes et NBT optionnels, ex: 'minecraft:stone', 'minecraft:oak_stairs[facing=north,half=top]', 'minecraft:chest{Items:[...]}'"), true)
                .prop("dimension", JsonSchema.string("Dimension. Defaut : dimension du joueur"), false)
                .build();
            return new ToolDefinition("set_block",
                "Place un bloc a une position donnee (ecrase le bloc existant), avec orientation/proprietes et NBT optionnels.",
                schema, false);
        }

        @Override
        public CompletableFuture<com.google.gson.JsonElement> execute(JsonObject params) {
            return MainThreadDispatcher.runOnServerThread(() -> {
                ServerLevel world = BlockPosCodec.resolveWorld(params);
                BlockPos pos = BlockPosCodec.fromJson(params);
                BlockStateCodec.place(world, pos, BlockStateCodec.parse(world, params.get("block").getAsString()), 3);
                JsonObject result = new JsonObject();
                result.addProperty("success", true);
                result.add("pos", BlockPosCodec.toJson(pos));
                return result;
            });
        }
    }

    // ---------------------------------------------------------------- fill_area

    static final class FillAreaTool implements Tool {
        @Override
        public ToolDefinition getDefinition() {
            JsonObject schema = JsonSchema.object()
                .prop("x1", JsonSchema.integer("X du premier coin"), true)
                .prop("y1", JsonSchema.integer("Y du premier coin"), true)
                .prop("z1", JsonSchema.integer("Z du premier coin"), true)
                .prop("x2", JsonSchema.integer("X du second coin"), true)
                .prop("y2", JsonSchema.integer("Y du second coin"), true)
                .prop("z2", JsonSchema.integer("Z du second coin"), true)
                .prop("block", JsonSchema.string("Bloc a placer, proprietes optionnelles, ex: 'minecraft:glass' ou 'minecraft:oak_log[axis=x]'"), true)
                .prop("mode", JsonSchema.stringEnum("Mode de remplissage. Defaut : replace",
                    "replace", "keep", "hollow", "outline"), false)
                .prop("dimension", JsonSchema.string("Dimension. Defaut : dimension du joueur"), false)
                .build();
            return new ToolDefinition("fill_area",
                "Remplit une zone cubique avec un bloc donne. Le volume est plafonne par maxVolumeBlocks (config).",
                schema, false);
        }

        @Override
        public CompletableFuture<com.google.gson.JsonElement> execute(JsonObject params) {
            return MainThreadDispatcher.runOnServerThread(() -> {
                ServerLevel world = BlockPosCodec.resolveWorld(params);
                BlockPos p1 = BlockPosCodec.fromJson(params, "1");
                BlockPos p2 = BlockPosCodec.fromJson(params, "2");
                BlockStateParser.BlockResult block = BlockStateCodec.parse(world, params.get("block").getAsString());
                String mode = params.has("mode") ? params.get("mode").getAsString() : "replace";

                long volume = volumeOf(p1, p2);
                if (volume > McpConfig.getMaxVolumeBlocks()) {
                    throw new JsonRpcException(JsonRpcException.SERVER_ERROR,
                        "Volume demande (" + volume + ") superieur a la limite configuree (" + McpConfig.getMaxVolumeBlocks() + ")");
                }

                int minX = Math.min(p1.getX(), p2.getX());
                int maxX = Math.max(p1.getX(), p2.getX());
                int minY = Math.min(p1.getY(), p2.getY());
                int maxY = Math.max(p1.getY(), p2.getY());
                int minZ = Math.min(p1.getZ(), p2.getZ());
                int maxZ = Math.max(p1.getZ(), p2.getZ());

                int placed = 0;
                for (int x = minX; x <= maxX; x++) {
                    for (int y = minY; y <= maxY; y++) {
                        for (int z = minZ; z <= maxZ; z++) {
                            boolean isEdge = x == minX || x == maxX || y == minY || y == maxY || z == minZ || z == maxZ;
                            if ("hollow".equals(mode) && !isEdge) {
                                continue;
                            }
                            if ("outline".equals(mode)) {
                                int touchingFaces = (x == minX || x == maxX ? 1 : 0)
                                    + (y == minY || y == maxY ? 1 : 0)
                                    + (z == minZ || z == maxZ ? 1 : 0);
                                if (touchingFaces < 2) {
                                    continue;
                                }
                            }
                            BlockPos pos = new BlockPos(x, y, z);
                            if ("keep".equals(mode) && !world.isEmptyBlock(pos)) {
                                continue;
                            }
                            BlockStateCodec.place(world, pos, block, 2);
                            placed++;
                        }
                    }
                }
                JsonObject result = new JsonObject();
                result.addProperty("success", true);
                result.addProperty("blocksPlaced", placed);
                return result;
            });
        }
    }

    // ---------------------------------------------------------------- clone_area

    static final class CloneAreaTool implements Tool {
        @Override
        public ToolDefinition getDefinition() {
            JsonObject schema = JsonSchema.object()
                .prop("fromX", JsonSchema.integer("X du premier coin source"), true)
                .prop("fromY", JsonSchema.integer("Y du premier coin source"), true)
                .prop("fromZ", JsonSchema.integer("Z du premier coin source"), true)
                .prop("toX", JsonSchema.integer("X du second coin source"), true)
                .prop("toY", JsonSchema.integer("Y du second coin source"), true)
                .prop("toZ", JsonSchema.integer("Z du second coin source"), true)
                .prop("destX", JsonSchema.integer("X de destination (coin correspondant a from)"), true)
                .prop("destY", JsonSchema.integer("Y de destination"), true)
                .prop("destZ", JsonSchema.integer("Z de destination"), true)
                .prop("dimension", JsonSchema.string("Dimension (source et destination). Defaut : dimension du joueur"), false)
                .build();
            return new ToolDefinition("clone_area",
                "Copie une zone cubique de blocs (et NBT des tile entities) vers une autre position. Volume plafonne par maxVolumeBlocks.",
                schema, false);
        }

        @Override
        public CompletableFuture<com.google.gson.JsonElement> execute(JsonObject params) {
            return MainThreadDispatcher.runOnServerThread(() -> {
                ServerLevel world = BlockPosCodec.resolveWorld(params);
                BlockPos from = new BlockPos(params.get("fromX").getAsInt(), params.get("fromY").getAsInt(), params.get("fromZ").getAsInt());
                BlockPos to = new BlockPos(params.get("toX").getAsInt(), params.get("toY").getAsInt(), params.get("toZ").getAsInt());
                BlockPos dest = new BlockPos(params.get("destX").getAsInt(), params.get("destY").getAsInt(), params.get("destZ").getAsInt());

                long volume = volumeOf(from, to);
                if (volume > McpConfig.getMaxVolumeBlocks()) {
                    throw new JsonRpcException(JsonRpcException.SERVER_ERROR,
                        "Volume demande (" + volume + ") superieur a la limite configuree (" + McpConfig.getMaxVolumeBlocks() + ")");
                }

                int minX = Math.min(from.getX(), to.getX());
                int maxX = Math.max(from.getX(), to.getX());
                int minY = Math.min(from.getY(), to.getY());
                int maxY = Math.max(from.getY(), to.getY());
                int minZ = Math.min(from.getZ(), to.getZ());
                int maxZ = Math.max(from.getZ(), to.getZ());

                List<BlockState> states = new ArrayList<>();
                List<CompoundTag> nbts = new ArrayList<>();
                for (int x = minX; x <= maxX; x++) {
                    for (int y = minY; y <= maxY; y++) {
                        for (int z = minZ; z <= maxZ; z++) {
                            BlockPos pos = new BlockPos(x, y, z);
                            states.add(world.getBlockState(pos));
                            BlockEntity te = world.getBlockEntity(pos);
                            nbts.add(te != null ? te.saveWithFullMetadata() : null);
                        }
                    }
                }

                int index = 0;
                int copied = 0;
                for (int x = minX; x <= maxX; x++) {
                    for (int y = minY; y <= maxY; y++) {
                        for (int z = minZ; z <= maxZ; z++) {
                            BlockPos destPos = dest.offset(x - minX, y - minY, z - minZ);
                            world.setBlock(destPos, states.get(index), 3);
                            CompoundTag nbt = nbts.get(index);
                            if (nbt != null) {
                                BlockEntity destTe = world.getBlockEntity(destPos);
                                if (destTe != null) {
                                    CompoundTag copy = nbt.copy();
                                    copy.putInt("x", destPos.getX());
                                    copy.putInt("y", destPos.getY());
                                    copy.putInt("z", destPos.getZ());
                                    destTe.load(copy);
                                    destTe.setChanged();
                                }
                            }
                            index++;
                            copied++;
                        }
                    }
                }

                JsonObject result = new JsonObject();
                result.addProperty("success", true);
                result.addProperty("blocksCopied", copied);
                return result;
            });
        }
    }

    // ---------------------------------------------------------------- find_nearest_block

    static final class FindNearestBlockTool implements Tool {
        @Override
        public ToolDefinition getDefinition() {
            JsonObject centerSchema = JsonSchema.object()
                .prop("x", JsonSchema.integer("X du centre"), true)
                .prop("y", JsonSchema.integer("Y du centre"), true)
                .prop("z", JsonSchema.integer("Z du centre"), true)
                .build();
            JsonObject schema = JsonSchema.object()
                .prop("type", JsonSchema.string("Identifiant de registre du bloc recherche, ex: 'minecraft:diamond_ore'"), true)
                .prop("center", centerSchema, true)
                .prop("radius", JsonSchema.integer("Rayon de recherche en blocs (max " + MAX_SCAN_RADIUS + ")"), true)
                .prop("dimension", JsonSchema.string("Dimension. Defaut : dimension du joueur"), false)
                .build();
            return new ToolDefinition("find_nearest_block",
                "Cherche le bloc du type donne le plus proche d'un centre, dans un rayon donne.",
                schema, true);
        }

        @Override
        public CompletableFuture<com.google.gson.JsonElement> execute(JsonObject params) {
            return MainThreadDispatcher.runOnServerThread(() -> {
                ServerLevel world = BlockPosCodec.resolveWorld(params);
                Block target = resolveBlock(params.get("type").getAsString());
                JsonObject centerJson = params.getAsJsonObject("center");
                BlockPos center = BlockPosCodec.fromJson(centerJson);
                int radius = Math.min(params.get("radius").getAsInt(), MAX_SCAN_RADIUS);

                BlockPos nearest = null;
                double nearestDistanceSq = Double.MAX_VALUE;
                for (int dx = -radius; dx <= radius; dx++) {
                    for (int dy = -radius; dy <= radius; dy++) {
                        for (int dz = -radius; dz <= radius; dz++) {
                            BlockPos pos = center.offset(dx, dy, dz);
                            if (!world.isLoaded(pos)) {
                                continue;
                            }
                            if (world.getBlockState(pos).getBlock() == target) {
                                double distSq = pos.distSqr(center);
                                if (distSq < nearestDistanceSq) {
                                    nearestDistanceSq = distSq;
                                    nearest = pos;
                                }
                            }
                        }
                    }
                }

                JsonObject result = new JsonObject();
                if (nearest == null) {
                    result.add("found", com.google.gson.JsonNull.INSTANCE);
                    return result;
                }
                result.addProperty("x", nearest.getX());
                result.addProperty("y", nearest.getY());
                result.addProperty("z", nearest.getZ());
                result.addProperty("distance", Math.sqrt(nearestDistanceSq));
                return result;
            });
        }
    }

    // ---------------------------------------------------------------- scan_area

    static final class ScanAreaTool implements Tool {
        @Override
        public ToolDefinition getDefinition() {
            JsonObject centerSchema = JsonSchema.object()
                .prop("x", JsonSchema.integer("X du centre"), true)
                .prop("y", JsonSchema.integer("Y du centre"), true)
                .prop("z", JsonSchema.integer("Z du centre"), true)
                .build();
            JsonObject schema = JsonSchema.object()
                .prop("center", centerSchema, true)
                .prop("radius", JsonSchema.integer("Rayon de scan en blocs (max " + MAX_SCAN_RADIUS + ")"), true)
                .prop("groupBy", JsonSchema.stringEnum("Regroupement du resultat. Defaut : block", "block", "y"), false)
                .prop("dimension", JsonSchema.string("Dimension. Defaut : dimension du joueur"), false)
                .build();
            return new ToolDefinition("scan_area",
                "Scanne une zone cubique et renvoie un histogramme des blocs presents (par type ou par couche Y).",
                schema, true);
        }

        @Override
        public CompletableFuture<com.google.gson.JsonElement> execute(JsonObject params) {
            return MainThreadDispatcher.runOnServerThread(() -> {
                ServerLevel world = BlockPosCodec.resolveWorld(params);
                JsonObject centerJson = params.getAsJsonObject("center");
                BlockPos center = BlockPosCodec.fromJson(centerJson);
                int radius = Math.min(params.get("radius").getAsInt(), MAX_SCAN_RADIUS);
                String groupBy = params.has("groupBy") ? params.get("groupBy").getAsString() : "block";

                Map<String, Integer> histogram = new HashMap<>();
                for (int dx = -radius; dx <= radius; dx++) {
                    for (int dy = -radius; dy <= radius; dy++) {
                        for (int dz = -radius; dz <= radius; dz++) {
                            BlockPos pos = center.offset(dx, dy, dz);
                            if (!world.isLoaded(pos)) {
                                continue;
                            }
                            BlockState state = world.getBlockState(pos);
                            String key;
                            if ("y".equals(groupBy)) {
                                if (state.getBlock() == Blocks.AIR) {
                                    continue;
                                }
                                key = String.valueOf(pos.getY());
                            } else {
                                key = state.getBlock().builtInRegistryHolder().key().location() == null
                                    ? "unknown" : state.getBlock().builtInRegistryHolder().key().location().toString();
                            }
                            histogram.merge(key, 1, Integer::sum);
                        }
                    }
                }

                JsonObject result = new JsonObject();
                JsonObject counts = new JsonObject();
                for (Map.Entry<String, Integer> entry : histogram.entrySet()) {
                    counts.addProperty(entry.getKey(), entry.getValue());
                }
                result.add("counts", counts);
                result.addProperty("groupBy", groupBy);
                return result;
            });
        }
    }

    // ---------------------------------------------------------------- get_biome

    static final class GetBiomeTool implements Tool {
        @Override
        public ToolDefinition getDefinition() {
            JsonObject schema = JsonSchema.object()
                .prop("x", JsonSchema.integer("Coordonnee X"), true)
                .prop("z", JsonSchema.integer("Coordonnee Z"), true)
                .prop("dimension", JsonSchema.string("Dimension. Defaut : dimension du joueur"), false)
                .build();
            return new ToolDefinition("get_biome", "Renvoie le biome present aux coordonnees X/Z donnees.", schema, true);
        }

        @Override
        public CompletableFuture<com.google.gson.JsonElement> execute(JsonObject params) {
            return MainThreadDispatcher.runOnServerThread(() -> {
                ServerLevel world = BlockPosCodec.resolveWorld(params);
                int x = params.get("x").getAsInt();
                int z = params.get("z").getAsInt();
                BlockPos pos = new BlockPos(x, 64, z);
                net.minecraft.core.Holder<Biome> biomeHolder = world.getBiome(pos);
                Biome biome = biomeHolder.value();
                ResourceLocation key = biomeHolder.unwrapKey().map(net.minecraft.resources.ResourceKey::location).orElse(null);
                JsonObject result = new JsonObject();
                result.addProperty("biome", key == null ? "unknown" : key.toString());
                result.addProperty("rainfall", biome.getModifiedClimateSettings().downfall());
                result.addProperty("temperature", biome.getBaseTemperature());
                return result;
            });
        }
    }

    // ---------------------------------------------------------------- get_light_level

    static final class GetLightLevelTool implements Tool {
        @Override
        public ToolDefinition getDefinition() {
            JsonObject schema = JsonSchema.object()
                .prop("x", JsonSchema.integer("Coordonnee X"), true)
                .prop("y", JsonSchema.integer("Coordonnee Y"), true)
                .prop("z", JsonSchema.integer("Coordonnee Z"), true)
                .prop("dimension", JsonSchema.string("Dimension. Defaut : dimension du joueur"), false)
                .build();
            return new ToolDefinition("get_light_level",
                "Renvoie le niveau de lumiere (brightness 0.0-1.0 et raw 0-15) a une position donnee.", schema, true);
        }

        @Override
        public CompletableFuture<com.google.gson.JsonElement> execute(JsonObject params) {
            return MainThreadDispatcher.runOnServerThread(() -> {
                ServerLevel world = BlockPosCodec.resolveWorld(params);
                BlockPos pos = BlockPosCodec.fromJson(params);
                JsonObject result = new JsonObject();
                result.addProperty("brightness", world.getMaxLocalRawBrightness(pos) / 15.0f);
                result.addProperty("rawLight", world.getMaxLocalRawBrightness(pos));
                return result;
            });
        }
    }

    // ---------------------------------------------------------------- raycast

    static final class RaycastTool implements Tool {
        @Override
        public ToolDefinition getDefinition() {
            JsonObject vecSchema = JsonSchema.object()
                .prop("x", JsonSchema.number("Composante X"), true)
                .prop("y", JsonSchema.number("Composante Y"), true)
                .prop("z", JsonSchema.number("Composante Z"), true)
                .build();
            JsonObject schema = JsonSchema.object()
                .prop("origin", vecSchema, true)
                .prop("direction", vecSchema, true)
                .prop("maxDist", JsonSchema.number("Distance maximale du rayon en blocs"), true)
                .build();
            return new ToolDefinition("raycast",
                "Lance un rayon depuis une origine dans une direction donnee et renvoie le premier bloc ou entite touche.",
                schema, true);
        }

        @Override
        public CompletableFuture<com.google.gson.JsonElement> execute(JsonObject params) {
            return MainThreadDispatcher.runOnServerThread(() -> {
                ServerLevel world = BlockPosCodec.resolveWorld(params);
                JsonObject originJson = params.getAsJsonObject("origin");
                JsonObject dirJson = params.getAsJsonObject("direction");
                Vec3 origin = new Vec3(originJson.get("x").getAsDouble(), originJson.get("y").getAsDouble(), originJson.get("z").getAsDouble());
                Vec3 direction = new Vec3(dirJson.get("x").getAsDouble(), dirJson.get("y").getAsDouble(), dirJson.get("z").getAsDouble());
                double maxDist = params.get("maxDist").getAsDouble();

                RaycastHelper.Result raycastResult = RaycastHelper.raycast(world, origin, direction, maxDist, null);

                JsonObject result = new JsonObject();
                result.addProperty("hit", raycastResult.hit);
                JsonObject posJson = new JsonObject();
                posJson.addProperty("x", raycastResult.hitVec.x);
                posJson.addProperty("y", raycastResult.hitVec.y);
                posJson.addProperty("z", raycastResult.hitVec.z);
                result.add("pos", posJson);

                if ("block".equals(raycastResult.hitType)) {
                    result.add("block", blockStateToJson(world, raycastResult.blockPos));
                } else if ("entity".equals(raycastResult.hitType)) {
                    Entity entity = raycastResult.entity;
                    JsonObject entityJson = new JsonObject();
                    entityJson.addProperty("id", entity.getId());
                    entityJson.addProperty("type", entity.getClass().getSimpleName());
                    result.add("entity", entityJson);
                }
                return result;
            });
        }
    }
}
