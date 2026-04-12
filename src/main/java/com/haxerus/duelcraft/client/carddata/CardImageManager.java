package com.haxerus.duelcraft.client.carddata;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import javax.imageio.ImageIO;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Dual-channel card image cache with rate-limited downloads.
 * <p>
 * Two channels:
 * <ul>
 *   <li><b>Card art</b> (cropped artwork) — for the card info banner</li>
 *   <li><b>Card texture</b> (full card, small) — for hand/field/inspector</li>
 * </ul>
 * Both use async download, JPEG→PNG conversion, disk caching, and DynamicTexture registration.
 * A 50ms delay between HTTP requests keeps us polite to ygoprodeck's CDN.
 */
public class CardImageManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(CardImageManager.class);
    private static final long THROTTLE_MS = 50;

    // Shared infrastructure
    private final HttpClient httpClient = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private final ExecutorService executor = Executors.newFixedThreadPool(2,
            r -> { Thread t = new Thread(r, "CardImageLoader"); t.setDaemon(true); return t; });
    private volatile long lastRequestTime = 0;
    private volatile Runnable onTextureLoaded;

    // Card art channel (cropped artwork for info banner)
    private final String artUrl;
    private final Path artCacheDir;
    private final Map<Integer, ResourceLocation> artCache = new ConcurrentHashMap<>();
    private final Set<Integer> artLoading = ConcurrentHashMap.newKeySet();
    private final Set<Integer> artFailed = ConcurrentHashMap.newKeySet();

    // Card texture channel (full card for hand/field/inspector)
    private final String cardUrl;
    private final Path cardCacheDir;
    private final Map<Integer, ResourceLocation> cardCache = new ConcurrentHashMap<>();
    private final Set<Integer> cardLoading = ConcurrentHashMap.newKeySet();
    private final Set<Integer> cardFailed = ConcurrentHashMap.newKeySet();

    /**
     * @param cardBaseUrl base URL for full card images (e.g. ".../cards_small/")
     * @param cacheDir    root cache directory; subdirs "art" and "cards" are created
     */
    public CardImageManager(String cardBaseUrl, Path cacheDir) {
        cardBaseUrl = cardBaseUrl.endsWith("/") ? cardBaseUrl : cardBaseUrl + "/";
        this.cardUrl = cardBaseUrl;
        this.artUrl = cardBaseUrl.replace("cards_small", "cards_cropped");

        this.cardCacheDir = cacheDir.resolve("cards");
        this.artCacheDir = cacheDir.resolve("art");
        try {
            Files.createDirectories(cardCacheDir);
            Files.createDirectories(artCacheDir);
        } catch (IOException e) {
            LOGGER.warn("Failed to create image cache directories: {}", e.getMessage());
        }
    }

    /** Called on the render thread when any texture finishes loading. */
    public void setOnTextureLoaded(Runnable callback) {
        this.onTextureLoaded = callback;
    }

    /** Cropped artwork for the card info banner. Returns null while loading. */
    public ResourceLocation getCardArt(int code) {
        return getTexture(code, artCache, artLoading, artFailed, artCacheDir, artUrl, "art_");
    }

    /** Full card image for hand/field/inspector. Returns null while loading. */
    public ResourceLocation getCardTexture(int code) {
        return getTexture(code, cardCache, cardLoading, cardFailed, cardCacheDir, cardUrl, "card_");
    }

    private ResourceLocation getTexture(int code,
                                         Map<Integer, ResourceLocation> cache,
                                         Set<Integer> loadingSet,
                                         Set<Integer> failedSet,
                                         Path diskDir,
                                         String urlBase,
                                         String locPrefix) {
        if (code == 0) return null;

        ResourceLocation cached = cache.get(code);
        if (cached != null) return cached;

        if (failedSet.contains(code)) return null;

        if (loadingSet.add(code)) {
            Path diskFile = diskDir.resolve(code + ".png");
            executor.submit(() -> {
                try {
                    if (Files.exists(diskFile)) {
                        loadFromDisk(code, diskFile, cache, locPrefix);
                    } else {
                        download(code, urlBase, diskDir, cache, failedSet, locPrefix);
                    }
                } finally {
                    loadingSet.remove(code);
                }
            });
        }

        return null;
    }

    private void loadFromDisk(int code, Path file,
                              Map<Integer, ResourceLocation> cache, String locPrefix) {
        try {
            byte[] data = Files.readAllBytes(file);
            Minecraft.getInstance().execute(() -> registerTexture(code, data, cache, locPrefix));
        } catch (IOException e) {
            LOGGER.warn("Failed to load cached card image {}: {}", code, e.getMessage());
        }
    }

    private void download(int code, String urlBase, Path diskDir,
                          Map<Integer, ResourceLocation> cache,
                          Set<Integer> failedSet, String locPrefix) {
        try {
            throttle();

            String url = urlBase + code + ".jpg";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(15))
                    .build();

            HttpResponse<byte[]> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofByteArray());

            if (response.statusCode() == 200) {
                byte[] pngData = convertToPng(response.body());
                if (pngData == null) {
                    LOGGER.warn("Failed to convert card image for {}", code);
                    failedSet.add(code);
                    return;
                }
                Files.write(diskDir.resolve(code + ".png"), pngData);
                Minecraft.getInstance().execute(() -> registerTexture(code, pngData, cache, locPrefix));
                LOGGER.debug("Downloaded card image for {} ({})", code, locPrefix);
            } else {
                LOGGER.debug("Card image not found for {}: HTTP {}", code, response.statusCode());
                failedSet.add(code);
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to download card image for {}: {}", code, e.getMessage());
            failedSet.add(code);
        }
    }

    private synchronized void throttle() throws InterruptedException {
        long now = System.currentTimeMillis();
        long elapsed = now - lastRequestTime;
        if (elapsed < THROTTLE_MS) {
            Thread.sleep(THROTTLE_MS - elapsed);
        }
        lastRequestTime = System.currentTimeMillis();
    }

    private void registerTexture(int code, byte[] data,
                                 Map<Integer, ResourceLocation> cache, String locPrefix) {
        try {
            NativeImage image = NativeImage.read(new ByteArrayInputStream(data));
            DynamicTexture texture = new DynamicTexture(image);
            ResourceLocation loc = ResourceLocation.fromNamespaceAndPath("duelcraft",
                    "dynamic/" + locPrefix + code);
            Minecraft.getInstance().getTextureManager().register(loc, texture);
            cache.put(code, loc);
            if (onTextureLoaded != null) onTextureLoaded.run();
        } catch (IOException e) {
            LOGGER.warn("Failed to decode card image for {}: {}", code, e.getMessage());
        }
    }

    private static byte[] convertToPng(byte[] imageData) {
        try {
            BufferedImage buffered = ImageIO.read(new ByteArrayInputStream(imageData));
            if (buffered == null) return null;
            var out = new ByteArrayOutputStream();
            ImageIO.write(buffered, "PNG", out);
            return out.toByteArray();
        } catch (IOException e) {
            return null;
        }
    }

    public void close() {
        executor.shutdown();
        try {
            executor.awaitTermination(2, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        var textureManager = Minecraft.getInstance().getTextureManager();
        for (ResourceLocation loc : artCache.values()) textureManager.release(loc);
        for (ResourceLocation loc : cardCache.values()) textureManager.release(loc);
        artCache.clear();
        cardCache.clear();
    }
}
