package com.haxerus.duelcraft.uitest;

import com.haxerus.duelcraft.api.DeckUseCheckEvent;
import net.neoforged.bus.api.SubscribeEvent;

/** Inert until explicitly registered by a DEV_ONLY scenario; never auto-subscribed. */
public final class CollectionPolicyTestRestriction {
    public static final String REASON = "Policy lifecycle fixture restriction";
    private final boolean throwing;

    public CollectionPolicyTestRestriction(boolean throwing) { this.throwing = throwing; }

    @SubscribeEvent
    public void check(DeckUseCheckEvent event) {
        if (throwing) throw new IllegalStateException("Policy lifecycle fixture failure");
        event.deny(REASON);
    }
}
