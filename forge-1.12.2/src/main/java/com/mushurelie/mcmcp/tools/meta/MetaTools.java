package com.mushurelie.mcmcp.tools.meta;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mushurelie.mcmcp.MinecraftMcp;
import com.mushurelie.mcmcp.mcp.types.JsonSchema;
import com.mushurelie.mcmcp.mcp.types.ToolDefinition;
import com.mushurelie.mcmcp.tools.Tool;
import com.mushurelie.mcmcp.tools.ToolRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Outils "Meta" : introspection et sante du serveur MCP, hors modele metier.
 */
public final class MetaTools {

    private MetaTools() {
    }

    public static List<Tool> createAll(ToolRegistry registry) {
        List<Tool> tools = new ArrayList<>();
        tools.add(new PingTool());
        tools.add(new ListToolsMetaTool(registry));
        return tools;
    }

    // ---------------------------------------------------------------- ping

    static final class PingTool implements Tool {
        @Override
        public ToolDefinition getDefinition() {
            return new ToolDefinition("ping", "Verifie que le serveur MCP repond et renvoie sa version.", JsonSchema.object().build(), true);
        }

        @Override
        public CompletableFuture<JsonElement> execute(JsonObject params) {
            JsonObject result = new JsonObject();
            result.addProperty("pong", true);
            result.addProperty("version", MinecraftMcp.VERSION);
            return CompletableFuture.completedFuture(result);
        }
    }

    // ---------------------------------------------------------------- list_tools_meta

    static final class ListToolsMetaTool implements Tool {
        private final ToolRegistry registry;

        ListToolsMetaTool(ToolRegistry registry) {
            this.registry = registry;
        }

        @Override
        public ToolDefinition getDefinition() {
            return new ToolDefinition("list_tools_meta",
                "Introspection complete de tous les outils enregistres (nom, description, schema, readOnly). "
                    + "Complementaire a tools/list (protocole MCP standard), pratique pour le debug.",
                JsonSchema.object().build(), true);
        }

        @Override
        public CompletableFuture<JsonElement> execute(JsonObject params) {
            JsonArray array = new JsonArray();
            for (ToolDefinition definition : registry.listDefinitions()) {
                array.add(definition.toMetaJson());
            }
            JsonObject result = new JsonObject();
            result.add("tools", array);
            result.addProperty("count", array.size());
            return CompletableFuture.completedFuture(result);
        }
    }
}
