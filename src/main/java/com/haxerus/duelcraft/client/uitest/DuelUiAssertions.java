package com.haxerus.duelcraft.client.uitest;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ScrollerView;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.Horizontal;
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import com.lowdragmc.lowdraglib2.uitest.ElementBounds;
import com.lowdragmc.lowdraglib2.uitest.TestContext;
import com.lowdragmc.lowdraglib2.utils.TextUtilities;

/** Geometry contracts from docs/ui-design-guide.md; captures still need visual review. */
public final class DuelUiAssertions {
    private DuelUiAssertions() {}

    public static void audit(TestContext ctx) {
        for (String id : new String[]{"toast", "banner", "status-label", "chain-count", "plr-hints", "opp-hints", "lp-delta-0", "lp-delta-1"}) {
            var ref = ctx.el("#" + id);
            if (visible(ref.element())) {
                contains(ctx, ctx.el("#duel-canvas").bounds(), ref.bounds(), id + " inside canvas");
                textFits(ctx, ref.as(TextElement.class));
            }
        }
        for (String id : new String[]{"prompt", "pause", "result"}) {
            if (ctx.el("#" + id + "-overlay").isVisible()) {
                surface(ctx, "#" + id + "-dialog");
                centered(ctx, "#" + id + "-title", "#" + id + "-dialog");
            }
        }
        if (ctx.el("#hint-modal").isVisible()) surface(ctx, "#hint-modal-dialog");
        if (ctx.el("#context-menu").isVisible()) surface(ctx, "#context-menu");
        for (String id : new String[]{"card-info-banner", "zone-inspector", "duel-log"}) {
            if (ctx.el("#" + id).isVisible()) surface(ctx, "#" + id);
        }
        for (var ref : ctx.all(".prompt-btn")) {
            if (visible(ref.element()) && ref.element() instanceof Button button) {
                contains(ctx, ElementBounds.of(button), ElementBounds.of(button.text), "button text: " + ref.text());
                ctx.check("button text has vertical room: " + ref.text(),
                        button.text.getContentHeight() >= button.text.getTextStyle().fontSize() - 1);
                textFits(ctx, button.text);
            }
        }
    }

    private static boolean visible(UIElement element) {
        for (var current = element; current != null; current = current.getParent()) {
            if (!current.isVisible() || !current.isDisplayed()) return false;
        }
        return true;
    }

    public static void surface(TestContext ctx, String selector) {
        var root = ctx.el(selector).element();
        contains(ctx, ctx.el("#duel-canvas").bounds(), ElementBounds.of(root), selector + " inside canvas");
        children(ctx, root);
    }

    private static void children(TestContext ctx, UIElement parent) {
        // Scrolling is intentional overflow; only the outer viewport belongs to the surface.
        if (parent instanceof ScrollerView scroll) {
            for (var text : scroll.viewContainer.select("*", TextElement.class).toList()) {
                textFits(ctx, text);
            }
            return;
        }
        if (parent instanceof TextElement text) textFits(ctx, text);
        var children = parent.getChildren().stream().filter(e -> e.isVisible() && e.isDisplayed()).toList();
        for (var child : children) {
            if (child.getSizeWidth() <= 0 || child.getSizeHeight() <= 0) continue;
            contains(ctx, ElementBounds.of(parent), ElementBounds.of(child), name(parent) + " contains " + name(child));
            children(ctx, child);
        }
        for (int i = 0; i < children.size(); i++) {
            for (int j = i + 1; j < children.size(); j++) {
                var a = ElementBounds.of(children.get(i));
                var b = ElementBounds.of(children.get(j));
                ctx.check(name(parent) + " siblings do not overlap: " + name(children.get(i)) + "/" + name(children.get(j)),
                        Math.min(a.right(), b.right()) - Math.max(a.x(), b.x()) <= 1
                                || Math.min(a.bottom(), b.bottom()) - Math.max(a.y(), b.y()) <= 1);
            }
        }
    }

    private static void textFits(TestContext ctx, TextElement text) {
        if (text.getText().getString().isEmpty()) return;
        var style = text.getTextStyle();
        float width = style.adaptiveWidth() || style.textWrap() != TextWrap.WRAP
                ? Float.MAX_VALUE : text.getContentWidth();
        var lines = TextUtilities.computeFormattedLines(text.getFont(),
                TextUtilities.withFont(text.getText(), style.font()), style.fontSize(), width);
        float height = lines.size() * (style.fontSize() + style.lineSpacing()) - style.lineSpacing();
        ctx.check(name(text) + " fits rendered lines vertically", height <= text.getContentHeight() + 1,
                text.getContentHeight(), height);
        if (style.textWrap() != TextWrap.ROLL && style.textWrap() != TextWrap.HOVER_ROLL) {
            float widest = lines.stream().map(t -> t.getB()).max(Float::compare).orElse(0f);
            ctx.check(name(text) + " fits rendered text horizontally", widest <= text.getContentWidth() + 1,
                    text.getContentWidth(), widest);
        }
    }

    public static void centered(TestContext ctx, String text, String parent) {
        var label = ctx.el(text).as(TextElement.class);
        var a = ctx.el(text).bounds();
        var b = ctx.el(parent).bounds();
        ctx.check(text + " centered in " + parent, Math.abs(a.centerX() - b.centerX()) <= 1
                && a.width() >= b.width() * .8f && label.getTextStyle().textAlignHorizontal() == Horizontal.CENTER,
                b.toString(), a.toString());
    }

    public static void contains(TestContext ctx, ElementBounds parent, ElementBounds child, String description) {
        ctx.check(description, child.x() >= parent.x() - 1 && child.y() >= parent.y() - 1
                && child.right() <= parent.right() + 1 && child.bottom() <= parent.bottom() + 1,
                parent.toString(), child.toString());
    }

    private static String name(UIElement element) {
        return element.getId().isEmpty() ? element.getElementName() + element.getClasses() : "#" + element.getId();
    }
}
