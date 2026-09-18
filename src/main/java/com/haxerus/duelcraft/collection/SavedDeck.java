package com.haxerus.duelcraft.collection;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Objects;
import java.util.UUID;

public record SavedDeck(UUID id, String name, DeckList cards) {
    static final Codec<UUID> UUID_CODEC = Codec.STRING.comapFlatMap(value -> {
        try {
            var id = UUID.fromString(value);
            return id.toString().equalsIgnoreCase(value) ? DataResult.success(id)
                    : DataResult.error(() -> "Invalid deck UUID");
        } catch (IllegalArgumentException exception) {
            return DataResult.error(() -> "Invalid deck UUID");
        }
    }, UUID::toString);
    private static final Codec<DeckList> CARDS_CODEC = RecordCodecBuilder.<DeckList>create(instance -> instance.group(
            CollectionCodecs.PASSCODE.listOf(0, CollectionLimits.DRAFT_CARDS).fieldOf("main").forGetter(DeckList::main),
            CollectionCodecs.PASSCODE.listOf(0, CollectionLimits.DRAFT_CARDS).fieldOf("extra").forGetter(DeckList::extra),
            CollectionCodecs.PASSCODE.listOf(0, CollectionLimits.DRAFT_CARDS).fieldOf("side").forGetter(DeckList::side)
    ).apply(instance, DeckList::new)).validate(cards -> total(cards) <= CollectionLimits.DRAFT_CARDS
            ? DataResult.success(cards) : DataResult.error(() -> "Draft exceeds the 512-card editor limit"));
    public static final Codec<SavedDeck> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            UUID_CODEC.fieldOf("id").forGetter(SavedDeck::id),
            Codec.STRING.validate(name -> validName(name) ? DataResult.success(name)
                    : DataResult.error(() -> "Deck name must contain 1–128 printable characters and cannot be blank"))
                    .fieldOf("name").forGetter(SavedDeck::name),
            CARDS_CODEC.fieldOf("cards").forGetter(SavedDeck::cards)
    ).apply(instance, SavedDeck::new));

    public SavedDeck {
        Objects.requireNonNull(id);
        Objects.requireNonNull(cards);
        if (!validName(name)) {
            throw new IllegalArgumentException("Deck name must contain 1–128 printable characters and cannot be blank");
        }
        if (total(cards) > CollectionLimits.DRAFT_CARDS) {
            throw new IllegalArgumentException("Draft exceeds the 512-card editor limit");
        }
    }

    private static boolean validName(String name) {
        return name != null && !name.isBlank() && name.length() <= CollectionLimits.NAME_LENGTH
                && name.codePoints().noneMatch(code -> Character.isISOControl(code)
                        || Character.getType(code) == Character.FORMAT
                        || Character.getType(code) == Character.SURROGATE
                        || Character.getType(code) == Character.UNASSIGNED
                        || code == 0x2028 || code == 0x2029);
    }

    private static long total(DeckList cards) {
        return (long) cards.main().size() + cards.extra().size() + cards.side().size();
    }
}
