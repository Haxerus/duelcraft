package com.haxerus.duelcraft.collection;

import net.minecraft.core.HolderLookup;
import com.haxerus.duelcraft.Duelcraft;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.neoforged.neoforge.attachment.IAttachmentHolder;
import net.neoforged.neoforge.attachment.IAttachmentSerializer;
import java.util.Optional;
import java.util.Objects;

public final class CollectionAttachment {
    public static final IAttachmentSerializer<Tag, CollectionAttachment> SERIALIZER = new IAttachmentSerializer<>() {
        @Override public CollectionAttachment read(IAttachmentHolder holder, Tag tag, HolderLookup.Provider provider) {
            var decoded = PlayerCollectionData.CODEC.parse(NbtOps.INSTANCE, tag);
            if (decoded.result().isPresent()) return valid(decoded.result().orElseThrow());
            Duelcraft.LOGGER.error("Cannot decode personal collection attachment: {}",
                    decoded.error().orElseThrow().message());
            return unreadable(tag);
        }
        @Override public Tag write(CollectionAttachment attachment, HolderLookup.Provider provider) {
            return attachment.raw != null ? attachment.raw.copy()
                    : PlayerCollectionData.CODEC.encodeStart(NbtOps.INSTANCE, attachment.data).getOrThrow();
        }
    };
    private final PlayerCollectionData data;
    private final Tag raw;
    private CollectionAttachment(PlayerCollectionData data, Tag raw) {
        this.data = data;
        this.raw = raw;
    }
    public static CollectionAttachment valid(PlayerCollectionData data) {
        return new CollectionAttachment(Objects.requireNonNull(data), null);
    }
    public static CollectionAttachment unreadable(Tag raw) {
        return new CollectionAttachment(null, Objects.requireNonNull(raw).copy());
    }
    public Optional<PlayerCollectionData> data() { return Optional.ofNullable(data); }
}
