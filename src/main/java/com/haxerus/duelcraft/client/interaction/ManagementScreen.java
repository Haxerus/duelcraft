package com.haxerus.duelcraft.client.interaction;

import com.lowdragmc.lowdraglib2.gui.holder.ModularUIScreen;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextElement;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.math.Size;
import com.lowdragmc.lowdraglib2.utils.XmlUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.glfw.GLFW;

/** Shared management canvas sizing and one-layer-at-a-time modal close. */
abstract class ManagementScreen extends ModularUIScreen {
    protected boolean removed;
    protected ManagementScreen(String resource, String title) { super(load(resource), Component.literal(title)); }
    private static ModularUI load(String resource) {
        var document = XmlUtils.loadXml(ResourceLocation.fromNamespaceAndPath("duelcraft", "ui/" + resource + ".xml"));
        if (document == null) throw new IllegalStateException("Missing management layout: " + resource);
        var parsed = UI.of(document);
        return ModularUI.of(UI.of(parsed.rootElement, parsed.stylesheets, size -> Size.of(1280, 720)));
    }
    protected UIElement element(String id) { return modularUI.ui.selectId(id).findFirst().orElseThrow(); }
    protected Button button(String id, Runnable action) {
        return bind((Button) element(id), action);
    }
    protected Button bind(Button button, Runnable action) {
        button.setFocusable(true);
        button.setOnClick(event -> action.run());
        button.addEventListener(UIEvents.KEY_DOWN, event -> {
            if ((event.keyCode == GLFW.GLFW_KEY_ENTER || event.keyCode == GLFW.GLFW_KEY_SPACE)
                    && usable(button) && keyboardSurface().isAncestorOf(button)) {
                event.stopPropagation();
                action.run();
            }
        });
        button.addEventListener(UIEvents.FOCUS_IN, event -> {
            button.addClass("keyboard-focus");
            for (var parent = button.getParent(); parent != null; parent = parent.getParent()) {
                if (parent instanceof com.lowdragmc.lowdraglib2.gui.ui.elements.ScrollerView scroll) scroll.scrollToChild(button);
            }
        });
        button.addEventListener(UIEvents.FOCUS_OUT, event -> button.removeClass("keyboard-focus"));
        return button;
    }
    protected void text(String id, String value) { ((TextElement) element(id)).setText(Component.literal(value)); }
    protected void text(String id, Component value) { ((TextElement) element(id)).setText(value); }
    @Override public void init() {
        super.init(); removed = false;
        modularUI.ui.rootElement.transform(t -> t.scale(Math.min(width / 1280f, height / 720f)));
    }
    @Override public void removed() { removed = true; super.removed(); }
    protected UIElement keyboardSurface() {
        return element("management-help").isDisplayed() ? element("management-help") : modularUI.ui.rootElement;
    }
    protected void hide(String id) {
        var surface = element(id);
        if (modularUI.getFocusedElement() != null && surface.isAncestorOf(modularUI.getFocusedElement())) modularUI.clearFocus();
        surface.setDisplay(false);
    }
    private static boolean usable(UIElement element) {
        for (var current = element; current != null; current = current.getParent()) {
            if (!current.isDisplayed() || !current.isActive() || !current.isVisible()) return false;
        }
        return true;
    }
    private void clearHiddenFocus() {
        var focused = modularUI.getFocusedElement();
        if (focused != null && (!usable(focused) || !keyboardSurface().isAncestorOf(focused))) modularUI.clearFocus();
    }
    protected boolean dismiss() {
        if (element("management-help").isDisplayed()) { hide("management-help"); return true; }
        return false;
    }
    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        if (key == GLFW.GLFW_KEY_ESCAPE) { if (!dismiss()) onClose(); return true; }
        clearHiddenFocus();
        if (key == GLFW.GLFW_KEY_TAB) {
            var controls = keyboardSurface().selfAndAllChildren().filter(e -> e.isFocusable() && usable(e)).toList();
            if (!controls.isEmpty()) {
                int index = controls.indexOf(modularUI.getFocusedElement());
                int direction = (modifiers & GLFW.GLFW_MOD_SHIFT) == 0 ? 1 : -1;
                controls.get(Math.floorMod(index < 0 ? (direction > 0 ? 0 : controls.size()-1) : index + direction, controls.size())).focus();
            }
            return true;
        }
        return super.keyPressed(key, scan, modifiers);
    }
    @Override public boolean charTyped(char character, int modifiers) {
        clearHiddenFocus();
        return super.charTyped(character, modifiers);
    }
}
