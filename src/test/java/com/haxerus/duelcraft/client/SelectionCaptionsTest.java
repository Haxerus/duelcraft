package com.haxerus.duelcraft.client;

import org.junit.jupiter.api.Test;

import java.util.List;

import static com.haxerus.duelcraft.core.OcgConstants.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

class SelectionCaptionsTest {

    @Test
    void titlesTheSingleCandidateSourceZone() {
        assertEquals("Deck",
                SelectionCaptions.sourceTitle(0,
                        List.of(source(0, LOCATION_DECK))));
    }

    @Test
    void titlesEachMixedCandidateSourceZoneOnce() {
        assertEquals("Hand / GY",
                SelectionCaptions.sourceTitle(0, List.of(
                        source(0, LOCATION_HAND), source(0, LOCATION_GRAVE), source(0, LOCATION_HAND))));
    }

    @Test
    void namesEverySupportedSelectionSource() {
        assertEquals("Deck / Hand / Monster Zone / Spell & Trap Zone / GY / Banished / Extra Deck / Xyz Material",
                SelectionCaptions.sourceTitle(0, List.of(
                        source(0, LOCATION_DECK), source(0, LOCATION_HAND), source(0, LOCATION_MZONE),
                        source(0, LOCATION_SZONE), source(0, LOCATION_GRAVE), source(0, LOCATION_REMOVED),
                        source(0, LOCATION_EXTRA), source(0, LOCATION_MZONE | LOCATION_OVERLAY))));
    }

    @Test
    void qualifiesSourcesWhenCandidatesBelongToBothPlayers() {
        assertEquals("Your GY / Opponent's GY",
                SelectionCaptions.sourceTitle(0,
                        List.of(source(0, LOCATION_GRAVE), source(1, LOCATION_GRAVE))));
    }

    @Test
    void qualifiesAnOpponentOnlySource() {
        assertEquals("Opponent's Hand",
                SelectionCaptions.sourceTitle(0,
                        List.of(source(1, LOCATION_HAND))));
    }

    @Test
    void returnsAnAlignedLabelForEachMixedDialogCandidate() {
        assertEquals(List.of("Hand", "GY", "Hand"),
                SelectionCaptions.sourceLabels(0, List.of(
                        source(0, LOCATION_HAND), source(0, LOCATION_GRAVE), source(0, LOCATION_HAND))));
    }

    @Test
    void leavesCodeOnlySelectionsWithoutAnInventedSource() {
        assertEquals("",
                SelectionCaptions.sourceTitle(0,
                        List.of(source(0, 0))));
    }

    @Test
    void iterativeCaptionUsesTheAlreadySelectedCountInsteadOfToggleBounds() {
        assertEquals("Select or deselect 1 card (2 selected)",
                SelectionCaptions.iterative(2));
    }

    private static SelectionCaptions.Source source(int controller, int location) {
        return new SelectionCaptions.Source(controller, location);
    }
}
