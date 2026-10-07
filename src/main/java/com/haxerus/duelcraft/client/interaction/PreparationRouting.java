package com.haxerus.duelcraft.client.interaction;

import com.haxerus.duelcraft.server.PreparationStatePayload;

/** Compatibility entry points for existing commands and runtime fixtures. */
public final class PreparationRouting {
    private PreparationRouting() {}
    public static long editorGeneration() { return DuelcraftScreens.generation(); }
    public static boolean managementBlocked() { return DuelcraftScreens.managementBlocked(); }
    public static void suspendEditor() { DuelcraftScreens.suspendEditor(); }
    public static boolean restoreEditor() { return DuelcraftScreens.restoreEditor(); }
    public static void apply(PreparationStatePayload state) { DuelcraftScreens.preparationChanged(state); }
    public static void disconnect() { DuelcraftScreens.disconnect(); }
}
