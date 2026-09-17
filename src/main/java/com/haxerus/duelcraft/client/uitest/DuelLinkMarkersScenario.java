package com.haxerus.duelcraft.client.uitest;

import com.haxerus.duelcraft.DuelcraftClient;
import com.haxerus.duelcraft.client.LDLibDuelScreen;
import com.haxerus.duelcraft.client.carddata.CardDatabase;
import com.haxerus.duelcraft.core.DuelRule;
import com.haxerus.duelcraft.duel.message.DuelMessage;
import com.haxerus.duelcraft.duel.message.LocInfo;
import com.haxerus.duelcraft.duel.message.QueriedCard;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextElement;
import com.lowdragmc.lowdraglib2.registry.RegistrationEnvironment;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegisterClient;
import com.lowdragmc.lowdraglib2.uitest.ScenarioBuilder;
import com.lowdragmc.lowdraglib2.uitest.ScenarioOptions;
import com.lowdragmc.lowdraglib2.uitest.TestContext;
import com.lowdragmc.lowdraglib2.uitest.UIScenario;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;

import static com.haxerus.duelcraft.core.OcgConstants.*;

/** Real inspector rendering with a tiny local database, independent of downloaded card data. */
@OnlyIn(Dist.CLIENT)
@LDLRegisterClient(name = "duel_link_markers", group = "duelcraft", registry = UIScenario.REGISTRY,
        environment = RegistrationEnvironment.DEV_ONLY)
public final class DuelLinkMarkersScenario implements UIScenario {
    private CardDatabase previousDatabase;
    private CardDatabase fixtureDatabase;
    private Path databasePath;

    @Override
    public void configure(ScenarioOptions options) { options.tags("duel").guiScale(3); }

    @Override
    public void define(ScenarioBuilder s) {
        s.openScreen("Link markers", ctx -> LDLibDuelScreen.create(DuelScreenFixture.startPayload(DuelRule.MR5)))
         .awaitModularUI().step("synthetic database and cards", ctx -> {
             installDatabase();
             for (int seq = 0; seq < 4; seq++) {
                 LDLibDuelScreen.applyMessage(new DuelMessage.Move(seq == 3 ? 0 : 99000001 + seq,
                         new LocInfo(0, 0, 0, 0),
                         new LocInfo(0, LOCATION_MZONE, seq, seq == 3 ? POS_FACEDOWN_DEFENSE : POS_FACEUP_ATTACK), 0));
             }
             var stats = new QueriedCard();
             stats.flags = QUERY_ATTACK | QUERY_DEFENSE | QUERY_LEVEL;
             stats.attack = 3000;
             stats.defense = 2500;
             stats.level = 12;
             LDLibDuelScreen.applyMessage(new DuelMessage.UpdateCard(0, LOCATION_MZONE, 1, stats));
         }).ticks(2)
         .checkText("#plr-mon-1 .stat-level", "★12")
         .hover("#plr-mon-3 .card-back").ticks(2).checkHidden("#card-info-banner")
         .hover("#plr-mon-0 .card").ticks(2)
         .checkText("#card-name-label", "Marker fixture")
         .step("all eight grid directions active", ctx -> checkGrid(ctx, 495))
         .step("all eight arrows and inspector containment", DuelUiAssertions::audit)
         .screenshot("all-link-directions")
         .hover("#plr-mon-1 .card").ticks(2).checkHidden("#card-link-markers")
         .step("live marker override", ctx -> updateMarkers(LINK_MARKER_TOP_LEFT | LINK_MARKER_RIGHT | LINK_MARKER_BOTTOM))
         .ticks(2).hover("#plr-mon-0 .card").ticks(2)
         .step("sparse live grid overrides printed markers", ctx -> checkGrid(ctx,
                 LINK_MARKER_TOP_LEFT | LINK_MARKER_RIGHT | LINK_MARKER_BOTTOM))
         .screenshot("sparse-live-link-directions")
         .hover("#plr-mon-1 .card").ticks(2)
         .step("live zero marker mask", ctx -> updateMarkers(0))
         .ticks(2).hover("#plr-mon-0 .card").ticks(2)
         .step("zero live mask leaves all eight directions gray", ctx -> checkGrid(ctx, 0))
         .screenshot("all-inactive-link-directions")
         .hover("#plr-mon-2 .card").ticks(2).checkHidden("#card-link-markers")
         .checkText("#card-name-label", "Card #99000003")
         .screenshot("unknown-card-clears-markers")
         .teardown("restore database and close", ctx -> {
             restoreDatabase();
             LDLibDuelScreen.close();
             ctx.mc().setScreen(null);
         });
    }

    private static void checkGrid(TestContext ctx, int markers) {
        var grid = ctx.el("#card-link-markers");
        ctx.check("Link grid is visible", grid.isVisible());
        ctx.check("eight arrow cells", ctx.all(".link-marker").size() == 8);
        ctx.check("compact square grid", Math.abs(grid.bounds().width() - grid.bounds().height()) <= 1
                && grid.element().getSizeWidth() >= 36 && grid.element().getSizeWidth() <= 48);
        if (ctx.all(".link-marker").size() != 8) return;
        var directions = new int[]{LINK_MARKER_TOP_LEFT, LINK_MARKER_TOP, LINK_MARKER_TOP_RIGHT,
                LINK_MARKER_LEFT, LINK_MARKER_RIGHT, LINK_MARKER_BOTTOM_LEFT, LINK_MARKER_BOTTOM, LINK_MARKER_BOTTOM_RIGHT};
        var names = new String[]{"tl", "t", "tr", "l", "r", "bl", "b", "br"};
        var arrows = new String[]{"↖", "↑", "↗", "←", "→", "↙", "↓", "↘"};
        int[] columns = {0, 1, 2, 0, 2, 0, 1, 2};
        int[] rows = {0, 0, 0, 1, 1, 2, 2, 2};
        float cell = grid.bounds().width() / 3;
        for (int i = 0; i < directions.length; i++) {
            var arrow = ctx.el("#card-link-" + names[i]);
            var text = arrow.as(TextElement.class);
            ctx.check("direction glyph " + names[i], text.getText().getString().equals(arrows[i]));
            ctx.check("direction position " + names[i],
                    Math.abs(arrow.bounds().centerX() - (grid.bounds().x() + (columns[i] + .5f) * cell)) <= 1
                    && Math.abs(arrow.bounds().centerY() - (grid.bounds().y() + (rows[i] + .5f) * cell)) <= 1);
            int expectedColor = (markers & directions[i]) != 0 ? 0xFF5555 : 0x777777;
            ctx.check("direction color " + names[i], (text.getTextStyle().textColor() & 0xFFFFFF) == expectedColor);
        }
        var center = ctx.el("#card-link-center");
        ctx.check("center is blank", center.element().getChildren().isEmpty());
        ctx.check("center occupies middle cell", Math.abs(center.bounds().centerX() - grid.bounds().centerX()) <= 1
                && Math.abs(center.bounds().centerY() - grid.bounds().centerY()) <= 1);
        DuelUiAssertions.audit(ctx);
    }

    private static void updateMarkers(int markers) {
        var query = new QueriedCard();
        query.flags = QUERY_TYPE | QUERY_LINK;
        query.type = TYPE_MONSTER | TYPE_LINK;
        query.linkRating = 8;
        query.linkMarker = markers;
        LDLibDuelScreen.applyMessage(new DuelMessage.UpdateCard(0, LOCATION_MZONE, 0, query));
    }

    private void installDatabase() {
        try {
            databasePath = Files.createTempFile("duelcraft-link-markers-", ".cdb");
            Class.forName("org.sqlite.JDBC");
            try (var connection = DriverManager.getConnection("jdbc:sqlite:" + databasePath);
                 var sql = connection.createStatement()) {
                sql.execute("CREATE TABLE datas (id INTEGER, type INTEGER, atk INTEGER, def INTEGER, level INTEGER, race INTEGER, attribute INTEGER)");
                sql.execute("CREATE TABLE texts (id INTEGER, name TEXT, desc TEXT)");
                sql.execute("INSERT INTO datas VALUES (99000001, " + (TYPE_MONSTER | TYPE_LINK) + ", 2300, 495, 8, 16777216, 32), (99000002, 17, 1000, 1000, 4, 1, 16)");
                sql.execute("INSERT INTO texts VALUES (99000001, 'Marker fixture', 'All eight Link directions.'), (99000002, 'Normal fixture', '')");
            }
            fixtureDatabase = new CardDatabase(databasePath);
            var field = DuelcraftClient.class.getDeclaredField("cardDatabase");
            field.setAccessible(true);
            previousDatabase = (CardDatabase) field.get(null);
            field.set(null, fixtureDatabase);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot install Link-marker fixture database", e);
        }
    }

    private void restoreDatabase() {
        try {
            if (fixtureDatabase != null) {
                var field = DuelcraftClient.class.getDeclaredField("cardDatabase");
                field.setAccessible(true);
                field.set(null, previousDatabase);
                fixtureDatabase.close();
            }
            if (databasePath != null) Files.deleteIfExists(databasePath);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot restore card database", e);
        }
    }
}
