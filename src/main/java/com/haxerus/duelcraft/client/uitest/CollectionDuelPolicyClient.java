package com.haxerus.duelcraft.client.uitest;

import com.haxerus.duelcraft.DuelcraftClient;
import com.haxerus.duelcraft.client.collection.CollectionScreen;
import com.haxerus.duelcraft.client.interaction.PreparationRouting;
import com.lowdragmc.lowdraglib2.uitest.ScenarioBuilder;
import net.minecraft.client.gui.screens.GenericMessageScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.ClientCommandHandler;

/** Client bytecode stays outside the dedicated scenario's verification path. */
public final class CollectionDuelPolicyClient {
    public static void openDirty(ScenarioBuilder s) {
        s.step("collection command", ctx -> ClientCommandHandler.runCommand("duel collection"))
         .waitUntil("real editor", ctx -> ctx.screen() instanceof CollectionScreen).awaitModularUI()
         .step("dirty editor", CollectionDuelPolicyScenario::dirty);
    }
    public static void disconnect(ScenarioBuilder s) {
        s.step("disconnect game while keeping test hub", ctx -> {
            ctx.mc().level.disconnect();
            ctx.mc().disconnect(new GenericMessageScreen(Component.literal("M4 disconnect")));
            ctx.mc().setScreen(new TitleScreen());
        }).waitUntil("private state cleared by real logout", ctx -> DuelcraftClient.preparation().view() == null && DuelcraftClient.getCollectionClient().state().view() == null)
          .check("no live state or suspended draft after logout", ctx -> !com.haxerus.duelcraft.client.LDLibDuelScreen.isDuelLive()
                  && !PreparationRouting.managementBlocked() && !PreparationRouting.restoreEditor());
    }
}
