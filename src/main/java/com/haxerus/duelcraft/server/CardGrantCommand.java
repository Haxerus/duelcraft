package com.haxerus.duelcraft.server;

import com.haxerus.duelcraft.collection.CollectionError;
import com.haxerus.duelcraft.server.collection.CardTransferService;
import com.haxerus.duelcraft.server.collection.InventoryTransferPlan;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import java.util.Map;
import java.util.Set;

/** Administrative source of physical cards; ordinary acquisition belongs to companion content. */
public final class CardGrantCommand {
    public static void onRegister(RegisterCommandsEvent event) { register(event.getDispatcher()); }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("duel").then(Commands.literal("card").requires(source -> source.hasPermission(2))
                .then(Commands.literal("give").then(Commands.argument("player", EntityArgument.player())
                .then(Commands.argument("passcode", IntegerArgumentType.integer(1))
                .then(Commands.argument("count", IntegerArgumentType.integer(1, 4096)).executes(context -> {
                    var manager = DuelManager.get();
                    var target = EntityArgument.getPlayer(context, "player");
                    var error = manager == null ? CollectionError.DATA_UNAVAILABLE : grant(CardTransferService.owner(target),
                            manager.collectionHandler().depositableCodes(), IntegerArgumentType.getInteger(context, "passcode"),
                            IntegerArgumentType.getInteger(context, "count"));
                    if (error != CollectionError.NONE) {
                        context.getSource().sendFailure(Component.translatable("duelcraft.collection.error_" + error.name().toLowerCase(java.util.Locale.ROOT)));
                        return 0;
                    }
                    context.getSource().sendSuccess(() -> Component.translatable("duelcraft.card.granted", target.getDisplayName()), false);
                    return 1;
                })))))));
    }

    public static CollectionError grant(CardTransferService.Owner owner, Set<Integer> known, int code, int count) {
        if (owner.busy()) return CollectionError.BUSY;
        if (!known.contains(code) || code <= 0 || count < 1 || count > 4096) return CollectionError.INVALID;
        var before = CardTransferService.snapshot(owner);
        var plan = InventoryTransferPlan.plan(before, Map.of(code, (long) count), known, InventoryTransferPlan.Kind.WITHDRAW, code, count);
        if (plan.error() != CollectionError.NONE) return plan.error();
        CardTransferService.replacements(before, plan.slots()).forEach(owner::slot);
        owner.inventoryChanged();
        return CollectionError.NONE;
    }
}
