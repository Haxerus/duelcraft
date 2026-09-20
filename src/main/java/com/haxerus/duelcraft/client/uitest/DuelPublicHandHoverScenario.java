package com.haxerus.duelcraft.client.uitest;

import com.haxerus.duelcraft.client.ClientDuelState;
import com.haxerus.duelcraft.client.LDLibDuelScreen;
import com.haxerus.duelcraft.core.DuelRule;
import com.haxerus.duelcraft.duel.message.DuelMessage;
import com.haxerus.duelcraft.duel.message.QueriedCard;
import com.lowdragmc.lowdraglib2.registry.RegistrationEnvironment;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegisterClient;
import com.lowdragmc.lowdraglib2.uitest.ScenarioBuilder;
import com.lowdragmc.lowdraglib2.uitest.ScenarioOptions;
import com.lowdragmc.lowdraglib2.uitest.UIScenario;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.List;

import static com.haxerus.duelcraft.core.OcgConstants.*;

/** Real pointer regression for revoking an inspector opened from a public opponent hand. */
@OnlyIn(Dist.CLIENT)
@LDLRegisterClient(name = "duel_public_hand_hover", group = "duelcraft", registry = UIScenario.REGISTRY,
        environment = RegistrationEnvironment.DEV_ONLY)
public final class DuelPublicHandHoverScenario implements UIScenario {
    private static final int CODE = 99000003; // Unknown fallback, independent of downloaded card data.

    @Override
    public void configure(ScenarioOptions options) { options.tags("duel", "visual-audit").guiScale(2); }

    @Override
    public void define(ScenarioBuilder s) {
        s.openScreen("public hand hover", ctx -> LDLibDuelScreen.create(DuelScreenFixture.startPayload(DuelRule.MR5)))
         .awaitModularUI().step("known opponent hand becomes public", ctx -> {
             LDLibDuelScreen.applyMessage(new DuelMessage.Draw(1,
                     List.of(new DuelMessage.DrawnCard(CODE, POS_FACEDOWN_DEFENSE))));
             publicHand(true);
         }).ticks(3).hover("#opponent-hand .card").ticks(3)
         .checkVisible("#card-info-banner").checkText("#card-name-label", "Card #" + CODE)
         .screenshot("stationary-public")
         .step("revoke while pointer stays on hand", ctx -> publicHand(false)).ticks(3)
         .screenshot("stationary-revoked").checkHidden("#card-info-banner")
         .check("public-off retains remembered identity", ctx -> state().hand[1].getFirst().code == CODE)
         .hover("#hud-bar").step("public effect returns", ctx -> publicHand(true)).ticks(3)
         .hover("#opponent-hand .card").ticks(3).checkVisible("#card-info-banner")
         .hover("#card-text-scroll").waitMs(700)
         .check("pointer reached inspector descendant", ctx -> ctx.el("#card-info-banner").element().isSelfOrChildHover())
         .checkVisible("#card-info-banner").screenshot("inspector-public")
         .step("revoke while pointer remains in inspector", ctx -> publicHand(false)).ticks(3)
         .screenshot("inspector-revoked").checkHidden("#card-info-banner")
         .check("inspector revocation keeps known code", ctx -> state().hand[1].getFirst().code == CODE)
         .teardown("close", ctx -> { LDLibDuelScreen.close(); ctx.mc().setScreen(null); });
    }

    private static void publicHand(boolean visible) {
        var query = new QueriedCard();
        query.flags = QUERY_IS_PUBLIC;
        query.isPublic = visible;
        LDLibDuelScreen.applyMessage(new DuelMessage.UpdateCard(1, LOCATION_HAND, 0, query));
    }

    private static ClientDuelState state() {
        try {
            var field = LDLibDuelScreen.class.getDeclaredField("activeState");
            field.setAccessible(true);
            return (ClientDuelState) field.get(null);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }
}
