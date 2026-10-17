package com.haxerus.duelcraft.client.uitest;

import com.haxerus.duelcraft.Duelcraft;
import com.haxerus.duelcraft.client.collection.CollectionScreen;
import com.haxerus.duelcraft.client.interaction.*;
import com.lowdragmc.lowdraglib2.registry.RegistrationEnvironment;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegisterClient;
import com.lowdragmc.lowdraglib2.uitest.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

/** Actual item/block interactions and server replies; no client screen factory bypass. */
@LDLRegisterClient(name = "duelcraft_entry_points", group = "duelcraft", registry = UIScenario.REGISTRY,
        environment = RegistrationEnvironment.DEV_ONLY)
public final class DuelcraftEntryScenario implements UIScenario {
    @Override public void define(ScenarioBuilder s) {
        s.check("template items are absent from the live registry", ctx ->
                !BuiltInRegistries.ITEM.containsKey(ResourceLocation.parse("duelcraft:example_item"))
                && !BuiltInRegistries.ITEM.containsKey(ResourceLocation.parse("duelcraft:example_block")))
         .check("template block is absent from the live registry", ctx ->
                !BuiltInRegistries.BLOCK.containsKey(ResourceLocation.parse("duelcraft:example_block")))
         .check("Duelcraft creative tab has its name and binder icon", ctx -> {
             var tabs = BuiltInRegistries.CREATIVE_MODE_TAB.entrySet().stream()
                     .filter(entry -> entry.getKey().location().getNamespace().equals(Duelcraft.MODID)).toList();
             return tabs.size() == 1 && tabs.getFirst().getValue().getDisplayName().getString().equals("Duelcraft")
                     && tabs.getFirst().getValue().getIconItem().is(Duelcraft.CARD_BINDER.get());
         });
        s.closeScreen().timeoutMs(60000).server("capture hands and mat position", sc -> {
            sc.put("serverPreparation", com.haxerus.duelcraft.server.DuelManager.get().preparation().view(sc.player().getUUID(), System.currentTimeMillis()).toString());
            sc.put("serverCollectionRevision", CollectionRuntimeFixture.data(sc).revision());
            sc.put("mainHand", sc.player().getMainHandItem().copy());
            sc.put("offHand", sc.player().getOffhandItem().copy());
            var pos = sc.player().blockPosition().offset(1, 0, 0);
            sc.put("matPos", pos); sc.put("matBefore", sc.level().getBlockState(pos));
        });
        for (var hand : InteractionHand.values()) {
            s.server("use binder " + hand, sc -> {
                sc.player().setItemInHand(hand, new ItemStack(Duelcraft.CARD_BINDER.get()));
                Duelcraft.CARD_BINDER.get().use(sc.level(), sc.player(), hand);
            }).waitUntil("binder opens own collection " + hand, ctx -> ctx.screen() instanceof CollectionScreen)
              .awaitModularUI().checkVisible("#collection-back").closeScreen().ticks(2);
        }
        for (boolean empty : new boolean[]{true, false}) {
            s.server("use mat with " + (empty ? "empty" : "occupied") + " hand", sc -> {
                BlockPos pos = sc.get("matPos");
                var state = Duelcraft.DUEL_MAT.get().defaultBlockState(); sc.level().setBlockAndUpdate(pos, state);
                var stack = empty ? ItemStack.EMPTY : new ItemStack(Items.STICK);
                sc.player().setItemInHand(InteractionHand.MAIN_HAND, stack);
                var hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
                if (empty) state.useWithoutItem(sc.level(), sc.player(), hit);
                else state.useItemOn(stack, sc.level(), sc.player(), InteractionHand.MAIN_HAND, hit);
            }).waitUntil("mat opens lobby", ctx -> ctx.screen() instanceof DuelLobbyScreen)
              .awaitModularUI().checkVisible("#lobby-home").screenshot("mat-lobby-" + empty).closeScreen().ticks(2);
        }
        s.server("break mat and remove binder", sc -> {
            sc.level().destroyBlock(sc.get("matPos"), false);
            sc.player().setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            sc.player().setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
        }).step("queue mapped J key", ctx -> net.minecraft.client.KeyMapping.click(com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_J)))
          .waitUntil("hotkey still opens Home without items", ctx -> ctx.screen() instanceof HomeScreen)
          .awaitModularUI().screenshot("home-featured-art")
          .teardown("record routing and close entry screen", ctx -> {
              var client = com.haxerus.duelcraft.DuelcraftClient.getCollectionClient();
              ctx.attach("entry-route-state", "server=" + ctx.get("serverPreparation")
                      + "; client=" + com.haxerus.duelcraft.DuelcraftClient.preparation().view()
                      + "; collectionRevision=" + ctx.get("serverCollectionRevision")
                      + "; connected=" + ctx.getField(client, "connected")
                      + "; requiredRevision=" + ctx.getField(client, "requiredRevision")
                      + "; live=" + com.haxerus.duelcraft.client.LDLibDuelScreen.isDuelLive());
              ctx.mc().setScreen(null);
          })
          .teardownServer("restore physical fixtures", sc -> {
              sc.player().setItemInHand(InteractionHand.MAIN_HAND, sc.get("mainHand"));
              sc.player().setItemInHand(InteractionHand.OFF_HAND, sc.get("offHand"));
              sc.level().setBlockAndUpdate(sc.get("matPos"), sc.get("matBefore"));
          });
    }
}
