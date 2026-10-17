package com.haxerus.duelcraft.server;

import com.haxerus.duelcraft.collection.*;
import com.haxerus.duelcraft.core.*;
import com.haxerus.duelcraft.duel.*;
import com.haxerus.duelcraft.server.collection.*;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Exercises the authenticated dispatch used by both text commands and packets. */
class DuelCommandPolicyTest {
    private PreparationStatePayload action(DuelPreparationService service, UUID actor, PreparationCommand command) {
        return PreparationPayloadHandler.apply(service, actor, new PreparationRequestPayload(UUID.randomUUID(), command), System.currentTimeMillis());
    }
    @Test void sharedChallengeAcceptHandFirstForfeitGetAndClearRespectPolicy() throws Exception {
        for (boolean ownership : List.of(false, true)) {
            var f = new DuelStartPolicyTest.Fixture(ownership); var service = f.manager.preparation();
            assertEquals("Fixture", f.manager.getPlayerCurrentDeck(f.a).orElseThrow());
            assertEquals(40, f.manager.resolveDeck(f.a).main().size());
            var invite = action(service, f.a, new PreparationCommand.Invite(f.b, DuelRule.MR5, 1L, PlayerOptions.standard()));
            assertEquals(PreparationResult.OK, invite.result());
            var accepted = action(service, f.b, new PreparationCommand.Accept(invite.view().flowId()));
            var before = f.data.get(f.a);
            assertFalse(f.manager.clearPlayerCurrentDeck(f.a)); assertEquals(before, f.data.get(f.a));
            var flow = accepted.view().flowId(); var round = accepted.view().roundId();
            assertEquals(PreparationResult.OK, action(service, f.a, new PreparationCommand.Hand(flow, round, FirstTurnLobby.Hand.ROCK)).result());
            assertEquals(PreparationResult.OK, action(service, f.b, new PreparationCommand.Hand(flow, round, FirstTurnLobby.Hand.SCISSORS)).result());
            assertEquals(PreparationResult.OK, action(service, f.a, new PreparationCommand.First(flow, true)).result());
            assertFalse(f.manager.clearPlayerCurrentDeck(f.a));
            f.manager.forfeit(f.a); assertTrue(f.manager.clearPlayerCurrentDeck(f.a));
            assertTrue(f.manager.getPlayerCurrentDeck(f.a).isEmpty()); assertThrows(java.io.IOException.class, () -> f.manager.resolveDeck(f.a));
        }
    }
    @Test void companionDenialAtAcceptAndChallengeAppliesWithOwnershipOffAndOn() {
        for (boolean ownership : List.of(false, true)) {
            var f = new DuelStartPolicyTest.Fixture(ownership); var service = f.manager.preparation();
            var invite = action(service, f.a, new PreparationCommand.Invite(f.b, DuelRule.MR5, null, PlayerOptions.standard()));
            f.deny = true;
            assertEquals(PreparationResult.INELIGIBLE, action(service, f.b, new PreparationCommand.Accept(invite.view().flowId())).result());
            assertFalse(f.manager.isBusy(f.a)); assertEquals(0, f.allocations);
            action(service, f.a, new PreparationCommand.Cancel(invite.view().flowId()));
            assertEquals(PreparationResult.INELIGIBLE, action(service, f.a, new PreparationCommand.Invite(f.b, DuelRule.MR5, null, PlayerOptions.standard())).result());
        }
    }
    @Test void authenticatedOutsidersDuplicateAndStaleActionsCannotChangeFlowOrExposeOpponentHand() {
        var f = new DuelStartPolicyTest.Fixture(false); var service = f.manager.preparation();
        var invite = action(service, f.a, new PreparationCommand.Invite(f.b, DuelRule.MR5, null, PlayerOptions.standard()));
        var flow = invite.view().flowId(); var outsider = UUID.randomUUID();
        assertEquals(PreparationResult.STALE, action(service, outsider, new PreparationCommand.Accept(flow)).result());
        assertEquals(PreparationResult.STALE, action(service, f.a, new PreparationCommand.Accept(flow)).result());
        var accepted = action(service, f.b, new PreparationCommand.Accept(flow)); var round = accepted.view().roundId();
        action(service, f.a, new PreparationCommand.Hand(flow, round, FirstTurnLobby.Hand.PAPER));
        assertEquals(PreparationResult.STALE, action(service, f.a, new PreparationCommand.Hand(flow, round, FirstTurnLobby.Hand.ROCK)).result());
        var view = action(service, f.b, new PreparationCommand.View()); var buf = Unpooled.buffer();
        try {
            PreparationStatePayload.STREAM_CODEC.encode(buf, view); var decoded = PreparationStatePayload.STREAM_CODEC.decode(buf);
            assertEquals(PreparationView.Mode.RPS, decoded.view().mode()); assertFalse(decoded.view().ownHandSubmitted());
            assertEquals(f.a, decoded.view().opponentId()); assertFalse(decoded.view().canChooseFirst());
        } finally { buf.release(); }
        assertEquals(PreparationResult.STALE, action(service, outsider, new PreparationCommand.Cancel(flow)).result());
        action(service, f.a, new PreparationCommand.Cancel(flow));
        var replacement = action(service, f.a, new PreparationCommand.Invite(f.b, DuelRule.MR5, null, PlayerOptions.standard()));
        assertEquals(PreparationResult.STALE, action(service, f.b, new PreparationCommand.Decline(flow)).result());
        assertEquals(replacement.view().flowId(), service.view(f.b, System.currentTimeMillis()).flowId());
        assertEquals(PreparationResult.OK, action(service, f.b, new PreparationCommand.Decline(replacement.view().flowId())).result());
    }
    @Test void busyCommandAndCollectionPacketUseIdenticalManagerLockAndPreserveAttachment() {
        var f = new DuelStartPolicyTest.Fixture(false); var flow = f.ready();
        var before = f.data.get(f.a); var reply = new CollectionReply[1];
        var sender = new CollectionPayloadHandler.Sender() {
            public UUID id() { return f.a; }
            public Optional<PlayerCollectionData> data() { return Optional.of(f.data.get(f.a)); }
            public boolean busy() { return f.manager.isBusy(f.a); }
            public void persist(PlayerCollectionData data) { f.data.put(f.a, data); }
            public void reply(CollectionReplyPayload value) { reply[0] = value.reply(); }
            public net.minecraft.world.item.ItemStack slot(int index) { throw new AssertionError("No inventory access for Save"); }
            public void slot(int index, net.minecraft.world.item.ItemStack stack) { throw new AssertionError("No inventory write for Save"); }
            public void inventoryChanged() { throw new AssertionError("No inventory change for Save"); }
        };
        var save = new CollectionRequestPayload(UUID.randomUUID(), new CollectionCommand.Save(before.revision(), before.decks().get(before.activeDeckId())));
        f.manager.collectionHandler().handle(save, sender, System.currentTimeMillis());
        assertEquals(CollectionError.BUSY, ((CollectionReply.Rejected) reply[0]).error());
        assertFalse(f.manager.clearPlayerCurrentDeck(f.a)); assertEquals(before, f.data.get(f.a));
        action(f.manager.preparation(), f.a, new PreparationCommand.Cancel(flow));
        f.manager.collectionHandler().handle(save, sender, System.currentTimeMillis());
        assertInstanceOf(CollectionReply.Changed.class, reply[0]);
        assertEquals(before.revision() + 1, f.data.get(f.a).revision());
    }

}
