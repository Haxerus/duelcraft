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
 * Two-tier card image cache: disk files + GPU-registered DynamicTextures.
 * Downloads card art on-demand from ygoprodeck API in a background thread pool.
 * Returns null while loading (caller should show fallback).
 */
public class CardImageManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(CardImageManager.class);

    private final String baseUrl;
    private final Path cacheDir;
    private final Map<Integer, ResourceLocation> textureCache = new ConcurrentHashMap<>();
    private final Set<Integer> loading = ConcurrentHashMap.newKeySet();
    private final Set<Integer> failed = ConcurrentHashMap.newKeySet();
    private final ExecutorService executor = Executors.newFixedThreadPool(2,
            r -> { Thread t = new Thread(r, "CardImageLoader"); t.setDaemon(true); return t; });
    private final HttpClient httpClient = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public CardImageManager(String baseUrl, Path cacheDir) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
        this.cacheDir = cacheDir;
        try {
            Files.createDirectories(cacheDir);
        } catch (IOException e) {
            LOGGER.warn("Failed to create image cache directory: {}", e.getMessage());
        }
    }

    /**
     * Get the texture ResourceLocation for a card image.
     * Returns null if the image is not yet loaded (triggers async download).
     * Returns null for code 0 (face-down/unknown) or previously failed downloads.
     */
    public ResourceLocation getCardTexture(int code) {
        if (code == 0) return null;

        // L1: Already registered as DynamicTexture
        ResourceLocation cached = textureCache.get(code);
        if (cached != null) return cached;

        // Don't retry known failures
        if (failed.contains(code)) return null;

        // L2/L3: Async load from disk cache or download
        if (loading.add(code)) {
            Path diskFile = cacheDir.resolve(code + ".png");
            executor.submit(() -> {
                try {
                    if (Files.exists(diskFile)) {
                        loadFromDiskAsync(code, diskFile);
                    } else {
                        downloadAndCache(code);
                    }
                } finally {
                    loading.remove(code);
                }
            });
        }

        return null;
    }

    /** Check if a specific card's image is loaded and ready. */
    public boolean isLoaded(int code) {
        return textureCache.containsKey(code);
    }

    private void loadFromDiskAsync(int code, Path file) {
        try {
            byte[] data = Files.readAllBytes(file);
            Minecraft.getInstance().execute(() -> registerTexture(code, data));
        } catch (IOException e) {
            LOGGER.warn("Failed to load cached card image {}: {}", code, e.getMessage());
        }
    }

    private void downloadAndCache(int code) {
        try {
            String url = baseUrl + code + ".jpg";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(15))
                    .build();

            HttpResponse<byte[]> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofByteArray());

            if (response.statusCode() == 200) {
                // Convert JPEG → PNG (NativeImage only reads PNG)
                byte[] pngData = convertToPng(response.body());
                if (pngData == null) {
                    LOGGER.warn("Failed to convert card image for {}", code);
                    failed.add(code);
                    return;
                }
                // Save PNG to disk cache
                Files.write(cacheDir.resolve(code + ".png"), pngData);
                // Register texture on the render thread
                Minecraft.getInstance().execute(() -> registerTexture(code, pngData));
                LOGGER.debug("Downloaded card image for {}", code);
            } else {
                LOGGER.debug("Card image not found for {}: HTTP {}", code, response.statusCode());
                failed.add(code);
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to download card image for {}: {}", code, e.getMessage());
            failed.add(code);
        }
    }

    /** Convert any image format (JPEG, etc.) to PNG bytes for NativeImage compatibility. */
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

    private ResourceLocation registerTexture(int code, byte[] data) {
        try {
            NativeImage image = NativeImage.read(new ByteArrayInputStream(data));
            DynamicTexture texture = new DynamicTexture(image);
            ResourceLocation loc = ResourceLocation.fromNamespaceAndPath("duelcraft",
                    "dynamic/card_" + code);
            Minecraft.getInstance().getTextureManager().register(loc, texture);
            textureCache.put(code, loc);
            return loc;
        } catch (IOException e) {
            LOGGER.warn("Failed to decode card image for {}: {}", code, e.getMessage());
            failed.add(code);
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
        for (ResourceLocation loc : textureCache.values()) {
            textureManager.release(loc);
        }
        textureCache.clear();
    }
}
