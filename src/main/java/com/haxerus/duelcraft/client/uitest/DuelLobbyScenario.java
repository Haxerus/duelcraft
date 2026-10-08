package com.haxerus.duelcraft.client.uitest;

import com.haxerus.duelcraft.client.collection.ClientCollectionState;
import com.haxerus.duelcraft.client.interaction.*;
import com.haxerus.duelcraft.collection.CollectionReply;
import com.haxerus.duelcraft.core.*;
import com.haxerus.duelcraft.duel.*;
import com.haxerus.duelcraft.server.PreparationStatePayload;
import com.lowdragmc.lowdraglib2.registry.RegistrationEnvironment;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegisterClient;
import com.lowdragmc.lowdraglib2.uitest.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;

@LDLRegisterClient(name = "duel_lobby", group = "duelcraft", registry = UIScenario.REGISTRY, environment = RegistrationEnvironment.DEV_ONLY)
public final class DuelLobbyScenario implements UIScenario {
    private static final UUID FLOW = UUID.randomUUID(), ROUND = UUID.randomUUID(), OTHER = UUID.randomUUID();
    @Override public void configure(ScenarioOptions options) { options.requiresWorld(false); }
    @Override public void define(ScenarioBuilder s) {
        s.openScreen("idle lobby", ctx -> {
            DuelLobbyScreen.clearForm();
            var state = ctx.put("preparation", new ClientPreparationState(request -> {})); state.connect();
            state.receive(new PreparationStatePayload(null, PreparationResult.OK, view(0, PreparationView.Mode.IDLE)));
            var id = UUID.randomUUID();
            var collection = new ClientCollectionState.View(0, Map.of(), List.of(new CollectionReply.Summary(id, "Spell toolbox", 40, 5, 5)), id);
            var targets = new ArrayList<DuelLobbyScreen.Target>(); targets.add(new DuelLobbyScreen.Target(OTHER, "Other player"));
            for (int i=0; i<6; i++) targets.add(new DuelLobbyScreen.Target(UUID.randomUUID(), "Player " + i));
            return DuelLobbyScreen.create(state, collection, targets, command -> {
                ctx.put("command", command);
                return ctx.put("pending", new CompletableFuture<PreparationStatePayload>());
            }, route -> ctx.put("route", route));
        }).awaitModularUI().ticks(3)
          .checkBounds("#lobby-panel", DuelScreenScenario::insideViewport)
          .checkCount(".player-row", 7)
          .click("#lobby-player-" + OTHER).step("press #lobby-settings", ctx -> com.haxerus.duelcraft.client.uitest.ManagementTestInput.click(ctx, "#lobby-settings"))
          .step("open rule presets", ctx -> ManagementTestInput.click(ctx, "#settings-rule")).ticks(3)
          .step("identify real rule rows", ctx -> {
              var selector = ctx.el("#settings-rule").as(com.lowdragmc.lowdraglib2.gui.ui.elements.Selector.class);
              selector.scrollerView.setId("rule-options-scroll");
              selector.dialog.selfAndAllChildren()
                      .filter(e -> e instanceof com.lowdragmc.lowdraglib2.gui.ui.elements.Label)
                      .map(e -> (com.lowdragmc.lowdraglib2.gui.ui.elements.Label) e)
                      .forEach(label -> label.setId("rule-option-" + label.getText().getString()));
          }).check("rule text fits each row without overlapping click targets", ctx -> {
              for (var rule : DuelRule.values()) {
                  var label = ctx.el("#rule-option-" + rule).as(com.lowdragmc.lowdraglib2.gui.ui.elements.Label.class);
                  if (label.getSizeHeight() < label.getTextStyle().fontSize() + 4) return false;
                  for (var child : label.getChildren()) if (child.getSizeHeight() > label.getSizeHeight()) return false;
              }
              return true;
          }).check("at least five rule rows fit before scrolling", ctx -> {
              var viewport = ctx.el("#rule-options-scroll").as(com.lowdragmc.lowdraglib2.gui.ui.elements.ScrollerView.class).viewPort;
              var first = ctx.el("#rule-option-MR1").element();
              var fifth = ctx.el("#rule-option-MR4").element();
              return fifth.getPositionY() > first.getPositionY()
                      && fifth.getPositionY() + fifth.getSizeHeight() <= viewport.getPositionY() + viewport.getSizeHeight();
          }).screenshot("m5-rule-dropdown")
          .step("select middle rule by mouse", ctx -> ManagementTestInput.click(ctx, "#rule-option-MR3"))
          .check("mouse selected MR3 and closed dropdown", ctx -> {
              var selector = ctx.el("#settings-rule").as(com.lowdragmc.lowdraglib2.gui.ui.elements.Selector.class);
              return selector.getValue() == DuelRule.MR3 && !selector.isOpen();
          }).step("reopen presets", ctx -> ManagementTestInput.click(ctx, "#settings-rule"))
          .step("wheel to final preset", ctx -> {
              var point = ctx.el("#rule-options-scroll").bounds().center();
              // LDLib advances one notch per event, regardless of wheel-delta magnitude.
              for (int i = 0; i < 12; i++) ctx.input().scroll(point.x, point.y, -1);
          }).ticks(3).screenshot("m5-rule-dropdown-scrolled")
          .step("select last rule by mouse", ctx -> ManagementTestInput.click(ctx, "#rule-option-RUSH"))
          .check("last scrolled rule selected", ctx -> ctx.el("#settings-rule").as(com.lowdragmc.lowdraglib2.gui.ui.elements.Selector.class).getValue() == DuelRule.RUSH)
          .step("reopen for Escape", ctx -> ManagementTestInput.click(ctx, "#settings-rule"))
          .key(org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE)
          .check("Escape closes only the preset dropdown", ctx -> !ctx.el("#settings-rule").as(com.lowdragmc.lowdraglib2.gui.ui.elements.Selector.class).isOpen())
          .checkVisible("#lobby-settings-dialog")
          .step("invalid life points", ctx -> ctx.el("#settings-lp").as(com.lowdragmc.lowdraglib2.gui.ui.elements.TextField.class).setText("0", true))
          .checkTextContains("#settings-error", "Life points")
          .check("invalid settings disable Invite", ctx -> !ctx.el("#lobby-invite").isActive())
          .step("restore valid life points", ctx -> ctx.el("#settings-lp").as(com.lowdragmc.lowdraglib2.gui.ui.elements.TextField.class).setText("8000", true))
          .step("focus life points", ctx -> ctx.el("#settings-lp").element().focus())
          .key(org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE).checkHidden("#lobby-settings-dialog")
          .step("typing after Settings is closed", ctx -> ctx.screen().charTyped('j', 0))
          .checkText("#settings-lp", "8000")
          .step("press #lobby-invite", ctx -> com.haxerus.duelcraft.client.uitest.ManagementTestInput.click(ctx, "#lobby-invite"))
          .check("invite targets selected UUID", ctx -> ctx.get("command") instanceof PreparationCommand.Invite invite && invite.target().equals(OTHER))
          .check("pending invite disabled", ctx -> !ctx.el("#lobby-invite").element().isActive())
          .screenshot("m5-lobby-idle");
        for (var mode : List.of(PreparationView.Mode.INVITED, PreparationView.Mode.RPS, PreparationView.Mode.FIRST_CHOICE, PreparationView.Mode.STARTING)) {
            s.step("server state " + mode, ctx -> {
                var reply = new PreparationStatePayload(null, PreparationResult.OK, view(mode.ordinal()+1, mode));
                ctx.<ClientPreparationState>get("preparation").receive(reply);
                CompletableFuture<PreparationStatePayload> pending = ctx.get("pending");
                if (pending != null) pending.complete(reply);
            })
             .ticks(2).checkBounds("#lobby-panel", DuelScreenScenario::insideViewport).screenshot("m5-lobby-" + mode.name().toLowerCase());
            if (mode == PreparationView.Mode.RPS) s.step("press #lobby-rock", ctx -> com.haxerus.duelcraft.client.uitest.ManagementTestInput.click(ctx, "#lobby-rock"))
                    .check("Rock carries exact flow and round", ctx -> ctx.get("command").equals(new PreparationCommand.Hand(FLOW, ROUND, FirstTurnLobby.Hand.ROCK)))
                    .check("pending hand disables choices", ctx -> !ctx.el("#lobby-hands").isActive());
            if (mode == PreparationView.Mode.STARTING) s.checkHidden("#lobby-cancel").checkHidden("#lobby-select-deck");
        }
    }
    private static PreparationView view(long revision, PreparationView.Mode mode) {
        boolean active = mode != PreparationView.Mode.IDLE;
        return new PreparationView(revision, mode, active?FLOW:null, mode==PreparationView.Mode.RPS?ROUND:null,
                active?OTHER:null, active?"Other player":"", false, false, mode==PreparationView.Mode.FIRST_CHOICE,
                active&&mode!=PreparationView.Mode.STARTING?60000:0, active?new DuelSettings(DuelRule.MR5, 20, PlayerOptions.standard()):null);
    }
}
