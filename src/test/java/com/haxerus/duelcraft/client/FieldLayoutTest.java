package com.haxerus.duelcraft.client;

import com.haxerus.duelcraft.client.FieldLayout.PendulumMode;
import com.haxerus.duelcraft.client.FieldLayout.Side;
import com.haxerus.duelcraft.client.FieldLayout.Zone;
import com.haxerus.duelcraft.core.DuelRule;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static com.haxerus.duelcraft.core.OcgConstants.*;
import static org.junit.jupiter.api.Assertions.*;

class FieldLayoutTest {

    private static final FieldLayout MR5 = FieldLayout.fromFlags(DuelRule.MR5.flags());
    private static final FieldLayout MR3 = FieldLayout.fromFlags(DuelRule.MR3.flags());
    private static final FieldLayout SPEED = FieldLayout.fromFlags(DuelRule.SPEED.flags());

    private static Zone z(Side side, int location, int sequence) {
        return new Zone(side, location, sequence);
    }

    // ---- fromFlags ----

    @Test
    void presetsResolveToTheThreeGeometries() {
        for (DuelRule r : List.of(DuelRule.MR1, DuelRule.GOAT, DuelRule.MR2)) {
            assertEquals(new FieldLayout(5, false, PendulumMode.NONE), FieldLayout.fromFlags(r.flags()), r.name());
        }
        assertEquals(new FieldLayout(5, false, PendulumMode.SEPARATE), MR3);
        for (DuelRule r : List.of(DuelRule.MR4, DuelRule.MR5)) {
            assertEquals(new FieldLayout(5, true, PendulumMode.SHARED), FieldLayout.fromFlags(r.flags()), r.name());
        }
        for (DuelRule r : List.of(DuelRule.SPEED, DuelRule.RUSH)) {
            assertEquals(new FieldLayout(3, false, PendulumMode.NONE), FieldLayout.fromFlags(r.flags()), r.name());
        }
    }

    @Test
    void threeColumnsNeverHaveExtraMonsterZones() {
        assertFalse(FieldLayout.fromFlags(DUEL_3_COLUMNS_FIELD | DUEL_EMZONE).emz());
    }

    @Test
    void separateBitWithoutPendulumBitMeansNoPendulum() {
        assertEquals(PendulumMode.NONE, FieldLayout.fromFlags(DUEL_SEPARATE_PZONE).pendulum());
    }

    // ---- MR5 ----

    @Test
    void mr5MapsMainZonesInTheOwnersFrame() {
        assertEquals(Optional.of("plr-mon-0"), MR5.slotId(z(Side.PLR, LOCATION_MZONE, 0)));
        assertEquals(Optional.of("opp-mon-4"), MR5.slotId(z(Side.OPP, LOCATION_MZONE, 4)));
        assertEquals(Optional.of("plr-st-2"), MR5.slotId(z(Side.PLR, LOCATION_SZONE, 2)));
        assertEquals(Optional.of("opp-field-spell"), MR5.slotId(z(Side.OPP, LOCATION_SZONE, 5)));
    }

    @Test
    void mr5CrossMapsExtraMonsterZones() {
        assertEquals(Optional.of("emz-left"), MR5.slotId(z(Side.PLR, LOCATION_MZONE, 5)));
        assertEquals(Optional.of("emz-right"), MR5.slotId(z(Side.PLR, LOCATION_MZONE, 6)));
        assertEquals(Optional.of("emz-right"), MR5.slotId(z(Side.OPP, LOCATION_MZONE, 5)));
        assertEquals(Optional.of("emz-left"), MR5.slotId(z(Side.OPP, LOCATION_MZONE, 6)));
    }

    @Test
    void mr5HasNoSeparatePendulumZones() {
        assertTrue(MR5.slotId(z(Side.PLR, LOCATION_SZONE, 6)).isEmpty());
        assertTrue(MR5.slotId(z(Side.OPP, LOCATION_SZONE, 7)).isEmpty());
        assertArrayEquals(new int[]{0, 4}, MR5.pendulumSequences());
        assertEquals(Set.of("plr-pz-left", "plr-pz-right", "opp-pz-left", "opp-pz-right"), MR5.hiddenSlotIds());
    }

    @Test
    void emzSlotsListTheViewersZoneFirst() {
        assertEquals(List.of(z(Side.PLR, LOCATION_MZONE, 5), z(Side.OPP, LOCATION_MZONE, 6)), MR5.zonesOf("emz-left"));
        assertEquals(List.of(z(Side.PLR, LOCATION_MZONE, 6), z(Side.OPP, LOCATION_MZONE, 5)), MR5.zonesOf("emz-right"));
    }

    // ---- MR3 ----

    @Test
    void mr3UsesSeparatePendulumSlotsAndHidesEmz() {
        assertEquals(Optional.of("plr-pz-left"), MR3.slotId(z(Side.PLR, LOCATION_SZONE, 6)));
        assertEquals(Optional.of("plr-pz-right"), MR3.slotId(z(Side.PLR, LOCATION_SZONE, 7)));
        assertEquals(Optional.of("opp-pz-left"), MR3.slotId(z(Side.OPP, LOCATION_SZONE, 6)));
        assertTrue(MR3.slotId(z(Side.PLR, LOCATION_MZONE, 5)).isEmpty());
        assertArrayEquals(new int[]{6, 7}, MR3.pendulumSequences());
        assertEquals(Set.of("emz-left", "emz-right"), MR3.hiddenSlotIds());
    }

    // ---- Speed ----

    @Test
    void speedShowsOnlyTheMiddleThreeColumns() {
        assertTrue(SPEED.slotId(z(Side.PLR, LOCATION_MZONE, 0)).isEmpty());
        assertEquals(Optional.of("plr-mon-1"), SPEED.slotId(z(Side.PLR, LOCATION_MZONE, 1)));
        assertEquals(Optional.of("opp-st-3"), SPEED.slotId(z(Side.OPP, LOCATION_SZONE, 3)));
        assertTrue(SPEED.slotId(z(Side.OPP, LOCATION_SZONE, 4)).isEmpty());
        assertTrue(SPEED.slotId(z(Side.PLR, LOCATION_MZONE, 5)).isEmpty());
        assertArrayEquals(new int[0], SPEED.pendulumSequences());
        assertEquals(Set.of("plr-mon-0", "plr-mon-4", "plr-st-0", "plr-st-4",
                        "opp-mon-0", "opp-mon-4", "opp-st-0", "opp-st-4",
                        "emz-left", "emz-right",
                        "plr-pz-left", "plr-pz-right", "opp-pz-left", "opp-pz-right"),
                SPEED.hiddenSlotIds());
    }

    @Test
    void sharedPendulumOnThreeColumnsUsesSequencesOneAndThree() {
        assertArrayEquals(new int[]{1, 3},
                FieldLayout.fromFlags(DUEL_3_COLUMNS_FIELD | DUEL_PZONE).pendulumSequences());
    }

    // ---- Piles, unknowns, round trip ----

    @Test
    void pilesMapForEveryRule() {
        for (FieldLayout layout : List.of(MR5, MR3, SPEED)) {
            assertEquals(Optional.of("plr-deck"), layout.slotId(z(Side.PLR, LOCATION_DECK, 0)));
            assertEquals(Optional.of("opp-graveyard"), layout.slotId(z(Side.OPP, LOCATION_GRAVE, 3)));
            assertEquals(Optional.of("plr-banished"), layout.slotId(z(Side.PLR, LOCATION_REMOVED, 0)));
            assertEquals(Optional.of("opp-extra-deck"), layout.slotId(z(Side.OPP, LOCATION_EXTRA, 0)));
        }
    }

    @Test
    void unknownLocationsAndSlotsHaveNoMapping() {
        assertTrue(MR5.slotId(z(Side.PLR, LOCATION_HAND, 0)).isEmpty());
        assertTrue(MR5.slotId(z(Side.PLR, LOCATION_MZONE, 7)).isEmpty());
        assertTrue(MR5.slotId(z(Side.PLR, LOCATION_SZONE, 8)).isEmpty());
        assertTrue(MR5.zonesOf("no-such-slot").isEmpty());
    }

    @Test
    void supersetHasThirtySixSlots() {
        assertEquals(36, FieldLayout.allSlotIds().size());
    }

    @Test
    void everyVisibleSlotRoundTrips() {
        for (FieldLayout layout : List.of(MR5, MR3, SPEED)) {
            for (String id : FieldLayout.allSlotIds()) {
                if (layout.hiddenSlotIds().contains(id)) continue;
                List<Zone> zones = layout.zonesOf(id);
                assertFalse(zones.isEmpty(), id);
                for (Zone zone : zones) {
                    assertEquals(Optional.of(id), layout.slotId(zone), id);
                }
            }
        }
    }
}
