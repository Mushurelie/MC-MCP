package com.mushurelie.mcmcp.tools;

import com.mushurelie.mcmcp.gui.GuiConfirmAction;
import net.minecraft.client.Minecraft;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Gere l'affichage de la popup de confirmation en jeu (accept/reject/timeout).
 * Une seule confirmation a la fois en v1 : une demande concurrente est refusee
 * immediatement plutot que mise en file d'attente.
 */
public final class ConfirmationManager implements ToolRegistry.ConfirmationRequester {

    private static final AtomicBoolean BUSY = new AtomicBoolean(false);

    @Override
    public CompletableFuture<Boolean> requestConfirmation(String toolName, String argsSummary, int timeoutSeconds) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        if (!BUSY.compareAndSet(false, true)) {
            future.complete(false);
            return future;
        }
        Minecraft.getMinecraft().addScheduledTask(() -> {
            GuiConfirmAction gui = new GuiConfirmAction(
                toolName,
                argsSummary,
                timeoutSeconds,
                accepted -> {
                    BUSY.set(false);
                    future.complete(accepted);
                }
            );
            Minecraft.getMinecraft().displayGuiScreen(gui);
        });
        return future;
    }
}
