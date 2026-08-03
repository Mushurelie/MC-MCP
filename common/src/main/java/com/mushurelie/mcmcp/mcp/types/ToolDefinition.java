package com.mushurelie.mcmcp.mcp.types;

import com.google.gson.JsonObject;

/**
 * Description statique d'un outil MCP : nom, description LLM-friendly, schema
 * JSON des parametres et indicateur lecture seule / ecriture.
 */
public final class ToolDefinition {

    private final String name;
    private final String description;
    private final JsonObject inputSchema;
    private final boolean readOnly;

    public ToolDefinition(String name, String description, JsonObject inputSchema, boolean readOnly) {
        this.name = name;
        this.description = description;
        this.inputSchema = inputSchema;
        this.readOnly = readOnly;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public JsonObject getInputSchema() {
        return inputSchema;
    }

    public boolean isReadOnly() {
        return readOnly;
    }

    /** Format attendu par tools/list : {name, description, inputSchema}. */
    public JsonObject toMcpJson() {
        JsonObject json = new JsonObject();
        json.addProperty("name", name);
        json.addProperty("description", description);
        json.add("inputSchema", inputSchema);
        return json;
    }

    /** Format enrichi utilise par list_tools_meta (introspection debug, hors spec MCP stricte). */
    public JsonObject toMetaJson() {
        JsonObject json = toMcpJson();
        json.addProperty("readOnly", readOnly);
        return json;
    }
}
