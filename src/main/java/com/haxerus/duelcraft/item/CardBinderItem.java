package com.haxerus.duelcraft.item;

import com.haxerus.duelcraft.interaction.ScreenRoute;
import com.haxerus.duelcraft.server.InteractionPayloadHandler;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import java.util.UUID;

public final class CardBinderItem extends Item {
    public CardBinderItem(Properties properties) { super(properties.stacksTo(1)); }
    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (player instanceof ServerPlayer serverPlayer)
            InteractionPayloadHandler.open(serverPlayer, ScreenRoute.COLLECTION, UUID.randomUUID(), true);
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
    }
}
