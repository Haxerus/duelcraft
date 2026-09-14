package com.haxerus.duelcraft.server;

import com.haxerus.duelcraft.core.Deck;
import com.haxerus.duelcraft.core.DeckValidator;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/** A client's selected deck contents. The name is a label, never a server file path. */
public record DuelDeckPayload(String name, Deck deck) implements CustomPacketPayload {
    public static final int MAX_NAME_LENGTH = 128;
    public static final Type<DuelDeckPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("duelcraft", "duel_deck"));
    public static final StreamCodec<ByteBuf, DuelDeckPayload> STREAM_CODEC =
            StreamCodec.of(DuelDeckPayload::encode, DuelDeckPayload::decode);

    public DuelDeckPayload {
        if (name.isBlank() || name.length() > MAX_NAME_LENGTH || name.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Deck name must contain 1-128 printable characters.");
        }
        if (deck.main().size() > DeckValidator.MAIN_MAX || deck.extra().size() > DeckValidator.EXTRA_MAX) {
            throw new IllegalArgumentException("Deck exceeds 60 main or 15 extra cards.");
        }
        deck = new Deck(List.copyOf(deck.main()), List.copyOf(deck.extra()));
    }

    private static void encode(ByteBuf buffer, DuelDeckPayload payload) {
        var buf = new FriendlyByteBuf(buffer);
        buf.writeUtf(payload.name, MAX_NAME_LENGTH);
        buf.writeVarInt(payload.deck.main().size());
        for (int code : payload.deck.main()) buf.writeVarInt(code);
        buf.writeVarInt(payload.deck.extra().size());
        for (int code : payload.deck.extra()) buf.writeVarInt(code);
    }

    private static DuelDeckPayload decode(ByteBuf buffer) {
        var buf = new FriendlyByteBuf(buffer);
        String name = buf.readUtf(MAX_NAME_LENGTH);
        return new DuelDeckPayload(name, new Deck(readCards(buf, DeckValidator.MAIN_MAX),
                readCards(buf, DeckValidator.EXTRA_MAX)));
    }

    private static List<Integer> readCards(FriendlyByteBuf buf, int limit) {
        int count = buf.readVarInt();
        if (count < 0 || count > limit) throw new IllegalArgumentException("Invalid deck section size: " + count);
        var cards = new ArrayList<Integer>(count);
        for (int i = 0; i < count; i++) cards.add(buf.readVarInt());
        return cards;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
