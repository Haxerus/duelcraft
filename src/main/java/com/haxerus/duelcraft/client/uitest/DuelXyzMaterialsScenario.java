package com.haxerus.duelcraft.client.uitest;

import com.haxerus.duelcraft.client.LDLibDuelScreen;
import com.haxerus.duelcraft.core.DuelRule;
import com.haxerus.duelcraft.duel.message.DuelMessage;
import com.haxerus.duelcraft.duel.message.LocInfo;
import com.haxerus.duelcraft.duel.message.QueriedCard;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.texture.SpriteTexture;
import com.lowdragmc.lowdraglib2.registry.RegistrationEnvironment;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegisterClient;
import com.lowdragmc.lowdraglib2.uitest.ElementBounds;
import com.lowdragmc.lowdraglib2.uitest.ScenarioBuilder;
import com.lowdragmc.lowdraglib2.uitest.ScenarioOptions;
import com.lowdragmc.lowdraglib2.uitest.TestContext;
import com.lowdragmc.lowdraglib2.uitest.UIScenario;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

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
             ctx.put("art", new DuelVisibilityFixture());
             ctx.<DuelVisibilityFixture>get("art").captureResponses();
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
             attachDecoratedMaterial(0, 2);
             attachAt(0, 2, 1, 33750025);
             attachAt(0, 2, 2, 0);
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
         .step("zero-material host has no read-only action", ctx -> clickHost(ctx, "#plr-mon-0"))
         .frames(2).checkHidden("#context-menu")
         .step("open normal browsing menu for own host", ctx -> clickHost(ctx, "#plr-mon-2"))
         .frames(2).checkVisible("#context-menu").checkCount(".ctx-action", 1)
         .step("open own materials", ctx -> clickContextAction(ctx, 0))
         .ticks(3).checkVisible("#zone-inspector")
         .checkText("#zone-inspector-title", "Materials (3)")
         .checkCount("#zone-inspector-list .material-card", 3)
         .step("own materials keep engine order and recipient identities", ctx -> {
             materialArtwork(ctx, 0, 89631139);
             materialArtwork(ctx, 1, 33750025);
             materialArtwork(ctx, 2, 0);
         })
         .step("attached material retains its old on-field query and hint state", ctx -> {
             var material = DuelVisibilityFixture.state()
                     .cardAt(0, LOCATION_MZONE | LOCATION_OVERLAY, 2, 0);
             ctx.check("material retained query state", material != null && material.stats != null
                     && material.stats.attack == 3000 && material.stats.defense == 2500);
             ctx.check("material retained on-field hint", material != null
                     && material.hintType == CHINT_TURN && material.hintValue == 12);
         })
         .step("hover known material", ctx -> hoverMaterial(ctx, 0)).ticks(2)
         .checkVisible("#card-info-banner")
         .checkHidden("#card-hints")
         .checkHidden("#card-link-markers")
         .step("close while known material owns details", ctx -> clickElement(ctx, "#zone-inspector-close"))
         .frames(2).checkHidden("#card-info-banner")
         .step("reopen own materials", ctx -> clickHost(ctx, "#plr-mon-2"))
         .frames(2).step("choose own materials again", ctx -> clickContextAction(ctx, 0))
         .ticks(2)
         .step("hover unknown material", ctx -> hoverMaterial(ctx, 2)).ticks(12)
         .checkHidden("#card-info-banner")
         .check("opening materials sends no response", ctx ->
                 ctx.<DuelVisibilityFixture>get("art").responses.isEmpty())
         .screenshot("own-material-inspector")
         .step("close own materials", ctx -> clickElement(ctx, "#zone-inspector-close")).ticks(2)
         .check("closing materials sends no response", ctx ->
                 ctx.<DuelVisibilityFixture>get("art").responses.isEmpty())
         .step("open opponent host materials", ctx -> clickHost(ctx, "#opp-mon-0"))
         .frames(2).checkCount(".ctx-action", 1)
         .step("choose opponent materials", ctx -> clickContextAction(ctx, 0))
         .ticks(3).checkText("#zone-inspector-title", "Materials (3)")
         .step("opponent material privacy is unchanged", ctx -> {
             materialArtwork(ctx, 0, 89631139);
             materialArtwork(ctx, 1, 33750025);
             materialArtwork(ctx, 2, 0);
         })
         .step("hover opponent known material", ctx -> hoverMaterial(ctx, 0)).ticks(2)
         .checkVisible("#card-info-banner")
         .step("recipient query redacts hovered material without moving the pointer", ctx ->
                 overlayQuery(1, 0, 0, 33750025, 0))
         .ticks(3).checkHidden("#card-info-banner")
         .step("redacted hovered material becomes an anonymous back", ctx -> materialArtwork(ctx, 0, 0))
         .screenshot("opponent-material-inspector")
         .step("show unrelated event details", ctx -> LDLibDuelScreen.applyMessage(
                 new DuelMessage.Hint(HINT_CARD, 0, 99000004)))
         .ticks(1).checkVisible("#card-info-banner").checkText("#card-name-label", "Card #99000004")
         .step("refresh with no material-owned details", ctx -> overlayQuery(1, 0, 0, 0, 0))
         .ticks(1).checkVisible("#card-info-banner").checkText("#card-name-label", "Card #99000004")
         .step("close does not clear unrelated details", ctx -> clickElement(ctx, "#zone-inspector-close"))
         .frames(2).checkVisible("#card-info-banner").checkText("#card-name-label", "Card #99000004")
         .step("move away after unrelated-details control", ctx -> ctx.input().moveTo(1, 1)).ticks(12)
         .checkHidden("#card-info-banner")
         .step("click exposed material edge with a host action", ctx -> DuelScreenFixture.promptRepositionOf(0, 1))
         .ticks(2)
         .step("material click", ctx -> {
             var material = ctx.el("#plr-mon-1 .xyz-material").bounds();
             var host = ElementBounds.of(host(ctx, "#plr-mon-1"));
             float x = (material.x() + host.x()) / 2;
             ctx.input().mouseDown(x, host.centerY(), 0);
             ctx.input().mouseUp(x, host.centerY(), 0);
         }).frames(2).checkVisible("#context-menu").checkCount(".ctx-action", 2)
         .step("material action stays separate from the original engine action", ctx ->
                 clickContextAction(ctx, 1))
         .ticks(2).checkText("#zone-inspector-title", "Materials (1)")
         .check("read-only action sends no engine choice", ctx ->
                 ctx.<DuelVisibilityFixture>get("art").responses.isEmpty())
         .step("close exposed-edge materials", ctx -> clickElement(ctx, "#zone-inspector-close")).ticks(2)
         .step("summon and activate keep their engine menu entries", ctx ->
                 LDLibDuelScreen.applyMessage(summonAndActivate(0, 2)))
         .ticks(2).step("open combined host menu", ctx -> clickHost(ctx, "#plr-mon-2"))
         .frames(2).checkCount(".ctx-action", 3)
         .step("combined menu stays inside the duel canvas", ctx ->
                 DuelUiAssertions.contains(ctx, ctx.el("#duel-canvas").bounds(),
                         ctx.el("#context-menu").bounds(), "materials context menu"))
         .screenshot("materials-context-menu")
         // A host with actions reopens its menu on either mouse button; click an empty zone to dismiss.
         .click("#plr-st-2").frames(2).checkHidden("#context-menu")
         .step("mandatory field selection retains click priority", ctx ->
                 LDLibDuelScreen.applyMessage(mandatoryFieldSelection()))
         .ticks(2).step("select material host", ctx -> clickHost(ctx, "#plr-mon-2"))
         .ticks(2).checkHidden("#context-menu")
         .check("mandatory click selects instead of inspecting", ctx ->
                 ctx.el("#plr-mon-2").element().hasClass("selected"))
         .check("mandatory selection has not auto-sent", ctx ->
                 ctx.<DuelVisibilityFixture>get("art").responses.isEmpty())
         .step("enter explicit field inspection", ctx ->
                 LDLibDuelScreen.applyMessage(new DuelMessage.SelectYesNo(0, 1L)))
         .ticks(2).click("#prompt-toggle-btn").ticks(2)
         .checkHidden("#prompt-overlay").checkText("#prompt-toggle-btn", "Show Prompt")
         .step("open materials while explicit inspection owns field clicks", ctx ->
                 clickHost(ctx, "#plr-mon-2"))
         .frames(2).checkCount(".ctx-action", 1)
         .step("choose inspection-mode materials", ctx -> clickContextAction(ctx, 0))
         .ticks(2).checkVisible("#zone-inspector")
         .checkHidden("#prompt-overlay").checkText("#prompt-toggle-btn", "Show Prompt")
         .check("explicit inspection keeps the prompt and sends no response", ctx ->
                 DuelVisibilityFixture.state().pendingPrompt instanceof DuelMessage.SelectYesNo
                         && ctx.<DuelVisibilityFixture>get("art").responses.isEmpty())
         .screenshot("materials-during-explicit-inspection")
         .step("close inspection-mode materials", ctx -> clickElement(ctx, "#zone-inspector-close")).ticks(2)
         .click("#prompt-toggle-btn").ticks(2).checkVisible("#prompt-overlay")
         .step("reopen changing host by object identity", ctx -> {
             LDLibDuelScreen.applyMessage(new DuelMessage.SelectIdleCmd(0,
                     List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                     true, true, false));
             ctx.put("inspected-host", DuelVisibilityFixture.state().cardAt(0, LOCATION_MZONE, 2));
         }).ticks(2).checkHidden("#prompt-overlay")
         .step("open changing host menu", ctx -> clickHost(ctx, "#plr-mon-2"))
         .frames(2).checkVisible("#context-menu").checkCount(".ctx-action", 1)
         .step("choose changing host materials", ctx -> clickContextAction(ctx, 0))
         .ticks(2).checkCount("#zone-inspector-list .material-card", 3)
         .step("detach first material", ctx -> LDLibDuelScreen.applyMessage(new DuelMessage.Move(89631139,
                 new LocInfo(0, LOCATION_MZONE | LOCATION_OVERLAY, 2, 0),
                 new LocInfo(0, LOCATION_GRAVE, 1, POS_FACEUP_ATTACK), 0)))
         .ticks(3).checkText("#zone-inspector-title", "Materials (2)")
         .step("detach refreshes current order", ctx -> {
             materialArtwork(ctx, 0, 33750025);
             materialArtwork(ctx, 1, 0);
         })
         .step("move unknown material to the front", ctx -> LDLibDuelScreen.applyMessage(new DuelMessage.Move(0,
                 new LocInfo(0, LOCATION_MZONE | LOCATION_OVERLAY, 2, 1),
                 new LocInfo(0, LOCATION_MZONE | LOCATION_OVERLAY, 2, 0), 0)))
         .ticks(3).step("reorder refreshes in engine order", ctx -> {
             materialArtwork(ctx, 0, 0);
             materialArtwork(ctx, 1, 33750025);
         })
         .step("host changes control while inspector remains associated", ctx ->
                 LDLibDuelScreen.applyMessage(new DuelMessage.Move(28406301,
                         new LocInfo(0, LOCATION_MZONE, 2, POS_FACEUP_DEFENSE),
                         new LocInfo(1, LOCATION_MZONE, 6, POS_FACEUP_DEFENSE), 0)))
         .ticks(3).checkVisible("#zone-inspector")
         .checkCount("#zone-inspector-list .material-card", 2)
         .check("control change retains the actual inspected host", ctx ->
                 DuelVisibilityFixture.state().cardAt(1, LOCATION_MZONE, 6) == ctx.get("inspected-host"))
         .screenshot("materials-after-detach-reorder-control-change")
         .step("hover known material before host departure", ctx -> hoverMaterial(ctx, 1)).ticks(2)
         .checkVisible("#card-info-banner")
         .step("host leaves the field", ctx -> LDLibDuelScreen.applyMessage(new DuelMessage.Move(28406301,
                 new LocInfo(1, LOCATION_MZONE, 6, POS_FACEUP_DEFENSE),
                 new LocInfo(1, LOCATION_GRAVE, 1, POS_FACEUP_ATTACK), 0)))
         .ticks(3).checkHidden("#zone-inspector")
         .checkHidden("#card-info-banner")
         .check("host departure and all inspection gestures send no response", ctx ->
                 ctx.<DuelVisibilityFixture>get("art").responses.isEmpty())
         .teardown("close", ctx -> {
             LDLibDuelScreen.close();
             ctx.mc().setScreen(null);
             ctx.<DuelVisibilityFixture>get("art").close();
         });
    }

    private static void attach(int player, int sequence, int count) {
        for (int index = 0; index < count; index++) {
            int code = index == 2 ? 0 : index == 0 ? 89631139 : 33750025;
            attachAt(player, sequence, index, code);
        }
    }

    private static void attachDecoratedMaterial(int player, int hostSequence) {
        int code = 89631139;
        var staging = new LocInfo(player, LOCATION_SZONE, 2, POS_FACEUP_ATTACK);
        LDLibDuelScreen.applyMessage(new DuelMessage.Move(code, new LocInfo(player, 0, 0, 0), staging, 0));
        var stats = new QueriedCard();
        stats.flags = QUERY_ATTACK | QUERY_DEFENSE;
        stats.attack = 3000;
        stats.defense = 2500;
        LDLibDuelScreen.applyMessage(new DuelMessage.UpdateCard(player, LOCATION_SZONE, 2, stats));
        LDLibDuelScreen.applyMessage(new DuelMessage.CardHint(staging, CHINT_TURN, 12));
        LDLibDuelScreen.applyMessage(new DuelMessage.Move(code, staging,
                new LocInfo(player, LOCATION_MZONE | LOCATION_OVERLAY, hostSequence, 0), 0));
    }

    private static void attachAt(int player, int hostSequence, int materialIndex, int code) {
        LDLibDuelScreen.applyMessage(new DuelMessage.Move(code, new LocInfo(player, 0, 0, 0),
                new LocInfo(player, LOCATION_MZONE | LOCATION_OVERLAY, hostSequence, materialIndex), 0));
    }

    private static DuelMessage.SelectIdleCmd summonAndActivate(int player, int sequence) {
        int code = 28406301;
        return new DuelMessage.SelectIdleCmd(player,
                List.of(new DuelMessage.IdleCmdCard(code, player, LOCATION_MZONE, sequence)),
                List.of(), List.of(), List.of(), List.of(),
                List.of(new DuelMessage.ActivatableCard(code, player, LOCATION_MZONE, sequence, 0, 1L, 0)),
                true, true, false);
    }

    private static DuelMessage.SelectCard mandatoryFieldSelection() {
        return new DuelMessage.SelectCard(0, false, 1, 2, List.of(
                new DuelMessage.CardInfo(28406301, 0, LOCATION_MZONE, 2, POS_FACEUP_DEFENSE),
                new DuelMessage.CardInfo(55415564, 0, LOCATION_MZONE, 3, POS_FACEUP_ATTACK)));
    }

    private static void overlayQuery(int player, int sequence, int... codes) {
        var query = new QueriedCard();
        query.flags = QUERY_OVERLAY_CARD;
        query.overlayCards = java.util.Arrays.stream(codes).boxed().toList();
        LDLibDuelScreen.applyMessage(new DuelMessage.UpdateCard(player, LOCATION_MZONE, sequence, query));
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

    private static void clickHost(TestContext ctx, String slot) {
        var bounds = ElementBounds.of(host(ctx, slot));
        ctx.input().mouseDown(bounds.centerX(), bounds.centerY(), 0);
        ctx.input().mouseUp(bounds.centerX(), bounds.centerY(), 0);
    }

    private static void clickContextAction(TestContext ctx, int index) {
        var bounds = ctx.all(".ctx-action").get(index).bounds();
        ctx.input().mouseDown(bounds.centerX(), bounds.centerY(), 0);
        ctx.input().mouseUp(bounds.centerX(), bounds.centerY(), 0);
    }

    private static void clickElement(TestContext ctx, String selector) {
        var bounds = ctx.el(selector).bounds();
        ctx.input().mouseDown(bounds.centerX(), bounds.centerY(), 0);
        ctx.input().mouseUp(bounds.centerX(), bounds.centerY(), 0);
    }

    private static void hoverMaterial(TestContext ctx, int index) {
        var bounds = ctx.all("#zone-inspector-list .material-card").get(index).bounds();
        ctx.input().moveTo(bounds.centerX(), bounds.centerY());
    }

    private static void materialArtwork(TestContext ctx, int index, int code) {
        var texture = ctx.all("#zone-inspector-list .material-card").get(index).element()
                .getStyle().backgroundTexture().getRawTexture();
        ResourceLocation expected = code == 0
                ? ResourceLocation.parse("duelcraft:textures/card_back.png")
                : DuelVisibilityFixture.texture(code);
        ctx.check("material " + index + " artwork identity " + code,
                texture instanceof SpriteTexture sprite && sprite.getImageLocation().equals(expected),
                expected, texture);
    }

    private static void unchanged(TestContext ctx, ElementBounds before, ElementBounds after, String name) {
        ctx.check(name + " bounds unchanged", Math.abs(before.x() - after.x()) <= 1
                && Math.abs(before.y() - after.y()) <= 1 && Math.abs(before.width() - after.width()) <= 1
                && Math.abs(before.height() - after.height()) <= 1, before.toString(), after.toString());
    }
}
