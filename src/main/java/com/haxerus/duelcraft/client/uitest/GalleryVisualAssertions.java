package com.haxerus.duelcraft.client.uitest;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.Horizontal;
import com.lowdragmc.lowdraglib2.gui.ui.data.Vertical;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ScrollerView;
import com.lowdragmc.lowdraglib2.gui.ui.style.PropertyRegistry;
import com.lowdragmc.lowdraglib2.gui.texture.Icons;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.texture.RectTexture;
import com.lowdragmc.lowdraglib2.gui.texture.SpriteTexture;
import com.lowdragmc.lowdraglib2.gui.ui.styletemplate.Sprites;
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

    public static void scroller(TestContext ctx, String selector) {
        var scroll = ctx.el(selector).as(ScrollerView.class).verticalScroller;
        boolean duel = !ctx.all(".gallery-duel").isEmpty();
        float arrowSize = duel ? 9 : 12;
        float trackSize = duel ? 3 : 4;
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
        ctx.check(selector + " thin track and thumb", scroll.scrollContainer.getSizeWidth() <= trackSize + .1f
                && scroll.scrollBar.getSizeWidth() <= trackSize + .1f);
        ctx.check(selector + " native up arrow textures", head.getButtonStyle().baseTexture() == Icons.UP_ARROW_NO_BAR_S
                && head.getButtonStyle().hoverTexture() == Icons.UP_ARROW_NO_BAR_S_LIGHT
                && head.getButtonStyle().pressedTexture() == Icons.UP_ARROW_NO_BAR_S_WHITE);
        ctx.check(selector + " native down arrow textures", tail.getButtonStyle().baseTexture() == Icons.DOWN_ARROW_NO_BAR_S
                && tail.getButtonStyle().hoverTexture() == Icons.DOWN_ARROW_NO_BAR_S_LIGHT
                && tail.getButtonStyle().pressedTexture() == Icons.DOWN_ARROW_NO_BAR_S_WHITE);
        ctx.check(selector + " native thumb textures", scroll.scrollBar.getButtonStyle().baseTexture() == Sprites.SCROLL_BAR_V
                && scroll.scrollBar.getButtonStyle().hoverTexture() == Sprites.SCROLL_BAR_LIGHT_V
                && scroll.scrollBar.getButtonStyle().pressedTexture() == Sprites.SCROLL_BAR_WHITE_V);
        ctx.check(selector + " internals are not actions", !head.getClasses().contains("gallery-action")
                && !tail.getClasses().contains("gallery-action") && !scroll.scrollBar.getClasses().contains("gallery-action"));
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
