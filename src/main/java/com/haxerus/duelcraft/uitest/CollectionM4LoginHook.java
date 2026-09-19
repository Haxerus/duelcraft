package com.haxerus.duelcraft.uitest;

import com.haxerus.duelcraft.Duelcraft;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/** Installs only an opt-in retained-world test restriction, before any player login. */
@EventBusSubscriber(modid = Duelcraft.MODID)
public final class CollectionM4LoginHook {
    private static CollectionProgressionFixture listener;
    public static boolean installed() { return listener != null; }
    @SubscribeEvent public static void starting(ServerStartingEvent event) {
        String phase = System.getProperty("duelcraft.uitest.collectionM4Lifecycle", "");
        if (FMLEnvironment.production || !(phase.equals("throwing") || phase.equals("restricted"))) return;
        listener = new CollectionProgressionFixture(); listener.mode = phase.equals("throwing") ? "throw" : "deny";
        NeoForge.EVENT_BUS.register(listener);
        Duelcraft.LOGGER.info("M4 login fixture installed before player login: {}", phase);
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) {
        if (listener != null) NeoForge.EVENT_BUS.unregister(listener);
        listener = null;
    }
}
