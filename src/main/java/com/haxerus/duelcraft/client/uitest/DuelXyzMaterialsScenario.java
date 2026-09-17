package com.haxerus.duelcraft.client.uitest;

import com.haxerus.duelcraft.client.LDLibDuelScreen;
import com.haxerus.duelcraft.core.DuelRule;
import com.haxerus.duelcraft.duel.message.DuelMessage;
import com.haxerus.duelcraft.duel.message.LocInfo;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.registry.RegistrationEnvironment;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegisterClient;
import com.lowdragmc.lowdraglib2.uitest.ElementBounds;
import com.lowdragmc.lowdraglib2.uitest.ScenarioBuilder;
import com.lowdragmc.lowdraglib2.uitest.ScenarioOptions;
import com.lowdragmc.lowdraglib2.uitest.TestContext;
import com.lowdragmc.lowdraglib2.uitest.UIScenario;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import static com.haxerus.duelcraft.core.OcgConstants.*;

/** Material edges stay beneath an unchanged host, inside its slot, and route clicks to that host. */
@OnlyIn(Dist.CLIENT)
@LDLRegisterClient(name = "duel_xyz_materials", group = "duelcraft", registry = UIScenario.REGISTRY,
        environment = RegistrationEnvironment.DEV_ONLY)
public final class DuelXyzMaterialsScenario implements UIScenario {
    private static final String[] SLOTS = {
            "#plr-mon-0", "#plr-mon-1", "#plr-mon-2", "#opp-mon-0", "#opp-mon-1", "#emz-left", "#emz-right"
    };

    @Override
    public void configure(ScenarioOptions options) {
        options.tags("duel").guiScale(3);
    }

    @Override
    public void define(ScenarioBuilder s) {
        s.openScreen("Xyz material stacks", ctx -> LDLibDuelScreen.create(DuelScreenFixture.startPayload(DuelRule.MR5)))
         .awaitModularUI()
         .step("hosts in attack, defense and both shared extra zones", ctx -> {
             DuelScreenFixture.populate(DuelRule.MR5);
             LDLibDuelScreen.applyMessage(new DuelMessage.PosChange(28406301, 0, LOCATION_MZONE, 2,
                     POS_FACEUP_ATTACK, POS_FACEUP_DEFENSE));
             LDLibDuelScreen.applyMessage(new DuelMessage.PosChange(33750025, 1, LOCATION_MZONE, 1,
                     POS_FACEUP_ATTACK, POS_FACEUP_DEFENSE));
             LDLibDuelScreen.applyMessage(new DuelMessage.Move(84013237, new LocInfo(1, 0, 0, 0),
                     new LocInfo(1, LOCATION_MZONE, 5, POS_FACEUP_DEFENSE), 0));
         }).ticks(5)
         .step("remember host and zone bounds before materials", ctx -> {
             for (String slot : SLOTS) {
                 ctx.put(slot, ctx.el(slot).bounds());
                 ctx.put(slot + "-host", ElementBounds.of(host(ctx, slot)));
             }
         })
         .step("one, several and unknown materials", ctx -> {
             attach(0, 1, 1);
             attach(0, 2, 3);
             attach(1, 0, 3);
             attach(1, 1, 1);
             attach(0, 5, 3);
             attach(1, 5, 3);
         }).ticks(5)
         .checkCount("#plr-mon-0 .xyz-material", 0)
         .checkCount("#plr-mon-1 .xyz-material", 1)
         .checkCount("#plr-mon-2 .xyz-material", 3)
         .checkCount("#opp-mon-0 .xyz-material", 3)
         .checkCount("#opp-mon-1 .xyz-material", 1)
         .checkCount("#emz-left .xyz-material", 3)
         .checkCount("#emz-right .xyz-material", 3)
         .checkCount("#plr-mon-2 .xyz-material.card-back", 1)
         .step("cards fit beneath unchanged hosts", ctx -> {
             for (String slot : SLOTS) {
                 unchanged(ctx, (ElementBounds) ctx.get(slot), ctx.el(slot).bounds(), slot);
                 unchanged(ctx, (ElementBounds) ctx.get(slot + "-host"), ElementBounds.of(host(ctx, slot)), slot + " host");
                 assertStack(ctx, slot);
             }
         }).screenshot("xyz-material-stacks")
         .step("click exposed material edge with a host action", ctx -> DuelScreenFixture.promptRepositionOf(0, 1))
         .ticks(2)
         .step("material click", ctx -> {
             var material = ctx.el("#plr-mon-1 .xyz-material").bounds();
             var host = ElementBounds.of(host(ctx, "#plr-mon-1"));
             float x = (material.x() + host.x()) / 2;
             ctx.input().mouseDown(x, host.centerY(), 0);
             ctx.input().mouseUp(x, host.centerY(), 0);
         }).frames(2).checkVisible("#context-menu").checkCount(".ctx-action", 1)
         // A host with actions reopens its menu on either mouse button; click an empty zone to dismiss.
         .click("#plr-st-2").frames(2).checkHidden("#context-menu")
         .step("detach only material and transfer another to zero-material host", ctx -> {
             LDLibDuelScreen.applyMessage(new DuelMessage.Waiting());
             LDLibDuelScreen.applyMessage(new DuelMessage.Move(89631139,
                     new LocInfo(0, LOCATION_MZONE | LOCATION_OVERLAY, 1, 0),
                     new LocInfo(0, LOCATION_GRAVE, 1, POS_FACEUP_ATTACK), 0));
             LDLibDuelScreen.applyMessage(new DuelMessage.Move(0,
                     new LocInfo(0, LOCATION_MZONE | LOCATION_OVERLAY, 2, 2),
                     new LocInfo(0, LOCATION_MZONE | LOCATION_OVERLAY, 0, 0), 0));
         }).ticks(5)
         .checkCount("#plr-mon-1 .xyz-material", 0)
         .checkCount("#plr-mon-2 .xyz-material", 2)
         .checkCount("#plr-mon-0 .xyz-material.card-back", 1)
         .step("refreshed stacks still fit", ctx -> {
             assertStack(ctx, "#plr-mon-0");
             assertStack(ctx, "#plr-mon-1");
             assertStack(ctx, "#plr-mon-2");
         }).screenshot("xyz-materials-detached-and-moved")
         .teardown("close", ctx -> {
             LDLibDuelScreen.close();
             ctx.mc().setScreen(null);
         });
    }

    private static void attach(int player, int sequence, int count) {
        for (int index = 0; index < count; index++) {
            int code = index == 2 ? 0 : index == 0 ? 89631139 : 33750025;
            LDLibDuelScreen.applyMessage(new DuelMessage.Move(code, new LocInfo(player, 0, 0, 0),
                    new LocInfo(player, LOCATION_MZONE | LOCATION_OVERLAY, sequence, index), 0));
        }
    }

    static void assertStack(TestContext ctx, String slot) {
        var host = host(ctx, slot);
        var hostBounds = ElementBounds.of(host);
        var children = ctx.el(slot).element().getChildren();
        ElementBounds previous = null;
        for (var ref : ctx.all(slot + " .xyz-material")) {
            var material = ref.bounds();
            DuelUiAssertions.contains(ctx, ctx.el(slot).bounds(), material, slot + " material inside slot");
            ctx.check(slot + " host covers material", children.indexOf(ref.element()) < children.indexOf(host));
            ctx.check(slot + " material preserves card size", Math.abs(material.width() - hostBounds.width()) <= 1
                    && Math.abs(material.height() - hostBounds.height()) <= 1);
            ctx.check(slot + " material has an exposed edge", material.center().distance(hostBounds.center()) > 0.25f);
            if (previous != null) {
                ctx.check(slot + " materials have distinct exposed edges", material.center().distance(previous.center()) > 0.1f);
            }
            // Comparing corner vectors checks 180/270-degree orientation as well as landscape bounds.
            var materialTop = material.corners()[1].sub(material.corners()[0], new org.joml.Vector2f());
            var hostTop = hostBounds.corners()[1].sub(hostBounds.corners()[0], new org.joml.Vector2f());
            ctx.check(slot + " material follows host orientation", materialTop.distance(hostTop) <= 1);
            previous = material;
        }
    }

    private static UIElement host(TestContext ctx, String slot) {
        return ctx.el(slot).element().getChildren().stream()
                .filter(c -> (c.hasClass("card") || c.hasClass("card-back")) && !c.hasClass("xyz-material"))
                .findFirst().orElseThrow();
    }

    private static void unchanged(TestContext ctx, ElementBounds before, ElementBounds after, String name) {
        ctx.check(name + " bounds unchanged", Math.abs(before.x() - after.x()) <= 1
                && Math.abs(before.y() - after.y()) <= 1 && Math.abs(before.width() - after.width()) <= 1
                && Math.abs(before.height() - after.height()) <= 1, before.toString(), after.toString());
    }
}
