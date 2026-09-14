package com.haxerus.duelcraft.core.data;

import net.neoforged.fml.loading.FMLPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/** One snapshot per process: never refresh script files underneath a running duel. */
public final class CardData {
    private static final Logger LOGGER = LoggerFactory.getLogger(CardData.class);
    private static CompletableFuture<CardDataCache.Snapshot> loading;

    private CardData() {}

    public static synchronized CompletableFuture<CardDataCache.Snapshot> load() {
        if (loading == null) {
            loading = CompletableFuture.supplyAsync(() -> {
                try {
                    return new CardDataCache().prepare(FMLPaths.GAMEDIR.get()
                            .resolve("duelcraft/cache/card-data"));
                } catch (IOException e) {
                    LOGGER.error("Card data initialization failed", e);
                    throw new CompletionException(e);
                }
            });
        }
        return loading;
    }
}
