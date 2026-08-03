package com.mushurelie.mcmcp.tools.observe;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mushurelie.mcmcp.dispatch.MainThreadDispatcher;
import com.mushurelie.mcmcp.mcp.JsonRpcException;
import com.mushurelie.mcmcp.mcp.types.JsonSchema;
import com.mushurelie.mcmcp.mcp.types.ToolDefinition;
import com.mushurelie.mcmcp.tools.Tool;
import com.mushurelie.mcmcp.util.BlockPosCodec;
import com.mushurelie.mcmcp.util.RaycastHelper;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.stats.StatBase;
import net.minecraft.stats.StatList;
import net.minecraft.util.ScreenShotHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.fml.common.FMLCommonHandler;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Outils "Observation / visuel". get_achievements interroge le systeme
 * d'Advancement (les "achievements" en tant que tels n'existent plus depuis
 * Minecraft 1.12 : ils ont ete remplaces par les advancements ; le nom
 * get_achievements est conserve pour coller au brief, mais la donnee renvoyee
 * est bien la progression des advancements du joueur).
 */
public final class ObserveTools {

    private static final int DEFAULT_MAX_SCREENSHOT_SIZE = 512;
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

    // ---------------------------------------------------------------- screenshot

    static final class ScreenshotTool implements Tool {
        @Override
        public ToolDefinition getDefinition() {
            JsonObject schema = JsonSchema.object()
                .prop("maxSize", JsonSchema.integer("Dimension maximale (largeur ou hauteur) en pixels. Defaut : " + DEFAULT_MAX_SCREENSHOT_SIZE), false)
                .build();
            return new ToolDefinition("screenshot", "Capture l'ecran du jeu et renvoie un PNG encode en base64 (redimensionne si besoin).", schema, true);
        }

        @Override
        public CompletableFuture<JsonElement> execute(JsonObject params) {
            return MainThreadDispatcher.runOnClientThread(() -> {
                Minecraft mc = Minecraft.getMinecraft();
                int maxSize = params.has("maxSize") ? params.get("maxSize").getAsInt() : DEFAULT_MAX_SCREENSHOT_SIZE;

                Framebuffer framebuffer = mc.getFramebuffer();
                BufferedImage image = ScreenShotHelper.createScreenshot(mc.displayWidth, mc.displayHeight, framebuffer);
                BufferedImage resized = resizeIfNeeded(image, maxSize);

                try {
                    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                    ImageIO.write(resized, "png", buffer);
                    String base64 = Base64.getEncoder().encodeToString(buffer.toByteArray());
                    JsonObject result = new JsonObject();
                    result.addProperty("format", "png");
                    result.addProperty("width", resized.getWidth());
                    result.addProperty("height", resized.getHeight());
                    result.addProperty("data", base64);
                    return result;
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

            BufferedImage resized = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_ARGB);
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
                Minecraft mc = Minecraft.getMinecraft();
                EntityPlayerSP player = mc.player;
                if (player == null) {
                    throw new JsonRpcException(JsonRpcException.SERVER_ERROR, "Aucun joueur en jeu (partie non chargee ?)");
                }
                double maxDist = params.has("maxDist") ? params.get("maxDist").getAsDouble() : DEFAULT_CAMERA_VIEW_DIST;

                Vec3d eyePos = new Vec3d(player.posX, player.posY + player.getEyeHeight(), player.posZ);
                JsonArray rays = new JsonArray();
                StringBuilder summary = new StringBuilder();

                for (int i = 0; i < YAW_OFFSETS.length; i++) {
                    float yaw = player.rotationYaw + (float) YAW_OFFSETS[i];
                    float pitch = player.rotationPitch + (float) PITCH_OFFSETS[i];
                    Vec3d direction = getLookVec(yaw, pitch);

                    RaycastHelper.Result raycastResult = RaycastHelper.raycast(player.world, eyePos, direction, maxDist, player);
                    JsonObject rayJson = new JsonObject();
                    rayJson.addProperty("direction", LABELS[i]);
                    rayJson.addProperty("hit", raycastResult.hit);
                    rayJson.addProperty("distance", raycastResult.distance);

                    String description;
                    if (!raycastResult.hit) {
                        description = LABELS[i] + ": rien (vide jusqu'a " + (int) maxDist + " blocs)";
                    } else if ("block".equals(raycastResult.hitType)) {
                        String blockName = raycastResult.blockState.getBlock().getRegistryName() == null
                            ? "inconnu" : raycastResult.blockState.getBlock().getRegistryName().toString();
                        rayJson.addProperty("block", blockName);
                        description = LABELS[i] + ": " + blockName + " a " + String.format("%.1f", raycastResult.distance) + " blocs";
                    } else {
                        Entity entity = raycastResult.entity;
                        rayJson.addProperty("entityId", entity.getEntityId());
                        rayJson.addProperty("entityType", entity.getClass().getSimpleName());
                        description = LABELS[i] + ": entite " + entity.getClass().getSimpleName()
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

        private static Vec3d getLookVec(float yaw, float pitch) {
            float yawRad = -yaw * 0.017453292F;
            float pitchRad = -pitch * 0.017453292F;
            double x = Math.sin(yawRad) * Math.cos(pitchRad);
            double y = Math.sin(pitchRad);
            double z = Math.cos(yawRad) * Math.cos(pitchRad);
            return new Vec3d(x, y, z);
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
                EntityPlayerMP player = requirePlayer(requireServer());
                JsonArray statsArray = new JsonArray();
                for (StatBase stat : StatList.ALL_STATS) {
                    int value = player.getStatFile().readStat(stat);
                    if (value == 0) {
                        continue;
                    }
                    JsonObject statJson = new JsonObject();
                    statJson.addProperty("id", stat.statId);
                    statJson.addProperty("name", stat.getStatName().getUnformattedText());
                    statJson.addProperty("value", value);
                    statsArray.add(statJson);
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
                EntityPlayerMP player = requirePlayer(server);

                JsonArray array = new JsonArray();
                for (Advancement advancement : server.getAdvancementManager().getAdvancements()) {
                    AdvancementProgress progress = player.getAdvancements().getProgress(advancement);
                    if (progress == null) {
                        continue;
                    }
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
