package com.mushurelie.mcmcp.mcp;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mushurelie.mcmcp.mcp.types.ToolDefinition;
import com.mushurelie.mcmcp.tools.ToolRegistry;

import java.util.concurrent.CompletableFuture;

/**
 * Implementation du handshake et des methodes MCP : initialize, notifications/initialized,
 * tools/list, tools/call. Toute methode non geree renvoie JsonRpcException.METHOD_NOT_FOUND.
 * Classe partagee entre toutes les versions Minecraft : aucune dependance directe au jeu.
 */
public class McpProtocolHandler {

    private static final String PROTOCOL_VERSION = "2024-11-05";
    private static final String SERVER_NAME = "minecraft-mcp";

    private final ToolRegistry registry;
    private final String serverVersion;

    public McpProtocolHandler(ToolRegistry registry, String serverVersion) {
        this.registry = registry;
        this.serverVersion = serverVersion;
    }

    /**
     * Traite une methode JSON-RPC. Le resultat, une fois resolu, doit etre place tel quel
     * dans le champ "result" de la reponse JSON-RPC (ou ignore si la requete est une notification).
     */
    public CompletableFuture<JsonElement> handle(String method, JsonElement paramsElement) {
        JsonObject params = (paramsElement != null && paramsElement.isJsonObject())
            ? paramsElement.getAsJsonObject()
            : new JsonObject();

        switch (method) {
            case "initialize":
                return CompletableFuture.completedFuture(initialize(params));
            case "notifications/initialized":
                // Notification pure (pas d'id dans la requete) : le routeur ne repondra pas,
                // quel que soit le resultat renvoye ici.
                return CompletableFuture.completedFuture(new JsonObject());
            case "tools/list":
                return CompletableFuture.completedFuture(listTools());
            case "tools/call":
                return callTool(params).thenApply(result -> (JsonElement) result);
            case "ping":
                return CompletableFuture.completedFuture(new JsonObject());
            default:
                throw new JsonRpcException(JsonRpcException.METHOD_NOT_FOUND, "Methode inconnue : " + method);
        }
    }

    private JsonObject initialize(JsonObject params) {
        String requestedVersion = params.has("protocolVersion") && !params.get("protocolVersion").isJsonNull()
            ? params.get("protocolVersion").getAsString()
            : PROTOCOL_VERSION;

        JsonObject result = new JsonObject();
        result.addProperty("protocolVersion", requestedVersion);

        JsonObject capabilities = new JsonObject();
        JsonObject toolsCapability = new JsonObject();
        toolsCapability.addProperty("listChanged", false);
        capabilities.add("tools", toolsCapability);
        result.add("capabilities", capabilities);

        JsonObject serverInfo = new JsonObject();
        serverInfo.addProperty("name", SERVER_NAME);
        serverInfo.addProperty("version", serverVersion);
        result.add("serverInfo", serverInfo);

        return result;
    }

    private JsonObject listTools() {
        JsonArray toolsArray = new JsonArray();
        for (ToolDefinition definition : registry.listDefinitions()) {
            toolsArray.add(definition.toMcpJson());
        }
        JsonObject result = new JsonObject();
        result.add("tools", toolsArray);
        return result;
    }

    private CompletableFuture<JsonObject> callTool(JsonObject params) {
        if (!params.has("name") || params.get("name").isJsonNull()) {
            throw new JsonRpcException(JsonRpcException.INVALID_PARAMS, "Parametre 'name' manquant pour tools/call");
        }
        String name = params.get("name").getAsString();
        JsonObject arguments = (params.has("arguments") && params.get("arguments").isJsonObject())
            ? params.getAsJsonObject("arguments")
            : new JsonObject();
        return registry.callTool(name, arguments);
    }
}
