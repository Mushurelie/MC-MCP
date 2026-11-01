package com.mushurelie.mcmcp.tools.observe;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.NativeImage;
import com.mushurelie.mcmcp.dispatch.MainThreadDispatcher;
import com.mushurelie.mcmcp.mcp.JsonRpcException;
import com.mushurelie.mcmcp.mcp.types.ContentBlock;
import com.mushurelie.mcmcp.mcp.types.JsonSchema;
import com.mushurelie.mcmcp.mcp.types.ToolDefinition;
import com.mushurelie.mcmcp.tools.Tool;
import com.mushurelie.mcmcp.util.BlockPosCodec;
import com.mushurelie.mcmcp.util.RaycastHelper;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.ServerStatsCounter;
import net.minecraft.stats.Stat;
import net.minecraft.stats.StatType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.server.ServerLifecycleHooks;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Outils "Observation / visuel". screenshot renvoie un vrai bloc image MCP (pas
 * seulement du base64 dans un JSON) pour que le LLM puisse voir la scene.
 */
public final class ObserveTools {

    private static final int DEFAULT_MAX_SCREENSHOT_SIZE = 768;
    private static final double DEFAULT_CAMERA_VIEW_DIST = 64.0;

    private ObserveTools() {
    }

    public static List<Tool> createAll() {
        List<Tool> tools = new ArrayList<>();
        tools.add(new ScreenshotTool());
        tools.add(new GetCameraViewTool());
        tools.add(new GetStatsTool());
        tools.add(new GetAchievementsTool());
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

    // ---------------------------------------------------------------- screenshot

    static final class ScreenshotTool implements Tool {
        @Override
        public ToolDefinition getDefinition() {
            JsonObject schema = JsonSchema.object()
                .prop("maxSize", JsonSchema.integer("Dimension maximale (largeur ou hauteur) en pixels. Defaut : " + DEFAULT_MAX_SCREENSHOT_SIZE), false)
                .build();
            return new ToolDefinition("screenshot", "Capture l'ecran du jeu et le renvoie comme image PNG (redimensionnee si besoin).", schema, true);
        }

        @Override
        public CompletableFuture<JsonElement> execute(JsonObject params) {
            return MainThreadDispatcher.runOnClientThread(() -> {
                Minecraft mc = Minecraft.getInstance();
                int maxSize = params.has("maxSize") ? params.get("maxSize").getAsInt() : DEFAULT_MAX_SCREENSHOT_SIZE;

                try (NativeImage nativeImage = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
                    BufferedImage image = ImageIO.read(new ByteArrayInputStream(nativeImage.asByteArray()));
                    BufferedImage resized = resizeIfNeeded(image, maxSize);
                    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                    ImageIO.write(resized, "png", buffer);

                    JsonArray content = new JsonArray();
                    content.add(ContentBlock.image(Base64.getEncoder().encodeToString(buffer.toByteArray()), "image/png"));
                    content.add(ContentBlock.text("{\"width\":" + resized.getWidth() + ",\"height\":" + resized.getHeight() + "}"));
                    return ContentBlock.rawContent(content);
                } catch (IOException e) {
                    throw new JsonRpcException(JsonRpcException.INTERNAL_ERROR, "Erreur lors de l'encodage PNG : " + e.getMessage());
                }
            });
        }

        private static BufferedImage resizeIfNeeded(BufferedImage source, int maxSize) {
            int width = source.getWidth();
            int height = source.getHeight();
            if (width <= maxSize && height <= maxSize) {
                return source;
            }
            double scale = Math.min((double) maxSize / width, (double) maxSize / height);
            int newWidth = Math.max(1, (int) (width * scale));
            int newHeight = Math.max(1, (int) (height * scale));

            BufferedImage resized = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = resized.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(source, 0, 0, newWidth, newHeight, null);
            g.dispose();
            return resized;
        }
    }

    // ---------------------------------------------------------------- get_camera_view

    static final class GetCameraViewTool implements Tool {
        private static final double[] YAW_OFFSETS = {0, 45, 90, 135, 180, -135, -90, -45, 0};
        private static final double[] PITCH_OFFSETS = {0, 0, 0, 0, 0, 0, 0, 0, 20};
        private static final String[] LABELS = {
            "devant", "devant-droite", "droite", "derriere-droite",
            "derriere", "derriere-gauche", "gauche", "devant-gauche", "haut"
        };

        @Override
        public ToolDefinition getDefinition() {
            JsonObject schema = JsonSchema.object()
                .prop("maxDist", JsonSchema.number("Distance maximale de detection en blocs. Defaut : " + DEFAULT_CAMERA_VIEW_DIST), false)
                .build();
            return new ToolDefinition("get_camera_view",
                "Lance un rayon principal + 8 rayons autour du regard du joueur et renvoie un resume textuel du champ de vision.",
                schema, true);
        }

        @Override
        public CompletableFuture<JsonElement> execute(JsonObject params) {
            return MainThreadDispatcher.runOnClientThread(() -> {
                Minecraft mc = Minecraft.getInstance();
                LocalPlayer player = mc.player;
                if (player == null || mc.level == null) {
                    throw new JsonRpcException(JsonRpcException.SERVER_ERROR, "Aucun joueur en jeu (partie non chargee ?)");
                }
                double maxDist = params.has("maxDist") ? params.get("maxDist").getAsDouble() : DEFAULT_CAMERA_VIEW_DIST;

                Vec3 eyePos = player.getEyePosition();
                JsonArray rays = new JsonArray();
                StringBuilder summary = new StringBuilder();

                for (int i = 0; i < YAW_OFFSETS.length; i++) {
                    float yaw = player.getYRot() + (float) YAW_OFFSETS[i];
                    float pitch = player.getXRot() + (float) PITCH_OFFSETS[i];
                    Vec3 direction = Vec3.directionFromRotation(pitch, yaw);

                    RaycastHelper.Result raycastResult = RaycastHelper.raycast(mc.level, eyePos, direction, maxDist, player);
                    JsonObject rayJson = new JsonObject();
                    rayJson.addProperty("direction", LABELS[i]);
                    rayJson.addProperty("hit", raycastResult.hit);
                    rayJson.addProperty("distance", raycastResult.distance);

                    String description;
                    if (!raycastResult.hit) {
                        description = LABELS[i] + ": rien (vide jusqu'a " + (int) maxDist + " blocs)";
                    } else if ("block".equals(raycastResult.hitType)) {
                        ResourceLocation key = ForgeRegistries.BLOCKS.getKey(raycastResult.blockState.getBlock());
                        String blockName = key == null ? "inconnu" : key.toString();
                        rayJson.addProperty("block", blockName);
                        rayJson.add("pos", BlockPosCodec.toJson(raycastResult.blockPos));
                        description = LABELS[i] + ": " + blockName + " a " + String.format("%.1f", raycastResult.distance) + " blocs";
                    } else {
                        Entity entity = raycastResult.entity;
                        ResourceLocation typeKey = ForgeRegistries.ENTITY_TYPES.getKey(entity.getType());
                        String typeName = typeKey == null ? entity.getClass().getSimpleName() : typeKey.toString();
                        rayJson.addProperty("entityId", entity.getId());
                        rayJson.addProperty("entityType", typeName);
                        description = LABELS[i] + ": entite " + typeName
                            + " a " + String.format("%.1f", raycastResult.distance) + " blocs";
                    }
                    summary.append(description).append(". ");
                    rays.add(rayJson);
                }

                JsonObject result = new JsonObject();
                result.add("rays", rays);
                result.addProperty("summary", summary.toString().trim());
                return result;
            });
        }
    }

    // ---------------------------------------------------------------- get_stats

    static final class GetStatsTool implements Tool {
        @Override
        public ToolDefinition getDefinition() {
            return new ToolDefinition("get_stats", "Renvoie les statistiques Minecraft non nulles du joueur (blocs mines, distance parcourue, etc.).",
                JsonSchema.object().build(), true);
        }

        @Override
        public CompletableFuture<JsonElement> execute(JsonObject params) {
            return MainThreadDispatcher.runOnServerThread(() -> {
                ServerPlayer player = requirePlayer(requireServer());
                ServerStatsCounter counter = player.getStats();
                JsonArray statsArray = new JsonArray();
                for (StatType<?> type : BuiltInRegistries.STAT_TYPE) {
                    for (Stat<?> stat : type) {
                        int value = counter.getValue(stat);
                        if (value == 0) {
                            continue;
                        }
                        JsonObject statJson = new JsonObject();
                        statJson.addProperty("id", stat.getName());
                        statJson.addProperty("value", value);
                        statsArray.add(statJson);
                    }
                }
                JsonObject result = new JsonObject();
                result.add("stats", statsArray);
                return result;
            });
        }
    }

    // ---------------------------------------------------------------- get_achievements

    static final class GetAchievementsTool implements Tool {
        @Override
        public ToolDefinition getDefinition() {
            return new ToolDefinition("get_achievements",
                "Renvoie la progression des advancements du joueur (systeme ayant remplace les achievements depuis Minecraft 1.12).",
                JsonSchema.object().build(), true);
        }

        @Override
        public CompletableFuture<JsonElement> execute(JsonObject params) {
            return MainThreadDispatcher.runOnServerThread(() -> {
                MinecraftServer server = requireServer();
                ServerPlayer player = requirePlayer(server);

                JsonArray array = new JsonArray();
                for (Advancement advancement : server.getAdvancements().getAllAdvancements()) {
                    AdvancementProgress progress = player.getAdvancements().getOrStartProgress(advancement);
                    JsonObject json = new JsonObject();
                    json.addProperty("id", advancement.getId().toString());
                    json.addProperty("done", progress.isDone());
                    json.addProperty("percent", progress.getPercent());
                    array.add(json);
                }
                JsonObject result = new JsonObject();
                result.add("achievements", array);
                return result;
            });
        }
    }
}
