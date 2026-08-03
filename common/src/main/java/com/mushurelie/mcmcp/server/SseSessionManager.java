package com.mushurelie.mcmcp.server;

import com.google.gson.JsonElement;
import com.mushurelie.mcmcp.mcp.spi.McpLogger;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gestion des connexions SSE (GET /mcp) pour la notification serveur -> client
 * (ex : futures notifications tools/list_changed). Le transport principal
 * (tools/call synchrone) n'en depend pas : chaque requete POST recoit sa reponse
 * directement, sans passer par le flux SSE.
 */
public class SseSessionManager {

    private final Map<String, HttpExchange> sessions = new ConcurrentHashMap<>();
    private final McpLogger logger;

    public SseSessionManager(McpLogger logger) {
        this.logger = logger;
    }

    /**
     * Ouvre une connexion SSE de longue duree pour une requete GET /mcp.
     * Bloque le thread appelant (thread du pool HTTP) jusqu'a deconnexion du client.
     */
    public void handleSseConnection(HttpExchange exchange) throws IOException {
        String sessionId = UUID.randomUUID().toString();
        exchange.getResponseHeaders().add("Content-Type", "text/event-stream; charset=utf-8");
        exchange.getResponseHeaders().add("Cache-Control", "no-cache");
        exchange.getResponseHeaders().add("Connection", "keep-alive");
        exchange.getResponseHeaders().add("Mcp-Session-Id", sessionId);
        exchange.sendResponseHeaders(200, 0);

        sessions.put(sessionId, exchange);
        logger.info("[MCP] session SSE ouverte : {}", sessionId);

        try {
            OutputStream out = exchange.getResponseBody();
            writeFrame(out, "endpoint", sessionId);
            while (sessions.containsKey(sessionId)) {
                Thread.sleep(15_000L);
                writeFrame(out, "ping", "{}");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (IOException e) {
            // Client deconnecte : comportement normal, pas une erreur a logguer bruyamment.
        } finally {
            sessions.remove(sessionId);
            exchange.close();
            logger.info("[MCP] session SSE fermee : {}", sessionId);
        }
    }

    /** Diffuse un evenement JSON a toutes les sessions SSE ouvertes. */
    public void broadcast(String event, JsonElement data) {
        for (Map.Entry<String, HttpExchange> entry : sessions.entrySet()) {
            try {
                writeFrame(entry.getValue().getResponseBody(), event, data.toString());
            } catch (IOException e) {
                sessions.remove(entry.getKey());
            }
        }
    }

    public void closeAll() {
        for (Map.Entry<String, HttpExchange> entry : sessions.entrySet()) {
            entry.getValue().close();
        }
        sessions.clear();
    }

    private static synchronized void writeFrame(OutputStream out, String event, String data) throws IOException {
        StringBuilder frame = new StringBuilder();
        frame.append("event: ").append(event).append('\n');
        frame.append("data: ").append(data).append('\n').append('\n');
        out.write(frame.toString().getBytes(StandardCharsets.UTF_8));
        out.flush();
    }
}
