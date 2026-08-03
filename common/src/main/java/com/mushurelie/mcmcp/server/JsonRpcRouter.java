package com.mushurelie.mcmcp.server;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.mushurelie.mcmcp.mcp.JsonRpcException;
import com.mushurelie.mcmcp.mcp.McpProtocolHandler;
import com.mushurelie.mcmcp.mcp.spi.McpLogger;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Routeur JSON-RPC 2.0 : parse le corps HTTP, valide l'enveloppe JSON-RPC,
 * delegue au McpProtocolHandler et serialise la reponse (ou renvoie null pour
 * une notification, auquel cas aucun corps HTTP ne doit etre ecrit).
 */
public class JsonRpcRouter {

    private static final long REQUEST_TIMEOUT_SECONDS = 30;
    private static final JsonParser PARSER = new JsonParser();

    private final McpProtocolHandler protocolHandler;
    private final McpLogger logger;

    public JsonRpcRouter(McpProtocolHandler protocolHandler, McpLogger logger) {
        this.protocolHandler = protocolHandler;
        this.logger = logger;
    }

    /** Retourne le corps de reponse JSON a ecrire, ou null si rien ne doit etre renvoye (notification(s) seules). */
    public String handleRequest(String rawBody) {
        JsonElement root;
        try {
            root = PARSER.parse(rawBody);
        } catch (JsonSyntaxException e) {
            return buildErrorResponse(JsonNull.INSTANCE, JsonRpcException.PARSE_ERROR,
                "JSON invalide : " + e.getMessage(), null).toString();
        }

        if (root.isJsonArray()) {
            JsonArray batchResponses = new JsonArray();
            for (JsonElement element : root.getAsJsonArray()) {
                JsonObject response = processSingle(element);
                if (response != null) {
                    batchResponses.add(response);
                }
            }
            return batchResponses.size() > 0 ? batchResponses.toString() : null;
        }

        JsonObject response = processSingle(root);
        return response != null ? response.toString() : null;
    }

    private JsonObject processSingle(JsonElement element) {
        boolean isObject = element.isJsonObject();
        boolean hasId = isObject && element.getAsJsonObject().has("id");
        boolean isNotification = isObject && !hasId;
        JsonElement id = hasId ? element.getAsJsonObject().get("id") : JsonNull.INSTANCE;

        try {
            if (!isObject) {
                throw new JsonRpcException(JsonRpcException.INVALID_REQUEST, "La requete doit etre un objet JSON-RPC");
            }
            JsonObject request = element.getAsJsonObject();

            if (!request.has("jsonrpc") || !"2.0".equals(safeString(request.get("jsonrpc")))) {
                throw new JsonRpcException(JsonRpcException.INVALID_REQUEST,
                    "Champ 'jsonrpc' manquant ou different de \"2.0\"");
            }
            if (!request.has("method") || !request.get("method").isJsonPrimitive()) {
                throw new JsonRpcException(JsonRpcException.INVALID_REQUEST, "Champ 'method' manquant ou invalide");
            }

            String method = request.get("method").getAsString();
            JsonElement params = request.has("params") ? request.get("params") : null;

            JsonElement result;
            try {
                result = protocolHandler.handle(method, params).get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            } catch (ExecutionException e) {
                Throwable cause = e.getCause() != null ? e.getCause() : e;
                if (cause instanceof JsonRpcException) {
                    throw (JsonRpcException) cause;
                }
                throw new JsonRpcException(JsonRpcException.INTERNAL_ERROR, describeCause(cause));
            } catch (TimeoutException e) {
                throw new JsonRpcException(JsonRpcException.INTERNAL_ERROR,
                    "Timeout : le thread principal du jeu n'a pas repondu a temps");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new JsonRpcException(JsonRpcException.INTERNAL_ERROR, "Requete interrompue");
            }

            if (isNotification) {
                return null;
            }
            return buildSuccessResponse(id, result);

        } catch (JsonRpcException e) {
            if (isNotification) {
                logger.warn("[MCP] notification en erreur (ignoree) : {}", e.getMessage());
                return null;
            }
            return buildErrorResponse(id, e.getCode(), e.getMessage(), e.getData());
        } catch (Exception e) {
            if (isNotification) {
                logger.warn("[MCP] notification en erreur inattendue (ignoree)", e);
                return null;
            }
            return buildErrorResponse(id, JsonRpcException.INTERNAL_ERROR, describeCause(e), null);
        }
    }

    private static String safeString(JsonElement element) {
        try {
            return element.getAsString();
        } catch (Exception e) {
            return null;
        }
    }

    private static String describeCause(Throwable t) {
        String message = t.getMessage();
        return message != null ? message : t.getClass().getSimpleName();
    }

    private static JsonObject buildSuccessResponse(JsonElement id, JsonElement result) {
        JsonObject response = new JsonObject();
        response.addProperty("jsonrpc", "2.0");
        response.add("result", result != null ? result : JsonNull.INSTANCE);
        response.add("id", id);
        return response;
    }

    private static JsonObject buildErrorResponse(JsonElement id, int code, String message, JsonElement data) {
        JsonObject error = new JsonObject();
        error.addProperty("code", code);
        error.addProperty("message", message != null ? message : "Erreur inconnue");
        if (data != null) {
            error.add("data", data);
        }
        JsonObject response = new JsonObject();
        response.addProperty("jsonrpc", "2.0");
        response.add("error", error);
        response.add("id", id);
        return response;
    }
}
