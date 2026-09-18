package com.haxerus.duelcraft.client;

import java.util.LinkedHashSet;
import java.util.List;

import static com.haxerus.duelcraft.core.OcgConstants.*;

/** Small text helpers shared by field and dialog card-selection prompts. */
final class SelectionCaptions {

    record Source(int controller, int location) { }

    private SelectionCaptions() { }

    static String sourceTitle(int localPlayer, List<Source> sources) {
        var labels = new LinkedHashSet<String>();
        sourceLabels(localPlayer, sources).stream().filter(label -> !label.isEmpty()).forEach(labels::add);
        return String.join(" / ", labels);
    }

    static List<String> sourceLabels(int localPlayer, List<Source> sources) {
        boolean includesLocal = sources.stream().anyMatch(source -> source.controller() == localPlayer);
        boolean includesOpponent = sources.stream().anyMatch(source -> source.controller() != localPlayer);
        return sources.stream().map(source -> {
            String zone = zoneName(source.location());
            if (zone == null) return "";
            if (source.controller() != localPlayer) return "Opponent's " + zone;
            return includesOpponent && includesLocal ? "Your " + zone : zone;
        }).toList();
    }

    static String iterative(int selectedCount) {
        return "Select or deselect 1 card (" + selectedCount + " selected)";
    }

    private static String zoneName(int location) {
        if ((location & LOCATION_OVERLAY) != 0) return "Xyz Material";
        return switch (location) {
            case LOCATION_DECK -> "Deck";
            case LOCATION_HAND -> "Hand";
            case LOCATION_MZONE -> "Monster Zone";
            case LOCATION_SZONE -> "Spell & Trap Zone";
            case LOCATION_GRAVE -> "GY";
            case LOCATION_REMOVED -> "Banished";
            case LOCATION_EXTRA -> "Extra Deck";
            default -> null;
        };
    }
}
