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

    /** Construit la reponse tools/call complete a partir d'un unique JSON textuel. */
    public static JsonObject fromJsonText(com.google.gson.JsonElement jsonResult) {
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
