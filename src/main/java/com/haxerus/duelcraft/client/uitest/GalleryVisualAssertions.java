package com.haxerus.duelcraft.client.uitest;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.Horizontal;
import com.lowdragmc.lowdraglib2.gui.ui.data.Vertical;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ScrollerView;
import com.lowdragmc.lowdraglib2.gui.ui.style.PropertyRegistry;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.texture.RectTexture;
import com.lowdragmc.lowdraglib2.gui.texture.SpriteTexture;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField;
import com.lowdragmc.lowdraglib2.uitest.ElementBounds;
import com.lowdragmc.lowdraglib2.uitest.TestContext;

/** Gallery-only component contracts; raster balance still requires visual review. */
public final class GalleryVisualAssertions {
    private GalleryVisualAssertions() {}

    public static void control(TestContext ctx, String selector) {
        var button = ctx.el(selector).as(Button.class);
        var text = button.text;
        var style = text.getTextStyle();
        float lineHeight = style.fontSize();
        float startY = text.getContentY() + switch (style.textAlignVertical()) {
            case TOP -> 0;
            case CENTER -> (text.getContentHeight() - lineHeight) / 2;
            case BOTTOM -> text.getContentHeight() - lineHeight;
        };
        ctx.attach(selector + " control", bounds(button) + " text=" + bounds(text)
                + " lineStartY=" + startY + " font=" + style.font() + " size=" + lineHeight
                + " align=" + style.textAlignHorizontal() + "/" + style.textAlignVertical()
                + " base=" + button.getStyleBag().computeCandidateSlot(PropertyRegistry.BASE_BACKGROUND)
                + " textures=" + textures(button)
                + " vertical=" + text.getStyleBag().computeCandidateSlot(PropertyRegistry.VERTICAL_ALIGN));
        ctx.check(selector + " text box vertically centered", Math.abs(text.getContentY() + text.getContentHeight()/2
                - button.getContentY() - button.getContentHeight()/2) <= .6f);
        ctx.check(selector + " line vertically centered", Math.abs(startY + lineHeight/2
                - button.getContentY() - button.getContentHeight()/2) <= .6f);
        ctx.check(selector + " text horizontally centered", Math.abs(text.getContentX() + text.getContentWidth()/2
                - button.getContentX() - button.getContentWidth()/2) <= .6f
                && style.textAlignHorizontal() == Horizontal.CENTER && style.textAlignVertical() == Vertical.CENTER);
        DuelUiAssertions.textFits(ctx, text);
    }

    public static void disabled(TestContext ctx, String selector) {
        var button = ctx.el(selector).as(Button.class);
        ctx.check(selector + " native disabled state", !button.isActive() && button.getClasses().contains("__disabled__")
                && !button.getClasses().contains("gallery-disabled"));
        ctx.check(selector + " readable native disabled fill/text", button.getButtonStyle().baseTexture() instanceof RectTexture rect
                && rect.getColor() == 0xff2b2233 && button.text.getTextStyle().textColor() == 0xffb8afbe);
        control(ctx, selector);
    }
    public static void scroller(TestContext ctx, String selector) {
        var scroll = ctx.el(selector).as(ScrollerView.class).verticalScroller;
        boolean duel = !ctx.all(".gallery-duel").isEmpty();
        float arrowSize = duel ? 9 : 12;
        float trackSize = arrowSize;
        var head = scroll.headButton;
        var tail = scroll.tailButton;
        ctx.attach(selector + " scroller", "head=" + bounds(head) + " tail=" + bounds(tail)
                + " track=" + bounds(scroll.scrollContainer) + " thumb=" + bounds(scroll.scrollBar)
                + " headBase=" + head.getStyleBag().computeCandidateSlot(PropertyRegistry.BASE_BACKGROUND)
                + " tailBase=" + tail.getStyleBag().computeCandidateSlot(PropertyRegistry.BASE_BACKGROUND)
                + " headTextures=" + textures(head) + " tailTextures=" + textures(tail)
                + " thumbTextures=" + textures(scroll.scrollBar));
        ctx.check(selector + " compact arrows", Math.abs(head.getSizeHeight() - arrowSize) <= .1f
                && Math.abs(tail.getSizeHeight() - arrowSize) <= .1f && Math.abs(head.getSizeWidth() - arrowSize) <= .1f && Math.abs(tail.getSizeWidth() - arrowSize) <= .1f);
        ctx.check(selector + " track and thumb match arrow width", Math.abs(scroll.scrollContainer.getSizeWidth() - trackSize) <= .1f
                && Math.abs(scroll.scrollBar.getSizeWidth() - trackSize) <= .1f);
        arrow(ctx, selector + " up", head, "up");
        arrow(ctx, selector + " down", tail, "down");
        thumb(ctx, scroll.scrollBar);
        ctx.check(selector + " internals are not actions", !head.getClasses().contains("gallery-action")
                && !tail.getClasses().contains("gallery-action") && !scroll.scrollBar.getClasses().contains("gallery-action"));
    }

    public static void field(TestContext ctx, String selector) {
        var field = ctx.el(selector).as(TextField.class);
        float inset = ctx.all(".gallery-duel").isEmpty() ? 10 : 7.5f;
        ctx.attach(selector + " editable inset", bounds(field));
        ctx.check(selector + " horizontal text inset", Math.abs(field.getContentX() - field.getPositionX() - inset) < .1f
                && Math.abs(field.getPositionX() + field.getSizeWidth() - field.getContentX() - field.getContentWidth() - inset) < .1f);
    }

    public static void thumb(TestContext ctx, Button thumb) {
        ctx.check("Gallery flat thumb states", fill(thumb.getButtonStyle().baseTexture(), 0xff8c7495)
                && fill(thumb.getButtonStyle().hoverTexture(), 0xffb8afbe)
                && fill(thumb.getButtonStyle().pressedTexture(), 0xffefd18e));
    }

    private static void arrow(TestContext ctx, String label, Button button, String direction) {
        var style = button.getButtonStyle();
        ctx.check(label + " Gallery arrow states", tintedGlyph(style.baseTexture(), direction, 0xffb8afbe)
                && tintedGlyph(style.hoverTexture(), direction, 0xffeee5d4)
                && tintedGlyph(style.pressedTexture(), direction, 0xffefd18e));
    }

    private static boolean tintedGlyph(IGuiTexture texture, String direction, int color) {
        return texture instanceof SpriteTexture sprite && sprite.getImageLocation().toString().equals("duelcraft:textures/uitest/gallery/scroll-" + direction + ".png")
                && sprite.color == color;
    }

    private static boolean fill(IGuiTexture texture, int color) {
        return texture instanceof RectTexture rect && rect.getColor() == color;
    }

    private static String textures(Button button) {
        var style = button.getButtonStyle();
        return "base=" + texture(style.baseTexture()) + " hover=" + texture(style.hoverTexture())
                + " pressed=" + texture(style.pressedTexture());
    }

    private static String texture(IGuiTexture texture) {
        if (texture instanceof RectTexture rect) return "rect(fill=" + Integer.toHexString(rect.getColor())
                + ",border=" + Integer.toHexString(rect.getBorderColor()) + ",stroke=" + rect.getStroke() + ")";
        if (texture instanceof SpriteTexture sprite) return sprite.getImageLocation() + "@"
                + sprite.spritePosition + "/" + sprite.spriteSize;
        return texture.getClass().getSimpleName();
    }

    private static String bounds(UIElement element) {
        return ElementBounds.of(element) + " content=(" + element.getContentX() + "," + element.getContentY()
                + "," + element.getContentWidth() + "," + element.getContentHeight() + ")";
    }
}
