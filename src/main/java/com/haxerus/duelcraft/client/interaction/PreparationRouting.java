package com.haxerus.duelcraft.client.interaction;

import com.haxerus.duelcraft.client.DuelScreen;
import com.haxerus.duelcraft.client.LDLibDuelScreen;
import com.haxerus.duelcraft.client.collection.CollectionScreen;
import com.haxerus.duelcraft.duel.PreparationResult;
import com.haxerus.duelcraft.duel.PreparationView;
import com.haxerus.duelcraft.server.PreparationStatePayload;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/** Minimal chat-lobby routing; a suspended editor belongs to this connection only. */
public final class PreparationRouting {
    private static CollectionScreen suspended;
    private static long editorGeneration;
    private PreparationRouting() {}
    /** Invalidates collection-open refreshes that started before preparation or disconnect. */
    public static long editorGeneration() { return editorGeneration; }

    public static boolean managementBlocked() {
        var view = com.haxerus.duelcraft.DuelcraftClient.preparation().view();
        return LDLibDuelScreen.isDuelLive() || (view != null
                && view.mode() != PreparationView.Mode.IDLE && view.mode() != PreparationView.Mode.INVITED);
    }

    public static void suspendEditor() {
        editorGeneration++;
        var mc = Minecraft.getInstance();
        if (mc.screen instanceof CollectionScreen screen) {
            if (suspended == null) suspended = screen;
            if (suspended == screen) screen.suspend();
            mc.setScreen(null);
        }
    }
    public static boolean restoreEditor() {
        if (managementBlocked()) return false;
        if (suspended == null) return false;
        var screen = suspended; suspended = null; screen.resume(); Minecraft.getInstance().setScreen(screen); return true;
    }
    public static void apply(PreparationStatePayload state) {
        var mc = Minecraft.getInstance(); var mode = state.view().mode();
        if (mode == PreparationView.Mode.RPS || mode == PreparationView.Mode.FIRST_CHOICE || mode == PreparationView.Mode.STARTING) suspendEditor();
        if (state.result() == PreparationResult.START_FAILED) {
            LDLibDuelScreen.close();
            if (mc.screen instanceof DuelScreen) mc.setScreen(null);
            restoreEditor();
            if (mc.player != null) mc.player.sendSystemMessage(Component.literal("Duel could not start. Check your active deck and try again."));
        } else if (mode == PreparationView.Mode.IDLE && !(mc.screen instanceof DuelScreen)) restoreEditor();
    }
    public static void disconnect() { editorGeneration++; if (suspended != null) suspended.disconnect(); suspended = null; }
}
