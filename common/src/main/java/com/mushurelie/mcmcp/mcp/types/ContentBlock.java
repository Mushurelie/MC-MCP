package com.mushurelie.mcmcp.mcp.types;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * Blocs de contenu MCP renvoyes par tools/call, au format standard :
 * {"content": [ {"type": "text", "text": "..."} , ... ], "isError": bool}
 */
public final class ContentBlock {

    private ContentBlock() {
    }

    public static JsonObject text(String text) {
        JsonObject block = new JsonObject();
        block.addProperty("type", "text");
        block.addProperty("text", text);
        return block;
    }

    public static JsonObject image(String base64Data, String mimeType) {
        JsonObject block = new JsonObject();
        block.addProperty("type", "image");
        block.addProperty("data", base64Data);
        block.addProperty("mimeType", mimeType);
        return block;
    }

    /**
     * Cle reservee : un outil qui renvoie {"__mcpContent": [blocs...]} fournit directement
     * ses blocs de contenu MCP (ex : une image pour screenshot) au lieu d'un JSON textuel.
     */
    public static final String RAW_CONTENT_KEY = "__mcpContent";

    /** Resultat d'outil compose de blocs de contenu explicites (voir RAW_CONTENT_KEY). */
    public static JsonObject rawContent(JsonArray content) {
        JsonObject wrapper = new JsonObject();
        wrapper.add(RAW_CONTENT_KEY, content);
        return wrapper;
    }

    /** Construit la reponse tools/call complete a partir d'un unique JSON textuel. */
    public static JsonObject fromJsonText(com.google.gson.JsonElement jsonResult) {
        if (jsonResult != null && jsonResult.isJsonObject()
            && jsonResult.getAsJsonObject().has(RAW_CONTENT_KEY)
            && jsonResult.getAsJsonObject().get(RAW_CONTENT_KEY).isJsonArray()) {
            return toolResult(jsonResult.getAsJsonObject().getAsJsonArray(RAW_CONTENT_KEY), false);
        }
        JsonArray content = new JsonArray();
        content.add(text(jsonResult.toString()));
        return toolResult(content, false);
    }

    public static JsonObject toolResult(JsonArray content, boolean isError) {
        JsonObject result = new JsonObject();
        result.add("content", content);
        result.addProperty("isError", isError);
        return result;
    }

    public static JsonObject errorResult(String message) {
        JsonArray content = new JsonArray();
        content.add(text(message));
        return toolResult(content, true);
    }
}
