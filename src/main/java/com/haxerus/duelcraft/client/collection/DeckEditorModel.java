package com.haxerus.duelcraft.client.collection;

import com.haxerus.duelcraft.collection.DeckList;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
}
