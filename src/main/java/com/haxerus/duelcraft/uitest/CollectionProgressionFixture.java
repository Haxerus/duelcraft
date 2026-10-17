package com.haxerus.duelcraft.uitest;

import com.haxerus.duelcraft.api.DeckUseCheckEvent;
import net.neoforged.bus.api.SubscribeEvent;
import java.util.UUID;

/** Mutable progression for an explicitly installed runtime fixture. */
public final class CollectionProgressionFixture {
    public UUID owner;
    public String mode = "allow";
    @SubscribeEvent public void check(DeckUseCheckEvent event) {
        if (owner != null && !owner.equals(event.context().owner())) return;
        if (mode.equals("throw")) throw new IllegalStateException("M4 progression fixture failure");
        if (mode.equals("deny")) event.deny("M4 progression requirement");
    }
}
