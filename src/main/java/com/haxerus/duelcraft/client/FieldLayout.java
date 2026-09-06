package com.haxerus.duelcraft.client;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static com.haxerus.duelcraft.core.OcgConstants.*;

/**
 * Which zones exist for a rule set and which XML slot each engine zone lives in.
 * Plain Java with no Minecraft or LDLib2 types, so JUnit covers it without a client.
 *
 * <p>Zones are viewer-relative ({@link Side}); {@code FieldRenderer} converts absolute player
 * indices before asking. Slot ids are in the owner's frame ({@code opp-st-0} is the opponent's
 * zone 0, mirrored by CSS) except the two Extra Monster Zone slots, which are shared physical
 * positions named in the viewer's frame.
 */
public record FieldLayout(int columns, boolean emz, PendulumMode pendulum) {

    public enum PendulumMode { NONE, SHARED, SEPARATE }

    /** Table side as seen by the local player. */
    public enum Side {
        PLR("plr"), OPP("opp");

        final String prefix;

        Side(String prefix) {
            this.prefix = prefix;
        }
    }

    public record Zone(Side side, int location, int sequence) {}

    /** Total: never throws. Three columns force no EMZ; a separate-pendulum bit needs the pendulum bit. */
    public static FieldLayout fromFlags(long flags) {
        int columns = (flags & DUEL_3_COLUMNS_FIELD) != 0 ? 3 : 5;
        boolean emz = (flags & DUEL_EMZONE) != 0 && columns == 5;
        PendulumMode pendulum = (flags & DUEL_PZONE) == 0 ? PendulumMode.NONE
                : (flags & DUEL_SEPARATE_PZONE) != 0 ? PendulumMode.SEPARATE : PendulumMode.SHARED;
        return new FieldLayout(columns, emz, pendulum);
    }

    /** Engine zone → slot id; empty when this rule has no such zone. */
    public Optional<String> slotId(Zone z) {
        String p = z.side().prefix;
        return Optional.ofNullable(switch (z.location()) {
            case LOCATION_MZONE -> {
                if (z.sequence() <= 4) yield columnVisible(z.sequence()) ? p + "-mon-" + z.sequence() : null;
                if (!emz) yield null;
                if (z.sequence() == 5) yield z.side() == Side.PLR ? "emz-left" : "emz-right";
                if (z.sequence() == 6) yield z.side() == Side.PLR ? "emz-right" : "emz-left";
                yield null;
            }
            case LOCATION_SZONE -> {
                if (z.sequence() <= 4) yield columnVisible(z.sequence()) ? p + "-st-" + z.sequence() : null;
                if (z.sequence() == 5) yield p + "-field-spell";
                if (pendulum != PendulumMode.SEPARATE) yield null;
                if (z.sequence() == 6) yield p + "-pz-left";
                if (z.sequence() == 7) yield p + "-pz-right";
                yield null;
            }
            case LOCATION_EXTRA -> p + "-extra-deck";
            case LOCATION_GRAVE -> p + "-graveyard";
            case LOCATION_REMOVED -> p + "-banished";
            case LOCATION_DECK -> p + "-deck";
            default -> null;
        });
    }

    /** Slot id → the engine zones that can occupy it, viewer's zone first. Empty for ids this rule hides. */
    public List<Zone> zonesOf(String id) {
        List<Zone> zones = new ArrayList<>(2);
        for (Zone zone : allZones()) {
            if (slotId(zone).filter(id::equals).isPresent()) zones.add(zone);
        }
        return zones;
    }

    /** Superset ids the XML contains that this rule does not use. */
    public Set<String> hiddenSlotIds() {
        Set<String> hidden = new LinkedHashSet<>();
        for (String id : allSlotIds()) {
            if (zonesOf(id).isEmpty()) hidden.add(id);
        }
        return hidden;
    }

    /** Engine S/T sequences that act as pendulum zones under this rule. */
    public int[] pendulumSequences() {
        return switch (pendulum) {
            case NONE -> new int[0];
            case SEPARATE -> new int[]{6, 7};
            case SHARED -> columns == 3 ? new int[]{1, 3} : new int[]{0, 4};
        };
    }

    /** Every slot id the XML must contain, for binding. */
    public static List<String> allSlotIds() {
        List<String> ids = new ArrayList<>();
        for (Side side : Side.values()) {
            String p = side.prefix;
            for (int i = 0; i <= 4; i++) ids.add(p + "-mon-" + i);
            for (int i = 0; i <= 4; i++) ids.add(p + "-st-" + i);
            ids.add(p + "-field-spell");
            ids.add(p + "-pz-left");
            ids.add(p + "-pz-right");
            ids.add(p + "-extra-deck");
            ids.add(p + "-graveyard");
            ids.add(p + "-banished");
            ids.add(p + "-deck");
        }
        ids.add("emz-left");
        ids.add("emz-right");
        return ids;
    }

    private boolean columnVisible(int sequence) {
        if (sequence < 0 || sequence > 4) return false;
        return columns == 5 || (sequence >= 1 && sequence <= 3);
    }

    /** Every zone a duel can address: MZONE 0-6, SZONE 0-7, and the four piles, PLR side first. */
    private static List<Zone> allZones() {
        List<Zone> zones = new ArrayList<>();
        for (Side side : Side.values()) {
            for (int s = 0; s <= 6; s++) zones.add(new Zone(side, LOCATION_MZONE, s));
            for (int s = 0; s <= 7; s++) zones.add(new Zone(side, LOCATION_SZONE, s));
            for (int loc : new int[]{LOCATION_EXTRA, LOCATION_GRAVE, LOCATION_REMOVED, LOCATION_DECK}) {
                zones.add(new Zone(side, loc, 0));
            }
        }
        return zones;
    }
}
