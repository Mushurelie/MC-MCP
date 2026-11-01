package com.mushurelie.mcmcp.tools.chat;

import net.minecraft.commands.CommandSource;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * CommandSource qui capture la sortie texte au lieu de l'envoyer dans le tchat.
 * Enveloppe dans un CommandSourceStack complet par ExecuteCommandTool pour executer
 * une commande avec les permissions/position du joueur, sortie capturee.
 */
final class CapturingCommandSource implements CommandSource {

    private final List<String> capturedLines = new ArrayList<>();

    List<String> getCapturedLines() {
        return capturedLines;
    }

    @Override
    public void sendSystemMessage(Component component) {
        capturedLines.add(component.getString());
    }

    @Override
    public boolean acceptsSuccess() {
        return true;
    }

    @Override
    public boolean acceptsFailure() {
        return true;
    }

    @Override
    public boolean shouldInformAdmins() {
        return false;
    }
}
