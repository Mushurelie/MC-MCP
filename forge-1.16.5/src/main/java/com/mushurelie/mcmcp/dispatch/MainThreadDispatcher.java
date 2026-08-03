package com.mushurelie.mcmcp.dispatch;

import com.google.gson.JsonElement;
import net.minecraft.client.Minecraft;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.fml.server.ServerLifecycleHooks;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * Point de passage OBLIGATOIRE pour toute action touchant le monde, le joueur,
 * les entites, l'inventaire ou envoyant un packet. Le serveur HTTP MCP tourne sur
 * ses propres threads (voir McpHttpServer) : rien ne doit toucher a l'etat du jeu
 * en dehors du thread client ou du thread serveur integre.
 *
 * <p>En solo, client et serveur integre partagent la meme JVM : ce dispatcher expose
 * les deux points d'entree et laisse chaque outil choisir le bon thread selon la
 * nature de l'action :</p>
 * <ul>
 *   <li>runOnClientThread : lecture/rendu/entree joueur (ClientPlayerEntity), chat local, screenshot</li>
 *   <li>runOnServerThread : mutation autoritaire du monde (ServerWorld), inventaire, entites serveur</li>
 * </ul>
 */
public final class MainThreadDispatcher {

    private static final Logger LOGGER = LogManager.getLogger("mcmcp");

    private MainThreadDispatcher() {
    }

    public static CompletableFuture<JsonElement> runOnClientThread(Supplier<JsonElement> task) {
        CompletableFuture<JsonElement> future = new CompletableFuture<>();
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) {
            future.completeExceptionally(new IllegalStateException("Instance client Minecraft indisponible"));
            return future;
        }
        mc.execute(() -> executeSafely(task, future));
        return future;
    }

    public static CompletableFuture<JsonElement> runOnServerThread(Supplier<JsonElement> task) {
        CompletableFuture<JsonElement> future = new CompletableFuture<>();
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            future.completeExceptionally(new IllegalStateException("Serveur integre indisponible (aucune partie en cours ?)"));
            return future;
        }
        server.execute(() -> executeSafely(task, future));
        return future;
    }

    private static void executeSafely(Supplier<JsonElement> task, CompletableFuture<JsonElement> future) {
        try {
            JsonElement result = task.get();
            future.complete(result);
        } catch (Throwable t) {
            LOGGER.error("Erreur pendant l'execution d'un outil MCP sur le thread principal", t);
            future.completeExceptionally(t);
        }
    }
}
