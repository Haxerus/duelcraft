package com.haxerus.duelcraft.server;

import com.haxerus.duelcraft.collection.*;
import com.haxerus.duelcraft.server.collection.CardTransferService;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CardGrantCommandTest {
    @Test void registeredGrantRouteRejectsNonoperators() {
        var dispatcher = new CommandDispatcher<CommandSourceStack>();
        CardGrantCommand.register(dispatcher);
        var node = dispatcher.getRoot().getChild("duel").getChild("card");
        assertFalse(node.canUse(source(0))); assertTrue(node.canUse(source(2)));
        assertThrows(com.mojang.brigadier.exceptions.CommandSyntaxException.class,
                () -> dispatcher.execute("duel card give @s 7 1", source(0)));
    }

    @Test void grantsRespectCapacityBoundsCatalogAndBusyWithoutCreditingCollection() {
        var stacks = new ItemStack[41]; Arrays.fill(stacks, new ItemStack(Items.STONE, 64));
        var busy = new boolean[1];
        var owner = new CardTransferService.Owner() {
            public UUID id() { return new UUID(0, 1); }
            public Optional<PlayerCollectionData> data() { return Optional.of(PlayerCollectionData.empty()); }
            public boolean busy() { return busy[0]; }
            public void persist(PlayerCollectionData ignored) { fail("Grant must never write collection"); }
            public ItemStack slot(int index) { return stacks[index]; }
            public void slot(int index, ItemStack value) { stacks[index] = value; }
            public void inventoryChanged() {}
        };
        assertEquals(CollectionError.INVENTORY_FULL, CardGrantCommand.grant(owner, Set.of(7), 7, 1));
        assertTrue(Arrays.stream(stacks).allMatch(stack -> stack.is(Items.STONE)));
        stacks[0] = ItemStack.EMPTY;
        for (int count : new int[]{0, -1, 4097}) assertEquals(CollectionError.INVALID, CardGrantCommand.grant(owner, Set.of(7), 7, count));
        assertEquals(CollectionError.INVALID, CardGrantCommand.grant(owner, Set.of(7), 8, 1));
        busy[0] = true;
        assertEquals(CollectionError.BUSY, CardGrantCommand.grant(owner, Set.of(7), 7, 1));
        busy[0] = false;
        assertEquals(CollectionError.NONE, CardGrantCommand.grant(owner, Set.of(7), 7, 64));
        assertEquals(64, stacks[0].getCount());
        assertEquals(7, com.haxerus.duelcraft.item.CardItem.code(stacks[0]).orElseThrow());
    }

    private static CommandSourceStack source(int permission) {
        return new CommandSourceStack(CommandSource.NULL, Vec3.ZERO, Vec2.ZERO, null, permission,
                "Fixture", Component.literal("Fixture"), null, null);
    }
}
