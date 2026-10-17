package com.haxerus.duelcraft.item;

import com.haxerus.duelcraft.Duelcraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class InteractionItemsTest {
    @Test void binderIsPortableAndMatHasNoInventoryOrCollision() {
        assertEquals(1, Duelcraft.CARD_BINDER.get().getDefaultInstance().getMaxStackSize());
        var state = Duelcraft.DUEL_MAT.get().defaultBlockState();
        assertFalse(state.hasBlockEntity());
        assertTrue(state.getCollisionShape(null, BlockPos.ZERO, CollisionContext.empty()).isEmpty());
        assertEquals(1d / 16, state.getShape(null, BlockPos.ZERO).bounds().maxY);
        assertEquals(Duelcraft.DUEL_MAT.get(), Duelcraft.DUEL_MAT_ITEM.get().getBlock());
    }
}
