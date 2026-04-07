package com.haxerus.duelcraft;

import com.lowdragmc.lowdraglib2.plugin.ILDLibPlugin;
import com.lowdragmc.lowdraglib2.plugin.LDLibPlugin;

/**
 * LDLib2 plugin entry point. Required for LDLib2 to recognize this mod.
 * Add initialization for custom LDLib2 features here (e.g., custom textures, stylesheets).
 */
@LDLibPlugin
public class DuelcraftLDLibPlugin implements ILDLibPlugin {
    @Override
    public void onLoad() {
        Duelcraft.LOGGER.info("Duelcraft LDLib2 plugin loaded");
    }
}
