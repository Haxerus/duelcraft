package com.haxerus.duelcraft.server;

import com.haxerus.duelcraft.duel.PreparationView;
import com.haxerus.duelcraft.interaction.ScreenRoute;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class InteractionPayloadTest {
    @Test void privateDenialRoundTripsAndIsBounded() {
        var notice = new PreparationNoticePayload("Complete the companion trial first.");
        var buf = Unpooled.buffer();
        try {
            PreparationNoticePayload.STREAM_CODEC.encode(buf, notice);
            assertEquals(notice, PreparationNoticePayload.STREAM_CODEC.decode(buf));
            buf.clear();
            PreparationNoticePayload.STREAM_CODEC.encode(buf, notice);
            buf.writeByte(0);
            assertThrows(RuntimeException.class, () -> PreparationNoticePayload.STREAM_CODEC.decode(buf));
        } finally { buf.release(); }
        assertThrows(IllegalArgumentException.class, () -> new PreparationNoticePayload("x".repeat(2049)));
    }
    @Test void managementRequestsRoundTripAndCannotRequestLiveDuel() {
        for (var route : new ScreenRoute[]{ScreenRoute.HOME, ScreenRoute.COLLECTION, ScreenRoute.LOBBY}) {
            var request = new InteractionRequestPayload(UUID.randomUUID(), route);
            var buf = Unpooled.buffer();
            try {
                InteractionRequestPayload.STREAM_CODEC.encode(buf, request);
                assertEquals(request, InteractionRequestPayload.STREAM_CODEC.decode(buf));
                assertFalse(buf.isReadable());
            } finally { buf.release(); }
        }
        assertThrows(IllegalArgumentException.class, () -> new InteractionRequestPayload(UUID.randomUUID(), ScreenRoute.DUEL));
    }

    @Test void unsolicitedReplyPreservesPrivatePreparationAndRejectsTrailingBytes() {
        var view = new PreparationView(4, PreparationView.Mode.IDLE, null, null, null, "", false, false, false, 0, null);
        var reply = new InteractionStatePayload(UUID.randomUUID(), ScreenRoute.COLLECTION, view, true);
        var buf = Unpooled.buffer();
        try {
            InteractionStatePayload.STREAM_CODEC.encode(buf, reply);
            assertEquals(reply, InteractionStatePayload.STREAM_CODEC.decode(buf));
            buf.clear();
            InteractionStatePayload.STREAM_CODEC.encode(buf, reply);
            buf.writeByte(0);
            assertThrows(RuntimeException.class, () -> InteractionStatePayload.STREAM_CODEC.decode(buf));
        } finally { buf.release(); }
    }

    @Test void invalidRouteTagIsRejected() {
        var buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            buf.writeUUID(UUID.randomUUID()); buf.writeByte(99);
            assertThrows(RuntimeException.class, () -> InteractionRequestPayload.STREAM_CODEC.decode(buf));
        } finally { buf.release(); }
    }
}
