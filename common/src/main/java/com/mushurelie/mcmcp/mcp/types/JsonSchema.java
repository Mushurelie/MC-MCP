package com.mushurelie.mcmcp.mcp.types;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * Petit builder pour construire des JSON Schema (draft-07 simplifie) utilises
 * comme inputSchema des outils MCP. Volontairement minimal : seuls les types
 * necessaires aux outils de ce mod sont couverts.
 */
public final class JsonSchema {

    private final JsonObject root = new JsonObject();
    private final JsonObject properties = new JsonObject();
    private final JsonArray required = new JsonArray();

    private JsonSchema() {
        root.addProperty("type", "object");
        root.add("properties", properties);
        root.add("required", required);
        root.addProperty("additionalProperties", false);
    }

    public static JsonSchema object() {
        return new JsonSchema();
    }

    public JsonSchema prop(String name, JsonObject schema, boolean isRequired) {
        properties.add(name, schema);
        if (isRequired) {
            required.add(name);
        }
        return this;
    }

    public JsonObject build() {
        return root;
    }

    public static JsonObject string(String description) {
        JsonObject s = new JsonObject();
        s.addProperty("type", "string");
        s.addProperty("description", description);
        return s;
    }

    public static JsonObject stringEnum(String description, String... values) {
        JsonObject s = string(description);
        JsonArray enumArray = new JsonArray();
        for (String v : values) {
            enumArray.add(v);
        }
        s.add("enum", enumArray);
        return s;
    }

    public static JsonObject integer(String description) {
        JsonObject s = new JsonObject();
        s.addProperty("type", "integer");
        s.addProperty("description", description);
        return s;
    }

    public static JsonObject number(String description) {
        JsonObject s = new JsonObject();
        s.addProperty("type", "number");
        s.addProperty("description", description);
        return s;
    }

    public static JsonObject bool(String description) {
        JsonObject s = new JsonObject();
        s.addProperty("type", "boolean");
        s.addProperty("description", description);
        return s;
    }

    public static JsonObject object(String description) {
        JsonObject s = new JsonObject();
        s.addProperty("type", "object");
        s.addProperty("description", description);
        return s;
    }

    public static JsonObject array(JsonObject items, String description) {
        JsonObject s = new JsonObject();
        s.addProperty("type", "array");
        s.addProperty("description", description);
        s.add("items", items);
        return s;
    }
}
