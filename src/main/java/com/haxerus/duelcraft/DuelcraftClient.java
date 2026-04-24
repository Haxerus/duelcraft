package com.haxerus.duelcraft;

import com.haxerus.duelcraft.client.carddata.CardDatabase;
import com.haxerus.duelcraft.client.carddata.CardDatabaseDownloader;
import com.haxerus.duelcraft.client.carddata.CardImageManager;
import com.haxerus.duelcraft.client.carddata.SystemStringTable;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;

@Mod(value = Duelcraft.MODID, dist = Dist.CLIENT)
public class DuelcraftClient {

    private static @Nullable CardDatabase cardDatabase;
    private static @Nullable CardImageManager cardImageManager;
    private static @Nullable SystemStringTable systemStringTable;

    public DuelcraftClient(IEventBus modBus, ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        modBus.addListener(DuelcraftClient::onClientSetup);
    }

    private static void onClientSetup(FMLClientSetupEvent event) {
        Duelcraft.LOGGER.info("Duelcraft client setup");
        java.util.concurrent.CompletableFuture.runAsync(DuelcraftClient::initCardData);
    }

    private static void initCardData() {
        try {
            Path gameDir = Minecraft.getInstance().gameDirectory.toPath();
            Path cacheDir = gameDir.resolve("duelcraft").resolve("cache");

            // Download card database if needed
            String dbUrl = Config.CARD_DATABASE_URL.get();
            Path dbPath = CardDatabaseDownloader.ensureDatabase(dbUrl, cacheDir);
            cardDatabase = new CardDatabase(dbPath);
            Duelcraft.LOGGER.info("Card database loaded from {}", dbPath);

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
}
