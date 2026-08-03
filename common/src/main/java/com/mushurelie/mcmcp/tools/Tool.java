package com.mushurelie.mcmcp.tools;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mushurelie.mcmcp.mcp.types.ToolDefinition;

import java.util.concurrent.CompletableFuture;

/**
 * Contrat commun a tous les outils MCP. Toute implementation qui touche au monde,
 * au joueur, aux entites, a l'inventaire ou qui envoie un packet DOIT scheduler son
 * travail via MainThreadDispatcher et renvoyer un CompletableFuture qui se termine
 * sur le thread principal.
 */
public interface Tool {

    ToolDefinition getDefinition();

    /**
     * Execute l'outil. params est l'objet JSON "arguments" de tools/call (jamais null,
     * un objet vide {} est fourni si l'appelant n'a rien passe).
     * Toute exception levee ici (ou completee exceptionnellement sur le future) est
     * automatiquement transformee en resultat MCP isError=true par ToolRegistry.
     */
    CompletableFuture<JsonElement> execute(JsonObject params);

    default String getName() {
        return getDefinition().getName();
    }

    default boolean isReadOnly() {
        return getDefinition().isReadOnly();
    }
}
