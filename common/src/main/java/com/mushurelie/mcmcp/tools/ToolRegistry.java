package com.mushurelie.mcmcp.tools;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mushurelie.mcmcp.mcp.JsonRpcException;
import com.mushurelie.mcmcp.mcp.spi.McpLogger;
import com.mushurelie.mcmcp.mcp.spi.McpServerConfig;
import com.mushurelie.mcmcp.mcp.types.ContentBlock;
import com.mushurelie.mcmcp.mcp.types.ToolDefinition;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Registre central de tous les outils MCP disponibles. Applique, dans l'ordre :
 * whitelist/blacklist -> readOnlyMode -> confirmation en jeu -> execution -> logging.
 * Toute exception (synchrone ou via le CompletableFuture retourne par l'outil) est
 * transformee en resultat MCP isError=true, jamais propagee brute au client HTTP.
 * Classe partagee entre toutes les versions Minecraft : le cablage des outils
 * concrets (par version) se fait a l'exterieur, via {@link #registerAll}.
 */
public final class ToolRegistry {

    private final Map<String, Tool> tools = new LinkedHashMap<>();
    private final McpLogger logger;
    private final McpServerConfig config;
    private final ConfirmationRequester confirmationRequester;

    public ToolRegistry(McpLogger logger, McpServerConfig config, ConfirmationRequester confirmationRequester) {
        this.logger = logger;
        this.config = config;
        this.confirmationRequester = confirmationRequester;
    }

    public void register(Tool tool) {
        tools.put(tool.getName(), tool);
    }

    public void registerAll(Collection<Tool> toolList) {
        for (Tool tool : toolList) {
            register(tool);
        }
    }

    public Collection<ToolDefinition> listDefinitions() {
        List<ToolDefinition> definitions = new ArrayList<>();
        for (Tool tool : tools.values()) {
            definitions.add(tool.getDefinition());
        }
        return definitions;
    }

    public boolean hasTool(String name) {
        return tools.containsKey(name);
    }

    public CompletableFuture<JsonObject> callTool(String name, JsonObject params) {
        JsonObject safeParams = params != null ? params : new JsonObject();
        Tool tool = tools.get(name);

        if (tool == null) {
            throw new JsonRpcException(JsonRpcException.TOOL_NOT_FOUND, "Outil inconnu : " + name);
        }
        if (!config.isToolAllowed(name)) {
            throw new JsonRpcException(JsonRpcException.TOOL_FORBIDDEN,
                "Outil '" + name + "' interdit par la configuration (whitelist/blacklist)");
        }
        if (config.isReadOnlyMode() && !tool.isReadOnly()) {
            throw new JsonRpcException(JsonRpcException.TOOL_FORBIDDEN,
                "Outil '" + name + "' bloque : le mode lecture seule (readOnlyMode) est actif");
        }

        String argsSummary = truncateArgs(safeParams);
        long start = System.currentTimeMillis();
        logger.info("[MCP] appel outil='{}' args={}", name, argsSummary);

        CompletableFuture<JsonObject> resultFuture;

        if (config.isRequireConfirmation() && !tool.isReadOnly()) {
            resultFuture = confirmationRequester.requestConfirmation(name, argsSummary, config.getConfirmationTimeoutSeconds())
                .thenCompose(accepted -> {
                    if (!accepted) {
                        return CompletableFuture.completedFuture(
                            ContentBlock.errorResult("Appel refuse par l'utilisateur (ou timeout de confirmation)"));
                    }
                    return runTool(tool, safeParams);
                });
        } else {
            resultFuture = runTool(tool, safeParams);
        }

        return resultFuture.whenComplete((result, throwable) -> {
            long duration = System.currentTimeMillis() - start;
            if (throwable != null) {
                logger.warn("[MCP] outil='{}' duree={}ms statut=ERREUR : {}",
                    name, duration, throwable.getMessage());
            } else {
                boolean isError = result != null && result.has("isError") && result.get("isError").getAsBoolean();
                logger.info("[MCP] outil='{}' duree={}ms statut={}",
                    name, duration, isError ? "ERREUR" : "OK");
            }
        });
    }

    private CompletableFuture<JsonObject> runTool(Tool tool, JsonObject params) {
        CompletableFuture<JsonObject> wrapped = new CompletableFuture<>();
        CompletableFuture<JsonElement> execution;
        try {
            execution = tool.execute(params);
        } catch (Throwable t) {
            wrapped.complete(ContentBlock.errorResult(describe(t)));
            return wrapped;
        }
        execution.whenComplete((result, throwable) -> {
            if (throwable != null) {
                wrapped.complete(ContentBlock.errorResult(describe(throwable)));
            } else {
                wrapped.complete(ContentBlock.fromJsonText(result));
            }
        });
        return wrapped;
    }

    private static String describe(Throwable t) {
        Throwable cause = t.getCause() != null ? t.getCause() : t;
        String message = cause.getMessage();
        return message != null ? message : cause.getClass().getSimpleName();
    }

    private static String truncateArgs(JsonObject params) {
        String raw = params.toString();
        int max = 300;
        return raw.length() > max ? raw.substring(0, max) + "...(tronque)" : raw;
    }

    /**
     * Abstraction de la popup de confirmation en jeu, independante du loader/version
     * (chaque version fournit sa propre implementation GUI).
     */
    public interface ConfirmationRequester {
        CompletableFuture<Boolean> requestConfirmation(String toolName, String argsSummary, int timeoutSeconds);
    }
}
