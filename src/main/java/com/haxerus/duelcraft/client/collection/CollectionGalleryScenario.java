package com.haxerus.duelcraft.client.collection;

import com.haxerus.duelcraft.client.carddata.CardInfo;
import com.haxerus.duelcraft.client.uitest.CollectionFixture;
import com.haxerus.duelcraft.client.uitest.DuelUiAssertions;
import com.haxerus.duelcraft.client.uitest.GalleryPreflight;
import com.haxerus.duelcraft.client.uitest.GalleryVisualAssertions;
import com.haxerus.duelcraft.client.uitest.ManagementTestInput;
import com.haxerus.duelcraft.collection.DeckListWriter;
import com.haxerus.duelcraft.collection.CollectionReply;
import com.haxerus.duelcraft.collection.SavedDeck;
import com.lowdragmc.lowdraglib2.gui.ui.elements.*;
import com.lowdragmc.lowdraglib2.registry.RegistrationEnvironment;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegisterClient;
import com.lowdragmc.lowdraglib2.uitest.*;
import com.lowdragmc.lowdraglib2.utils.XmlUtils;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static com.haxerus.duelcraft.core.OcgConstants.*;

/** Dense Gallery composition through the unchanged collection controller and real input routing. */
@LDLRegisterClient(name = "gallery_collection", group = "duelcraft_gallery", registry = UIScenario.REGISTRY,
        environment = RegistrationEnvironment.DEV_ONLY)
public final class CollectionGalleryScenario implements UIScenario {
    private static final ResourceLocation LAYOUT = ResourceLocation.fromNamespaceAndPath("duelcraft", "ui/uitest/gallery_collection.xml");
    private static final String LONG_NAME = "Amber Dragon, Guardian of the Ancient Library and the Last Unbroken Covenant";
    @Override public void configure(ScenarioOptions options) { options.tags("gallery", "collection").requiresWorld(false); }
    @Override public void define(ScenarioBuilder s) {
        var backdrop = new com.haxerus.duelcraft.client.uitest.GalleryBackdrop();
        s.step("install Gallery backdrop", ctx -> backdrop.install());
        s.teardown("remove Gallery backdrop", ctx -> backdrop.close());
        s.openScreen("unchanged collection behavioral baseline", ctx -> CollectionScreen.create(
                CollectionFixture.model(true), CollectionFixture.cards(), code -> null, CollectionFixture.LIST_ID,
                CollectionFixture::saved, CollectionFixture.query())).awaitModularUI().ticks(3)
         .click("#main-card-0").checkTextContains("#inspector-name", "Amber Dragon")
         .check("Gallery retains every original static controller ID", ctx -> {
             var original = XmlUtils.loadXml(ResourceLocation.fromNamespaceAndPath("duelcraft", "ui/collection_screen.xml"));
             var result = GalleryPreflight.check(LAYOUT, GalleryPreflight.ids(original));
             ctx.attach("preflight", result.toString());
             return result.errors().isEmpty();
         }).openScreen("normal Gallery with local artwork", ctx -> {
             var model = ctx.put("model", CollectionFixture.model(false));
             var owned = new java.util.HashMap<>(model.owned());
             owned.put(CollectionFixture.FIRST, 3L);
             model.replaceOwnership(owned);
             var cards = new ArrayList<>(CollectionFixture.cards());
             String[] names = {"Blue-Eyes White Dragon", "The 13th Grave", "Labrynth Cooclock", "Limit Reverse"};
             int[] types = {TYPE_MONSTER | TYPE_NORMAL, TYPE_MONSTER | TYPE_NORMAL, TYPE_MONSTER | TYPE_EFFECT, TYPE_TRAP | TYPE_CONTINUOUS};
             int[] attack = {3000, 1200, 0, 0}, defense = {2500, 900, 0, 0}, level = {8, 3, 1, 0};
             long[] race = {RACE_DRAGON, RACE_ZOMBIE, RACE_FIEND, 0};
             int[] attribute = {ATTRIBUTE_LIGHT, ATTRIBUTE_DARK, ATTRIBUTE_DARK, 0};
             for (int i = 0; i < cards.size(); i++) {
                 var card = cards.get(i);
                 int kind = i % 4;
                 cards.set(i, new CardInfo(card.code(), names[kind], "Deterministic local artwork specimen. Select Deck or Side, then add or remove a copy.",
                         types[kind], attack[kind], defense[kind], level[kind], race[kind], attribute[kind], card.scope()));
             }
             var screen = CollectionScreen.createFixture(LAYOUT, model, cards, CollectionGalleryScenario::art,
                     CollectionFixture.LIST_ID, CollectionFixture::saved, CollectionFixture.query());
             Object controller = ctx.getField(screen, "controller");
             SavedDeckController lists = ctx.getField(controller, "lists");
             lists.applyView(new ClientCollectionState.View(1, model.owned(), List.of(
                     new CollectionReply.Summary(CollectionFixture.LIST_ID, "Dragon study", 40, 5, 0)), CollectionFixture.LIST_ID));
             lists.rename("Dragon study");
             lists.save();
             return backdrop.include(screen);
         }).awaitModularUI().ticks(4).click("#main-card-0").ticks(2)
         .check("normal composition has 40 Main", ctx -> counts(ctx, 40, 5, 0))
         .checkText("#list-active-status", "Active")
         .click("#card-sort").ticks(2)
         .step("normal browser uses real Passcode sort", ctx -> choose(ctx, "card-sort", CardSearch.Sort.PASSCODE)).ticks(2)
         .step("normal header contract", CollectionGalleryScenario::header)
         .step("normal controls and scrollbars", ctx -> {
             for (String id : List.of("new-deck", "saved-lists", "import-export", "save-deck", "activate-deck", "help-button", "close-editor"))
                 GalleryVisualAssertions.control(ctx, "#" + id);
             GalleryVisualAssertions.scroller(ctx, "#main-grid");
             GalleryVisualAssertions.scroller(ctx, "#card-details-scroll");
             GalleryVisualAssertions.scroller(ctx, "#collection-results");
             ctx.attach("normal inspector art", ctx.el("#inspector-art").bounds() + " tile=" + ctx.el("#inspector-art .inspector-card").bounds());
         }).hoverAt(-100, -100).step("normal visible text audit", CollectionGalleryScenario::auditVisibleText).screenshot("gallery-collection-normal")
         .step("open active My decks", ctx -> ManagementTestInput.click(ctx, "#saved-lists")).ticks(2)
         .checkVisible("#list-clear-active")
         .step("relocated Clear active retains compact component", ctx -> {
             GalleryVisualAssertions.control(ctx, "#list-clear-active");
             GalleryVisualAssertions.scroller(ctx, "#saved-list-scroll");
         }).hoverAt(-100, -100).screenshot("gallery-collection-active-decks")
         .step("close active My decks", ctx -> ManagementTestInput.click(ctx, "#lists-close")).ticks(2)
         .step("open Help through row", ctx -> ManagementTestInput.click(ctx, "#help-button")).ticks(2)
         .checkVisible("#help-dialog")
         .step("close Help", ctx -> ManagementTestInput.click(ctx, "#help-close")).ticks(2)
         .typeInto("#list-name", "N".repeat(128)).ticks(2)
         .step("long editable title stays bounded", CollectionGalleryScenario::header)
         .hoverAt(-100, -100).step("long toolbar text audit", CollectionGalleryScenario::auditVisibleText).screenshot("gallery-collection-toolbar-long")
         .step("Close still routes through dirty confirmation", ctx -> ManagementTestInput.click(ctx, "#close-editor")).ticks(2)
         .checkVisible("#close-dialog")
         .step("retain normal draft", ctx -> ManagementTestInput.click(ctx, "#close-cancel")).ticks(2)
         .openScreen("dense Gallery with actual collection controller", ctx -> {
             var model = ctx.put("model", CollectionFixture.model(true));
             ctx.put("saveReply", new CompletableFuture<SavedDeck>());
             var cards = new ArrayList<>(CollectionFixture.cards());
             var first = cards.getFirst();
             cards.set(0, new CardInfo(first.code(), LONG_NAME, first.desc(), first.type(), first.atk(), first.def(),
                     first.level(), first.race(), first.attribute(), first.scope()));
             var screen = CollectionScreen.createFixture(LAYOUT, model, cards, code -> null, CollectionFixture.LIST_ID,
                     (name, draft) -> ctx.<CompletableFuture<SavedDeck>>get("saveReply"), CollectionFixture.query());
             Object controller = ctx.getField(screen, "controller");
             SavedDeckController lists = ctx.getField(controller, "lists");
             lists.applyView(new ClientCollectionState.View(1, model.owned(), List.of(
                     new CollectionReply.Summary(CollectionFixture.LIST_ID, "N".repeat(128), 60, 15, 15)), null));
             return backdrop.include(screen);
         }).awaitModularUI().ticks(4).click("#main-card-0").click("#toggle-side").ticks(3)
         .check("actual 60 Main / 15 Extra / 15 Side", ctx -> counts(ctx, 60, 15, 15))
         .checkTextContains("#inspector-name", LONG_NAME)
         .step("single aligned Gallery action row", CollectionGalleryScenario::header)
         .step("standard density content and measured bounds", CollectionGalleryScenario::audit)
         .hoverAt(-100, -100).step("gallery-collection-standard text audit", CollectionGalleryScenario::auditVisibleText).screenshot("gallery-collection-standard")
         .click("#card-density").ticks(3)
         .checkText("#card-density", "Cards: Large")
         .step("large density content and measured bounds", CollectionGalleryScenario::audit)
         .hoverAt(-100, -100).step("gallery-collection-large text audit", CollectionGalleryScenario::auditVisibleText).screenshot("gallery-collection-large")
         .step("remember independent scrolls", ctx -> {
             for (String id : List.of("main-grid", "collection-results")) ctx.put(id, pixels(ctx, id));
         }).scroll("#card-details-scroll", -12).ticks(2)
         .check("effect wheel is independent", ctx -> pixels(ctx, "card-details-scroll") > 0
                 && pixels(ctx, "main-grid") == ctx.<Float>get("main-grid")
                 && pixels(ctx, "collection-results") == ctx.<Float>get("collection-results"))
         .scroll("#main-grid", -15).ticks(2)
         .check("Main wheel is independent", ctx -> pixels(ctx, "main-grid") > 0
                 && pixels(ctx, "collection-results") == ctx.<Float>get("collection-results"))
         .step("Main endpoint", ctx -> end(ctx, "main-grid", 1)).ticks(2)
         .click("#main-card-59").checkTextContains("#inspector-name", "Sample 60")
         .step("Side endpoint", ctx -> end(ctx, "side-grid", 1)).ticks(2)
         .click("#side-card-14").checkTextContains("#inspector-name", "Sample 55")
         .step("remember Main before browser wheel", ctx -> ctx.put("mainBeforeBrowser", pixels(ctx, "main-grid")))
         .scroll("#collection-results", -8).ticks(2)
         .check("browser wheel scrolls independently", ctx -> pixels(ctx, "collection-results") > 0
                 && Math.abs(pixels(ctx, "main-grid") - ctx.<Float>get("mainBeforeBrowser")) < 1)
         .hoverAt(-100, -100).screenshot("gallery-collection-scrolled")
         .step("Extra endpoint", ctx -> end(ctx, "extra-grid", 1)).ticks(2)
         .click("#extra-card-14").checkTextContains("#inspector-name", "Sample 80")
         .step("restore browser and Main endpoints", ctx -> { end(ctx, "collection-results", 0); end(ctx, "main-grid", 0); }).ticks(2)
         .click("#section-deck").ticks(2)
         .step("browser right-click adds", ctx -> click(ctx, "#collection-card-10003", 1)).ticks(2)
         .check("browser right-click edits actual draft", ctx -> counts(ctx, 61, 15, 15))
         .step("deck right-click removes", ctx -> click(ctx, "#main-card-2", 1)).ticks(2)
         .check("deck right-click edits actual draft", ctx -> counts(ctx, 60, 15, 15))
         .click("#collection-card-10003").click("#add-card").ticks(2)
         .check("visible Add edits draft", ctx -> counts(ctx, 61, 15, 15))
         .click("#remove-card").ticks(2)
         .check("visible Remove edits draft", ctx -> counts(ctx, 60, 15, 15))
         .typeInto("#collection-search", "no matching gallery card").ticks(2)
         .checkVisible("#collection-empty").checkCount(".collection-card", 0)
         .hoverAt(-100, -100).step("gallery-collection-empty text audit", CollectionGalleryScenario::auditVisibleText).screenshot("gallery-collection-empty")
         .typeInto("#collection-search", "").ticks(2)
         .click("#toggle-filters").ticks(2)
         .checkVisible("#filter-panel").checkExists("#filter-category-monster")
         .step("expanded filter text fits", ctx -> {
             DuelUiAssertions.textFits(ctx, ctx.el("#filter-apply").as(Button.class).text);
             viewport(ctx);
         }).hoverAt(-100, -100).step("gallery-collection-filters text audit", CollectionGalleryScenario::auditVisibleText).screenshot("gallery-collection-filters")
         .step("filter bottom endpoint", ctx -> end(ctx, "filter-scroll", 1)).ticks(2)
         .click("#filter-measure").ticks(2)
         .step("real measure popup selection", ctx -> choose(ctx, "filter-measure", CardSearch.Measure.ANY))
         .click("#toggle-filters").ticks(2)
         .typeInto("#list-name", "A").ticks(2)
         .check("real name input owns keyboard focus", ctx -> ctx.el("#list-name").element().isFocused())
         .hoverAt(-100, -100).step("gallery-collection-name-focus text audit", CollectionGalleryScenario::auditVisibleText).screenshot("gallery-collection-name-focus")
         .check("one-unit deck name accepted", ctx -> lists(ctx).name().equals("A"))
         .typeInto("#list-name", "N".repeat(128)).ticks(2)
         .check("128-unit deck name accepted", ctx -> lists(ctx).name().length() == 128)
         .step("open My decks", ctx -> ManagementTestInput.click(ctx, "#saved-lists")).ticks(2)
         .checkVisible("#saved-list-scroll").checkExists("#saved-row-0")
         .hoverAt(-100, -100).step("gallery-collection-my-decks text audit", CollectionGalleryScenario::auditVisibleText).screenshot("gallery-collection-my-decks")
         .step("close My decks", ctx -> ManagementTestInput.click(ctx, "#lists-close")).ticks(2)
         .step("create unique discovered import fixture", ctx -> {
             var directory = ctx.mc().gameDirectory.toPath().resolve("duelcraft/decks");
             try {
                 Files.createDirectories(directory);
                 var file = Files.createTempFile(directory, "gallery-fixture-", ".ydk");
                 ctx.put("importFile", file);
                 Files.writeString(file, DeckListWriter.toYdk(model(ctx).draft()));
             } catch (IOException error) { throw new UncheckedIOException(error); }
         }).step("open real import surface", ctx -> ManagementTestInput.click(ctx, "#import-export")).ticks(2)
         .click("#list-import-picker").ticks(2)
         .hoverAt(-100, -100).step("gallery-collection-import-dropdown text audit", CollectionGalleryScenario::auditVisibleText).screenshot("gallery-collection-import-dropdown")
         .step("select exact generated basename through popup", ctx -> {
             String filename = ctx.<Path>get("importFile").getFileName().toString();
             choose(ctx, "list-import-picker", filename.substring(0, filename.length() - 4));
         }).step("open real export field without exporting", ctx -> ManagementTestInput.click(ctx, "#list-export")).ticks(2)
         .typeInto("#export-filename", "A.ydk").ticks(2)
         .check("one-unit export basename in real field", ctx -> ctx.el("#export-filename").as(TextField.class).getText().equals("A.ydk"))
         .typeInto("#export-filename", "E".repeat(96) + ".ydk").ticks(2)
         .check("96-unit export basename in real field", ctx -> ctx.el("#export-filename").as(TextField.class).getText().length() == 100)
         .hoverAt(-100, -100).step("gallery-collection-export-name text audit", CollectionGalleryScenario::auditVisibleText).screenshot("gallery-collection-export-name")
         .step("cancel export", ctx -> ManagementTestInput.click(ctx, "#export-cancel")).ticks(2)
         .step("close import without submitting", ctx -> ManagementTestInput.click(ctx, "#import-close")).ticks(2)
         .step("reset independent scrolls for long selected card", ctx -> { end(ctx, "main-grid", 0); end(ctx, "card-details-scroll", 0); }).ticks(2)
         .click("#main-card-0").ticks(2)
         .checkTextContains("#inspector-name", LONG_NAME)
         .step("long selected name text fits", ctx -> DuelUiAssertions.textFits(ctx, ctx.el("#inspector-name").as(TextElement.class)))
         .click("#remove-card").ticks(2)
         .check("remove before Save keeps edited draft valid", ctx -> counts(ctx, 59, 15, 15) && model(ctx).dirty())
         .step("Save through actual input", ctx -> ManagementTestInput.click(ctx, "#save-deck")).ticks(2)
         .check("pending Save does not acknowledge draft", ctx -> lists(ctx).pending() && model(ctx).dirty()
                 && !ctx.el("#save-deck").element().isActive())
         .hoverAt(-100, -100).step("gallery-collection-save-pending text audit", CollectionGalleryScenario::auditVisibleText).screenshot("gallery-collection-save-pending")
         .step("fail controlled Save acknowledgement", ctx -> ctx.<CompletableFuture<SavedDeck>>get("saveReply")
                 .completeExceptionally(new IllegalStateException("Gallery controlled Save failure"))).ticks(3)
         .check("failed Save retains editable dirty draft and actionable error", ctx -> !lists(ctx).pending()
                 && model(ctx).dirty() && counts(ctx, 59, 15, 15) && lists(ctx).status().equals("operation_failed")
                 && ctx.el("#save-deck").element().isActive())
         .step("error text remains readable", ctx -> DuelUiAssertions.textFits(ctx, ctx.el("#editor-status").as(TextElement.class)))
         .hoverAt(-100, -100).step("gallery-collection-save-failed text audit", CollectionGalleryScenario::auditVisibleText).screenshot("gallery-collection-save-failed")
         .step("prepare meaningful resize state", ctx -> { end(ctx, "main-grid", .5f); end(ctx, "collection-results", .5f); }).ticks(2)
         .step("resize same live fixture", ctx -> {
             var window = ctx.mc().getWindow();
             ctx.put("width", window.getWidth()); ctx.put("height", window.getHeight());
             ctx.put("beforeResize", model(ctx).draft());
             ctx.put("mainPixels", pixels(ctx, "main-grid")); ctx.put("browserPixels", pixels(ctx, "collection-results"));
             ctx.attach("before native resize", "maximized=" + GLFW.glfwGetWindowAttrib(window.getWindow(), GLFW.GLFW_MAXIMIZED));
             if (GLFW.glfwGetWindowAttrib(window.getWindow(), GLFW.GLFW_MAXIMIZED) == GLFW.GLFW_TRUE)
                 GLFW.glfwRestoreWindow(window.getWindow());
             GLFW.glfwSetWindowSize(window.getWindow(), 1100, 800);
             GLFW.glfwPollEvents();
         }).waitUntil("resized bounds settled", ctx -> {
             var window = ctx.mc().getWindow();
             boolean dirty = ctx.requireUI().getStyleEngine().requireCalculate();
             int[] nativeWidth = new int[1], nativeHeight = new int[1];
             GLFW.glfwGetWindowSize(window.getWindow(), nativeWidth, nativeHeight);
             ctx.attach("resize settle", "native=" + nativeWidth[0] + "x" + nativeHeight[0] + " framebuffer=" + window.getWidth() + "x" + window.getHeight() + " layoutDirty=" + dirty);
             return window.getWidth() == 1100 && window.getHeight() == 800 && !dirty;
         }).ticks(3)
         .check("draft selection and meaningful scroll survive live resize", ctx -> model(ctx).draft().equals(ctx.get("beforeResize"))
                 && ctx.el("#inspector-name").text().equals(LONG_NAME) && ctx.<Float>get("mainPixels") > 0
                 && Math.abs(pixels(ctx, "main-grid") - ctx.<Float>get("mainPixels")) < 1
                 && ctx.<Float>get("browserPixels") > 0 && Math.abs(pixels(ctx, "collection-results") - ctx.<Float>get("browserPixels")) < 1)
         .step("resized viewport", CollectionGalleryScenario::viewport)
         .hoverAt(-100, -100).step("gallery-collection-live-resize text audit", CollectionGalleryScenario::auditVisibleText).screenshot("gallery-collection-live-resize")
         .step("restore requested viewport", ctx -> GLFW.glfwSetWindowSize(ctx.mc().getWindow().getWindow(), ctx.get("width"), ctx.get("height"))).ticks(6)
         .teardown("close and delete only allocated fixture file", ctx -> {
             ctx.mc().setScreen(null);
             Path file = ctx.get("importFile");
             try { if (file != null) Files.deleteIfExists(file); }
             catch (IOException error) { throw new UncheckedIOException(error); }
         });
    }

    private static ResourceLocation art(int code) {
        int[] images = {89631139, 32864, 2511, 27551};
        return ResourceLocation.fromNamespaceAndPath("duelcraft", "textures/uitest/gallery/" + images[Math.floorMod(code - CollectionFixture.FIRST, 4)] + ".png");
    }

    private static void header(TestContext ctx) {
        var name = ctx.el("#list-name").bounds();
        ctx.check("editable title remains usefully bounded", name.width() >= ctx.el("#collection-canvas").bounds().width() * 220 / 1280
                && name.width() <= ctx.el("#collection-canvas").bounds().width() * 340 / 1280);
        for (String id : List.of("save-deck", "activate-deck")) {
            var classes = ctx.el("#" + id).element().getClasses();
            ctx.check(id + " has intended emphasis", classes.contains("gallery-primary") == id.equals("activate-deck"));
        }
        for (String id : List.of("new-deck", "saved-lists", "import-export", "save-deck", "activate-deck")) {
            var action = ctx.el("#" + id).bounds();
            var field = ctx.el("#list-name").element();
            var text = ctx.el("#" + id).as(Button.class).text;
            ctx.check(id + " content center aligns with editable field", Math.abs(field.getContentY() + field.getContentHeight() / 2
                    - text.getContentY() - text.getContentHeight() / 2) < 1);
            ctx.check(id + " shares editable title row", Math.abs(action.centerY() - name.centerY()) < 1);
        }
        var active = ctx.el("#list-active-status").bounds();
        ctx.check("Active immediately follows bounded name", active.x() >= name.right() && active.x() - name.right() <= 12);
        var summary = ctx.el("#deck-summary").bounds();
        var count = ctx.el("#main-count").bounds();
        ctx.check("Saved and Main do not overlap", count.right() <= summary.x() + 1 && summary.right() <= ctx.el("#sort-deck").bounds().x() + 1);
        DuelUiAssertions.textFits(ctx, ctx.el("#main-count").as(TextElement.class));
        DuelUiAssertions.textFits(ctx, ctx.el("#deck-summary").as(TextElement.class));
        var main = ctx.el("#main-header").bounds();
        ctx.check("saved feedback belongs beside Main", summary.y() >= main.y() && summary.bottom() <= main.bottom());
        ctx.check("no auxiliary navigation row", ctx.el("#editor-nav").bounds().height() == 0);
    }

    private static DeckEditorModel model(TestContext ctx) { return ctx.get("model"); }
    private static SavedDeckController lists(TestContext ctx) {
        Object controller = ctx.getField(ctx.screen(), "controller");
        return ctx.getField(controller, "lists");
    }
    private static boolean counts(TestContext ctx, int main, int extra, int side) {
        var draft = model(ctx).draft();
        return draft.main().size() == main && draft.extra().size() == extra && draft.side().size() == side;
    }
    private static float pixels(TestContext ctx, String id) { return Math.max(0, -ctx.el("#" + id).as(ScrollerView.class).viewContainer.getLayoutY()); }
    private static void end(TestContext ctx, String id, float value) { ctx.el("#" + id).as(ScrollerView.class).verticalScroller.setNormalizedValue(value, true); }
    private static void click(TestContext ctx, String selector, int button) {
        var point = ctx.el(selector).bounds().center();
        ctx.input().moveTo(point.x, point.y); ctx.input().mouseDown(point.x, point.y, button); ctx.input().mouseUp(point.x, point.y, button);
    }
    private static void viewport(TestContext ctx) {
        var window = ctx.mc().getWindow();
        ctx.attach("viewport", "requestedWindow=%s requestedScale=%s window=%dx%d framebuffer=%dx%d GUI=%dx%d effectiveScale=%.1f canvas=%s draft=%s".formatted(
                System.getProperty("ldlib2.uitest.window"), System.getProperty("ldlib2.uitest.guiScale"), window.getScreenWidth(), window.getScreenHeight(),
                window.getWidth(), window.getHeight(), window.getGuiScaledWidth(), window.getGuiScaledHeight(), window.getGuiScale(), ctx.el("#collection-canvas").bounds(), model(ctx).draft()));
    }
    private static void auditVisibleText(TestContext ctx) {
        for (var ref : ctx.all("*")) {
            var element = ref.element();
            boolean visible = true;
            for (var parent = element; parent != null; parent = parent.getParent()) {
                if (!parent.isVisible() || !parent.isDisplayed()) { visible = false; break; }
            }
            if (!visible || element.getSizeWidth() <= 0 || element.getSizeHeight() <= 0) continue;
            if (element instanceof Button button) {
                DuelUiAssertions.contains(ctx, ElementBounds.of(button), ElementBounds.of(button.text), ref.path() + " contains button text");
            }
            if (element instanceof TextElement text) {
                // Saved names are explicitly clipped compact rows with the full name in the existing tooltip.
                if (element.getParent() != null && element.getParent().getClasses().contains("saved-name")) {
                    ctx.check("compact saved name retains full tooltip", !element.getParent().getStyle().tooltips().isEmpty());
                } else DuelUiAssertions.textFits(ctx, text);
            }
        }
    }
    private static void audit(TestContext ctx) {
        viewport(ctx);
        DuelUiAssertions.textFits(ctx, ctx.el("#side-count").as(TextElement.class));
        var inspector = ctx.el("#card-inspector").bounds();
        var main = ctx.el("#main-grid").bounds();
        var browser = ctx.el("#collection-results").as(CollectionCardGrid.class);
        var previousSection = ctx.el("#main-section").bounds();
        float mainPitch = ctx.el("#main-card-1").bounds().x() - ctx.el("#main-card-0").bounds().x();
        for (String section : List.of("main", "extra", "side")) {
            var heading = ctx.el("#" + section + "-header").bounds();
            var label = ctx.el("#" + section + "-count").as(TextElement.class);
            ctx.check(section + " heading line vertically centered", label.getTextStyle().textAlignVertical()
                    == com.lowdragmc.lowdraglib2.gui.ui.data.Vertical.CENTER
                    && Math.abs(ElementBounds.of(label).centerY() - heading.centerY()) < 1);
            if (section.equals("main")) continue;
            var bounds = ctx.el("#" + section + "-section").bounds();
            ctx.check(section + " follows preceding full-width deck section", bounds.y() >= previousSection.bottom() - 1
                    && Math.abs(bounds.x() - previousSection.x()) < 1 && Math.abs(bounds.width() - previousSection.width()) < 1);
            float pitch = ctx.el("#" + section + "-card-1").bounds().x() - ctx.el("#" + section + "-card-0").bounds().x();
            ctx.check(section + " card spacing matches Main", Math.abs(pitch - mainPitch) < 1);
            previousSection = bounds;
        }
        var first = ctx.el("#main-card-0").bounds();
        var right = ctx.el("#collection-results").bounds();
        ctx.attach("content bounds", "inspector=%s main=%s firstMainCard=%s extra=%s side=%s browser=%s mountedRows=%d rowHeight=%s".formatted(
                inspector, main, first, ctx.el("#extra-grid").bounds(), ctx.el("#side-grid").bounds(), right, browser.getMountedItemCount(), browser.getVirtualScrollerViewStyle().estimatedItemHeight()));
        ctx.check("three columns have useful nonoverlapping content", inspector.width() > 0 && main.width() > 0 && right.width() > 0
                && inspector.right() <= main.x() + 1 && main.right() <= right.x() + 1);
        ctx.check("Main shows a complete card row with Extra and Side expanded", first.height() > 0 && first.bottom() <= main.bottom() + 1);
        var row = ctx.all(".collection-row").getFirst().element();
        ctx.check("four browser cards retain 128-unit virtual row", row.getChildren().size() == 4 && browser.getVirtualScrollerViewStyle().estimatedItemHeight() == 128);
        for (var cell : row.getChildren()) {
            var card = ElementBounds.of(cell.getChildren().getFirst());
            ctx.check("every browser column shows complete artwork at rest", card.width() > 0 && card.height() > 0 && card.x() >= right.x() - 1 && card.right() <= right.right() + 1 && card.bottom() <= right.bottom() + 1);
        }
        for (String id : List.of("add-card", "remove-card", "sort-deck", "card-density", "save-deck", "import-export", "toggle-side", "activate-deck")) {
            var button = ctx.el("#" + id).as(Button.class);
            DuelUiAssertions.textFits(ctx, button.text);
            DuelUiAssertions.contains(ctx, ElementBounds.of(button), ElementBounds.of(button.text), id + " contains its text");
        }
        for (String section : List.of("extra", "side")) {
            var bounds = ctx.el("#" + section + "-grid").as(ScrollerView.class);
            var viewport = ElementBounds.of(bounds.viewPort);
            float firstY = ctx.el("#" + section + "-card-0").bounds().y();
            for (var card : ctx.all("#" + section + "-grid .deck-card")) {
                var b = card.bounds();
                if (Math.abs(b.y() - firstY) < 1) DuelUiAssertions.contains(ctx, viewport, b, section + " first-row card fully visible");
            }
        }
    }
    @SuppressWarnings("unchecked")
    private static <T> void choose(TestContext ctx, String id, T value) {
        var selector = ctx.el("#" + id).as(Selector.class);
        Map<T, Button> candidates = ctx.getField(selector, "candidateButtons");
        var button = candidates.get(value);
        ctx.require("real popup contains requested row", selector.isOpen() && button != null);
        var anchor = ctx.el("#" + id).bounds(); var dialog = ElementBounds.of(selector.dialog); var hit = ElementBounds.of(button);
        float x = Math.clamp(anchor.x(), 0, Math.max(0, ctx.mc().getWindow().getGuiScaledWidth() - dialog.width()));
        float y = Math.clamp(anchor.bottom(), 0, Math.max(0, ctx.mc().getWindow().getGuiScaledHeight() - dialog.height()));
        ctx.attach("popup bounds", "anchor=%s dialog=%s hit=%s value=%s".formatted(anchor, dialog, hit, value));
        ctx.check("popup retains scaled anchor and clamp", Math.abs(dialog.x() - x) <= 2 && Math.abs(dialog.y() - y) <= 2 && dialog.width() >= anchor.width() - 1);
        ctx.input().mouseDown(hit.centerX(), hit.centerY(), 0); ctx.input().mouseUp(hit.centerX(), hit.centerY(), 0);
        ctx.check("real popup row selects requested value", value.equals(selector.getValue()) && !selector.isOpen());
    }
}
