package com.haxerus.duelcraft.collection;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.codecs.PrimitiveCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.jetbrains.annotations.Nullable;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public record PlayerCollectionData(long revision, Map<Integer, Long> counts,
                                   Map<UUID, SavedDeck> decks, @Nullable UUID activeDeckId) {
    // Codec.LONG coerces floating-point values with longValue(), which can silently saturate overflow.
    private static final Codec<Long> EXACT_LONG = new PrimitiveCodec<>() {
        @Override public <T> DataResult<Long> read(DynamicOps<T> ops, T input) {
            return ops.getNumberValue(input).flatMap(number -> {
                try {
                    return DataResult.success(new BigDecimal(number.toString()).longValueExact());
                } catch (ArithmeticException | NumberFormatException exception) {
                    return DataResult.error(() -> "Expected a signed 64-bit integer");
                }
            });
        }
        @Override public <T> T write(DynamicOps<T> ops, Long value) { return ops.createLong(value); }
    };
    private record Count(int code, long count) {
        private static final Codec<Count> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.intRange(1, Integer.MAX_VALUE).fieldOf("code").forGetter(Count::code),
                EXACT_LONG.validate(count -> count > 0 ? DataResult.success(count)
                        : DataResult.error(() -> "Collection counts must be positive"))
                        .fieldOf("count").forGetter(Count::count)
        ).apply(instance, Count::new));
    }
    private record Stored(int schema, long revision, List<Count> counts, List<SavedDeck> decks, Optional<UUID> activeDeckId) {
        private static final Codec<Stored> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.intRange(CollectionLimits.SCHEMA_VERSION, CollectionLimits.SCHEMA_VERSION)
                        .fieldOf("schema").forGetter(Stored::schema),
                EXACT_LONG.fieldOf("revision").forGetter(Stored::revision),
                Count.CODEC.listOf().fieldOf("counts").forGetter(Stored::counts),
                SavedDeck.CODEC.listOf().fieldOf("decks").forGetter(Stored::decks),
                SavedDeck.UUID_CODEC.optionalFieldOf("activeDeckId").forGetter(Stored::activeDeckId)
        ).apply(instance, Stored::new));
    }
    public static final Codec<PlayerCollectionData> CODEC = Stored.CODEC.comapFlatMap(stored -> {
        var counts = new HashMap<Integer, Long>();
        for (var entry : stored.counts()) {
            if (counts.putIfAbsent(entry.code(), entry.count()) != null) {
                return DataResult.error(() -> "Duplicate collection passcode");
            }
        }
        var decks = new HashMap<UUID, SavedDeck>();
        for (var deck : stored.decks()) {
            if (decks.putIfAbsent(deck.id(), deck) != null) {
                return DataResult.error(() -> "Duplicate saved-deck ID");
            }
        }
        try {
            return DataResult.success(new PlayerCollectionData(stored.revision(), counts, decks,
                    stored.activeDeckId().orElse(null)));
        } catch (IllegalArgumentException exception) {
            return DataResult.error(exception::getMessage);
        }
    }, data -> new Stored(CollectionLimits.SCHEMA_VERSION, data.revision(), data.counts().entrySet().stream()
            .map(entry -> new Count(entry.getKey(), entry.getValue())).toList(),
            List.copyOf(data.decks().values()), Optional.ofNullable(data.activeDeckId())));

    public PlayerCollectionData {
        if (revision < 0) throw new IllegalArgumentException("Collection revision must not be negative");
        counts = Map.copyOf(counts);
        decks = Map.copyOf(decks);
        if (counts.entrySet().stream().anyMatch(entry -> entry.getKey() <= 0 || entry.getValue() <= 0)) {
            throw new IllegalArgumentException("Collection passcodes and counts must be positive");
        }
        if (decks.entrySet().stream().anyMatch(entry -> !entry.getKey().equals(entry.getValue().id()))) {
            throw new IllegalArgumentException("Saved-deck map key must match its ID");
        }
        if (activeDeckId != null && !decks.containsKey(activeDeckId)) {
            throw new IllegalArgumentException("Active deck must refer to a saved list");
        }
    }

    public static PlayerCollectionData empty() {
        return new PlayerCollectionData(0, Map.of(), Map.of(), null);
    }
}
