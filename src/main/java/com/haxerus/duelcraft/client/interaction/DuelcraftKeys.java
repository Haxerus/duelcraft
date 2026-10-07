package com.haxerus.duelcraft.client.interaction;

import com.haxerus.duelcraft.interaction.ScreenRoute;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;

public final class DuelcraftKeys {
    private static final KeyMapping HOME = new KeyMapping("key.duelcraft.home", GLFW.GLFW_KEY_J, "key.categories.duelcraft");
    private DuelcraftKeys() {}
    public static void register(RegisterKeyMappingsEvent event) { event.register(HOME); }
    public static void tick(ClientTickEvent.Post event) {
        var mc = Minecraft.getInstance();
        while (HOME.consumeClick()) {
            if (mc.player != null && mc.level != null && mc.screen == null) DuelcraftScreens.request(ScreenRoute.HOME);
        }
    }
}
