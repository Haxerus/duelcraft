package com.haxerus.duelcraft.client;

import java.util.ArrayDeque;
import java.util.ArrayList;
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

    public enum Role { PLAIN, CARD_NAME, CONTEXT }

    public record Segment(String text, Role role) {}

    /** {@code code} is the whole-line click target, or 0 for a non-clickable line. */
    public record Entry(long timestamp, int code, List<Segment> segments) {
        public Entry { segments = List.copyOf(segments); }

        public String text() {
            var text = new StringBuilder();
            for (var segment : segments) text.append(segment.text());
            return text.toString();
        }
    }

    private final Deque<Entry> entries = new ArrayDeque<>();

    public void add(int code, String text) {
        add(code, List.of(new Segment(text, Role.PLAIN)));
    }

    public void add(int code, List<Segment> segments) {
        entries.addLast(new Entry(System.currentTimeMillis(), code, segments));
        while (entries.size() > MAX_ENTRIES) entries.removeFirst();
    }

    /** Fill the template's placeholders, never interpreting inserted card names as template text. */
    public static List<Segment> format(String template, Segment... values) {
        var segments = new ArrayList<Segment>();
        int from = 0;
        for (var value : values) {
            int at = template.indexOf("{}", from);
            if (at < 0) break;
            if (at > from) segments.add(new Segment(template.substring(from, at), Role.PLAIN));
            segments.add(value);
            from = at + 2;
        }
        if (from < template.length()) segments.add(new Segment(template.substring(from), Role.PLAIN));
        return segments;
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
