package com.haxerus.duelcraft.client.uitest;

import com.lowdragmc.lowdraglib2.gui.holder.ModularUIScreen;
import com.lowdragmc.lowdraglib2.gui.ui.*;
import com.lowdragmc.lowdraglib2.gui.ui.elements.*;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.math.Size;
import com.lowdragmc.lowdraglib2.registry.RegistrationEnvironment;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegisterClient;
import com.lowdragmc.lowdraglib2.uitest.*;
import com.lowdragmc.lowdraglib2.utils.XmlUtils;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.ModList;
import org.lwjgl.glfw.GLFW;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@LDLRegisterClient(name = "gallery_specimen", group = "duelcraft_gallery", registry = UIScenario.REGISTRY,
        environment = RegistrationEnvironment.DEV_ONLY)
public final class GallerySpecimenScenario implements UIScenario {
    @Override public void configure(ScenarioOptions options) { options.requiresWorld(false); }
    @Override public void define(ScenarioBuilder s) {
        var backdrop = new com.haxerus.duelcraft.client.uitest.GalleryBackdrop();
        s.step("install Gallery backdrop", ctx -> backdrop.install());
        s.teardown("remove Gallery backdrop", ctx -> backdrop.close());
        s.step("preflight Gallery fixture", ctx -> {
            var result = GalleryPreflight.check(GalleryPreflightScenario.SPECIMEN, GalleryPreflightScenario.REQUIRED);
            ctx.attach("resource preflight", result.toString());
            ctx.attach("unchecked owner", "This scenario runtime assertions and agent visual review own any unchecked fixture values");
            if (!result.errors().isEmpty()) throw new IllegalStateException(result.errors().toString());
        });
        for (boolean duel : new boolean[]{false, true}) {
            String canvas = duel ? "duel" : "management";
            s.openScreen(canvas + " vocabulary", ctx -> backdrop.include(new Specimen(duel ? 960 : 1280, duel ? 540 : 720, "gallery-" + canvas)))
             .awaitModularUI().ticks(3)
             .step("component centering and scroller contracts", ctx -> {
                 GalleryVisualAssertions.disabled(ctx, "#gallery-disabled");
                 GalleryVisualAssertions.control(ctx, "#gallery-action");
                 GalleryVisualAssertions.control(ctx, "#gallery-confirm");
                 GalleryVisualAssertions.scroller(ctx, "#gallery-rows");
             })
             .step("default component crops", ctx -> captureControls(ctx, canvas + "-default"))
             .step("font and bounded text contracts", ctx -> {
                 for (var text : ctx.all("*")) {
                     if (!(text.element() instanceof TextElement element)) continue;
                     boolean visible = true;
                     for (UIElement owner = element; owner != null; owner = owner.getParent())
                         if (!owner.isDisplayed() || !owner.isVisible()) visible = false;
                     if (!visible) continue;
                     if (element.getText().getString().isEmpty()) continue;
                     DuelUiAssertions.textFits(ctx, element);
                     ctx.check("explicit label height: " + element.getText().getString(),
                             element.getSizeHeight() >= element.getTextStyle().fontSize() + 4);
                 }
             })
             .step("runtime and viewport", ctx -> {
                 var w = ctx.mc().getWindow();
                 var screen = (Specimen) ctx.screen();
                 ctx.attach(canvas + " runtime", "LDLib=" + ModList.get().getModContainerById("ldlib2").orElseThrow().getModInfo().getVersion()
                         + " UI.codeSource=" + UI.class.getProtectionDomain().getCodeSource().getLocation());
                 ctx.attach(canvas + " viewport", "requestedWindow=" + System.getProperty("ldlib2.uitest.window") + " requestedScale=" + System.getProperty("ldlib2.uitest.guiScale") + " configuredScale=" + ctx.mc().options.guiScale().get()
                         + " window=" + w.getScreenWidth() + "x" + w.getScreenHeight() + " framebuffer=" + w.getWidth() + "x" + w.getHeight()
                         + " GUI=" + w.getGuiScaledWidth() + "x" + w.getGuiScaledHeight() + " effectiveScale=" + w.getGuiScale()
                         + " design=" + screen.designWidth + "x" + screen.designHeight + " rootScale=" + screen.scale());
             })
             .step("mouse activation", ctx -> ManagementTestInput.click(ctx, "#gallery-action"))
             .check("mouse increments once", ctx -> ((Specimen) ctx.screen()).clicks == 1)
             .key(GLFW.GLFW_KEY_TAB).check("Tab reaches name field", ctx -> ctx.el("#gallery-name").element().isFocused())
             .key(GLFW.GLFW_KEY_TAB, GLFW.GLFW_MOD_SHIFT).check("Shift Tab returns to action", ctx -> ctx.el("#gallery-action").element().isFocused())
             .key(GLFW.GLFW_KEY_ENTER).key(GLFW.GLFW_KEY_SPACE)
             .check("Enter and Space activate", ctx -> ((Specimen) ctx.screen()).clicks == 3)
             .step("disabled mouse", ctx -> ManagementTestInput.click(ctx, "#gallery-disabled"))
             .step("disabled keyboard", ctx -> { ctx.el("#gallery-disabled").element().focus(); ctx.screen().keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0); ctx.screen().keyPressed(GLFW.GLFW_KEY_SPACE, 0, 0); })
             .check("disabled cannot activate", ctx -> ((Specimen) ctx.screen()).clicks == 3)
             .step("pending submit", ctx -> ManagementTestInput.click(ctx, "#gallery-save"))
             .step("duplicate pending mouse and keyboard", ctx -> { ManagementTestInput.click(ctx, "#gallery-save"); ctx.el("#gallery-save").element().focus(); ctx.screen().keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0); })
             .check("one pending request", ctx -> ((Specimen) ctx.screen()).submits == 1)
             .checkTextContains("#gallery-feedback", "Saving")
             .step("resolved selected and pending textures", ctx -> {
                 var selected = ctx.el("#gallery-selected").as(Button.class).getButtonStyle().baseTexture();
                 var pending = ctx.el("#gallery-save").as(Button.class).getButtonStyle().baseTexture();
                 ctx.check("selected gold edge resolves", selected instanceof com.lowdragmc.lowdraglib2.gui.texture.RectTexture rect
                         && rect.getBorderColor() == 0xffc6ab77 && rect.getStroke() > 0);
                 ctx.check("pending warning edge and dark fill resolve", pending instanceof com.lowdragmc.lowdraglib2.gui.texture.RectTexture rect
                         && rect.getBorderColor() == 0xffe8b86d && rect.getColor() == 0xff34273e);
             })
             .step("selected and focused coexist", ctx -> ctx.el("#gallery-selected").element().focus())
             .check("focus outline resolves independently", ctx -> ctx.el("#gallery-selected").as(Button.class).getButtonStyle().baseTexture()
                     instanceof com.lowdragmc.lowdraglib2.gui.texture.RectTexture rect && rect.getBorderColor() == 0xffefd18e && rect.getStroke() == 2)
             .check("separate state markers", ctx -> ctx.el("#gallery-selected").element().getClasses().contains("gallery-selected")
                     && ctx.el("#gallery-selected").element().getClasses().contains("gallery-focus"))
             .step("focused component crops", ctx -> captureControls(ctx, canvas + "-focused"))
             .hoverAt(-100, -100).ticks(2).screenshot("gallery-" + canvas)
             .step("failed acknowledgement", ctx -> ((Specimen) ctx.screen()).pending.completeExceptionally(new IllegalStateException("fixture")))
             .checkTextContains("#gallery-feedback", "Try again")
             .step("open confirmation", ctx -> ManagementTestInput.click(ctx, "#gallery-confirm"))
             .checkVisible("#gallery-modal")
             .step("confirmation text metrics", ctx -> {
                 for (var element : ctx.el("#gallery-modal").element().selfAndAllChildren().toList()) {
                     if (element instanceof TextElement text && !text.getText().getString().isEmpty()) {
                         DuelUiAssertions.textFits(ctx, text);
                         ctx.check("confirmation text has padding", text.getSizeHeight() >= text.getTextStyle().fontSize() + 4);
                     }
                 }
             }).key(GLFW.GLFW_KEY_TAB)
             .check("modal traps focus", ctx -> ctx.el("#gallery-dismiss").element().isFocused())
             .screenshot("gallery-" + canvas + "-confirmation").key(GLFW.GLFW_KEY_ESCAPE)
             .checkHidden("#gallery-modal")
             .check("focus restored to initiating action", ctx -> ctx.el("#gallery-confirm").element().isFocused())
             .step("wheel bounded deck list", ctx -> {
                 var point = ctx.el("#gallery-rows").bounds().center();
                 for (int i = 0; i < 50; i++) ctx.input().scroll(point.x, point.y, -1);
             }).ticks(3)
             .check("last deck row reachable", ctx -> CollectionLayoutScenario.contained(ctx.el("#gallery-last-row").bounds(), ctx.el("#gallery-rows").bounds()))
             .step("open six preset choices", ctx -> ManagementTestInput.click(ctx, "#gallery-selector")).ticks(3)
             .step("selector row metrics", ctx -> {
                 var selector = ctx.el("#gallery-selector").as(Selector.class);
                 int index = 0;
                 for (var label : selector.scrollerView.viewContainer.select("label", Label.class).toList()) {
                     label.setId("gallery-option-" + index++);
                     DuelUiAssertions.textFits(ctx, label);
                 }
                 ctx.check("all six choices exist", index == 6);
                 ctx.check("five full choices visible", CollectionLayoutScenario.contained(ctx.el("#gallery-option-4").bounds(), ElementBounds.of(selector.scrollerView.viewPort)));
                 var anchor = ctx.el("#gallery-selector").bounds();
                 var dialog = ElementBounds.of(selector.dialog);
                 ctx.check("selector inside viewport", DuelScreenScenario.insideViewport(dialog));
                 ctx.check("scaled popup stays aligned with selector", Math.abs(anchor.x() - dialog.x()) <= 2
                         && Math.abs(anchor.bottom() - dialog.y()) <= 2);
             }).screenshot("gallery-" + canvas + "-selector")
             .step("select first choice by mouse", ctx -> ManagementTestInput.click(ctx, "#gallery-option-0"))
             .check("mouse selected first preset", ctx -> ctx.el("#gallery-selector").as(Selector.class).getValue().equals("Master rule 1"))
             .step("reopen selector", ctx -> ManagementTestInput.click(ctx, "#gallery-selector"))
             .key(GLFW.GLFW_KEY_ESCAPE)
             .check("Escape closes selector", ctx -> !ctx.el("#gallery-selector").as(Selector.class).isOpen())
             .key(GLFW.GLFW_KEY_DOWN)
             .check("focused selector keyboard changes choice", ctx -> ctx.el("#gallery-selector").as(Selector.class).getValue().equals("Master rule 2"))
             .key(GLFW.GLFW_KEY_ENTER)
             .check("Enter opens focused selector", ctx -> ctx.el("#gallery-selector").as(Selector.class).isOpen())
             .key(GLFW.GLFW_KEY_ESCAPE)
             .step("field accepts accents", ctx -> { ctx.el("#gallery-name").element().focus(); ctx.screen().charTyped('é', 0); })
             .checkTextContains("#gallery-name", "é")
             .hover("#gallery-action")
             .check("real button hover state", ctx -> ctx.el("#gallery-action").as(Button.class).getState() == Button.State.HOVERED)
             .step("hover component crops", ctx -> captureControls(ctx, canvas + "-hover"))
             .screenshot("gallery-" + canvas + "-hover")
             .step("hold real mouse press", ctx -> CollectionLayoutScenario.press(ctx, "#gallery-action"))
             .check("real button pressed state", ctx -> ctx.el("#gallery-action").as(Button.class).getState() == Button.State.PRESSED)
             .step("pressed component crops", ctx -> captureControls(ctx, canvas + "-pressed"))
             .screenshot("gallery-" + canvas + "-pressed")
             .step("release mouse", CollectionLayoutScenario::release)
             .step("isolate dense component states after confirmation tests", ctx ->
                     ctx.el("#gallery-confirm").as(Button.class).setOnClick(event -> {}))
             .hover("#gallery-confirm")
             .check("real dense hover state", ctx -> ctx.el("#gallery-confirm").as(Button.class).getState() == Button.State.HOVERED)
             .step("dense hover crops", ctx -> captureControls(ctx, canvas + "-dense-hover"))
             .step("dense press", ctx -> CollectionLayoutScenario.press(ctx, "#gallery-confirm"))
             .check("real dense pressed state", ctx -> ctx.el("#gallery-confirm").as(Button.class).getState() == Button.State.PRESSED)
             .step("dense pressed crops", ctx -> captureControls(ctx, canvas + "-dense-pressed"))
             .step("release dense", ctx -> { ctx.input().mouseUp(-100, -100, 0); ((Specimen) ctx.screen()).dismiss(); })
             .hoverAt(-100, -100)
             .step("ordinary focus", ctx -> ctx.el("#gallery-action").element().focus())
             .check("focused primary keeps readable cream fill", ctx -> ctx.el("#gallery-action").as(Button.class).getButtonStyle().baseTexture()
                     instanceof com.lowdragmc.lowdraglib2.gui.texture.RectTexture rect && rect.getColor() == 0xffeee5d4
                     && rect.getBorderColor() == 0xffefd18e && rect.getStroke() == 2)
             .checkHidden("#gallery-modal")
             .step("ordinary focused crops", ctx -> captureControls(ctx, canvas + "-action-focused"))
             .step("dense focus", ctx -> ctx.el("#gallery-confirm").element().focus())
             .step("dense focused crops", ctx -> captureControls(ctx, canvas + "-dense-focused"));
        }
    }

    private static void captureControls(TestContext ctx, String state) {
        for (String id : new String[]{"gallery-action", "gallery-confirm", "gallery-disabled", "gallery-selected", "gallery-rows"}) {
            ctx.screenshotElement(state + "-" + id, ctx.el("#" + id));
        }
    }

    private static final class Specimen extends ModularUIScreen {
        private final int designWidth, designHeight;
        private int clicks, submits;
        private CompletableFuture<Void> pending;
        @SuppressWarnings("unchecked")
        Specimen(int width, int height, String canvasClass) {
            super(load(width, height, canvasClass), Component.literal("Gallery specimen"));
            designWidth = width; designHeight = height;
            bind("gallery-action", () -> clicks++);
            bind("gallery-disabled", () -> clicks++);
            el("gallery-disabled").setActive(false);
            bind("gallery-selected", () -> {});
            bind("gallery-save", () -> {
                if (pending != null && !pending.isDone()) return;
                submits++;
                pending = new CompletableFuture<>();
                el("gallery-save").setActive(false);
                el("gallery-save").addClass("gallery-pending");
                text("gallery-feedback", "Saving…");
                pending.whenComplete((value, error) -> {
                    el("gallery-save").setActive(true);
                    el("gallery-save").removeClass("gallery-pending");
                    text("gallery-feedback", error == null ? "Saved" : "Could not save. Try again.");
                    if (error != null) el("gallery-feedback").addClass("gallery-error");
                });
            });
            bind("gallery-confirm", () -> { el("gallery-modal").setDisplay(true); el("gallery-dismiss").focus(); });
            bind("gallery-dismiss", this::dismiss);
            el("gallery-modal").setDisplay(false);
            el("gallery-name").setFocusable(true);
            ((TextField) el("gallery-name")).setText("Étoile — deck name", false);
            var selector = (Selector<String>) el("gallery-selector");
            selector.setCandidates(List.of("Master rule 1", "Master rule 2", "Master rule 3", "Master rule 4", "Master rule 5", "Rush duel"));
            selector.setSelected("Master rule 5", false);
            selector.selectorStyle(style -> style.maxItemCount(5).scrollerViewHeight(height * .28f));
            selector.dialog.addClass(canvasClass).addClass("gallery-popup");
            // Same pinned-runtime root clamp correction as CollectionController; fixture-local only.
            selector.dialog.getBubbleListeners(UIEvents.LAYOUT_CHANGED)
                    .forEach(listener -> selector.dialog.removeEventListener(UIEvents.LAYOUT_CHANGED, listener));
            selector.dialog.addEventListener(UIEvents.LAYOUT_CHANGED, event -> alignSelector(selector));
            selector.setFocusable(true);
            selector.addEventListener(UIEvents.KEY_DOWN, event -> {
                if (event.keyCode == GLFW.GLFW_KEY_ENTER || event.keyCode == GLFW.GLFW_KEY_SPACE) {
                    if (selector.isOpen()) selector.hide(); else selector.show();
                    event.stopPropagation();
                } else if (event.keyCode == GLFW.GLFW_KEY_UP || event.keyCode == GLFW.GLFW_KEY_DOWN) {
                    int direction = event.keyCode == GLFW.GLFW_KEY_UP ? -1 : 1;
                    int index = selector.getCandidates().indexOf(selector.getValue());
                    selector.setSelected(selector.getCandidates().get(Math.floorMod(index + direction, selector.getCandidates().size())));
                    event.stopPropagation();
                }
            });
        }
        private static ModularUI load(int width, int height, String canvasClass) {
            var resource = GalleryPreflightScenario.SPECIMEN;
            var document = XmlUtils.loadXml(resource);
            if (document == null) throw new IllegalStateException("Missing Gallery specimen");
            var parsed = UI.of(document);
            parsed.rootElement.addClass(canvasClass);
            return ModularUI.of(UI.of(parsed.rootElement, parsed.stylesheets, size -> Size.of(width, height)));
        }
        private void alignSelector(Selector<?> selector) {
            var dialog = selector.dialog;
            var root = modularUI.ui.rootElement;
            float width = Math.max(selector.getSizeWidth(), 50);
            float left = Math.clamp(selector.getPositionX() - root.getPositionX(), 0, Math.max(0, root.getSizeWidth() - width));
            float top = Math.clamp(selector.getPositionY() + selector.getSizeHeight() - root.getPositionY(), 0,
                    Math.max(0, root.getSizeHeight() - dialog.getSizeHeight()));
            if (Math.abs(dialog.getPositionX() - root.getPositionX() - left) < .1f
                    && Math.abs(dialog.getPositionY() - root.getPositionY() - top) < .1f
                    && Math.abs(dialog.getSizeWidth() - width) < .1f) return;
            dialog.layout(layout -> layout.left(left).top(top).width(width));
        }
        UIElement el(String id) { return modularUI.ui.selectId(id).findFirst().orElseThrow(); }
        void text(String id, String value) { ((TextElement) el(id)).setText(Component.literal(value)); }
        void bind(String id, Runnable action) {
            var button = (Button) el(id);
            button.setFocusable(true);
            button.setOnClick(event -> { if (usable(button)) action.run(); });
            button.addEventListener(UIEvents.KEY_DOWN, event -> {
                if ((event.keyCode == GLFW.GLFW_KEY_ENTER || event.keyCode == GLFW.GLFW_KEY_SPACE) && usable(button)) {
                    event.stopPropagation(); action.run();
                }
            });
            button.addEventListener(UIEvents.FOCUS_IN, event -> button.addClass("gallery-focus"));
            button.addEventListener(UIEvents.FOCUS_OUT, event -> button.removeClass("gallery-focus"));
        }
        boolean usable(UIElement element) {
            if (el("gallery-modal").isDisplayed() && !el("gallery-modal").isAncestorOf(element)) return false;
            for (var current = element; current != null; current = current.getParent())
                if (!current.isDisplayed() || !current.isVisible() || !current.isActive()) return false;
            return true;
        }
        void dismiss() { el("gallery-modal").setDisplay(false); el("gallery-confirm").focus(); }
        float scale() { return Math.min(width / (float) designWidth, height / (float) designHeight); }
        @Override public void init() { super.init(); modularUI.ui.rootElement.transform(t -> t.scale(scale())); }
        @Override public boolean keyPressed(int key, int scan, int modifiers) {
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                var selector = (Selector<?>) el("gallery-selector");
                if (selector.isOpen()) { selector.hide(); selector.focus(); }
                else if (el("gallery-modal").isDisplayed()) dismiss();
                return true;
            }
            if (key == GLFW.GLFW_KEY_TAB) {
                var controls = modularUI.ui.rootElement.selfAndAllChildren().filter(e -> e.isFocusable() && usable(e)).toList();
                int index = controls.indexOf(modularUI.getFocusedElement());
                int direction = (modifiers & GLFW.GLFW_MOD_SHIFT) == 0 ? 1 : -1;
                controls.get(Math.floorMod(index < 0 ? (direction > 0 ? 0 : controls.size()-1) : index + direction, controls.size())).focus();
                return true;
            }
            return super.keyPressed(key, scan, modifiers);
        }
    }
}
