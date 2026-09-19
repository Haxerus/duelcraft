package com.haxerus.duelcraft.server.collection;

import com.haxerus.duelcraft.collection.*;
import com.haxerus.duelcraft.server.DuelManager;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import java.util.*;

/** Authenticated synchronous boundary. NeoForge's default MAIN handler owns all reads and writes. */
public final class CollectionPayloadHandler {
    public interface Sender {
        UUID id();
        Optional<PlayerCollectionData> data();
        boolean busy();
        void persist(PlayerCollectionData data);
        void reply(CollectionReplyPayload payload);
    }
    private static final DeckEligibility.Report EMPTY = new DeckEligibility.Report(List.of(), Map.of(), false, false, null);
    private final CollectionService service;
    private final CollectionSnapshotStore snapshots;

    public CollectionPayloadHandler(CollectionService service, CollectionSnapshotStore snapshots) {
        this.service = service; this.snapshots = snapshots;
    }

    public static void handle(CollectionRequestPayload payload, IPayloadContext context) {
        var player = (ServerPlayer) context.player();
        var manager = DuelManager.get();
        if (manager == null) {
            context.reply(new CollectionReplyPayload(payload.requestId(), new CollectionReply.Rejected(CollectionError.DATA_UNAVAILABLE, 0, EMPTY)));
            return;
        }
        manager.collectionHandler().handle(payload, new Sender() {
            public UUID id() { return player.getUUID(); }
            public Optional<PlayerCollectionData> data() { return player.getData(CollectionAttachments.COLLECTION).data(); }
            public boolean busy() { return manager.isBusy(player); }
            public void persist(PlayerCollectionData data) { player.setData(CollectionAttachments.COLLECTION, CollectionAttachment.valid(data)); }
            public void reply(CollectionReplyPayload reply) { context.reply(reply); }
        }, System.currentTimeMillis());
    }

    /** Same boundary logic used by the authenticated context adapter above. */
    public void handle(CollectionRequestPayload payload, Sender sender, long now) {
        var data = sender.data();
        if (data.isEmpty()) { reject(payload, sender, CollectionError.DATA_UNAVAILABLE, 0, service.emptyReport()); return; }
        var before = data.orElseThrow();
        boolean busy = sender.busy();
        if (busy) { reject(payload, sender, CollectionError.BUSY, before.revision(), service.emptyReport()); return; }
        CollectionReply reply;
        switch (payload.command()) {
            case CollectionCommand.Open ignored -> reply = snapshots.open(sender.id(), before, now);
            case CollectionCommand.Page page -> {
                reply = snapshots.page(sender.id(), page, now);
                long capturedRevision = switch (reply) {
                    case CollectionReply.Counts counts -> counts.revision();
                    case CollectionReply.Decks decks -> decks.revision();
                    default -> before.revision();
                };
                if (capturedRevision != before.revision()) {
                    snapshots.invalidate(sender.id());
                    reply = new CollectionReply.Rejected(CollectionError.STALE, before.revision(), service.emptyReport());
                }
                if (reply instanceof CollectionReply.Rejected rejected) {
                    reply = new CollectionReply.Rejected(rejected.error(), before.revision(), service.emptyReport());
                }
            }
            case CollectionCommand.ReadDeck read -> {
                var deck = before.decks().get(read.id());
                reply = deck == null ? new CollectionReply.Rejected(CollectionError.NOT_FOUND, before.revision(), service.emptyReport()) : new CollectionReply.Deck(before.revision(), deck);
            }
            default -> {
                var change = switch (payload.command()) {
                    case CollectionCommand.Save save -> service.save(before, save.expectedRevision(), busy, sender.id(), save.deck());
                    case CollectionCommand.Delete delete -> service.delete(before, delete.expectedRevision(), busy, delete.id());
                    case CollectionCommand.Activate activate -> service.activate(before, activate.expectedRevision(), busy, sender.id(), activate.id());
                    case CollectionCommand.ClearActive clear -> service.clearActive(before, clear.expectedRevision(), busy);
                    default -> throw new IllegalStateException("Not a collection mutation");
                };
                if (!change.success()) reply = new CollectionReply.Rejected(change.error(), before.revision(), change.eligibility());
                else {
                    sender.persist(change.data());
                    snapshots.invalidate(sender.id());
                    var saved = payload.command() instanceof CollectionCommand.Save save ? change.data().decks().get(save.deck().id()) : null;
                    reply = new CollectionReply.Changed(change.data().revision(), change.data().activeDeckId(), saved, 0, change.eligibility());
                }
            }
        }
        sender.reply(new CollectionReplyPayload(payload.requestId(), reply));
    }

    private static void reject(CollectionRequestPayload payload, Sender sender, CollectionError error, long revision, DeckEligibility.Report report) {
        sender.reply(new CollectionReplyPayload(payload.requestId(), new CollectionReply.Rejected(error, revision, report)));
    }
}
