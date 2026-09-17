package com.haxerus.duelcraft.client.collection;

import com.haxerus.duelcraft.client.carddata.CardInfo;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ScrollerView;
import com.lowdragmc.lowdraglib2.gui.ui.elements.VirtualItemHeightMode;
import com.lowdragmc.lowdraglib2.gui.ui.elements.VirtualScrollerView;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;

/** Four cards per fixed-height virtual row. Only mounted rows request artwork. */
public final class CollectionCardGrid extends VirtualScrollerView<List<CardInfo>> {
    public static final int ROW_HEIGHT = 128;
    private IntFunction<String> caption = code -> "";
    private int selectedCode;
    private float mountedOffset = Float.NaN;
    private float mountedHeight = Float.NaN;

    public CollectionCardGrid(IntFunction<ResourceLocation> textures, IntConsumer selectCard) {
        virtualScrollerViewStyle(style -> style.itemHeightMode(VirtualItemHeightMode.FIXED)
                .estimatedItemHeight(ROW_HEIGHT).overscanPixels(ROW_HEIGHT));
        setItemUIProvider(cards -> {
            var row = new UIElement().addClass("collection-row");
            for (var card : cards) {
                var tile = new CardTile(card, textures, () -> selectCard.accept(card.code()));
                tile.setId("collection-card-" + card.code());
                tile.addClass("collection-card");
                if (card.code() == selectedCode) tile.addClass("selected");
                var cell = new UIElement().addClass("collection-cell");
                cell.addChildren(tile, new Label().setText(Component.literal(caption.apply(card.code())))
                        .addClass("collection-caption"));
                row.addChild(cell);
            }
            return row;
        });
    }

    public void showCards(List<CardInfo> cards) {
        var rows = new ArrayList<List<CardInfo>>();
        for (int index = 0; index < cards.size(); index += 4) {
            rows.add(List.copyOf(cards.subList(index, Math.min(index + 4, cards.size()))));
        }
        setItems(rows);
    }

    void setPresentation(int selectedCode, IntFunction<String> caption) {
        this.selectedCode = selectedCode;
        this.caption = caption;
        refreshVisibleItems();
    }

    @Override
    protected void onVerticalScroll(float value) {
        float height = viewPort.getContentHeight();
        float offset = value * Math.max(0, getTotalVirtualHeight() - height);
        // ScrollerView also calls this after layout. Rebuilding unchanged rows dirties layout again.
        if (offset == mountedOffset && height == mountedHeight) return;
        mountedOffset = offset;
        mountedHeight = height;
        super.onVerticalScroll(value);
    }

    /** Shared presentation for inspector, deck copies and virtual collection cards. */
    static final class CardTile extends Button {
        private final CardInfo card;
        private final IntFunction<ResourceLocation> textures;
        private ResourceLocation texture;
        private final UIElement image = new UIElement().addClass("card-art");
        private final UIElement placeholder;

        CardTile(CardInfo card, IntFunction<ResourceLocation> textures, Runnable select) {
            this.card = card;
            this.textures = textures;
            noText();
            addClass("card-tile");
            placeholder = new Label().setText(Component.literal(card.name() + "\n#" + card.code()))
                    .addClass("card-placeholder");
            image.addChild(placeholder);
            addChild(image);
            getStyle().tooltips(Component.literal(card.name()), Component.literal("#" + card.code()));
            setOnClick(event -> select.run());
        }

        @Override
        public void screenTick() {
            super.screenTick();
            // Deck copies are bounded lists, but artwork is still requested only inside the viewport.
            for (UIElement parent = getParent(); parent != null; parent = parent.getParent()) {
                if (!parent.isDisplayed()) return;
                if (parent instanceof ScrollerView scroll
                        && (getPositionY() + getSizeHeight() <= scroll.viewPort.getPositionY()
                        || getPositionY() >= scroll.viewPort.getPositionY() + scroll.viewPort.getSizeHeight())) return;
            }
            var available = textures.apply(card.code());
            if (available != null && !available.equals(texture)) {
                texture = available;
                image.lss("background", "sprite(" + available + ")");
                placeholder.setDisplay(false);
            }
        }
    }
}
