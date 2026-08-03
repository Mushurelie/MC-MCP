package com.mushurelie.mcmcp.gui;

import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.button.Button;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.text.StringTextComponent;

import java.util.function.Consumer;

/**
 * Popup de confirmation en jeu (MVP UX) affichee quand requireConfirmation=true
 * et qu'un outil en ecriture est appele. Se referme automatiquement (refus) au bout
 * de McpConfig.confirmationTimeoutSeconds secondes.
 */
public class GuiConfirmAction extends Screen {

    private final String toolName;
    private final String argsSummary;
    private final long deadlineMillis;
    private final Consumer<Boolean> onResult;
    private boolean resultSent = false;

    public GuiConfirmAction(String toolName, String argsSummary, int timeoutSeconds, Consumer<Boolean> onResult) {
        super(new StringTextComponent(I18n.get("mcmcp.chat.confirm.title")));
        this.toolName = toolName;
        this.argsSummary = argsSummary;
        this.onResult = onResult;
        this.deadlineMillis = System.currentTimeMillis() + (timeoutSeconds * 1000L);
    }

    @Override
    protected void init() {
        this.addButton(new Button(this.width / 2 - 105, this.height / 2 + 25, 100, 20,
            new StringTextComponent(I18n.get("mcmcp.chat.confirm.accept")), button -> complete(true)));
        this.addButton(new Button(this.width / 2 + 5, this.height / 2 + 25, 100, 20,
            new StringTextComponent(I18n.get("mcmcp.chat.confirm.reject")), button -> complete(false)));
    }

    @Override
    public void tick() {
        super.tick();
        if (!resultSent && System.currentTimeMillis() >= deadlineMillis) {
            complete(false);
        }
    }

    @Override
    public void render(MatrixStack matrixStack, int mouseX, int mouseY, float partialTicks) {
        this.renderBackground(matrixStack);
        drawCenteredString(matrixStack, font, I18n.get("mcmcp.chat.confirm.title"), width / 2, height / 2 - 40, 0xFFFFFF);
        drawCenteredString(matrixStack, font, toolName, width / 2, height / 2 - 22, 0xFFAA00);
        drawCenteredString(matrixStack, font, argsSummary, width / 2, height / 2 - 8, 0xAAAAAA);
        long remaining = Math.max(0, (deadlineMillis - System.currentTimeMillis()) / 1000L);
        drawCenteredString(matrixStack, font, remaining + "s", width / 2, height / 2 + 8, 0x55FF55);
        super.render(matrixStack, mouseX, mouseY, partialTicks);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void complete(boolean accepted) {
        if (resultSent) {
            return;
        }
        resultSent = true;
        onResult.accept(accepted);
        Minecraft.getInstance().setScreen(null);
    }
}
