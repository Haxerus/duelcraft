package com.haxerus.duelcraft.core.data;

import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipFile;

import static java.nio.file.StandardCopyOption.ATOMIC_MOVE;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

/** Downloads complete, revision-pinned upstream collections before publishing a usable snapshot. */
public final class CardDataCache {
    private static final Logger LOGGER = LoggerFactory.getLogger(CardDataCache.class);
    private static final long MAX_ARCHIVE_BYTES = 256L * 1024 * 1024;
    private static final long MAX_EXTRACTED_BYTES = 512L * 1024 * 1024;
    private final HttpClient http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(15)).build();
    private final URI apiBase;
    private final URI archiveBase;

    public record Snapshot(Path database, List<String> scriptPaths) {}

    public CardDataCache() {
        this(URI.create("https://api.github.com/repos/ProjectIgnis/"),
                URI.create("https://codeload.github.com/ProjectIgnis/"));
    }

    CardDataCache(URI apiBase, URI archiveBase) {
        this.apiBase = apiBase;
        this.archiveBase = archiveBase;
    }

    public Snapshot prepare(Path cacheDir) throws IOException {
        Path root = cacheDir.toAbsolutePath().normalize();
        Files.createDirectories(root);
        Path previous = current(root);
        Path staging = null;
        try {
            String scriptsRevision = revision("CardScripts");
            String databaseRevision = revision("BabelCDB");
            String revisions = scriptsRevision + "\n" + databaseRevision;
            if (previous != null && Files.readString(previous.resolve("revisions")).equals(revisions)) {
                LOGGER.info("Card data is current: {}", previous);
                return snapshot(previous);
            }
            LOGGER.info("Downloading card data: CardScripts {}, BabelCDB {}", scriptsRevision, databaseRevision);
            staging = Files.createTempDirectory(root, ".download-");
            download("CardScripts", scriptsRevision, staging.resolve("scripts"), true);
            download("BabelCDB", databaseRevision, staging.resolve("databases"), false);
            CardDatabaseMerger.merge(staging.resolve("databases"), staging.resolve("cards.cdb"));
            Files.writeString(staging.resolve("revisions"), revisions);
            Files.writeString(staging.resolve("script-count"), Long.toString(luaFiles(staging.resolve("scripts")).size()));
            snapshot(staging);

            Path published = root.resolve("snapshot-" + UUID.randomUUID());
            Files.move(staging, published, ATOMIC_MOVE);
            staging = null;
            Path pointer = Files.createTempFile(root, ".current-", ".tmp");
            try {
                Files.writeString(pointer, published.getFileName().toString());
                Files.move(pointer, root.resolve("current"), ATOMIC_MOVE, REPLACE_EXISTING);
            } finally {
                Files.deleteIfExists(pointer);
            }
            prune(root, published);
            LOGGER.info("Card data ready: {}", published);
            return snapshot(published);
        } catch (IOException | SQLException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            if (previous != null) {
                LOGGER.warn("Card data update failed; using cached snapshot {}", previous, e);
                try { return snapshot(previous); }
                catch (IOException | SQLException invalidCache) { e.addSuppressed(invalidCache); }
            }
            throw new IOException("Unable to prepare Duelcraft card data. Connect to the internet and restart "
                    + "Minecraft/the server to download ProjectIgnis/CardScripts and ProjectIgnis/BabelCDB. Cache: " + root, e);
        } finally {
            if (staging != null) {
                try { delete(staging); }
                catch (IOException cleanupFailure) {
                    LOGGER.warn("Could not remove incomplete card-data download {}", staging, cleanupFailure);
                }
            }
        }
    }

    /** Reclaims superseded snapshots and downloads abandoned by a killed process. */
    private void prune(Path root, Path published) {
        List<Path> stale;
        try (var entries = Files.list(root)) {
            stale = entries.filter(p -> !p.equals(published)
                    && !p.getFileName().toString().equals("current")).toList();
        } catch (IOException e) {
            LOGGER.warn("Could not list card data cache {}", root, e);
            return;
        }
        for (Path path : stale) {
            try { delete(path); }
            catch (IOException e) { LOGGER.warn("Could not remove stale card data {}", path, e); }
        }
    }

    private static void delete(Path directory) throws IOException {
        try (var paths = Files.walk(directory)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
        }
    }

    private Path current(Path root) {
        try {
            String name = Files.readString(root.resolve("current")).strip();
            if (!name.matches("snapshot-[0-9a-f-]{36}")) return null;
            Path path = root.resolve(name);
            snapshot(path);
            return path;
        } catch (IOException | SQLException e) {
            return null;
        }
    }

    private Snapshot snapshot(Path directory) throws IOException, SQLException {
        Path scripts = directory.resolve("scripts");
        for (String base : List.of("constant.lua", "utility.lua")) {
            if (!Files.isRegularFile(scripts.resolve(base)) || Files.size(scripts.resolve(base)) == 0) {
                throw new IOException("Missing base script: " + base);
            }
        }
        List<Path> lua = luaFiles(scripts);
        for (Path script : lua) {
            if (Files.size(script) == 0) throw new IOException("Empty card script: " + script);
        }
        if (!Files.readString(directory.resolve("script-count")).equals(Integer.toString(lua.size()))) {
            throw new IOException("Incomplete script snapshot: " + directory);
        }
        List<String> searchPaths = lua.stream().map(Path::getParent).distinct()
                .sorted(Comparator.<Path>comparingInt(p -> p.equals(scripts) ? 0
                        : p.equals(scripts.resolve("official")) ? 1 : 2).thenComparing(Path::toString))
                .map(Path::toString).toList();
        Path database = directory.resolve("cards.cdb");
        CardDatabaseMerger.validate(database);
        return new Snapshot(database, searchPaths);
    }

    private static List<Path> luaFiles(Path scripts) throws IOException {
        try (var files = Files.walk(scripts)) {
            return files.filter(p -> Files.isRegularFile(p) && p.toString().endsWith(".lua")).toList();
        }
    }

    private String revision(String repository) throws IOException, InterruptedException {
        var response = http.send(request(apiBase.resolve(repository + "/git/ref/heads/master")),
                HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) throw new IOException(repository + " revision HTTP " + response.statusCode());
        try {
            String sha = JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("object")
                    .get("sha").getAsString();
            if (!sha.matches("[0-9a-f]{40}")) throw new IllegalArgumentException("Invalid commit ID");
            return sha;
        } catch (RuntimeException e) {
            throw new IOException("Invalid revision response for " + repository, e);
        }
    }

    private void download(String repository, String revision, Path destination, boolean scripts)
            throws IOException, InterruptedException {
        Path archive = destination.resolveSibling(repository + ".zip");
        var response = http.send(request(archiveBase.resolve(repository + "/zip/" + revision)),
                HttpResponse.BodyHandlers.ofFile(archive));
        if (response.statusCode() != 200) throw new IOException(repository + " archive HTTP " + response.statusCode());
        if (Files.size(archive) > MAX_ARCHIVE_BYTES) throw new IOException("Archive too large: " + repository);
        Files.createDirectories(destination);
        LOGGER.info("Extracting {} into {}", repository, destination);
        long expanded = 0;
        try (var zip = new ZipFile(archive.toFile())) {
            var entries = zip.entries();
            while (entries.hasMoreElements()) {
                var entry = entries.nextElement();
                String name = entry.getName();
                int slash = name.indexOf('/');
                if (slash < 0) throw new IOException("Missing archive root: " + name);
                String relative = name.substring(slash + 1);
                if (relative.isEmpty()) continue;
                Path target = destination.resolve(relative).normalize();
                if (relative.contains("\\") || relative.contains(":") || !target.startsWith(destination)) {
                    throw new IOException("Unsafe archive path: " + name);
                }
                if (entry.isDirectory()) continue;
                boolean attribution = relative.equals("COPYING") || relative.equals("LICENSE") || relative.equals("README.md");
                if (!attribution && !(scripts ? relative.endsWith(".lua")
                        : !relative.contains("/") && relative.endsWith(".cdb"))) continue;
                if (entry.getSize() < 0 || entry.getSize() > MAX_EXTRACTED_BYTES - expanded) {
                    throw new IOException("Expanded archive too large: " + repository);
                }
                Files.createDirectories(target.getParent());
                try (var input = zip.getInputStream(entry); var output = Files.newOutputStream(target)) {
                    long copied = 0;
                    byte[] buffer = new byte[8192];
                    for (int read; (read = input.read(buffer)) != -1;) {
                        copied += read;
                        if (copied > entry.getSize()) throw new IOException("Oversized archive entry: " + name);
                        output.write(buffer, 0, read);
                    }
                    if (copied != entry.getSize()) throw new IOException("Truncated archive entry: " + name);
                    expanded += copied;
                }
            }
        }
        Files.delete(archive);
    }

    private static HttpRequest request(URI uri) {
        return HttpRequest.newBuilder(uri).timeout(Duration.ofMinutes(3))
                .header("User-Agent", "Duelcraft-card-data").GET().build();
    }
}
