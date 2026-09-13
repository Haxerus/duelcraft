package com.haxerus.duelcraft.client.uitest;

import com.haxerus.duelcraft.client.LDLibDuelScreen;
import com.haxerus.duelcraft.client.PromptController;
import com.haxerus.duelcraft.client.carddata.CardInfo;
import com.haxerus.duelcraft.core.DuelRule;
import com.haxerus.duelcraft.duel.message.DuelMessage;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField;
import com.lowdragmc.lowdraglib2.registry.RegistrationEnvironment;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegisterClient;
import com.lowdragmc.lowdraglib2.uitest.ScenarioBuilder;
import com.lowdragmc.lowdraglib2.uitest.ScenarioOptions;
import com.lowdragmc.lowdraglib2.uitest.UIScenario;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.stream.IntStream;

import static com.haxerus.duelcraft.client.uitest.DuelPromptLayoutScenario.capture;

/** Inject only external search data into the real controller; no downloaded database required. */
@OnlyIn(Dist.CLIENT)
@LDLRegisterClient(name = "duel_search_layout", group = "duelcraft", registry = UIScenario.REGISTRY,
        environment = RegistrationEnvironment.DEV_ONLY)
public final class DuelSearchLayoutScenario implements UIScenario {
    @Override
    public void configure(ScenarioOptions options) { options.tags("duel", "visual-audit"); }

    @Override
    public void define(ScenarioBuilder s) {
        s.openScreen("search layout", ctx -> LDLibDuelScreen.create(DuelScreenFixture.startPayload(DuelRule.MR5)))
         .awaitModularUI().step("install deterministic search data", ctx -> {
             PromptController prompt = ctx.getField(refresher(), "prompt");
             PromptController.Callbacks original = ctx.getField(prompt, "callbacks");
             ctx.put("prompt", prompt);
             ctx.put("callbacks", original);
             ctx.put("available", true);
             var rows = IntStream.rangeClosed(1, 50).mapToObj(i -> new CardInfo(i,
                     "Monster " + i + " with a deliberately long name that wraps across the result row",
                     "", 1, 0, 0, 1, 1, 1)).toList();
             var fixture = Proxy.newProxyInstance(PromptController.Callbacks.class.getClassLoader(),
                     new Class<?>[]{PromptController.Callbacks.class}, (proxy, method, args) -> switch (method.getName()) {
                         case "cardSearchAvailable" -> ctx.<Boolean>get("available");
                         case "searchDeclarable" -> ((String) args[0]).equals("missing") ? List.of() : rows;
                         default -> method.invoke(original, args);
                     });
             ctx.setField(prompt, "callbacks", fixture);
         }).step("search results", ctx -> LDLibDuelScreen.applyMessage(new DuelMessage.AnnounceCard(0, List.of(1L))));
        capture(s, "long-search-results");
        s.checkCount(".prompt-name-btn", 50)
         .step("remember first row", ctx -> ctx.put("rowY", ctx.all(".prompt-name-btn").getFirst().bounds().y()))
         .scroll(".prompt-name-scroller", -30).ticks(2)
         .check("search scrolls", ctx -> ctx.all(".prompt-name-btn").getFirst().bounds().y() < (float) ctx.get("rowY"));
        capture(s, "search-scrolled");
        s.step("empty search", ctx -> ctx.el("#announce-card-search").as(TextField.class).setValue("missing"));
        capture(s, "search-empty");
        s.checkCount(".prompt-name-btn", 0)
         .step("database unavailable", ctx -> {
             ctx.put("available", false);
             LDLibDuelScreen.applyMessage(new DuelMessage.AnnounceCard(0, List.of(1L)));
         });
        capture(s, "passcode-fallback");
        s.checkCount("#prompt-buttons .prompt-btn", 1)
         .checkVisible("#announce-card-search")
         .teardown("restore and close", ctx -> {
             if (ctx.get("prompt") != null) ctx.setField(ctx.get("prompt"), "callbacks", ctx.get("callbacks"));
             LDLibDuelScreen.close();
             ctx.mc().setScreen(null);
         });
    }

    private static Object refresher() {
        try {
            var field = LDLibDuelScreen.class.getDeclaredField("refresher");
            field.setAccessible(true);
            return field.get(null);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot install search fixture", e);
        }
    }
}
