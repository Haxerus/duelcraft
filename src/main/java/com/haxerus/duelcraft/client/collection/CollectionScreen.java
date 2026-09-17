package com.haxerus.duelcraft.client.collection;

import com.haxerus.duelcraft.client.carddata.CardInfo;
import com.haxerus.duelcraft.collection.DeckList;
import com.lowdragmc.lowdraglib2.gui.holder.ModularUIScreen;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.math.Size;
import com.lowdragmc.lowdraglib2.utils.XmlUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntFunction;

/** Independent fixed-canvas host; resizing keeps the editor and its draft alive. */
public final class CollectionScreen extends ModularUIScreen {
    public static final int DESIGN_WIDTH = 1280;
    public static final int DESIGN_HEIGHT = 720;
    private final UIElement canvas;
    private final CollectionController controller;

    private CollectionScreen(ModularUI ui, DeckEditorModel model, List<CardInfo> cards,
            IntFunction<ResourceLocation> textures, Consumer<DeckList> saveDraft) {
        super(ui, Component.translatable("duelcraft.collection.title"));
        canvas = ui.ui.selectId("collection-canvas").findFirst().orElseThrow();
        controller = new CollectionController(ui.ui, model, cards, textures, saveDraft, super::onClose);
    }

    public static CollectionScreen create(DeckEditorModel model, List<CardInfo> cards,
            IntFunction<ResourceLocation> textures, Consumer<DeckList> saveDraft) {
        var location = ResourceLocation.fromNamespaceAndPath("duelcraft", "ui/collection_screen.xml");
        var document = XmlUtils.loadXml(location);
        if (document == null) throw new IllegalStateException("Failed to load " + location);
        var parsed = UI.of(document);
        var ui = UI.of(parsed.rootElement, parsed.stylesheets,
                size -> Size.of(DESIGN_WIDTH, DESIGN_HEIGHT));
        return new CollectionScreen(ModularUI.of(ui), model, cards, textures, saveDraft);
    }

    @Override
    public void init() {
        super.init();
        if (width > 0 && height > 0) {
            float scale = Math.min(width / (float) DESIGN_WIDTH, height / (float) DESIGN_HEIGHT);
            canvas.transform(transform -> transform.scale(scale));
        }
    }

    @Override
    public void onClose() {
        controller.requestClose();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        controller.afterLayout();
    }
}
