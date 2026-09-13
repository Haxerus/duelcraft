package com.haxerus.duelcraft.client;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * The running list of duel events, edopro's {@code Game::AddLog} (`game.cpp:3006`): one line per
 * event, each carrying the card code it names so clicking the line can show that card.
 *
 * Minecraft-free so {@link ClientDuelState} stays testable under plain JUnit.
 */
public class DuelLog {

    /** How many lines the panel keeps; older ones are dropped from the front. */
    private static final int MAX_ENTRIES = 200;

    /** {@code code} is 0 for a line that names no card. */
    public record Entry(long timestamp, int code, String text) {}

    private final Deque<Entry> entries = new ArrayDeque<>();

    public void add(int code, String text) {
        entries.addLast(new Entry(System.currentTimeMillis(), code, text));
        while (entries.size() > MAX_ENTRIES) entries.removeFirst();
    }

    /** Oldest line first. */
    public List<Entry> entries() {
        return List.copyOf(entries);
    }

    /** The newest line's text, or null when nothing has been logged yet. */
    public String lastText() {
        return entries.isEmpty() ? null : entries.getLast().text();
    }
}
