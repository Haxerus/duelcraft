package com.haxerus.duelcraft.client.interaction;

import java.util.UUID;

/** Latest navigation wins; generations also guard deferred collection loads. */
final class ScreenRequests {
    private UUID pending;
    private long generation;
    UUID begin() { invalidate(); return pending = UUID.randomUUID(); }
    long generation() { return generation; }
    void invalidate() { pending = null; generation++; }
    boolean accept(UUID id, boolean unsolicited, boolean inWorld) {
        if (unsolicited ? !inWorld || pending != null : !id.equals(pending)) return false;
        invalidate();
        return true;
    }
}
