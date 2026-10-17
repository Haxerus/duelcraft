package com.haxerus.duelcraft.item;

import com.haxerus.duelcraft.Duelcraft;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import java.util.OptionalInt;

public final class CardItem extends Item {
    public CardItem(Properties properties) { super(properties.stacksTo(64)); }

    public static ItemStack stack(int code, int count) {
        CardComponents.positiveCode(code);
        if (count < 1 || count > 64) throw new IllegalArgumentException("Card stack count must be 1–64");
        var stack = new ItemStack(Duelcraft.CARD.get(), count);
        stack.set(CardComponents.CARD_CODE.get(), code);
        return stack;
    }

    public static OptionalInt code(ItemStack stack) {
        if (!stack.is(Duelcraft.CARD.get())) return OptionalInt.empty();
        var code = stack.get(CardComponents.CARD_CODE.get());
        return code == null || code <= 0 ? OptionalInt.empty() : OptionalInt.of(code);
    }

    public static boolean isCanonical(ItemStack stack) {
        var code = code(stack);
        return code.isPresent() && ItemStack.isSameItemSameComponents(stack, stack(code.getAsInt(), 1));
    }
}
