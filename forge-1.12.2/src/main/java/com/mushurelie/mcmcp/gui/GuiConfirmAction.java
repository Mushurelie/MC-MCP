package com.mushurelie.mcmcp.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;

import java.util.function.Consumer;

/**
 * Popup de confirmation en jeu (MVP UX) affichee quand requireConfirmation=true
 * et qu'un outil en ecriture est appele. Se referme automatiquement (refus) au bout
 * de McpConfig.confirmationTimeoutSeconds secondes.
 */
public class GuiConfirmAction extends GuiScreen {

    private final String toolName;
    private final String argsSummary;
    private final long deadlineMillis;
    private final Consumer<Boolean> onResult;
    private boolean resultSent = false;

    public GuiConfirmAction(String toolName, String argsSummary, int timeoutSeconds, Consumer<Boolean> onResult) {
        this.toolName = toolName;
        this.argsSummary = argsSummary;
        this.onResult = onResult;
        this.deadlineMillis = System.currentTimeMillis() + (timeoutSeconds * 1000L);
    }

    @Override
    public void initGui() {
        this.buttonList.clear();
        this.buttonList.add(new GuiButton(0, this.width / 2 - 105, this.height / 2 + 25, 100, 20,
            I18n.format("mcmcp.chat.confirm.accept")));
        this.buttonList.add(new GuiButton(1, this.width / 2 + 5, this.height / 2 + 25, 100, 20,
            I18n.format("mcmcp.chat.confirm.reject")));
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == 0) {
            complete(true);
        } else if (button.id == 1) {
            complete(false);
        }
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        if (!resultSent && System.currentTimeMillis() >= deadlineMillis) {
            complete(false);
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();
        drawCenteredString(fontRenderer, I18n.format("mcmcp.chat.confirm.title"), width / 2, height / 2 - 40, 0xFFFFFF);
        drawCenteredString(fontRenderer, toolName, width / 2, height / 2 - 22, 0xFFAA00);
        drawCenteredString(fontRenderer, argsSummary, width / 2, height / 2 - 8, 0xAAAAAA);
        long remaining = Math.max(0, (deadlineMillis - System.currentTimeMillis()) / 1000L);
        drawCenteredString(fontRenderer, remaining + "s", width / 2, height / 2 + 8, 0x55FF55);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    private void complete(boolean accepted) {
        if (resultSent) {
            return;
        }
        resultSent = true;
        onResult.accept(accepted);
        Minecraft.getMinecraft().displayGuiScreen(null);
    }
}
