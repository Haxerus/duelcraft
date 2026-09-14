package com.haxerus.duelcraft.client;

import com.lowdragmc.lowdraglib2.gui.holder.ModularUIScreen;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.network.chat.Component;

/**
 * Hosts the duel UI on a fixed 960x540 design canvas and scales it uniformly to the window.
 * LDLib2 centers the root itself, and Minecraft calls {@link #init()} on open and on every
 * resize, so the scale factor tracks the window with no extra hooks.
 */
public final class DuelScreen extends ModularUIScreen {

    public static final int DESIGN_WIDTH = 960;
    public static final int DESIGN_HEIGHT = 540;
    private static final float MIN_SCALE = 0.25f;

    private final UIElement canvas;

    public DuelScreen(ModularUI modularUI, UIElement canvas, Component title) {
        super(modularUI, title);
        this.canvas = canvas;
    }

    /** ESC must not abandon a live duel; {@code /duel show} reopens the screen if it closes another way. */
    @Override
    public boolean shouldCloseOnEsc() {
        return !LDLibDuelScreen.isDuelLive();
    }

    /**
     * While the duel is live ESC toggles the leave-duel dialog instead of closing the screen; the
     * dialog is drawn above every other overlay, so it works even under a modal prompt.
     */
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (modifiers != 0) LDLibDuelScreen.setChainSkipHeld(false);
        if (keyCode == InputConstants.KEY_C && modifiers == 0 && LDLibDuelScreen.isDuelLive() && !isTyping()) {
            LDLibDuelScreen.setChainSkipHeld(true);
            return true;
        }
        if (keyCode == InputConstants.KEY_ESCAPE && LDLibDuelScreen.isDuelLive()) {
            LDLibDuelScreen.togglePauseMenu();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private boolean isTyping() {
        for (var element = modularUI.getFocusedElement(); element != null; element = element.getParent()) {
            if (element instanceof TextField) return true;
        }
        return false;
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (keyCode == InputConstants.KEY_C) LDLibDuelScreen.setChainSkipHeld(false);
        return super.keyReleased(keyCode, scanCode, modifiers);
    }

    @Override
    public void tick() {
        if (!minecraft.isWindowActive() || isTyping()) LDLibDuelScreen.setChainSkipHeld(false);
        super.tick();
    }

    @Override
    public void removed() {
        LDLibDuelScreen.setChainSkipHeld(false);
        super.removed();
        LDLibDuelScreen.onScreenRemoved(this);
    }

    @Override
    public void init() {
        super.init();
        if (width == 0 || height == 0) return; // minimized window
        float k = Math.max(MIN_SCALE,
                Math.min(width / (float) DESIGN_WIDTH, height / (float) DESIGN_HEIGHT));
        // Transform2D.scale assigns, and the default pivot is the center, which sits on the
        // screen center because LDLib2 centered the root.
        canvas.transform(t -> t.scale(k));
    }
}
