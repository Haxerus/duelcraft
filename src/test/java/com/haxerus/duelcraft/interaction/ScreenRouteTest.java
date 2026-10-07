package com.haxerus.duelcraft.interaction;

import com.haxerus.duelcraft.server.InteractionPayloadHandler;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ScreenRouteTest {
    @Test void liveAndAcceptedPreparationOverrideEveryManagementEntry() {
        for (var route : new ScreenRoute[]{ScreenRoute.HOME, ScreenRoute.COLLECTION, ScreenRoute.LOBBY}) {
            assertEquals(ScreenRoute.DUEL, InteractionPayloadHandler.resolve(route, true, false));
            assertEquals(ScreenRoute.DUEL, InteractionPayloadHandler.resolve(route, true, true));
            assertEquals(ScreenRoute.LOBBY, InteractionPayloadHandler.resolve(route, false, true));
            assertEquals(route, InteractionPayloadHandler.resolve(route, false, false));
        }
    }
}
