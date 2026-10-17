package com.haxerus.duelcraft;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.haxerus.duelcraft.server.DuelManager;
import net.neoforged.bus.api.BusBuilder;
import net.neoforged.fml.config.IConfigSpec;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

import static org.junit.jupiter.api.Assertions.*;

class ServerConfigTest {
    @AfterEach void reset() {
        if (DuelManager.get() != null) DuelManager.onServerStopped(new ServerStoppedEvent(null));
        ServerConfig.reset();
        ServerConfig.SPEC.acceptConfig(null);
    }

    @Test void ownershipDefaultsToOptional() {
        assertFalse(ServerConfig.REQUIRE_CARD_OWNERSHIP.getDefault());
        assertEquals(List.of("ownership", "requireCardOwnership"),
                ServerConfig.REQUIRE_CARD_OWNERSHIP.getPath());
    }

    @Test void registeredLifecycleLoadsBeforeStartAndResetsBetweenIntegratedWorlds() throws Exception {
        var modBus = BusBuilder.builder().build();
        var serverBus = BusBuilder.builder().build();
        Duelcraft.registerServerLifecycle(modBus, serverBus);

        modBus.post(new ModConfigEvent.Loading(serverConfig(false)));
        serverBus.post(new ServerStartingEvent(null));
        assertNotNull(DuelManager.get());
        assertFalse(ServerConfig.requireCardOwnership());
        modBus.post(new ModConfigEvent.Reloading(serverConfig(true)));
        assertFalse(ServerConfig.requireCardOwnership(), "live reload must not change the running policy");

        serverBus.post(new ServerStoppedEvent(null));
        assertNull(DuelManager.get());
        assertThrows(IllegalStateException.class, ServerConfig::requireCardOwnership);

        ServerConfig.SPEC.acceptConfig(null);
        modBus.post(new ModConfigEvent.Loading(serverConfig(true)));
        serverBus.post(new ServerStartingEvent(null));
        assertNotNull(DuelManager.get());
        assertTrue(ServerConfig.requireCardOwnership(), "the next world must capture its own setting");
        serverBus.post(new ServerStoppedEvent(null));
    }

    @Test void effectiveSettingIsUnavailableOutsideAServerLifecycle() {
        assertThrows(IllegalStateException.class, ServerConfig::requireCardOwnership);
    }

    private static ModConfig serverConfig(boolean ownershipRequired) throws Exception {
        var config = CommentedConfig.inMemory();
        ServerConfig.SPEC.correct(config);
        config.set(List.of("ownership", "requireCardOwnership"), ownershipRequired);

        Constructor<ModConfig> configConstructor = ModConfig.class.getDeclaredConstructor(ModConfig.Type.class,
                IConfigSpec.class, net.neoforged.fml.ModContainer.class, String.class, ReentrantLock.class);
        configConstructor.setAccessible(true);
        var modConfig = configConstructor.newInstance(ModConfig.Type.SERVER, ServerConfig.SPEC, null,
                "duelcraft-server.toml", new ReentrantLock());

        Class<?> loadedType = Class.forName("net.neoforged.fml.config.LoadedConfig");
        var loadedConstructor = loadedType.getDeclaredConstructor(CommentedConfig.class, Path.class, ModConfig.class);
        loadedConstructor.setAccessible(true);
        var loaded = (IConfigSpec.ILoadedConfig) loadedConstructor.newInstance(config, null, modConfig);
        ServerConfig.SPEC.acceptConfig(loaded);
        return modConfig;
    }
}
