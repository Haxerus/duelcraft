package com.haxerus.duelcraft.client.uitest;

import com.haxerus.duelcraft.client.LDLibDuelScreen;
import com.haxerus.duelcraft.client.DuelScreen;
import com.haxerus.duelcraft.core.DuelRule;
import com.haxerus.duelcraft.duel.message.DuelMessage;
import com.lowdragmc.lowdraglib2.registry.RegistrationEnvironment;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegisterClient;
import com.lowdragmc.lowdraglib2.uitest.ScenarioBuilder;
import com.lowdragmc.lowdraglib2.uitest.ScenarioOptions;
import com.lowdragmc.lowdraglib2.uitest.UIScenario;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.lwjgl.glfw.GLFW;

import java.util.List;

import static com.haxerus.duelcraft.core.OcgConstants.*;
import static com.haxerus.duelcraft.client.uitest.DuelPanelLayoutScenario.press;
import static com.haxerus.duelcraft.client.uitest.DuelPromptLayoutScenario.capture;

/** Real input regression coverage for inspecting a pending prompt and optional chain skipping. */
@OnlyIn(Dist.CLIENT)
@LDLRegisterClient(name = "duel_prompt_controls", group = "duelcraft", registry = UIScenario.REGISTRY,
        environment = RegistrationEnvironment.DEV_ONLY)
public final class DuelPromptControlsScenario implements UIScenario {
    @Override
    public void configure(ScenarioOptions options) { options.tags("duel", "visual-audit"); }

    @Override
    public void define(ScenarioBuilder s) {
        s.openScreen("prompt controls", ctx -> LDLibDuelScreen.create(DuelScreenFixture.startPayload(DuelRule.MR5)))
         .awaitModularUI().step("populate", ctx -> DuelScreenFixture.populate(DuelRule.MR5)).ticks(2)
         .checkText("#chain-toggle-btn", "Chain: ON").checkHidden("#prompt-toggle-btn")
         .step("choose two races", ctx -> LDLibDuelScreen.applyMessage(new DuelMessage.AnnounceRace(0, 2, 0x7f)))
         .ticks(2).click("#announce-bit-0").ticks(2).checkTextContains("#prompt-body", "Select 1");
        capture(s, "prompt-visible");
        press(s, "#prompt-toggle-btn");
        s.checkHidden("#prompt-overlay").checkText("#prompt-toggle-btn", "Show Prompt")
         .hover("#plr-mon-0 .card").ticks(2).checkVisible("#card-info-banner")
         .click("#plr-graveyard").ticks(2).checkVisible("#zone-inspector")
         .step("RMB cannot answer a hidden prompt", ctx -> {
             var b = ctx.el("#player-hand").bounds();
             ctx.input().mouseDown(b.centerX(), b.centerY(), 1);
             ctx.input().mouseUp(b.centerX(), b.centerY(), 1);
         });
        capture(s, "inspect-field");
        press(s, "#prompt-toggle-btn");
        s.checkVisible("#prompt-overlay").checkTextContains("#prompt-body", "Select 1");
        capture(s, "selection-restored");
        press(s, "#prompt-toggle-btn");
        s.step("new prompt is shown automatically", ctx -> LDLibDuelScreen.applyMessage(new DuelMessage.SelectYesNo(0, 1)))
         .ticks(2).checkVisible("#prompt-overlay").checkText("#prompt-toggle-btn", "Hide Prompt");

        s.step("mixed field and deck selection", ctx -> LDLibDuelScreen.applyMessage(new DuelMessage.SelectCard(0, true, 1, 2,
                List.of(new DuelMessage.CardInfo(89631139, 0, LOCATION_MZONE, 0, 1),
                        new DuelMessage.CardInfo(0, 0, LOCATION_DECK, 0, 1)))))
         .ticks(2).step("select first candidate", ctx -> {
             var b = ctx.all("#prompt-body .card").getFirst().bounds();
             ctx.input().mouseDown(b.centerX(), b.centerY(), 0);
             ctx.input().mouseUp(b.centerX(), b.centerY(), 0);
         }).ticks(2).checkText("#prompt-dialog-action-btn", "Finish");
        press(s, "#prompt-toggle-btn");
        s.click("#plr-mon-0 .card").ticks(2)
         .step("RMB cannot finish the hidden selection", ctx -> {
             var b = ctx.el("#player-hand").bounds();
             ctx.input().mouseDown(b.centerX(), b.centerY(), 1);
             ctx.input().mouseUp(b.centerX(), b.centerY(), 1);
         }).ticks(2).checkText("#prompt-toggle-btn", "Show Prompt");
        press(s, "#prompt-toggle-btn");
        s.checkVisible("#prompt-overlay").checkCount("#prompt-body .target", 1)
         .checkText("#prompt-dialog-action-btn", "Finish");
        press(s, "#prompt-toggle-btn");
        s.step("retry shows prompt", ctx -> LDLibDuelScreen.applyMessage(new DuelMessage.Retry()))
         .ticks(2).checkVisible("#prompt-overlay");

        chain(s, false);
        s.checkVisible("#prompt-overlay");
        press(s, "#chain-toggle-btn");
        s.checkText("#chain-toggle-btn", "Chain: OFF").checkHidden("#prompt-overlay").checkHidden("#prompt-toggle-btn");
        chain(s, false);
        s.checkHidden("#prompt-overlay");
        chain(s, true);
        s.checkVisible("#prompt-overlay");
        capture(s, "forced-chain-off");
        // Synthetic input deliberately runs without taking desktop focus. Held shortcuts need
        // the active-window precondition; the explicit lost-focus check below still exercises it.
        s.step("active window for synthetic held shortcuts", ctx -> {
            boolean active = ctx.mc().isWindowActive();
            ctx.put("window-active-before-shortcuts", active);
            ctx.log("Window active before shortcut fixture: " + active);
            ctx.mc().setWindowActive(true);
        });
        s.keyDown(GLFW.GLFW_KEY_C).ticks(2).checkVisible("#prompt-overlay")
         .keyUp(GLFW.GLFW_KEY_C).ticks(2).checkText("#chain-toggle-btn", "Chain: OFF");
        s.step("other prompts are never skipped", ctx -> LDLibDuelScreen.applyMessage(new DuelMessage.SelectYesNo(0, 1)))
         .ticks(2).checkVisible("#prompt-overlay");
        press(s, "#chain-toggle-btn");
        chain(s, false);
        s.keyDown(GLFW.GLFW_KEY_C).ticks(2).checkText("#chain-toggle-btn", "OFF (hold C)").checkHidden("#prompt-overlay");
        chain(s, false);
        s.checkHidden("#prompt-overlay");
        s.hover("#hud-bar");
        capture(s, "chain-hold");
        s.keyUp(GLFW.GLFW_KEY_C).ticks(2).checkText("#chain-toggle-btn", "Chain: ON");
        chain(s, false);
        s.checkVisible("#prompt-overlay");
        s.step("RMB still passes an optional chain", ctx -> {
            var b = ctx.el("#prompt-title").bounds();
            ctx.input().mouseDown(b.centerX(), b.centerY(), 1);
            ctx.input().mouseUp(b.centerX(), b.centerY(), 1);
        }).ticks(2).checkHidden("#prompt-overlay");

        s.step("card search", ctx -> LDLibDuelScreen.applyMessage(new DuelMessage.AnnounceCard(0, List.of())))
         .ticks(2).click("#announce-card-search").keyDown(GLFW.GLFW_KEY_C).type("c").ticks(2)
         .checkText("#chain-toggle-btn", "Chain: ON")
         .check("C reaches the text field", ctx -> ctx.el("#announce-card-search").as(TextField.class).getValue().contains("c"))
         .keyUp(GLFW.GLFW_KEY_C);
        capture(s, "typing-c");
        press(s, "#prompt-toggle-btn");
        s.keyDown(GLFW.GLFW_KEY_C).ticks(2).checkText("#chain-toggle-btn", "OFF (hold C)")
         .step("lost window focus clears the hold", ctx -> {
             ctx.mc().setWindowActive(false);
             try { ctx.mc().screen.tick(); }
             finally { ctx.mc().setWindowActive(true); }
         }).ticks(2).checkText("#chain-toggle-btn", "Chain: ON").keyUp(GLFW.GLFW_KEY_C);
        s.keyDown(GLFW.GLFW_KEY_C).ticks(2).checkText("#chain-toggle-btn", "OFF (hold C)")
         .step("remove screen without releasing C", ctx -> {
             ctx.put("oldScreen", ctx.mc().screen);
             ctx.mc().setScreen(null);
         }).step("restore same screen", ctx -> ctx.mc().setScreen((DuelScreen) ctx.get("oldScreen")))
         .ticks(2).checkText("#chain-toggle-btn", "Chain: ON").keyUp(GLFW.GLFW_KEY_C);
        press(s, "#chain-toggle-btn");
        s.step("reopen duel", ctx -> ctx.mc().setScreen(LDLibDuelScreen.reopen()))
         .awaitModularUI().ticks(2).checkText("#chain-toggle-btn", "Chain: OFF");
        press(s, "#chain-toggle-btn");
        s.step("Ctrl+C is not a chain shortcut", ctx -> ctx.input().keyDown(GLFW.GLFW_KEY_C, GLFW.GLFW_MOD_CONTROL))
         .ticks(2).checkText("#chain-toggle-btn", "Chain: ON").keyUp(GLFW.GLFW_KEY_C);
        press(s, "#prompt-toggle-btn");
        s.keyDown(GLFW.GLFW_KEY_C).ticks(2).checkText("#chain-toggle-btn", "OFF (hold C)")
         .step("modifier after C clears the hold", ctx -> ctx.input().keyDown(GLFW.GLFW_KEY_LEFT_CONTROL, GLFW.GLFW_MOD_CONTROL))
         .ticks(2).checkText("#chain-toggle-btn", "Chain: ON").keyUp(GLFW.GLFW_KEY_C).keyUp(GLFW.GLFW_KEY_LEFT_CONTROL);
        s.step("new duel resets preference", ctx -> ctx.mc().setScreen(LDLibDuelScreen.create(DuelScreenFixture.startPayload(DuelRule.MR5))))
         .awaitModularUI().ticks(2).checkText("#chain-toggle-btn", "Chain: ON");
        s.teardown("close", ctx -> {
            try { LDLibDuelScreen.close(); ctx.mc().setScreen(null); }
            finally {
                Boolean active = ctx.get("window-active-before-shortcuts");
                if (active != null) ctx.mc().setWindowActive(active);
            }
        });
    }

    private static void chain(ScenarioBuilder s, boolean forced) {
        s.step(forced ? "forced chain" : "optional chain", ctx -> LDLibDuelScreen.applyMessage(
                new DuelMessage.SelectChain(0, 0, forced, 0, 0, List.of(
                        new DuelMessage.ActivatableCard(89631139, 0, LOCATION_MZONE, 0, 1, 1, 0)))))
         .ticks(2);
    }
}
