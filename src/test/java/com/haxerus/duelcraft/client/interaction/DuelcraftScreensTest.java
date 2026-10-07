package com.haxerus.duelcraft.client.interaction;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class DuelcraftScreensTest {
    @Test void lateHomeCannotReplaceNewerCollectionOrCompleteTwice() {
        var requests = new ScreenRequests();
        var home = requests.begin();
        var collection = requests.begin();
        assertFalse(requests.accept(home, false, false));
        assertTrue(requests.accept(collection, false, false));
        assertFalse(requests.accept(collection, false, false));
    }

    @Test void forcedRouteAndDisconnectInvalidateRepliesAndDeferredLoads() {
        var requests = new ScreenRequests();
        var request = requests.begin();
        long generation = requests.generation();
        requests.invalidate();
        assertFalse(requests.accept(request, false, true));
        assertNotEquals(generation, requests.generation());
    }

    @Test void physicalEntryCannotReplaceScreenOrOvertakeRequestedNavigation() {
        var requests = new ScreenRequests();
        assertFalse(requests.accept(UUID.randomUUID(), true, false));
        assertTrue(requests.accept(UUID.randomUUID(), true, true));
        var requested = requests.begin();
        assertFalse(requests.accept(UUID.randomUUID(), true, true));
        assertTrue(requests.accept(requested, false, true));
    }
}
