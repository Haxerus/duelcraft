package com.haxerus.duelcraft;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ServerConfigTest {
    @AfterEach void reset() {
        ServerConfig.reset();
    }

    @Test void ownershipDefaultsToOptional() {
        assertFalse(ServerConfig.REQUIRE_CARD_OWNERSHIP.getDefault());
        assertEquals(List.of("ownership", "requireCardOwnership"),
                ServerConfig.REQUIRE_CARD_OWNERSHIP.getPath());
    }

    @Test void capturesOncePerServerAndResetsBetweenIntegratedWorlds() {
        ServerConfig.capture(false);
        assertFalse(ServerConfig.requireCardOwnership());
        ServerConfig.capture(true);
        assertFalse(ServerConfig.requireCardOwnership(), "live reload must not change the effective policy");

        ServerConfig.reset();
        ServerConfig.capture(true);
        assertTrue(ServerConfig.requireCardOwnership(), "the next world must capture its own setting");
    }

    @Test void effectiveSettingIsUnavailableOutsideAServerLifecycle() {
        assertThrows(IllegalStateException.class, ServerConfig::requireCardOwnership);
    }
}
