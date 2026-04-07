package com.haxerus.duelcraft;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

@Mod(value = Duelcraft.MODID, dist = Dist.CLIENT)
public class DuelcraftClient {
    public DuelcraftClient(IEventBus modBus, ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        modBus.addListener(DuelcraftClient::onClientSetup);
    }

    private static void onClientSetup(FMLClientSetupEvent event) {
        Duelcraft.LOGGER.info("HELLO FROM CLIENT SETUP");
        Duelcraft.LOGGER.info("MINECRAFT NAME >> {}", Minecraft.getInstance().getUser().getName());
    }
}
