package com.haxerus.duelcraft.core.data;

import com.haxerus.duelcraft.client.carddata.CardDatabase;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.*;

class CardDataCacheTest {
    @TempDir Path temp;
    private HttpServer server;
    private CardDataCache cache;
    private String revision = "a".repeat(40);
    private byte[] scripts;
    private byte[] databases;
    private int archiveRequests;
    private boolean offline;

    @BeforeEach
    void setup() throws Exception {
        Class.forName("org.sqlite.JDBC");
        scripts = zip(Map.of(
                "repo/constant.lua", bytes("-- constants"),
                "repo/utility.lua", bytes("-- utility"),
                "repo/official/c1.lua", bytes("-- official"),
                "repo/unofficial/c1.lua", bytes("-- unofficial"),
                "repo/goat/c3.lua", bytes("-- goat")));
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("repo/cards.cdb", database("official", Map.of(1, "Official")));
        entries.put("repo/cards-unofficial.cdb", database("unofficial", Map.of(1, "Old", 2, "Anime")));
        entries.put("repo/goat-entries.cdb", database("goat", Map.of(3, "GOAT")));
        entries.put("repo/prerelease-new.cdb", database("prerelease", Map.of(4, "Preview")));
        databases = zip(entries);
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            byte[] body;
            int status = offline ? 503 : 200;
            if (path.contains("/git/ref/")) {
                body = bytes("{\"object\":{\"sha\":\"" + revision + "\"}}");
            } else {
                archiveRequests++;
                body = path.contains("CardScripts") ? scripts : databases;
            }
            exchange.sendResponseHeaders(status, body.length);
            try (var output = exchange.getResponseBody()) { output.write(body); }
        });
        server.start();
        URI base = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/");
        cache = new CardDataCache(base, base);
    }

    @AfterEach
    void stop() { if (server != null) server.stop(0); }

    @Test
    void downloadsFullCollectionWithOfficialPrecedenceAndClientReadableTexts() throws Exception {
        var data = cache.prepare(temp.resolve("cache"));
        try (var db = new CardDatabase(data.database())) {
            assertEquals("Official", db.getCard(1).name());
            assertEquals("Anime", db.getCard(2).name());
            assertEquals("GOAT", db.getCard(3).name());
            assertEquals("Preview", db.getCard(4).name());
            assertEquals("effect text", db.getCard(3).desc());
        }
        assertTrue(Files.exists(Path.of(data.scriptPaths().getFirst()).resolve("constant.lua")));
        Path firstMatch = data.scriptPaths().stream().map(Path::of).map(p -> p.resolve("c1.lua"))
                .filter(Files::exists).findFirst().orElseThrow();
        assertEquals("-- official", Files.readString(firstMatch));
        assertTrue(data.scriptPaths().stream().anyMatch(p -> Files.exists(Path.of(p, "c3.lua"))));
    }

    @Test
    void emptySupplementDoesNotPreventLoadingOfficialCards() throws Exception {
        databases = zip(Map.of("repo/cards.cdb", database("base", Map.of(1, "Official")),
                "repo/prerelease-empty.cdb", database("empty", Map.of())));
        try (var db = new CardDatabase(cache.prepare(temp.resolve("cache")).database())) {
            assertEquals("Official", db.getCard(1).name());
        }
    }

    @Test
    void mismatchedCardAndTextRowsCannotBecomeActive() throws Exception {
        database("unmatched", Map.of(1, "Official"));
        try (var db = DriverManager.getConnection("jdbc:sqlite:" + temp.resolve("unmatched.cdb"));
             var sql = db.createStatement()) { sql.executeUpdate("DELETE FROM texts"); }
        databases = zip(Map.of("repo/cards.cdb", Files.readAllBytes(temp.resolve("unmatched.cdb"))));
        assertThrows(java.io.IOException.class, () -> cache.prepare(temp.resolve("cache")));
    }

    @Test
    void unchangedRevisionsReuseSnapshotWithoutDownloadingArchives() throws Exception {
        var first = cache.prepare(temp.resolve("cache"));
        assertEquals(first, cache.prepare(temp.resolve("cache")));
        assertEquals(2, archiveRequests);
    }

    @Test
    void updatePublishesNewDataAndKeepsServingItOffline() throws Exception {
        var first = cache.prepare(temp.resolve("cache"));
        revision = "b".repeat(40);
        databases = zip(Map.of("repo/cards.cdb", database("updated", Map.of(1, "Updated"))));
        var updated = cache.prepare(temp.resolve("cache"));
        assertNotEquals(first.database(), updated.database());
        try (var db = new CardDatabase(updated.database())) {
            assertEquals("Updated", db.getCard(1).name());
        }
        offline = true;
        assertEquals(updated, cache.prepare(temp.resolve("cache")));
    }

    @Test
    void publishingRemovesSupersededSnapshotsAndAbandonedDownloads() throws Exception {
        Path root = temp.resolve("cache");
        var first = cache.prepare(root);
        Path abandoned = Files.createDirectory(root.resolve(".download-killed"));
        Files.writeString(abandoned.resolve("CardScripts.zip"), "partial");
        revision = "b".repeat(40);
        var updated = cache.prepare(root);
        try (var entries = Files.list(root)) {
            assertEquals(List.of("current", updated.database().getParent().getFileName().toString()),
                    entries.map(p -> p.getFileName().toString()).sorted().toList());
        }
        assertFalse(Files.exists(first.database()));
    }

    @Test
    void offlineStartupUsesPreviouslyValidatedCache() throws Exception {
        var first = cache.prepare(temp.resolve("cache"));
        offline = true;
        assertEquals(first, cache.prepare(temp.resolve("cache")));
    }

    @Test
    void offlineFirstRunFailsWithoutPublishingCache() {
        offline = true;
        assertThrows(java.io.IOException.class, () -> cache.prepare(temp.resolve("cache")));
        assertFalse(Files.exists(temp.resolve("cache/current")));
    }

    @Test
    void malformedDatabaseUpdatePreservesWorkingSnapshot() throws Exception {
        var first = cache.prepare(temp.resolve("cache"));
        revision = "b".repeat(40);
        databases = zip(Map.of("repo/cards.cdb", bytes("not SQLite")));
        assertEquals(first, cache.prepare(temp.resolve("cache")));
        offline = true;
        assertEquals(first, cache.prepare(temp.resolve("cache")));
    }

    @Test
    void missingBaseScriptsCannotBecomeActive() throws Exception {
        scripts = zip(Map.of("repo/official/c1.lua", bytes("-- card")));
        assertThrows(java.io.IOException.class, () -> cache.prepare(temp.resolve("cache")));
        assertFalse(Files.exists(temp.resolve("cache/current")));
    }

    @Test
    void traversalArchiveIsRejectedWithoutWritingOutsideStaging() throws Exception {
        scripts = zip(Map.of("repo/../../escaped.lua", bytes("-- unsafe")));
        assertThrows(java.io.IOException.class, () -> cache.prepare(temp.resolve("cache")));
        assertFalse(Files.exists(temp.resolve("cache/escaped.lua")));
        assertFalse(Files.exists(temp.resolve("escaped.lua")));
    }

    @Test
    void missingCachedScriptIsRepairedEvenWhenUpstreamRevisionIsUnchanged() throws Exception {
        var first = cache.prepare(temp.resolve("cache"));
        Files.delete(Path.of(first.scriptPaths().getFirst()).resolve("constant.lua"));
        var repaired = cache.prepare(temp.resolve("cache"));
        assertTrue(Files.exists(Path.of(repaired.scriptPaths().getFirst()).resolve("constant.lua")));
        assertEquals(4, archiveRequests);
    }

    @Test
    void truncatedCachedCardScriptIsRepairedEvenWhenFileCountIsUnchanged() throws Exception {
        var first = cache.prepare(temp.resolve("cache"));
        Path card = first.scriptPaths().stream().map(Path::of).map(p -> p.resolve("c3.lua"))
                .filter(Files::exists).findFirst().orElseThrow();
        Files.writeString(card, "");
        var repaired = cache.prepare(temp.resolve("cache"));
        Path restored = repaired.scriptPaths().stream().map(Path::of).map(p -> p.resolve("c3.lua"))
                .filter(Files::exists).findFirst().orElseThrow();
        assertEquals("-- goat", Files.readString(restored));
        assertEquals(4, archiveRequests);
    }

    @Test
    void corruptCacheIsNotUsedAsOfflineFallback() throws Exception {
        var first = cache.prepare(temp.resolve("cache"));
        Files.writeString(first.database(), "broken");
        offline = true;
        assertThrows(java.io.IOException.class, () -> cache.prepare(temp.resolve("cache")));
    }

    private byte[] database(String file, Map<Integer, String> cards) throws Exception {
        Path path = temp.resolve(file + ".cdb");
        try (var db = DriverManager.getConnection("jdbc:sqlite:" + path); var sql = db.createStatement()) {
            sql.execute("CREATE TABLE datas (id INTEGER PRIMARY KEY, ot INTEGER, alias INTEGER, setcode INTEGER, "
                    + "type INTEGER, atk INTEGER, def INTEGER, level INTEGER, race INTEGER, attribute INTEGER, category INTEGER)");
            StringBuilder schema = new StringBuilder("CREATE TABLE texts (id INTEGER PRIMARY KEY, name TEXT, desc TEXT");
            for (int i = 1; i <= 16; i++) schema.append(", str").append(i).append(" TEXT");
            sql.execute(schema + ")");
            for (var card : cards.entrySet()) {
                sql.execute("INSERT INTO datas VALUES (" + card.getKey() + ", 3, 0, 0, 1, 1000, 1000, 4, 1, 1, 0)");
                try (var insert = db.prepareStatement("INSERT INTO texts (id,name,desc) VALUES (?, ?, 'effect text')")) {
                    insert.setInt(1, card.getKey());
                    insert.setString(2, card.getValue());
                    insert.executeUpdate();
                }
            }
        }
        return Files.readAllBytes(path);
    }

    private static byte[] bytes(String value) { return value.getBytes(UTF_8); }

    private static byte[] zip(Map<String, byte[]> entries) throws Exception {
        var output = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(output)) {
            for (var entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue());
                zip.closeEntry();
            }
        }
        return output.toByteArray();
    }
}
