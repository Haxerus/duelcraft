package com.haxerus.duelcraft.client.collection;

import com.haxerus.duelcraft.client.carddata.CardInfo;
import com.haxerus.duelcraft.collection.DeckList;
import com.haxerus.duelcraft.DuelcraftClient;
import com.haxerus.duelcraft.collection.CollectionCommand;
import com.haxerus.duelcraft.collection.CollectionReply;
import com.lowdragmc.lowdraglib2.gui.holder.ModularUIScreen;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.math.Size;
import com.lowdragmc.lowdraglib2.utils.XmlUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.IntFunction;

/** Independent fixed-canvas host; resizing keeps the editor and its draft alive. */
public final class CollectionScreen extends ModularUIScreen {
    public static final int DESIGN_WIDTH = 1280;
    public static final int DESIGN_HEIGHT = 720;
    private final UIElement root;
    private final CollectionController controller;

    private CollectionScreen(ModularUI ui, DeckEditorModel model, List<CardInfo> cards,
            IntFunction<ResourceLocation> textures, IntFunction<ResourceLocation> art, UUID id,
            DeckSaveHandler saveDraft, CollectionQuery search,
            Function<CollectionCommand, CompletionStage<CollectionReply>> request,
            Supplier<CompletionStage<ClientCollectionState.View>> refresh, Executor client) {
        super(ui, Component.translatable("duelcraft.collection.title"));
        root = ui.ui.rootElement;
        controller = new CollectionController(ui.ui, model, cards, textures, art, id, saveDraft, search,
                request, refresh, client, super::onClose);
    }

    public static CollectionScreen create(DeckEditorModel model, List<CardInfo> cards,
            IntFunction<ResourceLocation> textures, UUID id, DeckSaveHandler saveDraft, CollectionQuery search) {
        return create(model, cards, textures, textures, id, saveDraft, search,
                () -> CompletableFuture.completedFuture(new ClientCollectionState.View(0, model.owned(), List.of(), null)));
    }

    public static CollectionScreen create(DeckEditorModel model, List<CardInfo> cards,
            IntFunction<ResourceLocation> textures, IntFunction<ResourceLocation> art,
            UUID id, DeckSaveHandler saveDraft, CollectionQuery search) {
        return create(model, cards, textures, art, id, saveDraft, search,
                () -> CompletableFuture.completedFuture(new ClientCollectionState.View(0, model.owned(), List.of(), null)));
    }

    public static CollectionScreen create(DeckEditorModel model, List<CardInfo> cards,
            IntFunction<ResourceLocation> textures, UUID id, DeckSaveHandler saveDraft, CollectionQuery search,
            Supplier<CompletionStage<ClientCollectionState.View>> refresh) {
        return create(model, cards, textures, textures, id, saveDraft, search, refresh);
    }

    private static CollectionScreen create(DeckEditorModel model, List<CardInfo> cards,
            IntFunction<ResourceLocation> textures, IntFunction<ResourceLocation> art, UUID id,
            DeckSaveHandler saveDraft, CollectionQuery search,
            Supplier<CompletionStage<ClientCollectionState.View>> refresh) {
        var screen = createWithArt(model, cards, textures, art, id, saveDraft, search,
                command -> CompletableFuture.failedFuture(new IllegalStateException("Sample list actions unavailable")), refresh, Runnable::run);
        if (saveDraft == null) screen.controller.initializeCollections();
        return screen;
    }

    /** Real private collection; routing to this screen is supplied by the later management milestone. */
    public static CollectionScreen create(ClientCollectionState.View initialView) {
        Executor client = Minecraft.getInstance()::execute;
        var connection = DuelcraftClient.getCollectionClient();
        var worker = new CollectionSearchWorker(client);
        CollectionQuery search = new CollectionQuery() {
            @Override public void submit(List<CardInfo> cards, String text, CardSearch.Filters filters,
                    Map<Integer, Long> counts, DeckList draft, java.util.function.Consumer<List<CardInfo>> apply) {
                worker.submit(cards, text, filters, counts, draft, apply);
            }
            @Override public void close() { worker.close(); }
        };
        var model = new DeckEditorModel(new DeckList(List.of(), List.of(), List.of()), Map.of());
        IntFunction<ResourceLocation> textures = code -> {
            var images = DuelcraftClient.getCardImageManager();
            return images == null ? null : images.getCardTexture(code);
        };
        IntFunction<ResourceLocation> art = code -> {
            var images = DuelcraftClient.getCardImageManager();
            return images == null ? null : images.getCardArt(code);
        };
        var screen = createWithArt(model, List.of(), textures, art, UUID.randomUUID(), null, search,
                connection::request, connection::refresh, client);
        screen.controller.catalogLoading();
        screen.controller.initializeCollections(initialView);
        DuelcraftClient.getCollectionCatalog().whenCompleteAsync((cards, error) ->
                screen.controller.setCatalog(error == null ? cards : List.of(), error != null), client);
        return screen;
    }

    private static CollectionScreen create(DeckEditorModel model, List<CardInfo> cards,
            IntFunction<ResourceLocation> textures, UUID id, DeckSaveHandler saveDraft, CollectionQuery search,
            Function<CollectionCommand, CompletionStage<CollectionReply>> request,
            Supplier<CompletionStage<ClientCollectionState.View>> refresh, Executor client) {
        return createWithArt(model, cards, textures, textures, id, saveDraft, search, request, refresh, client);
    }

    private static CollectionScreen createWithArt(DeckEditorModel model, List<CardInfo> cards,
            IntFunction<ResourceLocation> textures, IntFunction<ResourceLocation> art, UUID id,
            DeckSaveHandler saveDraft, CollectionQuery search,
            Function<CollectionCommand, CompletionStage<CollectionReply>> request,
            Supplier<CompletionStage<ClientCollectionState.View>> refresh, Executor client) {
        var location = ResourceLocation.fromNamespaceAndPath("duelcraft", "ui/collection_screen.xml");
        var document = XmlUtils.loadXml(location);
        if (document == null) throw new IllegalStateException("Failed to load " + location);
        var parsed = UI.of(document);
        var ui = UI.of(parsed.rootElement, parsed.stylesheets,
                size -> Size.of(DESIGN_WIDTH, DESIGN_HEIGHT));
        return new CollectionScreen(ModularUI.of(ui), model, cards, textures, art, id, saveDraft, search,
                request, refresh, client);
    }

    @Override
    public void init() {
        super.init();
        if (width > 0 && height > 0) {
            float scale = Math.min(width / (float) DESIGN_WIDTH, height / (float) DESIGN_HEIGHT);
            root.transform(transform -> transform.scale(scale));
        }
    }

    @Override
    public void onClose() {
        controller.requestClose();
    }

    @Override public void removed() {
        controller.dispose();
        super.removed();
    }

    /** Logout invalidates private callbacks before Minecraft replaces the screen. */
    public void disconnect() { controller.dispose(); }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        controller.refreshTransfers();
        super.render(graphics, mouseX, mouseY, partialTick);
        controller.afterLayout();
    }
}
