package com.haxerus.duelcraft.client.uitest;

import com.haxerus.duelcraft.client.collection.CollectionCardGrid;
import com.haxerus.duelcraft.client.collection.ClientCollectionState;
import com.haxerus.duelcraft.client.collection.CollectionScreen;
import com.haxerus.duelcraft.client.collection.DeckEditorModel;
import com.haxerus.duelcraft.client.collection.SavedDeckController;
import com.haxerus.duelcraft.collection.CollectionCommand;
import com.haxerus.duelcraft.collection.CollectionError;
import com.haxerus.duelcraft.collection.CollectionReply;
import com.haxerus.duelcraft.collection.DeckEligibility;
import com.haxerus.duelcraft.collection.DeckList;
import com.haxerus.duelcraft.collection.DeckListLoader;
import com.haxerus.duelcraft.client.collection.DeckExportService;
import com.haxerus.duelcraft.collection.SavedDeck;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Selector;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField;
import com.lowdragmc.lowdraglib2.registry.RegistrationEnvironment;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegisterClient;
import com.lowdragmc.lowdraglib2.uitest.ScenarioBuilder;
import com.lowdragmc.lowdraglib2.uitest.ScenarioOptions;
import com.lowdragmc.lowdraglib2.uitest.TestContext;
import com.lowdragmc.lowdraglib2.uitest.UIScenario;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;
import java.util.function.Supplier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.IOException;
import java.io.UncheckedIOException;

/** Real pointer coverage for collection double-click and exact deck-tile removal. */
@OnlyIn(Dist.CLIENT)
@LDLRegisterClient(name = "collection_editor_qa", group = "duelcraft", registry = UIScenario.REGISTRY,
        environment = RegistrationEnvironment.DEV_ONLY)
public final class CollectionEditorQAScenario implements UIScenario {
    private static final int MAIN = 10003;
    private static final int OTHER = 10002;
    private static final UUID ACTIVE = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID SELECTED = UUID.fromString("00000000-0000-0000-0000-0000000000b2");
    private static final String LONG_NAME = "Explicit Save keeps this deliberately long current deck name stable while typing 0123456789";

    @Override public void configure(ScenarioOptions options) {
        options.tags("collection", "pointer").requiresWorld(false).guiScale(2);
    }

    @Override public void define(ScenarioBuilder s) {
        s.step("set 1280x720 pointer viewport", ctx -> {
            var window = ctx.mc().getWindow();
            ctx.put("originalWidth", window.getWidth());
            ctx.put("originalHeight", window.getHeight());
            GLFW.glfwSetWindowSize(window.getWindow(), 1280, 720);
        }).waitUntil("1280x720 pointer viewport applied", ctx -> ctx.mc().getWindow().getWidth() == 1280
                && ctx.mc().getWindow().getHeight() == 720)
          .openScreen("collection pointer editing", ctx -> {
              var model = ctx.put("model", new DeckEditorModel(empty(), CollectionFixture.owned()));
              ctx.put("attempts", new ArrayList<CompletableFuture<SavedDeck>>());
              return CollectionScreen.create(model, CollectionFixture.cards(), code -> null,
                      CollectionFixture.LIST_ID, (name, draft) -> {
                          var submitted = new SavedDeck(CollectionFixture.LIST_ID, name, draft);
                          ctx.put("submitted", submitted);
                          var pending = new CompletableFuture<SavedDeck>();
                          ctx.<ArrayList<CompletableFuture<SavedDeck>>>get("attempts").add(pending);
                          return pending;
                      }, CollectionFixture.query());
          }).awaitModularUI().ticks(3)
          .step("record actual pointer viewport", ctx -> ctx.attach("pointer viewport",
                  "%dx%d physical, GUI scale %.1f, %dx%d scaled".formatted(
                          ctx.mc().getWindow().getWidth(), ctx.mc().getWindow().getHeight(),
                          ctx.mc().getWindow().getGuiScale(), ctx.mc().getWindow().getGuiScaledWidth(),
                          ctx.mc().getWindow().getGuiScaledHeight())))
          .check("actual viewport and GUI scale are active", ctx -> ctx.mc().getWindow().getWidth() == 1280
                  && ctx.mc().getWindow().getHeight() == 720 && ctx.mc().getWindow().getGuiScale() == 2)
          .check("collection rows are virtualized", ctx -> {
              var grid = grid(ctx);
              return grid.getItemCount() == 20 && grid.getMountedItemCount() > 0
                      && grid.getMountedItemCount() < grid.getItemCount();
          });

        leftPress(s, "single inspect", "#collection-card-" + MAIN);
        s.check("single left click only inspects", ctx -> model(ctx).draft().equals(empty())
                && ctx.el("#inspector-name").text().equals("Cinder Dragon"))
         .ticks(7);

        doubleLeft(s, "first Main double click", "#collection-card-" + MAIN);
        s.check("two left clicks add exactly one Main copy", ctx ->
                model(ctx).draft().equals(new DeckList(List.of(MAIN), List.of(), List.of())));

        s.step("filter to the interleaving card", ctx -> ctx.el("#collection-search").as(TextField.class)
                    .setText(String.valueOf(OTHER), true)).ticks(2)
         .checkExists("#collection-card-" + OTHER);
        doubleLeft(s, "filtered Main double click", "#collection-card-" + OTHER);
        s.check("filter refresh double click adds once", ctx ->
                model(ctx).draft().equals(new DeckList(List.of(MAIN, OTHER), List.of(), List.of())));

        s.step("filter back to the repeated card", ctx -> ctx.el("#collection-search").as(TextField.class)
                    .setText(String.valueOf(MAIN), true)).ticks(2)
         .checkExists("#collection-card-" + MAIN);
        doubleLeft(s, "second Main double click", "#collection-card-" + MAIN);
        s.check("second double click adds one more copy", ctx ->
                model(ctx).draft().equals(new DeckList(List.of(MAIN, OTHER, MAIN), List.of(), List.of())))
         .click("#add-card").ticks(2)
         .check("button route adds the third aggregate copy", ctx ->
                 model(ctx).draft().equals(new DeckList(List.of(MAIN, OTHER, MAIN, MAIN), List.of(), List.of())))
         .check("shared cap disables the Add button", ctx -> !ctx.el("#add-card").isActive());
        doubleLeft(s, "rejected fourth Main copy", "#collection-card-" + MAIN);
        s.check("fourth copy is rejected without a false success", ctx ->
                 model(ctx).draft().equals(new DeckList(List.of(MAIN, OTHER, MAIN, MAIN), List.of(), List.of()))
                         && ctx.el("#editor-status").text().contains("Three copies"))
         .step("filter to a different inspector identity", ctx -> ctx.el("#collection-search").as(TextField.class)
                 .setText(String.valueOf(OTHER), true)).ticks(2);
        leftPress(s, "preselect different identity", "#collection-card-" + OTHER);
        s.check("different identity is selected before deck removal", ctx ->
                ctx.el("#inspector-name").text().equals("Beacon Spell"));

        rightPress(s, "exact repeated tile", "#main-card-2");
        s.check("right press removes the rendered index only", ctx ->
                model(ctx).draft().equals(new DeckList(List.of(MAIN, OTHER, MAIN), List.of(), List.of())))
         .step("release exact repeated tile", CollectionEditorQAScenario::rightRelease)
         .check("right release does not remove again or fire left selection", ctx ->
                 model(ctx).draft().equals(new DeckList(List.of(MAIN, OTHER, MAIN), List.of(), List.of()))
                         && ctx.el("#inspector-name").text().equals("Beacon Spell"));

        s.step("restore all virtual rows", ctx -> ctx.el("#collection-search").as(TextField.class).setText("", true))
         .ticks(2)
         .step("scroll virtual rows to the Extra card", ctx -> grid(ctx).verticalScroller.setNormalizedValue(1, true))
         .ticks(2)
         .checkExists("#collection-card-10080");
        doubleLeft(s, "virtualized Extra double click", "#collection-card-10080");
        s.check("Deck mode routes Extra type to Extra", ctx -> model(ctx).draft().extra().equals(List.of(10080)));

        s.step("filter to Side sample", ctx -> ctx.el("#collection-search").as(TextField.class).setText("10004", true))
         .ticks(2).click("#section-side").ticks(2);
        doubleLeft(s, "Side double click", "#collection-card-10004");
        s.check("Side mode routes pointer addition to Side", ctx -> model(ctx).draft().side().equals(List.of(10004)));

        s.step("make mounted deck tiles stale", ctx -> {
            ctx.put("staleMain", ctx.el("#main-card-0").element());
            ctx.put("beforeStaleClick", model(ctx).draft());
            ctx.check("stale setup mutation succeeds", model(ctx).add(DeckEditorModel.Section.SIDE, OTHER));
        }).check("stale tile remains mounted after the draft changes", ctx ->
                ctx.el("#main-card-0").element() == ctx.get("staleMain"));
        rightPress(s, "stale rendered tile", "#main-card-0");
        s.step("release stale rendered tile", CollectionEditorQAScenario::rightRelease)
         .check("stale rendered callback cannot mutate the newer draft", ctx -> {
             DeckList before = ctx.get("beforeStaleClick");
             var expected = new DeckList(before.main(), before.extra(), List.of(10004, OTHER));
             return model(ctx).draft().equals(expected);
         })
         .click("#sort-deck").ticks(2)
         .step("begin delayed Save", ctx -> CollectionLayoutScenario.press(ctx, "#save-deck"))
         .step("release delayed Save", CollectionLayoutScenario::release).ticks(2)
         .check("pending Save freezes all edit controls", ctx -> !ctx.el("#add-card").isActive()
                 && !ctx.el("#remove-card").isActive() && !ctx.el("#sort-deck").isActive());

        s.step("filter while Save is pending", ctx -> ctx.el("#collection-search").as(TextField.class)
                    .setText(String.valueOf(OTHER), true)).ticks(2)
         .step("remember frozen draft", ctx -> ctx.put("frozenDraft", model(ctx).draft()));
        doubleLeft(s, "pending collection double click", "#collection-card-" + OTHER);
        s.check("pending double click leaves the frozen draft unchanged", ctx ->
                model(ctx).draft().equals(ctx.get("frozenDraft")));
        rightPress(s, "pending deck tile", "#main-card-0");
        s.step("release pending deck tile", CollectionEditorQAScenario::rightRelease)
         .check("pending pointer gestures leave the frozen draft unchanged", ctx ->
                 model(ctx).draft().equals(ctx.get("frozenDraft")))
         .step("fail delayed Save", ctx -> ctx.<ArrayList<CompletableFuture<SavedDeck>>>get("attempts")
                 .getLast().completeExceptionally(new IllegalStateException("delayed save failed"))).ticks(2)
         .check("failed delayed Save retains the dirty editor", ctx -> ctx.screen() instanceof CollectionScreen
                 && model(ctx).dirty() && ctx.el("#sort-deck").isActive()
                 && ctx.el("#editor-status").text().contains("delayed save failed"))
         .step("press visible Close with dirty draft", ctx -> CollectionLayoutScenario.press(ctx, "#close-editor"))
         .step("release visible Close with dirty draft", CollectionLayoutScenario::release).ticks(2)
         .checkVisible("#close-dialog")
         .keyDown(GLFW.GLFW_KEY_ESCAPE).ticks(2)
         .check("Escape keeps editing and cancels queued dirty navigation", ctx ->
                 ctx.screen() instanceof CollectionScreen && !ctx.el("#close-dialog").isVisible()
                         && model(ctx).dirty())
         .keyUp(GLFW.GLFW_KEY_ESCAPE)
         .step("retry delayed Save", ctx -> CollectionLayoutScenario.press(ctx, "#save-deck"))
         .step("release delayed Save retry", CollectionLayoutScenario::release).ticks(2)
         .check("retry remains pending until acknowledgement", ctx ->
                 ctx.<ArrayList<CompletableFuture<SavedDeck>>>get("attempts").size() == 2
                         && !ctx.el("#sort-deck").isActive())
         .step("acknowledge delayed Save", ctx -> ctx.<ArrayList<CompletableFuture<SavedDeck>>>get("attempts")
                 .getLast().complete(ctx.get("submitted"))).ticks(2)
         .check("Save acknowledgement unfreezes and cleans the draft", ctx ->
                 ctx.el("#sort-deck").isActive() && !model(ctx).dirty())
         .step("open My decks", ctx -> CollectionLayoutScenario.press(ctx, "#saved-lists"))
         .step("release My decks", CollectionLayoutScenario::release).ticks(2)
         .check("sample has no saved rows to delete", ctx -> !ctx.el("#list-delete").isActive())
         .keyDown(GLFW.GLFW_KEY_ESCAPE).ticks(2)
         .check("Escape dismisses only My decks", ctx -> !ctx.el("#lists-dialog").isVisible()
                 && ctx.screen() instanceof CollectionScreen)
         .keyUp(GLFW.GLFW_KEY_ESCAPE)
         .hoverAt(-100, -100).screenshot("collection-editor-pointer-qa")
         .keyDown(GLFW.GLFW_KEY_ESCAPE).ticks(2)
         .check("next Escape closes the clean editor", ctx -> !(ctx.screen() instanceof CollectionScreen))
          .keyUp(GLFW.GLFW_KEY_ESCAPE)
          .step("write disposable explicit-Save import fixture", ctx -> {
              String name = "collection-editor-c3-" + UUID.randomUUID();
              Path file = ctx.mc().gameDirectory.toPath().resolve("duelcraft/decks/" + name + ".ydk");
              try {
                  Files.createDirectories(file.getParent());
                  Files.writeString(file, "#main\n10003\n10002\n#extra\n10080\n!side\n10004\n");
              } catch (IOException error) { throw new UncheckedIOException(error); }
              ctx.put("c3ImportName", name);
              ctx.put("c3ImportFile", file);
          })
          .openScreen("explicit Save and My decks", CollectionEditorQAScenario::commandScreen)
          .awaitModularUI().ticks(3)
          .checkText("#editor-title", "My Collection")
          .check("current name is a primary visible header field", ctx ->
                  ctx.el("#list-name").isVisible() && !ctx.el("#lists-dialog").isVisible()
                          && ctx.el("#list-name").bounds().y() < ctx.el("#editor-body").bounds().y())
          .check("Active status and Use for duels are separate header controls", ctx ->
                  ctx.el("#list-active-status").isVisible() && ctx.el("#activate-deck").isVisible()
                          && ctx.el("#activate-deck").bounds().y() < ctx.el("#editor-body").bounds().y())
          .step("open My decks for saved selection", ctx -> CollectionLayoutScenario.press(ctx, "#saved-lists"))
          .step("release My decks for saved selection", CollectionLayoutScenario::release).ticks(2)
          .checkVisible("#saved-list-scroll")
          .check("My decks starts with zero persistence or activation requests", ctx ->
                  count(ctx, CollectionCommand.Save.class) == 0 && count(ctx, CollectionCommand.Activate.class) == 0)
          .step("identify different saved list row", ctx -> {
              Map<UUID, com.lowdragmc.lowdraglib2.gui.ui.elements.Button> buttons =
                      ctx.getField(ctx.getField(ctx.screen(), "controller"), "savedRows");
              ctx.check("saved list view exposes many saved lists", buttons.size() == 12);
              buttons.get(SELECTED).setId("c3-selected-list");
          })
          .step("choose different saved list", ctx -> CollectionLayoutScenario.press(ctx, "#c3-selected-list"))
          .step("release different saved list", CollectionLayoutScenario::release).ticks(2)
          .check("selection alone keeps the active draft", ctx -> lists(ctx).id().equals(ACTIVE))
          .step("open selected saved list", ctx -> CollectionLayoutScenario.press(ctx, "#list-open"))
          .step("release Open", CollectionLayoutScenario::release).ticks(2)
          .check("Open changes the current saved UUID", ctx -> lists(ctx).id().equals(SELECTED))
          .checkText("#list-name", "Selected fixture")
          .typeInto("#list-name", LONG_NAME).ticks(2)
          .check("typing keeps current-name focus and exact value", ctx -> {
              var field = ctx.el("#list-name").as(TextField.class);
              return field.isFocused() && LONG_NAME.equals(field.getValue());
          })
          .step("select card for explicit Save edit", ctx -> CollectionLayoutScenario.press(ctx, "#collection-card-" + MAIN))
          .step("release card for explicit Save edit", CollectionLayoutScenario::release).ticks(2)
          .step("add card for explicit Save edit", ctx -> CollectionLayoutScenario.press(ctx, "#add-card"))
          .step("release add for explicit Save edit", CollectionLayoutScenario::release).ticks(2)
          .step("submit explicit Save", ctx -> CollectionLayoutScenario.press(ctx, "#save-deck"))
          .step("release explicit Save", CollectionLayoutScenario::release).ticks(2)
          .check("Save sends exactly one Save and zero Activate requests with current identity and content", ctx -> {
              var saves = ctx.<ArrayList<CollectionCommand>>get("c3Commands").stream()
                      .filter(CollectionCommand.Save.class::isInstance).map(CollectionCommand.Save.class::cast).toList();
              return saves.size() == 1 && count(ctx, CollectionCommand.Activate.class) == 0
                      && saves.getFirst().deck().id().equals(SELECTED)
                      && saves.getFirst().deck().name().equals(LONG_NAME)
                      && saves.getFirst().deck().cards().main().equals(List.of(OTHER, MAIN));
          })
          .step("reject explicit Save", ctx -> nextMutation(ctx).complete(new CollectionReply.Rejected(
                  CollectionError.INVALID, 4, emptyEligibility()))).ticks(2)
          .check("failed Save keeps the dirty draft and current editor", ctx ->
                  model(ctx).dirty() && lists(ctx).id().equals(SELECTED)
                          && ctx.el("#editor-status").text().contains("retained"))
          .step("retry explicit Save", ctx -> CollectionLayoutScenario.press(ctx, "#save-deck"))
          .step("release explicit Save retry", CollectionLayoutScenario::release).ticks(2)
          .step("acknowledge explicit Save", ctx -> {
              var save = ctx.<ArrayList<CollectionCommand>>get("c3Commands").stream()
                      .filter(CollectionCommand.Save.class::isInstance).map(CollectionCommand.Save.class::cast)
                      .reduce((first, second) -> second).orElseThrow();
              nextMutation(ctx).complete(new CollectionReply.Changed(5, ACTIVE, save.deck(), 0, emptyEligibility()));
          }).ticks(2)
          .check("successful Save updates the baseline without changing the active ID", ctx ->
                  !model(ctx).dirty() && lists(ctx).stored() && lists(ctx).id().equals(SELECTED)
                          && lists(ctx).activeId().equals(ACTIVE) && count(ctx, CollectionCommand.Activate.class) == 0)
          .step("request Use for duels", ctx -> CollectionLayoutScenario.press(ctx, "#activate-deck"))
          .step("release Use for duels", CollectionLayoutScenario::release).ticks(2)
          .check("Use for duels is the sole explicit activation request", ctx ->
                  count(ctx, CollectionCommand.Activate.class) == 1)
          .step("reject explicit activation with policy detail", ctx -> nextMutation(ctx).complete(
                  new CollectionReply.Rejected(CollectionError.INELIGIBLE, 5,
                          new DeckEligibility.Report(List.of(), Map.of(), false, true,
                                  "fixture policy restriction")))).ticks(2)
          .check("activation failure retains the list and explanatory feedback", ctx ->
                  lists(ctx).id().equals(SELECTED) && !model(ctx).dirty()
                          && ctx.el("#editor-status").text().contains("fixture policy restriction"))
          .step("dismiss activation details", ctx -> CollectionLayoutScenario.press(ctx, "#eligibility-close"))
          .step("release activation details", CollectionLayoutScenario::release).ticks(2)
          .step("open My decks for draft actions", ctx -> CollectionLayoutScenario.press(ctx, "#saved-lists"))
          .step("release My decks for draft actions", CollectionLayoutScenario::release).ticks(2)
          .step("close My decks before New", ctx -> CollectionLayoutScenario.press(ctx, "#lists-close"))
          .step("release My decks", CollectionLayoutScenario::release).ticks(2)
          .step("create New draft", ctx -> CollectionLayoutScenario.press(ctx, "#new-deck"))
          .step("release New draft", CollectionLayoutScenario::release).ticks(2)
          .step("capture New identity", ctx -> ctx.put("c3NewId", lists(ctx).id()))
          .step("open My decks for selected copy", ctx -> CollectionLayoutScenario.press(ctx, "#saved-lists"))
          .step("release My decks for copy", CollectionLayoutScenario::release).ticks(2)
          .step("make a copy without saving", ctx -> CollectionLayoutScenario.press(ctx, "#list-duplicate"))
          .step("release Make a copy", CollectionLayoutScenario::release).ticks(2)
          .check("New and Make a copy create distinct unsaved identities without requests", ctx ->
                  !lists(ctx).stored() && !lists(ctx).id().equals(ctx.get("c3NewId"))
                          && count(ctx, CollectionCommand.Save.class) == 2
                          && count(ctx, CollectionCommand.Activate.class) == 1)
          .step("open import/export", ctx -> CollectionLayoutScenario.press(ctx, "#import-export"))
          .step("release import/export", CollectionLayoutScenario::release).ticks(2)
          .step("open import picker for explicit draft", ctx -> CollectionLayoutScenario.press(ctx, "#list-import-picker"))
          .step("release import picker", CollectionLayoutScenario::release).ticks(2)
          .step("identify disposable import row", ctx -> {
              Map<String, com.lowdragmc.lowdraglib2.gui.ui.elements.Button> buttons =
                      ctx.getField(ctx.el("#list-import-picker").element(), "candidateButtons");
              buttons.get(ctx.<String>get("c3ImportName")).setId("c3-import-choice");
          })
          .step("choose disposable import row", ctx -> CollectionLayoutScenario.press(ctx, "#c3-import-choice"))
          .step("release disposable import row", CollectionLayoutScenario::release).ticks(2)
          .step("load import as draft", ctx -> CollectionLayoutScenario.press(ctx, "#list-import"))
          .step("release import draft", CollectionLayoutScenario::release).ticks(2)
          .checkVisible("#close-dialog")
          .step("discard unsaved copy before import", ctx -> CollectionLayoutScenario.press(ctx, "#close-discard"))
          .step("release Discard", CollectionLayoutScenario::release).ticks(2)
          .check("Import loads all sections and remains explicitly unsaved", ctx ->
                  model(ctx).draft().equals(new DeckList(List.of(MAIN, OTHER), List.of(10080), List.of(10004)))
                          && !lists(ctx).stored() && count(ctx, CollectionCommand.Save.class) == 2
                          && count(ctx, CollectionCommand.Activate.class) == 1)
          .hoverAt(-100, -100).screenshot("collection-explicit-save-management");
        exportSteps(s);
        s.step("close explicit-Save screen", ctx -> ctx.mc().setScreen(null))
          .openScreen("empty unsaved draft closes without discard", ctx -> {
             var model = ctx.put("emptyModel", new DeckEditorModel(empty(), CollectionFixture.owned()));
             return CollectionScreen.create(model, CollectionFixture.cards(), code -> null,
                     CollectionFixture.LIST_ID, CollectionFixture::saved, CollectionFixture.query());
         }).awaitModularUI().ticks(2)
         .step("create empty unsaved draft", ctx -> CollectionLayoutScenario.press(ctx, "#new-deck"))
         .step("release New", CollectionLayoutScenario::release).ticks(2)
         .check("empty unsaved draft remains saveable", ctx -> ctx.el("#save-deck").isActive()
                 && ctx.el("#deck-summary").text().contains("Unsaved"))
         .step("close empty unsaved draft", ctx -> CollectionLayoutScenario.press(ctx, "#close-editor"))
         .step("release empty unsaved Close", CollectionLayoutScenario::release).ticks(2)
         .check("empty unsaved draft closes without a discard decision", ctx ->
                 !(ctx.screen() instanceof CollectionScreen))
         .openScreen("renamed saved empty list still prompts", ctx -> CollectionScreen.create(
                 new DeckEditorModel(empty(), CollectionFixture.owned()), CollectionFixture.cards(), code -> null,
                 CollectionFixture.LIST_ID, CollectionFixture::saved, CollectionFixture.query()))
         .awaitModularUI().ticks(2)
         .typeInto("#list-name", "Renamed empty saved list").ticks(2)
         .step("close renamed saved empty list", ctx -> CollectionLayoutScenario.press(ctx, "#close-editor"))
         .step("release renamed saved empty Close", CollectionLayoutScenario::release).ticks(2)
         .checkVisible("#close-dialog")
         .check("saved empty rename still requires a discard decision", ctx ->
                 ctx.screen() instanceof CollectionScreen)
          .teardown("restore pointer viewport", ctx -> {
              ctx.mc().setScreen(null);
              try {
                  Files.deleteIfExists(ctx.get("c3ImportFile"));
                  Path directory = ctx.get("c4Directory");
                  if (directory != null) {
                      try (var files = Files.list(directory)) {
                          for (Path file : files.toList()) Files.delete(file);
                      }
                      Files.delete(directory);
                  }
              }
              catch (IOException error) { throw new UncheckedIOException(error); }
              GLFW.glfwSetWindowSize(ctx.mc().getWindow().getWindow(),
                      ctx.get("originalWidth"), ctx.get("originalHeight"));
          });
    }

    private static void exportSteps(ScenarioBuilder s) {
        s.step("invalid current deck name", ctx -> {
            ctx.put("c4OriginalName", lists(ctx).name());
            lists(ctx).rename(" ");
        });
        leftPress(s, "reject invalid name export", "#list-export");
        s.check("invalid deck name stays local without opening export", ctx -> !ctx.el("#export-dialog").isVisible()
                && ctx.el("#editor-status").text().contains("valid deck name"))
         .step("restore valid current name", ctx -> lists(ctx).rename(ctx.get("c4OriginalName")));
        leftPress(s, "open export filename", "#list-export");
        s.check("Export opens a filename dialog", ctx -> ctx.exists("#export-dialog")
                && ctx.el("#export-dialog").isVisible())
         .check("export buttons retain the existing dialog hit size", ctx -> ctx.el("#export-submit").bounds().height()
                 >= ctx.el("#list-export").bounds().height())
         .step("isolate exports and capture unsaved draft", ctx -> {
             try {
                 Path directory = Files.createTempDirectory("duelcraft-c4-export-");
                 ctx.put("c4Directory", directory);
                 ctx.setField(ctx.getField(ctx.screen(), "controller"), "exports", new DeckExportService(directory));
             } catch (IOException error) { throw new UncheckedIOException(error); }
             ctx.put("c4Snapshot", lists(ctx).snapshot());
             ctx.put("c4Counts", model(ctx).owned());
             ctx.put("c4Revision", lists(ctx).revision());
             ctx.put("c4Commands", ctx.<ArrayList<CollectionCommand>>get("c3Commands").size());
         })
         .check("suggestion uses current unsaved name", ctx -> ctx.el("#export-filename").as(TextField.class)
                 .getText().equals(lists(ctx).name() + ".ydk"))
         .keyDown(GLFW.GLFW_KEY_ESCAPE).ticks(2).keyUp(GLFW.GLFW_KEY_ESCAPE)
         .check("Escape cancels only filename dialog", ctx -> !ctx.el("#export-dialog").isVisible()
                 && ctx.el("#import-dialog").isVisible() && lists(ctx).dirty());
        leftPress(s, "reopen local export", "#list-export");
        s.step("submit traversal filename", ctx -> ctx.el("#export-filename").as(TextField.class).setText("../escape.ydk", true));
        leftPress(s, "reject traversal export", "#export-submit");
        s.waitUntil("filename error reaches client", ctx -> ctx.el("#export-error").text().contains("filename"))
         .checkVisible("#export-dialog")
         .step("choose new filename", ctx -> ctx.el("#export-filename").as(TextField.class).setText("Unsaved draft.ydk", true))
         .hoverAt(-100, -100).screenshot("collection-export-filename");
        leftPress(s, "write new local export", "#export-submit");
        s.waitUntil("new export closes filename dialog", ctx -> !ctx.el("#export-dialog").isVisible())
         .check("exported unsaved draft round trips every section", ctx -> {
             try {
                 return ctx.<SavedDeck>get("c4Snapshot").cards().equals(DeckListLoader.loadFromFile(
                         ctx.<Path>get("c4Directory").resolve("Unsaved draft.ydk")));
             } catch (IOException error) { throw new UncheckedIOException(error); }
         });
        leftPress(s, "open collision export", "#list-export");
        s.step("choose existing filename", ctx -> ctx.el("#export-filename").as(TextField.class).setText("Unsaved draft.ydk", true));
        leftPress(s, "request collision export", "#export-submit");
        s.waitUntil("collision asks for confirmation", ctx -> ctx.el("#export-overwrite-dialog").isVisible())
         .checkTextContains("#export-overwrite-copy", "Unsaved draft.ydk")
         .check("overwrite buttons retain existing dialog hit size", ctx -> ctx.el("#export-overwrite-confirm").bounds().height()
                 >= ctx.el("#list-export").bounds().height())
         .hoverAt(-100, -100).screenshot("collection-export-overwrite")
         .keyDown(GLFW.GLFW_KEY_ESCAPE).ticks(2).keyUp(GLFW.GLFW_KEY_ESCAPE)
         .check("Escape dismisses only overwrite decision", ctx -> !ctx.el("#export-overwrite-dialog").isVisible()
                 && ctx.el("#export-dialog").isVisible());
        leftPress(s, "cancel filename locally", "#export-cancel");
        s.check("collision cancellation preserves exported bytes", ctx -> {
            try { return ctx.<SavedDeck>get("c4Snapshot").cards().equals(DeckListLoader.loadFromFile(
                    ctx.<Path>get("c4Directory").resolve("Unsaved draft.ydk"))); }
            catch (IOException error) { throw new UncheckedIOException(error); }
        }).step("prepare different collision content", ctx -> {
            try { Files.writeString(ctx.<Path>get("c4Directory").resolve("Unsaved draft.ydk"), "original collision bytes"); }
            catch (IOException error) { throw new UncheckedIOException(error); }
        });
        leftPress(s, "open confirmed replacement", "#list-export");
        s.step("choose replacement filename", ctx -> ctx.el("#export-filename").as(TextField.class).setText("Unsaved draft.ydk", true));
        leftPress(s, "request overwrite decision", "#export-submit");
        s.waitUntil("replacement decision is visible", ctx -> ctx.el("#export-overwrite-dialog").isVisible())
         .step("changed filename invalidates confirmation", ctx -> ctx.el("#export-filename").as(TextField.class)
                 .setText("Different.ydk", true))
         .checkHidden("#export-overwrite-dialog")
         .step("choose original collision again", ctx -> ctx.el("#export-filename").as(TextField.class)
                 .setText("Unsaved draft.ydk", true));
        leftPress(s, "request fresh filename decision", "#export-submit");
        s.waitUntil("fresh filename decision is visible", ctx -> ctx.el("#export-overwrite-dialog").isVisible())
         .step("change draft after collision", ctx -> lists(ctx).rename("Changed during confirmation"));
        leftPress(s, "reject stale snapshot confirmation", "#export-overwrite-confirm");
        s.check("changed snapshot restarts filename decision", ctx -> !ctx.el("#export-overwrite-dialog").isVisible()
                 && ctx.el("#export-dialog").isVisible()
                 && ctx.el("#export-filename").as(TextField.class).getText().equals("Changed during confirmation.ydk"))
         .step("restore current draft name", ctx -> lists(ctx).rename(ctx.<SavedDeck>get("c4Snapshot").name()));
        leftPress(s, "cancel changed draft decision", "#export-cancel");
        leftPress(s, "restart original draft export", "#list-export");
        s.step("choose final replacement filename", ctx -> ctx.el("#export-filename").as(TextField.class).setText("Unsaved draft.ydk", true));
        leftPress(s, "request final overwrite decision", "#export-submit");
        s.waitUntil("final replacement decision is visible", ctx -> ctx.el("#export-overwrite-dialog").isVisible())
         .check("changed decisions never replaced old bytes", ctx -> {
             try { return Files.readString(ctx.<Path>get("c4Directory").resolve("Unsaved draft.ydk")).equals("original collision bytes"); }
             catch (IOException error) { throw new UncheckedIOException(error); }
         });
        leftPress(s, "confirm replacement", "#export-overwrite-confirm");
        s.waitUntil("replacement closes filename dialog", ctx -> !ctx.el("#export-dialog").isVisible())
         .check("confirmed replacement imports all captured sections", ctx -> {
             try { return ctx.<SavedDeck>get("c4Snapshot").cards().equals(new com.haxerus.duelcraft.client.collection.DeckImportService(
                     com.haxerus.duelcraft.core.DeckRegistry.open(ctx.get("c4Directory"))).load("Unsaved draft").cards()); }
             catch (IOException error) { throw new UncheckedIOException(error); }
         })
         .check("local export never mutates server state or acknowledges the draft", ctx ->
                 lists(ctx).snapshot().equals(ctx.get("c4Snapshot")) && lists(ctx).dirty() && !lists(ctx).stored()
                         && lists(ctx).activeId().equals(ACTIVE) && model(ctx).owned().equals(ctx.get("c4Counts"))
                         && lists(ctx).revision() == ctx.<Long>get("c4Revision")
                         && ctx.<ArrayList<CollectionCommand>>get("c3Commands").size() == ctx.<Integer>get("c4Commands"))
         .hoverAt(-100, -100).screenshot("collection-export-complete")
         .step("hold export callback for disposal", ctx -> {
             var callbacks = ctx.put("c4Callbacks", new java.util.concurrent.ConcurrentLinkedQueue<Runnable>());
             ctx.setField(ctx.getField(ctx.screen(), "controller"), "client", (java.util.concurrent.Executor) callbacks::add);
             ctx.put("c4Status", ctx.el("#editor-status").as(com.lowdragmc.lowdraglib2.gui.ui.elements.TextElement.class));
             ctx.put("c4StatusBefore", ctx.el("#editor-status").text());
         });
        leftPress(s, "open late callback export", "#list-export");
        s.step("choose late callback filename", ctx -> ctx.el("#export-filename").as(TextField.class).setText("Disposed.ydk", true));
        leftPress(s, "write before disposal callback", "#export-submit");
        s.waitUntil("completion awaits client dispatch", ctx -> !ctx.<java.util.concurrent.ConcurrentLinkedQueue<Runnable>>get("c4Callbacks").isEmpty())
         .step("dispose before dispatching completion", ctx -> {
             ctx.mc().setScreen(null);
             ctx.<java.util.concurrent.ConcurrentLinkedQueue<Runnable>>get("c4Callbacks").remove().run();
         })
         .check("disposed completion cannot change feedback or reopen screen", ctx -> !(ctx.screen() instanceof CollectionScreen)
                 && ctx.<com.lowdragmc.lowdraglib2.gui.ui.elements.TextElement>get("c4Status").getText().getString()
                         .equals(ctx.<String>get("c4StatusBefore")));
    }

    private static void leftPress(ScenarioBuilder s, String name, String selector) {
        s.step(name + " press", ctx -> press(ctx, selector, 0, name))
         .step(name + " release", ctx -> releaseSame(ctx, selector, 0, name));
    }

    private static void doubleLeft(ScenarioBuilder s, String name, String selector) {
        s.step(name + " first press", ctx -> press(ctx, selector, 0, name))
         .step(name + " first release", ctx -> releaseSame(ctx, selector, 0, name))
         .step(name + " second press", ctx -> pressSame(ctx, selector, 0, name))
         .step(name + " second release", ctx -> releaseSame(ctx, selector, 0, name));
    }

    private static void rightPress(ScenarioBuilder s, String name, String selector) {
        s.step(name + " right press", ctx -> press(ctx, selector, 1, name));
    }

    private static void press(TestContext ctx, String selector, int button, String key) {
        var target = ctx.el(selector);
        var bounds = target.bounds();
        ctx.require("pointer target is visible: " + selector, target.isVisible() && bounds.width() > 0
                && bounds.height() > 0 && DuelScreenScenario.insideViewport(bounds));
        ctx.put("pointerElement", target.element());
        ctx.put("pointerX", bounds.center().x);
        ctx.put("pointerY", bounds.center().y);
        ctx.put("pointerKey", key);
        ctx.input().moveTo(bounds.center().x, bounds.center().y);
        ctx.input().mouseDown(bounds.center().x, bounds.center().y, button);
    }

    private static void pressSame(TestContext ctx, String selector, int button, String key) {
        UIElement mounted = ctx.get("pointerElement");
        ctx.check(key + " keeps the mounted hit target between clicks", ctx.el(selector).element() == mounted);
        ctx.input().mouseDown(ctx.<Float>get("pointerX"), ctx.<Float>get("pointerY"), button);
    }

    private static void releaseSame(TestContext ctx, String selector, int button, String key) {
        UIElement mounted = ctx.get("pointerElement");
        ctx.check(key + " keeps the mounted hit target through release", ctx.el(selector).element() == mounted);
        ctx.input().mouseUp(ctx.<Float>get("pointerX"), ctx.<Float>get("pointerY"), button);
    }

    private static void rightRelease(TestContext ctx) {
        ctx.input().mouseUp(ctx.<Float>get("pointerX"), ctx.<Float>get("pointerY"), 1);
    }

    private static CollectionCardGrid grid(TestContext ctx) {
        return ctx.el("#collection-results").as(CollectionCardGrid.class);
    }

    @SuppressWarnings("unchecked")
    private static CollectionScreen commandScreen(TestContext ctx) {
        var model = ctx.put("model", new DeckEditorModel(empty(), CollectionFixture.owned()));
        var commands = ctx.put("c3Commands", new ArrayList<CollectionCommand>());
        var mutations = ctx.put("c3Mutations", new ArrayList<CompletableFuture<CollectionReply>>());
        var activeDeck = new SavedDeck(ACTIVE, "Active fixture", empty());
        var selectedDeck = new SavedDeck(SELECTED, "Selected fixture",
                new DeckList(List.of(OTHER), List.of(), List.of()));
        var current = new java.util.concurrent.atomic.AtomicReference<SavedDeckController>();
        Function<CollectionCommand, CompletionStage<CollectionReply>> request = command -> {
            commands.add(command);
            if (command instanceof CollectionCommand.ReadDeck read) {
                var deck = read.id().equals(ACTIVE) ? activeDeck : selectedDeck;
                return CompletableFuture.completedFuture(new CollectionReply.Deck(
                        current.get() == null ? 4 : current.get().revision(), deck));
            }
            var reply = new CompletableFuture<CollectionReply>();
            mutations.add(reply);
            return reply;
        };
        var summaries = new ArrayList<CollectionReply.Summary>();
        summaries.add(new CollectionReply.Summary(ACTIVE, activeDeck.name(), 0, 0, 0));
        summaries.add(new CollectionReply.Summary(SELECTED, selectedDeck.name(), 1, 0, 0));
        for (int index = 0; index < 10; index++) {
            summaries.add(new CollectionReply.Summary(new UUID(0, 0xc0 + index),
                    "Saved fixture " + (index + 1), index, 0, 0));
        }
        var view = new ClientCollectionState.View(4, CollectionFixture.owned(), List.copyOf(summaries), ACTIVE);
        Supplier<CompletionStage<ClientCollectionState.View>> refresh = () -> {
            var lists = current.get();
            return CompletableFuture.completedFuture(lists == null ? view : new ClientCollectionState.View(
                    lists.revision(), model.owned(), List.copyOf(summaries), lists.activeId()));
        };
        try {
            var method = CollectionScreen.class.getDeclaredMethod("create", DeckEditorModel.class, List.class,
                    java.util.function.IntFunction.class, UUID.class,
                    com.haxerus.duelcraft.client.collection.DeckSaveHandler.class,
                    com.haxerus.duelcraft.client.collection.CollectionQuery.class, Function.class,
                    Supplier.class, java.util.concurrent.Executor.class);
            method.setAccessible(true);
            var screen = (CollectionScreen) method.invoke(null, model, CollectionFixture.cards(),
                    (java.util.function.IntFunction<net.minecraft.resources.ResourceLocation>) code -> null,
                    UUID.randomUUID(), null, CollectionFixture.query(), request, refresh,
                    (java.util.concurrent.Executor) ctx.mc()::execute);
            Object controller = ctx.getField(screen, "controller");
            current.set(ctx.getField(controller, "lists"));
            current.get().openInitial(view);
            return screen;
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException(error);
        }
    }

    private static long count(TestContext ctx, Class<? extends CollectionCommand> type) {
        return ctx.<ArrayList<CollectionCommand>>get("c3Commands").stream().filter(type::isInstance).count();
    }

    private static CompletableFuture<CollectionReply> nextMutation(TestContext ctx) {
        return ctx.<ArrayList<CompletableFuture<CollectionReply>>>get("c3Mutations").removeFirst();
    }

    private static SavedDeckController lists(TestContext ctx) {
        Object controller = ctx.getField(ctx.screen(), "controller");
        return ctx.getField(controller, "lists");
    }

    private static DeckEligibility.Report emptyEligibility() {
        return new DeckEligibility.Report(List.of(), Map.of(), false, false, null);
    }

    private static DeckEditorModel model(TestContext ctx) { return ctx.get("model"); }

    private static DeckList empty() { return new DeckList(List.of(), List.of(), List.of()); }
}
