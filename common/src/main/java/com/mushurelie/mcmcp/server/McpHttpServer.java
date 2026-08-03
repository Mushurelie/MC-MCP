package com.mushurelie.mcmcp.server;

import com.mushurelie.mcmcp.mcp.McpProtocolHandler;
import com.mushurelie.mcmcp.mcp.spi.McpLogger;
import com.mushurelie.mcmcp.mcp.spi.McpServerConfig;
import com.mushurelie.mcmcp.tools.ToolRegistry;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.BindException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Serveur HTTP MCP (JDK com.sun.net.httpserver, zero dependance externe).
 * Un seul serveur par JVM : start() refuse de demarrer si le port est deja occupe
 * et loggue clairement l'erreur au lieu de planter le jeu.
 *
 * <p>Endpoint unique : /mcp
 * <ul>
 *   <li>POST /mcp : requete(s) JSON-RPC 2.0 (initialize, tools/list, tools/call, notifications/initialized)</li>
 *   <li>GET /mcp : ouverture d'un flux SSE optionnel pour les notifications serveur -> client</li>
 * </ul>
 */
public class McpHttpServer {

    private static final int HTTP_THREAD_POOL_SIZE = 8;

    private final McpLogger logger;
    private final McpServerConfig config;
    private final ToolRegistry toolRegistry;
    private final McpProtocolHandler protocolHandler;
    private final SseSessionManager sseSessionManager;
    private final JsonRpcRouter router;

    private HttpServer server;
    private ExecutorService executor;

    public McpHttpServer(ToolRegistry toolRegistry, String serverVersion, McpLogger logger, McpServerConfig config) {
        this.logger = logger;
        this.config = config;
        this.toolRegistry = toolRegistry;
        this.protocolHandler = new McpProtocolHandler(toolRegistry, serverVersion);
        this.sseSessionManager = new SseSessionManager(logger);
        this.router = new JsonRpcRouter(protocolHandler, logger);
    }

    public void start() {
        if (server != null) {
            logger.warn("Le serveur MCP est deja demarre, appel ignore.");
            return;
        }
        try {
            InetSocketAddress address = new InetSocketAddress(config.getBindAddress(), config.getPort());
            server = HttpServer.create(address, 0);
            executor = Executors.newFixedThreadPool(HTTP_THREAD_POOL_SIZE, new NamedDaemonThreadFactory());
            server.setExecutor(executor);
            server.createContext("/mcp", new McpHttpHandler(router, sseSessionManager, config, logger));
            server.start();
            logger.info("Serveur MCP demarre sur {}:{}", config.getBindAddress(), config.getPort());
        } catch (BindException e) {
            logger.error(
                "Impossible de demarrer le serveur MCP : le port {} est deja utilise (autre instance en cours ?)",
                config.getPort());
            server = null;
        } catch (IOException e) {
            logger.error("Impossible de demarrer le serveur MCP sur {}:{}", config.getBindAddress(), config.getPort(), e);
            server = null;
        }
    }

    public void stop() {
        sseSessionManager.closeAll();
        if (server != null) {
            server.stop(0);
            server = null;
        }
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
        logger.info("Serveur MCP arrete");
    }

    private static final class NamedDaemonThreadFactory implements java.util.concurrent.ThreadFactory {
        private final AtomicInteger counter = new AtomicInteger(0);

        @Override
        public Thread newThread(Runnable r) {
            Thread thread = new Thread(r, "mcmcp-http-" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        }
    }

    /**
     * Handler HTTP unique : verifie Origin (CSRF) + jeton bearer optionnel, puis
     * route vers le JsonRpcRouter (POST) ou le SseSessionManager (GET).
     */
    private static final class McpHttpHandler implements HttpHandler {

        private final JsonRpcRouter router;
        private final SseSessionManager sseSessionManager;
        private final McpServerConfig config;
        private final McpLogger logger;

        McpHttpHandler(JsonRpcRouter router, SseSessionManager sseSessionManager, McpServerConfig config, McpLogger logger) {
            this.router = router;
            this.sseSessionManager = sseSessionManager;
            this.config = config;
            this.logger = logger;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                if (!isOriginAllowed(exchange)) {
                    sendPlainText(exchange, 403, "Origin non autorise");
                    return;
                }
                if (!isAuthorized(exchange)) {
                    sendPlainText(exchange, 401, "Jeton bearer manquant ou invalide");
                    return;
                }

                String method = exchange.getRequestMethod();
                if ("POST".equalsIgnoreCase(method)) {
                    handlePost(exchange);
                } else if ("GET".equalsIgnoreCase(method)) {
                    sseSessionManager.handleSseConnection(exchange);
                } else {
                    sendPlainText(exchange, 405, "Methode HTTP non supportee");
                }
            } catch (Exception e) {
                logger.error("Erreur inattendue dans le handler HTTP MCP", e);
                try {
                    sendPlainText(exchange, 500, "Erreur interne du serveur MCP");
                } catch (IOException ignored) {
                    // Rien de plus a faire si l'ecriture de la reponse d'erreur echoue elle-meme.
                }
            }
        }

        private void handlePost(HttpExchange exchange) throws IOException {
            String body = readBody(exchange);
            String responseBody = router.handleRequest(body);
            if (responseBody == null) {
                // Notification pure : aucune reponse, statut 202 Accepted sans corps.
                exchange.sendResponseHeaders(202, -1);
                exchange.close();
                return;
            }
            byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        }

        private static String readBody(HttpExchange exchange) throws IOException {
            try (InputStream in = exchange.getRequestBody()) {
                java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
                byte[] chunk = new byte[4096];
                int read;
                while ((read = in.read(chunk)) != -1) {
                    buffer.write(chunk, 0, read);
                }
                return buffer.toString(StandardCharsets.UTF_8.name());
            }
        }

        private static boolean isOriginAllowed(HttpExchange exchange) {
            List<String> originHeaders = exchange.getRequestHeaders().get("Origin");
            if (originHeaders == null || originHeaders.isEmpty()) {
                // Les clients natifs (Claude Desktop, curl, agents custom) n'envoient generalement pas Origin.
                return true;
            }
            String origin = originHeaders.get(0);
            return origin.startsWith("http://127.0.0.1") || origin.startsWith("http://localhost")
                || origin.startsWith("https://127.0.0.1") || origin.startsWith("https://localhost");
        }

        private boolean isAuthorized(HttpExchange exchange) {
            String bearerToken = config.getBearerToken();
            if (bearerToken == null || bearerToken.isEmpty()) {
                return true;
            }
            Map<String, List<String>> headers = exchange.getRequestHeaders();
            List<String> authHeaders = headers.get("Authorization");
            if (authHeaders == null || authHeaders.isEmpty()) {
                return false;
            }
            String expected = "Bearer " + bearerToken;
            return authHeaders.get(0).equals(expected);
        }

        private static void sendPlainText(HttpExchange exchange, int status, String message) throws IOException {
            byte[] bytes = message.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/plain; charset=utf-8");
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        }
    }
}
