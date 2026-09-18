package com.haxerus.duelcraft.collection;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.StringTag;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CollectionAttachmentTest {
    @Test void validDataSurvivesSerializerCopy() {
        var original = new PlayerCollectionData(4, Map.of(1, 1000L), Map.of(), null);
        var encoded = CollectionAttachment.SERIALIZER.write(CollectionAttachment.valid(original), null);
        var copied = CollectionAttachment.SERIALIZER.read(null, encoded, null);
        assertEquals(original, copied.data().orElseThrow());
    }

    @Test void unsupportedVersionIsPreservedAndExposesNoData() {
        var raw = (CompoundTag) PlayerCollectionData.CODEC.encodeStart(NbtOps.INSTANCE, PlayerCollectionData.empty()).getOrThrow();
        raw.putInt("schema", 99);
        raw.putString("futureField", "keep me");
        var expected = raw.copy();
        var unreadable = CollectionAttachment.SERIALIZER.read(null, raw, null);
        raw.remove("futureField");
        assertTrue(unreadable.data().isEmpty());
        var written = CollectionAttachment.SERIALIZER.write(unreadable, null);
        assertEquals(expected, written);
        ((CompoundTag) written).remove("futureField");
        assertEquals(expected, CollectionAttachment.SERIALIZER.write(unreadable, null));
    }

    @Test void malformedNoncompoundTagsAlsoSurvive() {
        var raw = StringTag.valueOf("broken");
        var unreadable = CollectionAttachment.SERIALIZER.read(null, raw, null);
        assertTrue(unreadable.data().isEmpty());
        assertEquals(raw, CollectionAttachment.SERIALIZER.write(unreadable, null));
    }
}
