package com.haxerus.duelcraft.item;

import com.haxerus.duelcraft.Duelcraft;
import io.netty.buffer.Unpooled;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CardItemTest {
    @Test void identityIncludesPasscodeButNotQuantity() {
        var a = CardItem.stack(89631139, 1);
        assertTrue(ItemStack.isSameItemSameComponents(a, CardItem.stack(89631139, 64)));
        assertFalse(ItemStack.isSameItemSameComponents(a, CardItem.stack(46986414, 1)));
        assertEquals(89631139, CardItem.code(a).orElseThrow());
        assertEquals(64, a.getMaxStackSize());
        assertTrue(CardItem.isCanonical(a));
    }

    @Test void missingInvalidAndCustomizedCardsAreNotCanonical() {
        var stack = new ItemStack(Duelcraft.CARD.get());
        assertTrue(CardItem.code(stack).isEmpty());
        assertFalse(CardItem.isCanonical(stack));
        stack.set(CardComponents.CARD_CODE.get(), 0);
        assertTrue(CardItem.code(stack).isEmpty());
        stack.set(CardComponents.CARD_CODE.get(), -7);
        assertFalse(CardItem.isCanonical(stack));
        stack = CardItem.stack(7, 1);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("Keep me"));
        assertFalse(CardItem.isCanonical(stack));
        assertThrows(IllegalArgumentException.class, () -> CardItem.stack(0, 1));
        assertThrows(IllegalArgumentException.class, () -> CardItem.stack(7, 0));
        assertThrows(IllegalArgumentException.class, () -> CardItem.stack(7, 65));
    }

    @Test void componentPersistsAndSynchronizesOnlyPositiveCodes() {
        var component = CardComponents.CARD_CODE.get();
        var tag = component.codec().encodeStart(NbtOps.INSTANCE, 89631139).getOrThrow();
        assertEquals(89631139, component.codec().parse(NbtOps.INSTANCE, tag).getOrThrow());
        assertTrue(component.codec().encodeStart(NbtOps.INSTANCE, 0).error().isPresent());
        var buffer = new net.minecraft.network.RegistryFriendlyByteBuf(Unpooled.buffer(), net.minecraft.core.RegistryAccess.fromRegistryOfRegistries(net.minecraft.core.registries.BuiltInRegistries.REGISTRY));
        try {
            component.streamCodec().encode(buffer, 46986414);
            assertEquals(46986414, component.streamCodec().decode(buffer));
            buffer.writeByte(0);
            assertThrows(IllegalArgumentException.class, () -> component.streamCodec().decode(buffer));
        } finally { buffer.release(); }
    }
}

