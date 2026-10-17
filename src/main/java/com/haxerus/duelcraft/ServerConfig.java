package com.haxerus.duelcraft;

import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

/** World-specific server rules captured once for each server lifecycle. */
public final class ServerConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue REQUIRE_CARD_OWNERSHIP = BUILDER
            .push("ownership")
            .comment("Require deposited copies of every card before a saved deck can be used")
            .define("requireCardOwnership", false);

    static {
        BUILDER.pop();
    }

    public static final ModConfigSpec SPEC = BUILDER.build();
    private static Boolean capturedOwnershipRequirement;

    private ServerConfig() {}

    public static void onLoading(ModConfigEvent.Loading event) {
        if (isOurServerConfig(event.getConfig())) capture(REQUIRE_CARD_OWNERSHIP.getAsBoolean());
    }

    public static void onUnloading(ModConfigEvent.Unloading event) {
        if (isOurServerConfig(event.getConfig())) reset();
    }

    public static synchronized boolean requireCardOwnership() {
        if (capturedOwnershipRequirement == null) {
            throw new IllegalStateException("Duelcraft server config was not loaded before server startup");
        }
        return capturedOwnershipRequirement;
    }

    static synchronized void capture(boolean ownershipRequired) {
        if (capturedOwnershipRequirement == null) capturedOwnershipRequirement = ownershipRequired;
    }

    public static synchronized void reset() {
        capturedOwnershipRequirement = null;
    }

    private static boolean isOurServerConfig(ModConfig config) {
        return config.getType() == ModConfig.Type.SERVER && config.getSpec() == SPEC;
    }
}
