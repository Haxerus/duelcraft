package com.haxerus.duelcraft.client.uitest;

import com.haxerus.duelcraft.client.LDLibDuelScreen;
import com.haxerus.duelcraft.core.DuelRule;
import com.haxerus.duelcraft.duel.message.DuelMessage;
import com.lowdragmc.lowdraglib2.registry.RegistrationEnvironment;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegisterClient;
import com.lowdragmc.lowdraglib2.uitest.ScenarioBuilder;
import com.lowdragmc.lowdraglib2.uitest.ScenarioOptions;
import com.lowdragmc.lowdraglib2.uitest.UIScenario;
import com.lowdragmc.lowdraglib2.uitest.input.Keys;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.List;

import static com.haxerus.duelcraft.core.OcgConstants.*;

/**
 * The three MSG_HINT surfaces: a HINT_SELECTMSG captioning the prompt that follows it, a
 * HINT_OPSELECTED toast, and a HINT_MESSAGE modal that stays up until its OK is clicked.
 */
@OnlyIn(Dist.CLIENT)
@LDLRegisterClient(name = "duel_hints", group = "duelcraft", registry = UIScenario.REGISTRY,
        environment = RegistrationEnvironment.DEV_ONLY)
public final class DuelHintsScenario implements UIScenario {

    @Override
    public void configure(ScenarioOptions options) {
        options.tags("duel").guiScale(3);
    }

    @Override
    public void define(ScenarioBuilder s) {
        s.openScreen("duel hints", ctx -> LDLibDuelScreen.create(DuelScreenFixture.startPayload(DuelRule.MR5)))
         .awaitModularUI()
         .step("populate field", ctx -> DuelScreenFixture.populate(DuelRule.MR5))
         .ticks(2)
         .checkHidden("#toast")
         .checkHidden("#hint-modal")
         // The caption carries the hint's own text plus edopro's "(min-max)" suffix, which the
         // hard-coded "Select 1 card(s)" default has not got.
         .step("hint why the next selection is happening",
                 ctx -> LDLibDuelScreen.applyMessage(new DuelMessage.Hint(HINT_SELECTMSG, 0, 501L)))
         .step("select one on-field monster",
                 ctx -> LDLibDuelScreen.applyMessage(new DuelMessage.SelectCard(0, false, 1, 1, List.of(
                         new DuelMessage.CardInfo(89631139, 0, LOCATION_MZONE, 0, POS_FACEUP_ATTACK)))))
         .ticks(2)
         .checkVisible("#status-label")
         .checkTextContains("#status-label", "(1-1)")
         .step("the opponent announces its choice",
                 ctx -> LDLibDuelScreen.applyMessage(new DuelMessage.Hint(HINT_OPSELECTED, 1, 501L)))
         .ticks(2)
         .checkVisible("#toast")
         .step("a script sends a message",
                 ctx -> LDLibDuelScreen.applyMessage(new DuelMessage.Hint(HINT_MESSAGE, 0, 1L)))
         .ticks(2)
         .checkVisible("#hint-modal")
         .checkVisible("#hint-modal-ok")
         .screenshot("duel_hints")
         // OK dismisses the modal on the press, so the builder's click() cannot re-resolve the
         // button for its release step; the press and release go to one set of bounds instead.
         .step("acknowledge the message", ctx -> {
             var ok = ctx.el("#hint-modal-ok").bounds();
             float x = ok.x() + ok.width() / 2f;
             float y = ok.y() + ok.height() / 2f;
             ctx.input().mouseDown(x, y, Keys.MOUSE_LEFT);
             ctx.input().mouseUp(x, y, Keys.MOUSE_LEFT);
         })
         .frames(2)
         .checkHidden("#hint-modal")
         .teardown("close", ctx -> {
             LDLibDuelScreen.close();
             ctx.mc().setScreen(null);
         });
    }
}
