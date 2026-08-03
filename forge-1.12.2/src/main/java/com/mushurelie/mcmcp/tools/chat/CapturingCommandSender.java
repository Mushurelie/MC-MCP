package com.mushurelie.mcmcp.tools.chat;

import net.minecraft.command.CommandResultStats;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

/**
 * Enveloppe le joueur reel comme ICommandSender pour capturer la sortie texte
 * d'execute_command (au lieu de l'envoyer uniquement dans le tchat en jeu).
 */
final class CapturingCommandSender implements ICommandSender {

    private final EntityPlayerMP wrapped;
    private final List<String> capturedLines = new ArrayList<>();

    CapturingCommandSender(EntityPlayerMP wrapped) {
        this.wrapped = wrapped;
    }

    List<String> getCapturedLines() {
        return capturedLines;
    }

    @Override
    public String getName() {
        return wrapped.getName();
    }

    @Override
    public boolean canUseCommand(int permLevel, String commandName) {
        return wrapped.canUseCommand(permLevel, commandName);
    }

    @Override
    public ITextComponent getDisplayName() {
        return wrapped.getDisplayName();
    }

    @Override
    public void sendMessage(ITextComponent component) {
        capturedLines.add(component.getUnformattedText());
    }

    @Override
    public BlockPos getPosition() {
        return wrapped.getPosition();
    }

    @Override
    public Vec3d getPositionVector() {
        return wrapped.getPositionVector();
    }

    @Override
    public World getEntityWorld() {
        return wrapped.getEntityWorld();
    }

    @Override
    public Entity getCommandSenderEntity() {
        return wrapped;
    }

    @Override
    public boolean sendCommandFeedback() {
        return wrapped.sendCommandFeedback();
    }

    @Override
    public void setCommandStat(CommandResultStats.Type type, int amount) {
        wrapped.setCommandStat(type, amount);
    }

    @Override
    public MinecraftServer getServer() {
        return wrapped.getServer();
    }
}
