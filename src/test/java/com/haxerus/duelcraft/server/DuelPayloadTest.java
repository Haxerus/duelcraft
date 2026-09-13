package com.haxerus.duelcraft.server;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Wire round trips for the lifecycle payloads. */
class DuelPayloadTest {

    @Test
    void duelEndRoundTrips() {
        ByteBuf buf = Unpooled.buffer();
        DuelEndPayload.STREAM_CODEC.encode(buf, new DuelEndPayload(1, DuelEndPayload.REASON_DISCONNECT));

        var decoded = DuelEndPayload.STREAM_CODEC.decode(buf);

        assertEquals(1, decoded.winner());
        assertEquals(DuelEndPayload.REASON_DISCONNECT, decoded.reason());
        assertEquals(0, buf.readableBytes());
    }

    @Test
    void duelEndCarriesADraw() {
        ByteBuf buf = Unpooled.buffer();
        DuelEndPayload.STREAM_CODEC.encode(buf, new DuelEndPayload(DuelEndPayload.WINNER_DRAW, 0));

        var decoded = DuelEndPayload.STREAM_CODEC.decode(buf);

        assertEquals(DuelEndPayload.WINNER_DRAW, decoded.winner());
        assertEquals(0, decoded.reason());
    }

    @Test
    void duelConcedeRoundTripsWithAnEmptyBody() {
        ByteBuf buf = Unpooled.buffer();
        DuelConcedePayload.STREAM_CODEC.encode(buf, new DuelConcedePayload());

        assertEquals(0, buf.readableBytes());
        assertEquals(new DuelConcedePayload(), DuelConcedePayload.STREAM_CODEC.decode(buf));
        assertEquals(DuelConcedePayload.TYPE, new DuelConcedePayload().type());
    }
}
