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
    @Test void sortGroupsTypesStablyWithinEachSectionWithoutChangingCopies() {
        var types = Map.of(1, TYPE_MONSTER | TYPE_NORMAL, 2, TYPE_MONSTER | TYPE_EFFECT,
                3, TYPE_SPELL, 4, TYPE_TRAP, 5, TYPE_MONSTER | TYPE_EFFECT,
                6, TYPE_MONSTER | TYPE_FUSION, 7, TYPE_MONSTER | TYPE_NORMAL | TYPE_PENDULUM);
        var initial = new DeckList(List.of(4, 5, 3, 1, 2, 7, 5, 99), List.of(6, 6), List.of(3, 6, 4, 1));
        var model = new DeckEditorModel(initial, Map.of(1, 1L));

        model.sortByType(code -> types.getOrDefault(code, 0));

        assertEquals(new DeckList(List.of(1, 7, 5, 5, 2, 3, 4, 99), List.of(6, 6), List.of(1, 6, 3, 4)), model.draft());
        assertEquals(initial.requiredCopies(), model.draft().requiredCopies());
        assertEquals(Map.of(1, 1L), model.owned());
        assertTrue(model.dirty());
        model.markSaved();
        model.sortByType(code -> types.getOrDefault(code, 0));
        assertFalse(model.dirty());
    }

    @Test void sortGroupsIdenticalCardsWithinEachTypeAndSection() {
        var initial = new DeckList(List.of(7, 8, 7, 8, 7), List.of(9, 10, 9, 10, 9), List.of(8, 7, 8));
        var owned = Map.of(7, 2L, 8, 1L);
        var model = new DeckEditorModel(initial, owned);

        model.sortByType(code -> TYPE_SPELL);

        assertEquals(List.of(7, 7, 7, 8, 8), model.draft().main());
        assertEquals(List.of(9, 9, 9, 10, 10), model.draft().extra());
        assertEquals(List.of(8, 8, 7), model.draft().side());
        assertEquals(initial.requiredCopies(), model.draft().requiredCopies());
        assertEquals(owned, model.owned());
        assertTrue(model.dirty());

        var sorted = model.draft();
        model.sortByType(code -> TYPE_SPELL);
        assertEquals(sorted, model.draft());
        model.markSaved();
        assertFalse(model.dirty());
        model.sortByType(code -> TYPE_SPELL);
        assertFalse(model.dirty());

        var unknownTypes = new DeckEditorModel(new DeckList(List.of(9, 10, 9), List.of(10, 9), List.of()), Map.of());
        unknownTypes.sortByType(code -> 0);
        assertEquals(new DeckList(List.of(9, 9, 10), List.of(10, 9), List.of()), unknownTypes.draft());

        var emptySections = new DeckEditorModel(new DeckList(List.of(), List.of(11), List.of()), Map.of());
        emptySections.sortByType(code -> TYPE_SPELL);
        assertEquals(new DeckList(List.of(), List.of(11), List.of()), emptySections.draft());
    }

    @Test void sortCannotChangeAFrozenDraft() {
        var initial = new DeckList(List.of(4, 1), List.of(), List.of());
        var model = new DeckEditorModel(initial, Map.of());
        model.setFrozen(true);
        assertThrows(IllegalStateException.class, () -> model.sortByType(code -> code == 4 ? TYPE_TRAP : TYPE_MONSTER | TYPE_NORMAL));
        assertEquals(initial, model.draft());
        assertFalse(model.dirty());
    }

    @ParameterizedTest
    @ValueSource(ints = {
            TYPE_MONSTER | TYPE_FUSION,
            TYPE_MONSTER | TYPE_SYNCHRO,
            TYPE_MONSTER | TYPE_XYZ,
            TYPE_MONSTER | TYPE_LINK
    })
    void deckAddRoutesExtraDeckKindsToExtra(int type) {
        var model = new DeckEditorModel(empty(), Map.of());
        assertTrue(model.addToDeck(type, 10));
        assertEquals(new DeckList(List.of(), List.of(10), List.of()), model.draft());
    }

    @Test void pendulumAloneRoutesToMain() {
        var model = new DeckEditorModel(empty(), Map.of());
        assertTrue(model.addToDeck(TYPE_MONSTER | TYPE_PENDULUM, 10));
        assertEquals(new DeckList(List.of(10), List.of(), List.of()), model.draft());
    }

    @Test void capCountsAllSectionsWithoutChangingImportedDrafts() {
        var cards = new DeckList(List.of(7), List.of(7), List.of(7));
        var model = new DeckEditorModel(cards, Map.of());
        assertFalse(model.add(DeckEditorModel.Section.SIDE, 7));
        assertEquals(cards, model.draft());
        assertFalse(model.dirty());
        assertTrue(model.add(DeckEditorModel.Section.MAIN, 8));
        var imported = new DeckList(List.of(7, 7, 7, 7), List.of(), List.of());
        model.load(imported);
        assertEquals(imported, model.draft());
        assertFalse(model.canAdd(7));
    }

    @ParameterizedTest
    @EnumSource(DeckEditorModel.Section.class)
    void manualAddReturnsSuccessForEverySection(DeckEditorModel.Section section) {
        var model = new DeckEditorModel(empty(), Map.of());
        assertTrue(model.add(section, 7));
        assertEquals(1, model.draft().requiredCopies().get(7));
    }

    @Test void fourthCopyIsRejectedAcrossSections() {
        var initial = new DeckList(List.of(7), List.of(7), List.of());
        var model = new DeckEditorModel(initial, Map.of());
        assertTrue(model.add(DeckEditorModel.Section.SIDE, 7));
        assertFalse(model.add(DeckEditorModel.Section.MAIN, 7));
        assertEquals(new DeckList(List.of(7), List.of(7), List.of(7)), model.draft());
    }

    @Test void removingACopyAllowsAnotherManualAdd() {
        var model = new DeckEditorModel(new DeckList(List.of(7, 8, 7), List.of(), List.of(7)), Map.of());
        assertFalse(model.canAdd(7));
        assertTrue(model.removeAt(DeckEditorModel.Section.MAIN, 2));
        assertTrue(model.canAdd(7));
        assertTrue(model.add(DeckEditorModel.Section.EXTRA, 7));
        assertEquals(new DeckList(List.of(7, 8), List.of(7), List.of(7)), model.draft());
    }

    @Test void rightClickRemovalKeepsOtherCopiesInPlace() {
        var model = new DeckEditorModel(
                new DeckList(List.of(7, 8, 7), List.of(), List.of()), Map.of());
        assertTrue(model.removeAt(DeckEditorModel.Section.MAIN, 2));
        assertEquals(List.of(7, 8), model.draft().main());
    }

    @Test void invalidRemovalIndexLeavesDraftUnchanged() {
        var initial = new DeckList(List.of(7), List.of(), List.of());
        var model = new DeckEditorModel(initial, Map.of());
        assertFalse(model.removeAt(DeckEditorModel.Section.MAIN, -1));
        assertFalse(model.removeAt(DeckEditorModel.Section.MAIN, 1));
        assertEquals(initial, model.draft());
        assertFalse(model.dirty());
    }

    @Test void frozenDraftRejectsManualAddAndIndexedRemoval() {
        var initial = new DeckList(List.of(7), List.of(), List.of());
        var model = new DeckEditorModel(initial, Map.of());
        model.setFrozen(true);
        assertThrows(IllegalStateException.class, () -> model.add(DeckEditorModel.Section.MAIN, 8));
        assertThrows(IllegalStateException.class, () -> model.removeAt(DeckEditorModel.Section.MAIN, 0));
        assertEquals(initial, model.draft());
        assertFalse(model.dirty());
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
        var main = java.util.Collections.nCopies(61, 1);
        var extra = java.util.Collections.nCopies(16, 1);
        var side = java.util.Collections.nCopies(16, 1);
        var model = new DeckEditorModel(empty(), Map.of(1, 2L));
        model.load(new DeckList(main, extra, side));
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
