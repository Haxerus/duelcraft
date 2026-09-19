package com.haxerus.duelcraft;

import com.haxerus.duelcraft.client.DuelClientCommand;
import com.haxerus.duelcraft.client.interaction.ClientPreparationState;
import com.haxerus.duelcraft.client.interaction.PreparationRouting;
import com.haxerus.duelcraft.client.LDLibDuelScreen;
import com.haxerus.duelcraft.client.ClientPayloadHandler;
import com.haxerus.duelcraft.client.collection.CollectionCatalog;
import com.haxerus.duelcraft.client.collection.CollectionClient;
import com.haxerus.duelcraft.client.collection.CollectionScreen;
import com.haxerus.duelcraft.client.carddata.CardDatabase;
import com.haxerus.duelcraft.client.carddata.CardInfo;
import com.haxerus.duelcraft.core.data.CardData;
import com.haxerus.duelcraft.client.carddata.CardImageManager;
import com.haxerus.duelcraft.client.carddata.SystemStringTable;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

@Mod(value = Duelcraft.MODID, dist = Dist.CLIENT)
public class DuelcraftClient {

    private static volatile @Nullable CardDatabase cardDatabase;
    private static volatile @Nullable CardImageManager cardImageManager;
    private static volatile @Nullable SystemStringTable systemStringTable;
    private static final ClientPreparationState preparation = new ClientPreparationState(net.neoforged.neoforge.network.PacketDistributor::sendToServer);
    private static final CollectionClient collectionClient = new CollectionClient(task -> Minecraft.getInstance().execute(task));
    private static final CompletableFuture<List<CardInfo>> collectionCatalog = new CompletableFuture<>();

    public DuelcraftClient(IEventBus modBus, ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        modBus.addListener(DuelcraftClient::onClientSetup);
        NeoForge.EVENT_BUS.addListener(DuelClientCommand::register);
        NeoForge.EVENT_BUS.addListener(com.haxerus.duelcraft.client.collection.CardItemTooltip::append);
        NeoForge.EVENT_BUS.addListener(DuelcraftClient::onLoggingOut);
        NeoForge.EVENT_BUS.addListener(DuelcraftClient::onLoggingIn);
    }

    /** Leaving the server drops any duel screen state, so a rejoin cannot resume a dead duel. */
    private static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        if (Minecraft.getInstance().screen instanceof CollectionScreen screen) screen.disconnect();
        collectionClient.disconnect();
        preparation.disconnect();
        PreparationRouting.disconnect();
        LDLibDuelScreen.close();
    }

    private static void onLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        collectionClient.connect();
        preparation.connect();
    }

    private static void onClientSetup(FMLClientSetupEvent event) {
        Duelcraft.LOGGER.info("Duelcraft client setup");
        event.enqueueWork(() -> ClientPayloadHandler.setCollectionReceiver(collectionClient::receive));
        java.util.concurrent.CompletableFuture.runAsync(DuelcraftClient::initCardData);
    }

    private static void initCardData() {
        try {
            Path gameDir = Minecraft.getInstance().gameDirectory.toPath();
            Path cacheDir = gameDir.resolve("duelcraft").resolve("cache");

            Path dbPath = CardData.load().join().database();
            cardDatabase = new CardDatabase(dbPath);
            Duelcraft.LOGGER.info("Card database loaded from {}", dbPath);

            // The setup worker reads metadata once; publication and callbacks use the client thread.
            try {
                var cards = CollectionCatalog.load(dbPath);
                Minecraft.getInstance().execute(() -> collectionCatalog.complete(cards));
            } catch (Exception exception) {
                Minecraft.getInstance().execute(() -> collectionCatalog.completeExceptionally(exception));
                Duelcraft.LOGGER.error("Failed to load collection catalog", exception);
            }

            // Initialize image manager
            String imageBaseUrl = Config.CARD_IMAGE_BASE_URL.get();
            Path imageDir = cacheDir.resolve("images");
            cardImageManager = new CardImageManager(imageBaseUrl, imageDir);
            Duelcraft.LOGGER.info("Card image manager initialized");

            // Load system/counter/victory/setname strings
            String stringsUrl = Config.STRINGS_CONF_URL.get();
            systemStringTable = new SystemStringTable(stringsUrl, cacheDir);

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                if (cardImageManager != null) cardImageManager.close();
                if (cardDatabase != null) {
                    try { cardDatabase.close(); } catch (Exception e) { /* ignore */ }
                }
            }, "DuelcraftShutdown"));

        } catch (Exception e) {
            Minecraft.getInstance().execute(() -> collectionCatalog.completeExceptionally(e));
            Duelcraft.LOGGER.error("Failed to initialize card data pipeline", e);
        }
    }

    public static @Nullable CardDatabase getCardDatabase() {
        return cardDatabase;
    }

    public static @Nullable CardImageManager getCardImageManager() {
        return cardImageManager;
    }

    public static @Nullable SystemStringTable getSystemStringTable() {
        return systemStringTable;
    }

    public static ClientPreparationState preparation() { return preparation; }

    public static CollectionClient getCollectionClient() { return collectionClient; }

    /** Pending means loading, exceptional means failed, and a successful empty list means empty. */
    public static CompletionStage<List<CardInfo>> getCollectionCatalog() {
        return collectionCatalog.minimalCompletionStage();
    }
}
