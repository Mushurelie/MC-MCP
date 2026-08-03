package com.mushurelie.mcmcp.dispatch;

import com.google.gson.JsonElement;
import com.mushurelie.mcmcp.MinecraftMcp;
import net.minecraft.client.Minecraft;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.fml.common.FMLCommonHandler;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * Point de passage OBLIGATOIRE pour toute action touchant le monde, le joueur,
 * les entites, l'inventaire ou envoyant un packet. Le serveur HTTP MCP tourne sur
 * ses propres threads (voir McpHttpServer) : rien ne doit toucher a l'etat du jeu
 * en dehors du thread client ou du thread serveur integre.
 *
 * <p>En solo, client et serveur integre partagent la meme JVM : ce dispatcher expose
 * les deux points d'entree (addScheduledTask cote client, addScheduledTask cote serveur)
 * et laisse chaque outil choisir le bon thread selon la nature de l'action :</p>
 * <ul>
 *   <li>runOnClientThread : lecture/rendu/entree joueur (EntityPlayerSP), chat local, screenshot</li>
 *   <li>runOnServerThread : mutation autoritaire du monde (WorldServer), inventaire, entites serveur</li>
 * </ul>
 */
public final class MainThreadDispatcher {

    private MainThreadDispatcher() {
    }

    public static CompletableFuture<JsonElement> runOnClientThread(Supplier<JsonElement> task) {
        CompletableFuture<JsonElement> future = new CompletableFuture<>();
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null) {
            future.completeExceptionally(new IllegalStateException("Instance client Minecraft indisponible"));
            return future;
        }
        mc.addScheduledTask(() -> executeSafely(task, future));
        return future;
    }

    public static CompletableFuture<JsonElement> runOnServerThread(Supplier<JsonElement> task) {
        CompletableFuture<JsonElement> future = new CompletableFuture<>();
        MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
        if (server == null) {
            future.completeExceptionally(new IllegalStateException("Serveur integre indisponible (aucune partie en cours ?)"));
            return future;
        }
        server.addScheduledTask(() -> executeSafely(task, future));
        return future;
    }

    private static void executeSafely(Supplier<JsonElement> task, CompletableFuture<JsonElement> future) {
        try {
            JsonElement result = task.get();
            future.complete(result);
        } catch (Throwable t) {
            MinecraftMcp.logger.error("Erreur pendant l'execution d'un outil MCP sur le thread principal", t);
            future.completeExceptionally(t);
        }
    }
}
