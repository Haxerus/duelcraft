package com.haxerus.duelcraft.client.uitest;

import net.minecraft.client.gui.screens.Screen;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.function.Consumer;

/** Quiet letterboxing for exact Gallery fixtures; never applied to baseline screens. */
public final class GalleryBackdrop {
    private final Set<Screen> screens = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Consumer<ScreenEvent.Render.Pre> listener = event -> {
        var screen = event.getScreen();
        if (screens.contains(screen)) {
            event.getGuiGraphics().fill(0, 0, screen.width, screen.height, 0xff211c2a);
            event.getGuiGraphics().flush();
        }
    };

    public void install() { NeoForge.EVENT_BUS.addListener(listener); }
    public <T extends Screen> T include(T screen) { screens.add(screen); return screen; }
    public void close() { NeoForge.EVENT_BUS.unregister(listener); screens.clear(); }
}
