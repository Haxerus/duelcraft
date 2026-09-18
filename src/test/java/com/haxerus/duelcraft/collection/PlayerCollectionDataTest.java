package com.haxerus.duelcraft.collection;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class PlayerCollectionDataTest {
    private static final UUID ID = UUID.randomUUID();

    @Test void incompleteUnownedAndUnknownDraftsRoundTripWithLargeCounts() {
        var draft = new SavedDeck(ID, "Missing cards", new DeckList(List.of(89631139), List.of(), List.of()));
        var original = new PlayerCollectionData(4, Map.of(89631139, 1000L), Map.of(ID, draft), ID);
        assertEquals(original, PlayerCollectionData.CODEC.parse(NbtOps.INSTANCE, encode(original)).getOrThrow());
        var unknown = new SavedDeck(ID, "Unknown", new DeckList(List.of(Integer.MAX_VALUE), List.of(), List.of()));
        assertEquals(unknown, SavedDeck.CODEC.parse(NbtOps.INSTANCE,
                SavedDeck.CODEC.encodeStart(NbtOps.INSTANCE, unknown).getOrThrow()).getOrThrow());
        assertEquals(PlayerCollectionData.empty(), PlayerCollectionData.CODEC.parse(NbtOps.INSTANCE,
                encode(PlayerCollectionData.empty())).getOrThrow());
        var empty = new SavedDeck(ID, "Empty", new DeckList(List.of(), List.of(), List.of()));
        assertEquals(empty, SavedDeck.CODEC.parse(NbtOps.INSTANCE,
                SavedDeck.CODEC.encodeStart(NbtOps.INSTANCE, empty).getOrThrow()).getOrThrow());
    }

    @Test void countsAndDecksAreCopiedAndImmutable() {
        var counts = new HashMap<>(Map.of(1, 65L));
        var decks = new HashMap<>(Map.of(ID, draft()));
        var data = new PlayerCollectionData(0, counts, decks, null);
        counts.clear();
        decks.clear();
        assertEquals(Map.of(1, 65L), data.counts());
        assertEquals(Map.of(ID, draft()), data.decks());
        assertThrows(UnsupportedOperationException.class, () -> data.counts().clear());
        assertThrows(UnsupportedOperationException.class, () -> data.decks().clear());
        assertTrue(PlayerCollectionData.empty().counts().isEmpty());
    }

    @Test void constructorsRejectInvalidState() {
        for (long count : new long[]{0, -1}) {
            assertThrows(IllegalArgumentException.class, () -> new PlayerCollectionData(0, Map.of(1, count), Map.of(), null));
        }
        assertThrows(IllegalArgumentException.class, () -> new PlayerCollectionData(-1, Map.of(), Map.of(), null));
        assertThrows(IllegalArgumentException.class, () -> new PlayerCollectionData(0, Map.of(0, 1L), Map.of(), null));
        assertThrows(IllegalArgumentException.class, () -> new PlayerCollectionData(0, Map.of(), Map.of(), ID));
        assertThrows(IllegalArgumentException.class, () -> new PlayerCollectionData(0, Map.of(), Map.of(UUID.randomUUID(), draft()), null));
    }

    @Test void draftLimitIsTotalAcrossSections() {
        var allowed = new SavedDeck(ID, "Maximum", new DeckList(Collections.nCopies(510, 1), List.of(2), List.of(3)));
        assertEquals(allowed, SavedDeck.CODEC.parse(NbtOps.INSTANCE,
                SavedDeck.CODEC.encodeStart(NbtOps.INSTANCE, allowed).getOrThrow()).getOrThrow());
        assertThrows(IllegalArgumentException.class, () -> new SavedDeck(ID, "Too big",
                new DeckList(Collections.nCopies(511, 1), List.of(2), List.of(3))));
        var encoded = (CompoundTag) SavedDeck.CODEC.encodeStart(NbtOps.INSTANCE, allowed).getOrThrow();
        encoded.getCompound("cards").putIntArray("side", new int[]{3, 3});
        assertTrue(SavedDeck.CODEC.parse(NbtOps.INSTANCE, encoded).error().isPresent());
    }

    @Test void namesMustBePrintableNonblankAndBounded() {
        for (var name : List.of("", " ", "\nname", "name\u007f", "x".repeat(129))) {
            assertThrows(IllegalArgumentException.class, () -> new SavedDeck(ID, name, draft().cards()));
            var tag = (CompoundTag) SavedDeck.CODEC.encodeStart(NbtOps.INSTANCE, draft()).getOrThrow();
            tag.putString("name", name);
            assertTrue(SavedDeck.CODEC.parse(NbtOps.INSTANCE, tag).error().isPresent());
        }
        assertDoesNotThrow(() -> new SavedDeck(ID, "界".repeat(128), draft().cards()));
    }

    @Test void codecRejectsInvalidSchemaCountsRevisionAndActiveId() {
        var data = new PlayerCollectionData(0, Map.of(1, Long.MAX_VALUE), Map.of(ID, draft()), null);
        var tag = encode(data);
        tag.putInt("schema", 2);
        reject(tag);
        tag = encode(data);
        tag.putLong("revision", -1);
        reject(tag);
        for (long count : new long[]{0, -1}) {
            tag = encode(data);
            tag.getList("counts", 10).getCompound(0).putLong("count", count);
            reject(tag);
        }
        tag = encode(data);
        tag.getList("counts", 10).getCompound(0).put("count", DoubleTag.valueOf(1E30));
        reject(tag);
        tag = encode(data);
        tag.putString("activeDeckId", UUID.randomUUID().toString());
        reject(tag);
    }

    @Test void codecRejectsDuplicatePasscodesAndDeckIdsBeforeMapConversion() {
        var data = new PlayerCollectionData(0, Map.of(1, 1L), Map.of(ID, draft()), null);
        for (var field : List.of("counts", "decks")) {
            var tag = encode(data);
            ListTag entries = tag.getList(field, 10);
            entries.add(entries.get(0).copy());
            reject(tag);
        }
    }

    @Test void duplicateDisplayNamesRetainSeparateUuidIdentities() {
        var otherId = UUID.randomUUID();
        var other = new SavedDeck(otherId, draft().name(), draft().cards());
        var data = new PlayerCollectionData(Long.MAX_VALUE, Map.of(1, Long.MAX_VALUE),
                Map.of(ID, draft(), otherId, other), null);
        assertEquals(data, PlayerCollectionData.CODEC.parse(NbtOps.INSTANCE, encode(data)).getOrThrow());
    }

    @Test void codecRejectsNonpositivePasscodesAndMalformedUuid() {
        var data = new PlayerCollectionData(0, Map.of(1, 1L), Map.of(ID, draft()), null);
        for (int code : new int[]{0, -1}) {
            var tag = encode(data);
            tag.getList("counts", 10).getCompound(0).putInt("code", code);
            reject(tag);
            for (var section : List.of("main", "extra", "side")) {
                tag = encode(data);
                tag.getList("decks", 10).getCompound(0).getCompound("cards").putIntArray(section, new int[]{code});
                reject(tag);
            }
        }
        var tag = encode(data);
        tag.getList("decks", 10).getCompound(0).putString("id", "invalid");
        reject(tag);
        tag = encode(data);
        tag.putString("activeDeckId", "invalid");
        reject(tag);
    }

    @ParameterizedTest
    @MethodSource("malformedIntegerTags")
    void codecRejectsFractionalAndOverflowingSchemaAndPasscodes(CompoundTag tag) {
        reject(tag);
    }

    static Stream<CompoundTag> malformedIntegerTags() {
        return Stream.of("schema", "code", "main", "extra", "side").flatMap(field ->
                Stream.of(DoubleTag.valueOf(1.5), LongTag.valueOf(4294967297L)).map(number -> {
                    var tag = encode(new PlayerCollectionData(0, Map.of(1, 1L), Map.of(ID, draft()), null));
                    if (field.equals("schema")) {
                        tag.put(field, number);
                    } else if (field.equals("code")) {
                        tag.getList("counts", Tag.TAG_COMPOUND).getCompound(0).put(field, number);
                    } else {
                        var cards = new ListTag();
                        cards.add(number);
                        tag.getList("decks", Tag.TAG_COMPOUND).getCompound(0).getCompound("cards").put(field, cards);
                    }
                    return tag;
                }));
    }

    private static SavedDeck draft() {
        return new SavedDeck(ID, "Draft", new DeckList(List.of(1), List.of(), List.of()));
    }

    private static CompoundTag encode(PlayerCollectionData data) {
        return (CompoundTag) PlayerCollectionData.CODEC.encodeStart(NbtOps.INSTANCE, data).getOrThrow();
    }

    private static void reject(CompoundTag tag) {
        assertTrue(PlayerCollectionData.CODEC.parse(NbtOps.INSTANCE, tag).error().isPresent());
    }
}
