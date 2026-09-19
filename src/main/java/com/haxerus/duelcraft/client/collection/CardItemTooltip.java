package com.haxerus.duelcraft.client.collection;

import com.haxerus.duelcraft.DuelcraftClient;
import com.haxerus.duelcraft.item.CardItem;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

public final class CardItemTooltip {
    public static void append(ItemTooltipEvent event) {
        var code = CardItem.code(event.getItemStack());
        if (code.isEmpty()) return;
        var database = DuelcraftClient.getCardDatabase();
        var card = database == null ? null : database.getCard(code.getAsInt());
        if (card != null) event.getToolTip().add(Component.literal(card.name()));
        event.getToolTip().add(Component.translatable("item.duelcraft.card.passcode", code.getAsInt()));
    }
}
