package com.haxerus.duelcraft.client.uitest;

import com.haxerus.duelcraft.DuelcraftClient;
import com.haxerus.duelcraft.duel.PreparationView;
import com.lowdragmc.lowdraglib2.uitest.ScenarioBuilder;
import com.lowdragmc.lowdraglib2.uitest.TestContext;
import net.minecraft.client.GuiMessage;
import net.minecraft.client.gui.screens.GenericMessageScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;

import java.util.List;

/** Client-only preparation view and fresh chat capture for the dedicated notification scenario. */
public final class PreparationNotificationClient {
    private PreparationNotificationClient() {}

    public static void clearChat(ScenarioBuilder s) {
        s.step("clear historical chat", ctx -> ctx.mc().gui.getChat().clearMessages(false));
    }

    public static void checkPrompts(ScenarioBuilder s, String stage, long invitations, long rps, long winners) {
        s.step(stage + " prompt counts", ctx -> {
            var messages = messages(ctx);
            ctx.attach(stage + "-chat", String.join("\n", messages));
            ctx.check("invitation prompts", count(messages, "LDTestA challenged you.") == invitations,
                    invitations, count(messages, "LDTestA challenged you."));
            ctx.check("RPS prompts", count(messages, "Rock-paper-scissors:") == rps,
                    rps, count(messages, "Rock-paper-scissors:"));
            ctx.check("winner prompts", count(messages, "You won the roll.") == winners,
                    winners, count(messages, "You won the roll."));
        });
    }

    public static String viewSignature() {
        return signature(DuelcraftClient.preparation().view());
    }

    public static String signature(PreparationView view) {
        if (view == null) return "null";
        return view.mode() + "|" + view.flowId() + "|" + view.roundId() + "|"
                + view.ownHandSubmitted() + "|" + view.canChooseFirst();
    }

    public static void disconnect(ScenarioBuilder s) {
        s.step("disconnect game while keeping test hub", ctx -> {
            ctx.mc().level.disconnect();
            ctx.mc().disconnect(new GenericMessageScreen(Component.literal("B1 disconnect")));
            ctx.mc().setScreen(new TitleScreen());
        }).waitUntil("private preparation state cleared", ctx -> DuelcraftClient.preparation().view() == null);
    }

    private static List<String> messages(TestContext ctx) {
        List<GuiMessage> messages = ctx.getField(ctx.mc().gui.getChat(), "allMessages");
        return messages.stream().map(message -> message.content().getString()).toList();
    }

    private static long count(List<String> messages, String prefix) {
        return messages.stream().filter(message -> message.startsWith(prefix)).count();
    }
}
