package com.mushurelie.mcmcp.tools.worldglobal;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mushurelie.mcmcp.dispatch.MainThreadDispatcher;
import com.mushurelie.mcmcp.mcp.JsonRpcException;
import com.mushurelie.mcmcp.mcp.types.JsonSchema;
import com.mushurelie.mcmcp.mcp.types.ToolDefinition;
import com.mushurelie.mcmcp.tools.Tool;
import com.mushurelie.mcmcp.util.BlockPosCodec;
import net.minecraft.server.MinecraftServer;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.ServerLevelData;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Outils "Monde global" : informations generales, temps, meteo.
 */
public final class WorldGlobalTools {

    private static final int DEFAULT_WEATHER_DURATION_TICKS = 6000;

    private WorldGlobalTools() {
    }

    public static List<Tool> createAll() {
        List<Tool> tools = new ArrayList<>();
        tools.add(new GetWorldInfoTool());
        tools.add(new SetTimeTool());
        tools.add(new SetWeatherTool());
        return tools;
    }

    private static MinecraftServer requireServer() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            throw new JsonRpcException(JsonRpcException.SERVER_ERROR, "Serveur integre indisponible");
        }
        return server;
    }

    // ---------------------------------------------------------------- get_world_info

    static final class GetWorldInfoTool implements Tool {
        @Override
        public ToolDefinition getDefinition() {
            return new ToolDefinition("get_world_info",
                "Renvoie les informations globales de la partie : temps, jour, meteo, seed, dimensions chargees.",
                JsonSchema.object().build(), true);
        }

        @Override
        public CompletableFuture<JsonElement> execute(JsonObject params) {
            return MainThreadDispatcher.runOnServerThread(() -> {
                MinecraftServer server = requireServer();
                ServerLevel overworld = server.getLevel(Level.OVERWORLD);
                ServerLevelData info = (ServerLevelData) overworld.getLevelData();

                JsonObject result = new JsonObject();
                result.addProperty("time", overworld.getDayTime());
                result.addProperty("day", overworld.getDayTime() / 24000L);
                result.addProperty("isRaining", overworld.isRaining());
                result.addProperty("isThundering", overworld.isThundering());
                result.addProperty("seed", overworld.getSeed());

                JsonArray dimensions = new JsonArray();
                for (ResourceKey<Level> key : server.levelKeys()) {
                    dimensions.add(key.location().toString());
                }
                result.add("dimensions", dimensions);
                return result;
            });
        }
    }

    // ---------------------------------------------------------------- set_time

    static final class SetTimeTool implements Tool {
        @Override
        public ToolDefinition getDefinition() {
            JsonObject schema = JsonSchema.object()
                .prop("time", JsonSchema.string(
                    "Nombre de ticks (0-24000) sous forme de chaine ou nombre, ou mot-cle : "
                        + "'day' (1000), 'noon' (6000), 'night' (13000), 'midnight' (18000)"), true)
                .prop("dimension", JsonSchema.string("Dimension. Defaut : dimension du joueur"), false)
                .build();
            return new ToolDefinition("set_time", "Change l'heure du monde.", schema, false);
        }

        @Override
        public CompletableFuture<JsonElement> execute(JsonObject params) {
            return MainThreadDispatcher.runOnServerThread(() -> {
                ServerLevel world = BlockPosCodec.resolveWorld(params);
                long ticks = parseTime(params.get("time"));
                world.setDayTime(ticks);
                JsonObject result = new JsonObject();
                result.addProperty("success", true);
                result.addProperty("time", ticks);
                return result;
            });
        }

        private static long parseTime(com.google.gson.JsonElement element) {
            if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) {
                return element.getAsLong();
            }
            String value = element.getAsString().trim();
            try {
                return Long.parseLong(value);
            } catch (NumberFormatException ignored) {
                // Pas un nombre : on essaie les mots-cles ci-dessous.
            }
            switch (value.toLowerCase()) {
                case "day": return 1000L;
                case "noon": return 6000L;
                case "night": return 13000L;
                case "midnight": return 18000L;
                default:
                    throw new JsonRpcException(JsonRpcException.INVALID_PARAMS, "Valeur de temps invalide : " + value);
            }
        }
    }

    // ---------------------------------------------------------------- set_weather

    static final class SetWeatherTool implements Tool {
        @Override
        public ToolDefinition getDefinition() {
            JsonObject schema = JsonSchema.object()
                .prop("weather", JsonSchema.stringEnum("Type de meteo", "clear", "rain", "thunder"), true)
                .prop("duration", JsonSchema.integer("Duree en ticks. Defaut : " + DEFAULT_WEATHER_DURATION_TICKS), false)
                .prop("dimension", JsonSchema.string("Dimension. Defaut : dimension du joueur"), false)
                .build();
            return new ToolDefinition("set_weather", "Change la meteo du monde.", schema, false);
        }

        @Override
        public CompletableFuture<JsonElement> execute(JsonObject params) {
            return MainThreadDispatcher.runOnServerThread(() -> {
                ServerLevel world = BlockPosCodec.resolveWorld(params);
                String weather = params.get("weather").getAsString();
                int duration = params.has("duration") ? params.get("duration").getAsInt() : DEFAULT_WEATHER_DURATION_TICKS;
                ServerLevelData info = (ServerLevelData) world.getLevelData();

                switch (weather) {
                    case "clear":
                        info.setClearWeatherTime(duration);
                        info.setRainTime(0);
                        info.setThunderTime(0);
                        info.setRaining(false);
                        info.setThundering(false);
                        break;
                    case "rain":
                        info.setClearWeatherTime(0);
                        info.setRainTime(duration);
                        info.setThunderTime(0);
                        info.setRaining(true);
                        info.setThundering(false);
                        break;
                    case "thunder":
                        info.setClearWeatherTime(0);
                        info.setRainTime(duration);
                        info.setThunderTime(duration);
                        info.setRaining(true);
                        info.setThundering(true);
                        break;
                    default:
                        throw new JsonRpcException(JsonRpcException.INVALID_PARAMS, "Meteo invalide : " + weather);
                }

                JsonObject result = new JsonObject();
                result.addProperty("success", true);
                return result;
            });
        }
    }
}
