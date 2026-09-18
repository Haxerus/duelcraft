package com.haxerus.duelcraft.server.collection;

import com.haxerus.duelcraft.collection.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CollectionHandlerTest {
    static class Sender implements CollectionPayloadHandler.Sender {
        final UUID owner = UUID.randomUUID();
        PlayerCollectionData data = PlayerCollectionData.empty();
        boolean busy; int writes; final List<CollectionReplyPayload> replies = new ArrayList<>();
        public UUID id() { return owner; }
        public Optional<PlayerCollectionData> data() { return Optional.ofNullable(data); }
        public boolean busy() { return busy; }
        public void persist(PlayerCollectionData value) { data = value; writes++; }
        public void reply(CollectionReplyPayload value) { replies.add(value); }
    }
    static CollectionReply send(CollectionPayloadHandler handler, Sender sender, CollectionCommand command) {
        var request = UUID.randomUUID(); handler.handle(new CollectionRequestPayload(request, command), sender, 0);
        var reply = sender.replies.getLast(); assertEquals(request, reply.requestId()); return reply.reply();
    }
    @Test void authenticatedSavePersistsOnceRejectsReplayAndInvalidatesPages() {
        var handler = new CollectionPayloadHandler(new CollectionService(Map.of()), new CollectionSnapshotStore());
        var alice = new Sender(); var bob = new Sender();
        var opened = (CollectionReply.Opened) send(handler, alice, new CollectionCommand.Open());
        var deck = new SavedDeck(UUID.randomUUID(), "Draft", new DeckList(List.of(), List.of(), List.of()));
        var save = new CollectionCommand.Save(0, deck);
        var changed = (CollectionReply.Changed) send(handler, alice, save);
        assertEquals(deck, changed.saved()); assertEquals(1, changed.revision()); assertEquals(1, alice.writes);
        assertEquals(PlayerCollectionData.empty(), bob.data); assertTrue(bob.replies.isEmpty());
        assertEquals(CollectionError.STALE, ((CollectionReply.Rejected) send(handler, alice, save)).error());
        assertEquals(1, alice.writes);
        assertEquals(CollectionError.STALE, ((CollectionReply.Rejected) send(handler, alice, new CollectionCommand.Page(opened.snapshotId(), CollectionCommand.PageKind.COUNTS, 0))).error());
        assertEquals(CollectionError.NOT_FOUND, ((CollectionReply.Rejected) send(handler, bob, new CollectionCommand.ReadDeck(deck.id()))).error());
        assertEquals(deck, ((CollectionReply.Deck) send(handler, alice, new CollectionCommand.ReadDeck(deck.id()))).deck());
    }
    @Test void currentRevisionMustStillMatchCapturedPages() {
        var handler = new CollectionPayloadHandler(new CollectionService(Map.of()), new CollectionSnapshotStore());
        var sender = new Sender(); sender.data = new PlayerCollectionData(0, Map.of(1, 2L), Map.of(), null);
        var opened = (CollectionReply.Opened) send(handler, sender, new CollectionCommand.Open());
        sender.data = new PlayerCollectionData(1, Map.of(1, 3L), Map.of(), null);
        var rejected = (CollectionReply.Rejected) send(handler, sender, new CollectionCommand.Page(opened.snapshotId(), CollectionCommand.PageKind.COUNTS, 0));
        assertEquals(CollectionError.STALE, rejected.error()); assertEquals(1, rejected.revision());
    }
    @Test void busyAndUnreadableDataRejectAtHandlerBoundaryWithoutWrites() {
        var handler = new CollectionPayloadHandler(new CollectionService(Map.of()), new CollectionSnapshotStore()); var sender = new Sender(); sender.busy = true;
        var id = UUID.randomUUID(); var deck = new SavedDeck(id, "Draft", new DeckList(List.of(), List.of(), List.of()));
        for (var command : List.of(new CollectionCommand.Save(0, deck), new CollectionCommand.Delete(0, id), new CollectionCommand.Activate(0, id), new CollectionCommand.ClearActive(0))) {
            assertEquals(CollectionError.BUSY, ((CollectionReply.Rejected) send(handler, sender, command)).error());
        }
        sender.busy = false; sender.data = null;
        assertEquals(CollectionError.DATA_UNAVAILABLE, ((CollectionReply.Rejected) send(handler, sender, new CollectionCommand.Open())).error());
        assertEquals(CollectionError.DATA_UNAVAILABLE, ((CollectionReply.Rejected) send(handler, sender, new CollectionCommand.ClearActive(0))).error());
        assertEquals(0, sender.writes);
    }
    @Test void serviceFailuresAndAllMutationsUseCurrentAuthenticatedState() {
        var facts = new HashMap<Integer, com.haxerus.duelcraft.core.data.CardCatalog.Facts>();
        var counts = new HashMap<Integer, Long>(); var cards = new ArrayList<Integer>();
        for (int code = 1; code <= 40; code++) {
            cards.add(code); counts.put(code, 1L);
            facts.put(code, new com.haxerus.duelcraft.core.data.CardCatalog.Facts(code, com.haxerus.duelcraft.core.OcgConstants.TYPE_MONSTER));
        }
        var handler = new CollectionPayloadHandler(new CollectionService(facts), new CollectionSnapshotStore());
        var sender = new Sender(); sender.data = new PlayerCollectionData(0, counts, Map.of(), null);
        var deck = new SavedDeck(UUID.randomUUID(), "Playable", new DeckList(cards, List.of(), List.of()));
        send(handler, sender, new CollectionCommand.Save(0, deck));
        var active = (CollectionReply.Changed) send(handler, sender, new CollectionCommand.Activate(1, deck.id()));
        assertEquals(deck.id(), active.activeId()); assertNull(active.saved()); assertEquals(0, active.transferred());
        var cleared = (CollectionReply.Changed) send(handler, sender, new CollectionCommand.ClearActive(2));
        assertNull(cleared.activeId()); assertNull(sender.data.activeDeckId());
        var deleted = (CollectionReply.Changed) send(handler, sender, new CollectionCommand.Delete(3, deck.id()));
        assertEquals(4, deleted.revision()); assertTrue(sender.data.decks().isEmpty()); assertEquals(4, sender.writes);
        assertEquals(CollectionError.NOT_FOUND, ((CollectionReply.Rejected) send(handler, sender, new CollectionCommand.Delete(4, deck.id()))).error());
        var draft = new SavedDeck(UUID.randomUUID(), "Incomplete", new DeckList(List.of(100), List.of(), List.of()));
        send(handler, sender, new CollectionCommand.Save(4, draft));
        var failed = (CollectionReply.Rejected) send(handler, sender, new CollectionCommand.Activate(5, draft.id()));
        assertEquals(CollectionError.INELIGIBLE, failed.error()); assertEquals(5, sender.writes);
        assertFalse(failed.eligibility().eligible()); assertEquals(Map.of(100, 1), failed.eligibility().missing());
        for (var command : List.of(new CollectionCommand.Delete(4, draft.id()), new CollectionCommand.Activate(4, draft.id()), new CollectionCommand.ClearActive(4))) {
            assertEquals(CollectionError.STALE, ((CollectionReply.Rejected) send(handler, sender, command)).error());
        }
        assertEquals(5, sender.writes); assertEquals(5, sender.data.revision());
    }}


