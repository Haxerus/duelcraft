package com.haxerus.duelcraft.client.carddata;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

/**
 * Downloads cards.cdb from a remote URL (typically BabelCDB on GitHub)
 * to a local cache directory. Skips download if file already exists.
 */
public final class CardDatabaseDownloader {

    private static final Logger LOGGER = LoggerFactory.getLogger(CardDatabaseDownloader.class);

    private CardDatabaseDownloader() {}

    /**
     * Ensure cards.cdb exists at cacheDir/cards.cdb.
     * Downloads from url if not already cached.
     *
     * @return path to the local .cdb file
     */
    public static Path ensureDatabase(String url, Path cacheDir) throws IOException {
        Files.createDirectories(cacheDir);
        Path dbFile = cacheDir.resolve("cards.cdb");

        if (Files.exists(dbFile) && Files.size(dbFile) > 0) {
            LOGGER.info("Card database already cached at {}", dbFile);
            return dbFile;
        }

        LOGGER.info("Downloading card database from {}...", url);
        try (HttpClient client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(15))
                .build()) {

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(60))
                    .build();

            HttpResponse<Path> response = client.send(request,
                    HttpResponse.BodyHandlers.ofFile(dbFile));

            if (response.statusCode() != 200) {
                Files.deleteIfExists(dbFile);
                throw new IOException("Failed to download card database: HTTP " + response.statusCode());
            }

            LOGGER.info("Card database downloaded ({} bytes)", Files.size(dbFile));
            return dbFile;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            Files.deleteIfExists(dbFile);
            throw new IOException("Download interrupted", e);
        }
    }
}
