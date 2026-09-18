package com.haxerus.duelcraft.client.collection;

import com.haxerus.duelcraft.collection.*;
import org.jetbrains.annotations.Nullable;
import java.util.*;

/** Client-thread snapshot assembly. Only complete views are published. */
public final class ClientCollectionState {
    public record View(long revision, Map<Integer, Long> counts,
                       List<CollectionReply.Summary> summaries, @Nullable UUID activeId) {
        public View { counts = Map.copyOf(counts); summaries = List.copyOf(summaries); }
    }

    private @Nullable View view;
    private @Nullable CollectionReply.Opened opened;
    private final Map<Integer, Long> counts = new HashMap<>();
    private final Map<UUID, CollectionReply.Summary> summaries = new LinkedHashMap<>();
    private int countIndex;
    private int deckIndex;

    public @Nullable View view() { return view; }

    public void begin(CollectionReply.Opened header) {
        require(header.snapshotId() != null && header.revision() >= 0
                && header.countPages() >= 0 && header.deckPages() >= 0, "Invalid snapshot header");
        require(view == null || header.revision() >= view.revision(), "Older snapshot revision");
        require(header.activeId() == null || header.deckPages() > 0, "Active deck missing from snapshot");
        discardPending();
        opened = header;
        publishIfComplete();
    }

    public @Nullable CollectionCommand.Page nextPage() {
        if (opened == null) return null;
        if (countIndex < opened.countPages()) {
            return new CollectionCommand.Page(opened.snapshotId(), CollectionCommand.PageKind.COUNTS, countIndex);
        }
        if (deckIndex < opened.deckPages()) {
            return new CollectionCommand.Page(opened.snapshotId(), CollectionCommand.PageKind.DECKS, deckIndex);
        }
        return null;
    }

    public void accept(CollectionReply page) {
        var expected = nextPage();
        require(expected != null, "No snapshot page expected");
        switch (page) {
            case CollectionReply.Counts data -> {
                require(expected.kind() == CollectionCommand.PageKind.COUNTS, "Wrong page kind");
                validatePage(data.snapshotId(), data.revision(), data.index(), expected);
                validateSize(data.entries().size(), CollectionLimits.COUNT_PAGE, countIndex, opened.countPages());
                for (var entry : data.entries().entrySet()) {
                    require(entry.getKey() > 0 && entry.getValue() > 0 && !counts.containsKey(entry.getKey()), "Invalid or duplicate count");
                }
                counts.putAll(data.entries());
                countIndex++;
            }
            case CollectionReply.Decks data -> {
                require(expected.kind() == CollectionCommand.PageKind.DECKS, "Wrong page kind");
                validatePage(data.snapshotId(), data.revision(), data.index(), expected);
                validateSize(data.entries().size(), CollectionLimits.SUMMARY_PAGE, deckIndex, opened.deckPages());
                var ids = new HashSet<UUID>();
                for (var summary : data.entries()) {
                    require(summary.id() != null && ids.add(summary.id()) && !summaries.containsKey(summary.id()), "Duplicate deck ID");
                    require(summary.name() != null && !summary.name().isBlank()
                            && summary.name().length() <= CollectionLimits.NAME_LENGTH
                            && summary.name().codePoints().noneMatch(Character::isISOControl), "Invalid deck name");
                    require(summary.main() >= 0 && summary.extra() >= 0 && summary.side() >= 0
                            && (long) summary.main() + summary.extra() + summary.side() <= CollectionLimits.DRAFT_CARDS, "Invalid summary sizes");
                }
                if (deckIndex == opened.deckPages() - 1 && opened.activeId() != null) {
                    require(summaries.containsKey(opened.activeId()) || ids.contains(opened.activeId()), "Active deck missing from snapshot");
                }
                for (var summary : data.entries()) summaries.put(summary.id(), summary);
                deckIndex++;
            }
            default -> throw new IllegalArgumentException("Not a snapshot page");
        }
        publishIfComplete();
    }

    private void validatePage(UUID id, long revision, int index, CollectionCommand.Page expected) {
        require(expected.snapshotId().equals(id) && revision == opened.revision()
                && index == expected.index(), "Wrong snapshot, revision or page index");
    }

    private static void validateSize(int size, int limit, int index, int pages) {
        require(size > 0 && size <= limit && (index == pages - 1 || size == limit), "Invalid page size");
    }

    private void publishIfComplete() {
        if (nextPage() != null) return;
        view = new View(opened.revision(), counts, new ArrayList<>(summaries.values()), opened.activeId());
        discardPending();
    }

    public void discardPending() {
        opened = null;
        counts.clear(); summaries.clear();
        countIndex = 0; deckIndex = 0;
    }

    public void clear() { discardPending(); view = null; }

    private static void require(boolean valid, String message) {
        if (!valid) throw new IllegalArgumentException(message);
    }
}
