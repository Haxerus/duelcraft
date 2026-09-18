package com.haxerus.duelcraft.collection;

import com.haxerus.duelcraft.Duelcraft;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public final class CollectionAttachments {
    public static final DeferredRegister<AttachmentType<?>> TYPES = DeferredRegister.create(
            NeoForgeRegistries.Keys.ATTACHMENT_TYPES, Duelcraft.MODID);
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<CollectionAttachment>> COLLECTION =
            TYPES.register("collection", () -> AttachmentType.builder(
                    () -> CollectionAttachment.valid(PlayerCollectionData.empty()))
                    .serialize(CollectionAttachment.SERIALIZER).copyOnDeath().build());

    private CollectionAttachments() {}
}
