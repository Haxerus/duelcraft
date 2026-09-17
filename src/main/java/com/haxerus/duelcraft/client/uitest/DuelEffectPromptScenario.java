package com.haxerus.duelcraft.client.uitest;

import com.haxerus.duelcraft.DuelcraftClient;
import com.haxerus.duelcraft.client.LDLibDuelScreen;
import com.haxerus.duelcraft.client.carddata.CardDatabase;
import com.haxerus.duelcraft.client.carddata.SystemStringTable;
import com.haxerus.duelcraft.core.DuelRule;
import com.haxerus.duelcraft.duel.message.DuelMessage;
import com.haxerus.duelcraft.duel.message.LocInfo;
import com.lowdragmc.lowdraglib2.registry.RegistrationEnvironment;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegisterClient;
import com.lowdragmc.lowdraglib2.uitest.ScenarioBuilder;
import com.lowdragmc.lowdraglib2.uitest.ScenarioOptions;
import com.lowdragmc.lowdraglib2.uitest.UIScenario;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;

import static com.haxerus.duelcraft.core.OcgConstants.*;

/** Reproduces legacy wide-string templates through the real effect prompt renderer. */
@LDLRegisterClient(name = "duel_effect_prompt", group = "duelcraft", registry = UIScenario.REGISTRY,
        environment = RegistrationEnvironment.DEV_ONLY)
public final class DuelEffectPromptScenario implements UIScenario {
    private Path directory;
    private CardDatabase database;
    private CardDatabase previousDatabase;
    private SystemStringTable previousStrings;
    private boolean installed;

    @Override
    public void configure(ScenarioOptions options) { options.tags("duel", "visual-audit").guiScale(3); }

    @Override
    public void define(ScenarioBuilder s) {
        s.openScreen("effect question formatting", ctx -> {
            install();
            return LDLibDuelScreen.create(DuelScreenFixture.startPayload(DuelRule.MR5));
        }).awaitModularUI();
        DuelPromptLayoutScenario.prompt(s, "trigger-effect", new DuelMessage.SelectEffectYn(
                0, 99000011, new LocInfo(0, LOCATION_HAND, 0, 0), 221));
        s.checkText("#prompt-title", "Activate the Trigger Effect of \"Dracotail Lukias\" from [Hand]?")
         .checkHidden("#prompt-source-title");
        DuelPromptLayoutScenario.prompt(s, "default-effect-field-spell", new DuelMessage.SelectEffectYn(
                0, 99000011, new LocInfo(0, LOCATION_SZONE, 5, POS_FACEUP_ATTACK), 0));
        s.checkText("#prompt-title", "Use the effect of \"Dracotail Lukias\" from [Field Spell Zone]?");
        DuelPromptLayoutScenario.prompt(s, "plain-effect-description", new DuelMessage.SelectEffectYn(
                0, 99000011, new LocInfo(0, LOCATION_MZONE, 0, POS_FACEUP_ATTACK), 42));
        s.checkText("#prompt-title", "Special Summon 1 monster.\n(Dracotail Lukias)");
        s.teardown("restore data and close", ctx -> {
            try { restore(); }
            catch (Exception e) { throw new IllegalStateException("Cannot restore effect-prompt data", e); }
            finally { LDLibDuelScreen.close(); ctx.mc().setScreen(null); }
        });
    }

    private void install() {
        try {
            directory = Files.createTempDirectory("duelcraft-effect-prompt-");
            Files.writeString(directory.resolve("strings.conf"), """
                    !system 200 Use the effect of "%ls" from [%ls]?
                    !system 221 Activate the Trigger Effect of "%ls" from [%ls]?
                    !system 223
                    !system 42 Special Summon 1 monster.
                    !system 1001 Hand
                    !system 1008 Field Spell Zone
                    """);
            var strings = new SystemStringTable("", directory);
            var path = directory.resolve("cards.cdb");
            try (var connection = DriverManager.getConnection("jdbc:sqlite:" + path);
                 var sql = connection.createStatement()) {
                sql.execute("CREATE TABLE datas (id INTEGER, type INTEGER, atk INTEGER, def INTEGER, level INTEGER, race INTEGER, attribute INTEGER)");
                sql.execute("CREATE TABLE texts (id INTEGER, name TEXT, desc TEXT)");
                sql.execute("INSERT INTO datas VALUES (99000011, 17, 1800, 1000, 4, 1, 16)");
                sql.execute("INSERT INTO texts VALUES (99000011, 'Dracotail Lukias', '')");
            }
            database = new CardDatabase(path);
            var dbField = DuelcraftClient.class.getDeclaredField("cardDatabase");
            var stringsField = DuelcraftClient.class.getDeclaredField("systemStringTable");
            dbField.setAccessible(true);
            stringsField.setAccessible(true);
            previousDatabase = (CardDatabase) dbField.get(null);
            previousStrings = (SystemStringTable) stringsField.get(null);
            dbField.set(null, database);
            installed = true;
            stringsField.set(null, strings);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot install effect-prompt fixture", e);
        }
    }

    private void restore() throws Exception {
        try {
            if (installed) {
                var dbField = DuelcraftClient.class.getDeclaredField("cardDatabase");
                var stringsField = DuelcraftClient.class.getDeclaredField("systemStringTable");
                dbField.setAccessible(true);
                stringsField.setAccessible(true);
                dbField.set(null, previousDatabase);
                stringsField.set(null, previousStrings);
            }
        } finally {
            if (database != null) database.close();
            if (directory != null) {
                Files.deleteIfExists(directory.resolve("cards.cdb"));
                Files.deleteIfExists(directory.resolve("strings.conf"));
                Files.deleteIfExists(directory);
            }
        }
    }
}
