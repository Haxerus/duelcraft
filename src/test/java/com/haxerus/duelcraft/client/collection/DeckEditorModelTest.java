package com.haxerus.duelcraft.client.collection;

import com.haxerus.duelcraft.collection.DeckList;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static com.haxerus.duelcraft.core.OcgConstants.*;

class DeckEditorModelTest {
    @ParameterizedTest
    @ValueSource(ints = {
            TYPE_MONSTER | TYPE_FUSION,
            TYPE_MONSTER | TYPE_SYNCHRO,
            TYPE_MONSTER | TYPE_XYZ,
            TYPE_MONSTER | TYPE_LINK
    })
    void deckAddRoutesExtraDeckKindsToExtra(int type) {
        var model = new DeckEditorModel(empty(), Map.of());
        model.addToDeck(type, 10);
        assertEquals(new DeckList(List.of(), List.of(10), List.of()), model.draft());
    }

    @Test void pendulumAloneRoutesToMain() {
        var model = new DeckEditorModel(empty(), Map.of());
        model.addToDeck(TYPE_MONSTER | TYPE_PENDULUM, 10);
        assertEquals(new DeckList(List.of(10), List.of(), List.of()), model.draft());
    }

    @Test void deckRemovalHonorsClickedStoredSectionEvenWhenMisplaced() {
        var model = new DeckEditorModel(new DeckList(List.of(10), List.of(10), List.of()), Map.of());
        assertTrue(model.removeFromDeck(TYPE_MONSTER | TYPE_FUSION, 10, DeckEditorModel.Section.MAIN));
        assertEquals(new DeckList(List.of(), List.of(10), List.of()), model.draft());
    }

    @Test void collectionDeckRemovalPrefersNaturalSectionThenFallsBack() {
        var model = new DeckEditorModel(new DeckList(List.of(10), List.of(10), List.of()), Map.of());
        assertTrue(model.removeFromDeck(TYPE_MONSTER | TYPE_FUSION, 10, null));
        assertEquals(new DeckList(List.of(10), List.of(), List.of()), model.draft());
        assertTrue(model.removeFromDeck(TYPE_MONSTER | TYPE_FUSION, 10, null));
        assertEquals(empty(), model.draft());
    }

    @Test void ownershipRefreshPreservesDraftAndAcknowledgedBaseline() {
        var model = new DeckEditorModel(empty(), Map.of());
        model.add(DeckEditorModel.Section.MAIN, 1);
        model.replaceOwnership(Map.of(1, 2L));
        assertTrue(model.dirty());
        assertEquals(List.of(1), model.draft().main());
        assertEquals(0, model.missing(1));
        model.remove(DeckEditorModel.Section.MAIN, 1);
        assertFalse(model.dirty());
    }
    private static DeckList empty() {
        return new DeckList(List.of(), List.of(), List.of());
    }

    @Test void missingCardsDoNotPreventEditingOrSavingADraft() {
        var model = new DeckEditorModel(empty(), Map.of());
        model.add(DeckEditorModel.Section.MAIN, 89631139);
        assertEquals(1, model.missing(89631139));
        assertTrue(model.dirty());
        model.markSaved();
        assertFalse(model.dirty());
        assertEquals(List.of(89631139), model.draft().main());
    }

    @ParameterizedTest
    @EnumSource(DeckEditorModel.Section.class)
    void editingEachSectionPreservesEarlierSnapshot(DeckEditorModel.Section section) {
        var model = new DeckEditorModel(empty(), Map.of(10, 1L));
        var before = model.draft();
        model.add(section, 10);
        assertEquals(empty(), before);
        assertEquals(Map.of(10, 1), model.draft().requiredCopies());
        assertEquals(section == DeckEditorModel.Section.MAIN ? List.of(10) : List.of(), model.draft().main());
        assertEquals(section == DeckEditorModel.Section.EXTRA ? List.of(10) : List.of(), model.draft().extra());
        assertEquals(section == DeckEditorModel.Section.SIDE ? List.of(10) : List.of(), model.draft().side());
        assertTrue(model.remove(section, 10));
        assertEquals(empty(), model.draft());
        assertFalse(model.dirty());
        assertEquals(Map.of(10, 1L), model.owned());
    }

    @Test void removalDeletesOneCopyOnlyFromTheRequestedSection() {
        var model = new DeckEditorModel(new DeckList(List.of(1, 2, 1), List.of(1), List.of(1)), Map.of());
        assertTrue(model.remove(DeckEditorModel.Section.MAIN, 1));
        assertEquals(new DeckList(List.of(2, 1), List.of(1), List.of(1)), model.draft());
        assertFalse(model.remove(DeckEditorModel.Section.EXTRA, 2));
        assertEquals(new DeckList(List.of(2, 1), List.of(1), List.of(1)), model.draft());
    }

    @Test void removingAbsentCardDoesNotDirtyTheDraft() {
        var model = new DeckEditorModel(empty(), Map.of());
        assertFalse(model.remove(DeckEditorModel.Section.SIDE, 1));
        assertFalse(model.dirty());
    }

    @Test void fourthCopyAndOversizedSectionsRemainSavable() {
        var model = new DeckEditorModel(empty(), Map.of(1, 2L));
        for (int i = 0; i < 61; i++) model.add(DeckEditorModel.Section.MAIN, 1);
        for (int i = 0; i < 16; i++) {
            model.add(DeckEditorModel.Section.EXTRA, 1);
            model.add(DeckEditorModel.Section.SIDE, 1);
        }
        assertEquals(93, model.draft().requiredCopies().get(1));
        assertEquals(91, model.missing(1));
        model.markSaved();
        assertFalse(model.dirty());
    }

    @Test void ownershipIsAnImmutableSnapshotAndShortageNeverGoesNegative() {
        var owned = new HashMap<>(Map.of(1, 10L));
        var model = new DeckEditorModel(new DeckList(List.of(1), List.of(), List.of(1)), owned);
        owned.clear();
        assertEquals(Map.of(1, 10L), model.owned());
        assertThrows(UnsupportedOperationException.class, () -> model.owned().clear());
        assertEquals(0, model.missing(1));
        assertEquals(0, model.missing(2));
    }

    @Test void savingMovesTheDirtyBaseline() {
        var model = new DeckEditorModel(empty(), Map.of());
        model.add(DeckEditorModel.Section.SIDE, 1);
        model.markSaved();
        model.add(DeckEditorModel.Section.SIDE, 2);
        assertTrue(model.dirty());
        model.remove(DeckEditorModel.Section.SIDE, 2);
        assertFalse(model.dirty());
        model.remove(DeckEditorModel.Section.SIDE, 1);
        assertTrue(model.dirty());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void invalidAddLeavesDraftUnchanged(int code) {
        var model = new DeckEditorModel(empty(), Map.of());
        assertThrows(IllegalArgumentException.class, () -> model.add(DeckEditorModel.Section.MAIN, code));
        assertEquals(empty(), model.draft());
        assertFalse(model.dirty());
    }
}
