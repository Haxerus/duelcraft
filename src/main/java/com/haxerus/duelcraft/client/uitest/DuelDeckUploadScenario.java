package com.haxerus.duelcraft.client.uitest;

import com.haxerus.duelcraft.server.DuelManager;
import com.lowdragmc.lowdraglib2.registry.RegistrationEnvironment;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegisterClient;
import com.lowdragmc.lowdraglib2.uitest.ScenarioBuilder;
import com.lowdragmc.lowdraglib2.uitest.ScenarioOptions;
import com.lowdragmc.lowdraglib2.uitest.UIScenario;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.ClientCommandHandler;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/** Exercises registered client commands and the real upload packet against the integrated server. */
@OnlyIn(Dist.CLIENT)
@LDLRegisterClient(name = "duel_deck_upload", group = "duelcraft", registry = UIScenario.REGISTRY,
        environment = RegistrationEnvironment.DEV_ONLY)
public final class DuelDeckUploadScenario implements UIScenario {
    @Override
    public void configure(ScenarioOptions options) { options.tags("duel", "network"); }

    @Override
    public void define(ScenarioBuilder s) {
        s.step("write unique local deck", ctx -> {
            try {
                var dir = ctx.mc().gameDirectory.toPath().resolve("duelcraft/decks");
                Files.createDirectories(dir);
                var file = Files.createTempFile(dir, "ui-deck local-", ".ydk");
                ctx.put("uploadFile", file);
                String name = file.getFileName().toString();
                ctx.put("uploadName", name.substring(0, name.length() - 4));
                Files.writeString(file, "#main\n" + IntStream.range(1000, 1040).mapToObj(String::valueOf)
                        .collect(Collectors.joining("\n")) + "\n#extra\n9000\n!side\n");
            } catch (IOException e) { throw new UncheckedIOException(e); }
        }).step("local command suggestions include spaced names", ctx -> {
            var dispatcher = ClientCommandHandler.getDispatcher();
            String expected = StringArgumentType.escapeIfRequired(ctx.get("uploadName"));
            for (String prefix : new String[]{"duel deck set ui-deck", "duel deck set \"ui-deck"}) {
                var result = dispatcher.getCompletionSuggestions(dispatcher.parse(prefix, ClientCommandHandler.getSource())).join();
                ctx.check("local completion for " + prefix, result.getList().stream().anyMatch(x -> x.getText().equals(expected)));
            }
        }).step("select via registered client command", ctx -> {
            ctx.check("list handled locally", ClientCommandHandler.runCommand("duel deck list"));
            ctx.check("set handled locally", ClientCommandHandler.runCommand("duel deck set "
                    + StringArgumentType.escapeIfRequired(ctx.get("uploadName"))));
        }).waitUntilServer("upload accepted for sending player", ctx -> DuelManager.get()
                .getPlayerCurrentDeck(ctx.player().getUUID()).filter(ctx.get("uploadName")::equals).isPresent())
          .step("remove local file after upload", ctx -> {
              try { Files.delete(ctx.get("uploadFile")); }
              catch (IOException e) { throw new UncheckedIOException(e); }
          }).server("server retains uploaded cards without a file", ctx -> {
              try {
                  var deck = DuelManager.get().resolveDeck(ctx.player());
                  ctx.check("main deck contents received", deck.main().equals(IntStream.range(1000, 1040).boxed().toList()));
                  ctx.check("extra deck contents received", deck.extra().equals(java.util.List.of(9000)));
              } catch (IOException e) { throw new UncheckedIOException(e); }
          }).step("clear falls through to the server", ctx -> {
              ctx.check("get is a server command", !ClientCommandHandler.runCommand("duel deck get"));
              ctx.mc().player.connection.sendCommand("duel deck clear");
          }).waitUntilServer("selection cleared", ctx -> DuelManager.get().getPlayerCurrentDeck(ctx.player().getUUID()).isEmpty())
          .teardown("remove fixture file", ctx -> {
              Path file = ctx.get("uploadFile");
              try { if (file != null) Files.deleteIfExists(file); }
              catch (IOException e) { throw new UncheckedIOException(e); }
          });
    }
}
