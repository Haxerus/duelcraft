package com.haxerus.duelcraft.client;

import com.lowdragmc.lowdraglib2.gui.holder.ModularUIScreen;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
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
