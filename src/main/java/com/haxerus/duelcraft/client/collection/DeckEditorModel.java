package com.haxerus.duelcraft.client.collection;

import com.haxerus.duelcraft.collection.DeckList;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.IntUnaryOperator;

import static com.haxerus.duelcraft.core.OcgConstants.*;

public final class DeckEditorModel {
    public enum Section { MAIN, EXTRA, SIDE }

    private DeckList draft;
    private DeckList saved;
    private Map<Integer, Long> owned;
    private boolean frozen;

    public DeckEditorModel(DeckList initial, Map<Integer, Long> owned) {
        draft = initial;
        saved = initial;
        this.owned = Map.copyOf(owned);
    }

    public DeckList draft() { return draft; }
    public Map<Integer, Long> owned() { return owned; }
    public void replaceOwnership(Map<Integer, Long> counts) { owned = Map.copyOf(counts); }
    void setFrozen(boolean frozen) { this.frozen = frozen; }
    void load(DeckList cards) { draft = cards; saved = cards; }
    void acknowledge(DeckList cards) { saved = cards; }
    void discard() { draft = saved; }

    public void add(Section section, int code) {
        requireEditable();
        var cards = new ArrayList<>(cards(section));
        cards.add(code);
        replace(section, cards);
    }

    public boolean remove(Section section, int code) {
        requireEditable();
        var cards = new ArrayList<>(cards(section));
        if (!cards.remove(Integer.valueOf(code))) return false;
        replace(section, cards);
        return true;
    }

    public void sortByType(IntUnaryOperator cardType) {
        requireEditable();
        Comparator<Integer> order = Comparator.comparingInt(code -> typeOrder(cardType.applyAsInt(code)));
        draft = new DeckList(draft.main().stream().sorted(order).toList(),
                draft.extra().stream().sorted(order).toList(), draft.side().stream().sorted(order).toList());
    }

    private static int typeOrder(int type) {
        if ((type & TYPE_MONSTER) != 0) return (type & TYPE_NORMAL) != 0 ? 0 : 1;
        if ((type & TYPE_SPELL) != 0) return 2;
        if ((type & TYPE_TRAP) != 0) return 3;
        return 4;
    }

    private List<Integer> cards(Section section) {
        return switch (section) {
            case MAIN -> draft.main();
            case EXTRA -> draft.extra();
            case SIDE -> draft.side();
        };
    }

    private void replace(Section section, List<Integer> cards) {
        draft = switch (section) {
            case MAIN -> new DeckList(cards, draft.extra(), draft.side());
            case EXTRA -> new DeckList(draft.main(), cards, draft.side());
            case SIDE -> new DeckList(draft.main(), draft.extra(), cards);
        };
    }

    public long missing(int code) {
        return Math.max(0L, draft.requiredCopies().getOrDefault(code, 0) - owned.getOrDefault(code, 0L));
    }

    public boolean dirty() { return !draft.equals(saved); }
    public void markSaved() { saved = draft; }
    private void requireEditable() {
        if (frozen) throw new IllegalStateException("Wait for the pending collection operation");
    }

    public void addToDeck(int type, int code) {
        add(naturalSection(type), code);
    }

    public boolean removeFromDeck(int type, int code, Section storedSection) {
        if (storedSection == Section.MAIN || storedSection == Section.EXTRA) {
            return remove(storedSection, code);
        }
        Section natural = naturalSection(type);
        return remove(natural, code) || remove(natural == Section.MAIN ? Section.EXTRA : Section.MAIN, code);
    }

    static Section naturalSection(int type) {
        return (type & (TYPE_FUSION | TYPE_SYNCHRO | TYPE_XYZ | TYPE_LINK)) != 0 ? Section.EXTRA : Section.MAIN;
    }
}
