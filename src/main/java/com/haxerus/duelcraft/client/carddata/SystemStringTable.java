package com.haxerus.duelcraft.client.carddata;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Loads EDOPro's {@code strings.conf} file — the global string table for
 * system strings, victory reasons, counter names, and archetype (setname)
 * names. The file is downloaded from a configured URL at startup, cached to
 * disk, and parsed into in-memory maps.
 *
 * <p>Robustness: if the network download fails, we fall back to the
 * last-cached copy. If there's no cached copy either, the table loads empty —
 * callers should provide a reasonable fallback (e.g., {@code "System #N"}).
 *
 * <p>File format (one directive per line; comments and blanks ignored):
 * <pre>{@code
 *   !system 1 Normal Summon
 *   !system 2 Special Summon
 *   !victory 0 Duel Lost
 *   !counter 0x1 Spell Counter
 *   !setname 0x91 Blue-Eyes
 * }</pre>
 */
public class SystemStringTable {

    private static final Logger LOGGER = LoggerFactory.getLogger(SystemStringTable.class);
    private static final String CACHE_FILENAME = "strings.conf";

    private final Map<Integer, String> system = new HashMap<>();
    private final Map<Integer, String> victory = new HashMap<>();
    private final Map<Integer, String> counter = new HashMap<>();
    private final Map<Integer, String> setname = new HashMap<>();

    /**
     * Load the table: try downloading from {@code url}, fall back to the on-disk
     * cache at {@code cacheDir/strings.conf} if the download fails, and leave the
     * table empty if both fail.
     */
    public SystemStringTable(String url, Path cacheDir) {
        Path cacheFile = cacheDir.resolve(CACHE_FILENAME);
        try {
            Files.createDirectories(cacheDir);
        } catch (IOException e) {
            LOGGER.warn("Could not create cache dir {}: {}", cacheDir, e.getMessage());
        }

        boolean loaded = false;
        byte[] fresh = tryDownload(url);
        if (fresh != null) {
            parse(new String(fresh, StandardCharsets.UTF_8));
            loaded = true;
            // Write-through to cache for offline use next time
            try {
                Path tmp = cacheDir.resolve(CACHE_FILENAME + ".tmp");
                Files.write(tmp, fresh);
                Files.move(tmp, cacheFile, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                LOGGER.warn("Failed to write strings.conf cache: {}", e.getMessage());
            }
        }

        if (!loaded && Files.exists(cacheFile)) {
            try {
                parse(Files.readString(cacheFile, StandardCharsets.UTF_8));
                loaded = true;
                LOGGER.info("Using cached strings.conf (network unavailable)");
            } catch (IOException e) {
                LOGGER.warn("Failed to read cached strings.conf: {}", e.getMessage());
            }
        }

        if (loaded) {
            LOGGER.info("SystemStringTable loaded: {} system, {} victory, {} counter, {} setname",
                    system.size(), victory.size(), counter.size(), setname.size());
        } else {
            LOGGER.warn("SystemStringTable failed to load — option prompts will show placeholder text");
        }
    }

    /** Returns the system string for the given code, or null if not present. */
    public String getSystem(int code) { return system.get(code); }

    /** Returns the victory reason string for the given code, or null if not present. */
    public String getVictory(int code) { return victory.get(code); }

    /** Returns the counter name for the given code, or null if not present. */
    public String getCounter(int code) { return counter.get(code); }

    /** Returns the archetype/setname for the given code, or null if not present. */
    public String getSetname(int code) { return setname.get(code); }

    // ── Internals ──────────────────────────────────────────────────────────

    private static byte[] tryDownload(String url) {
        try (HttpClient client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(10))
                .build()) {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(30))
                    .build();
            HttpResponse<byte[]> res = client.send(req, HttpResponse.BodyHandlers.ofByteArray());
            if (res.statusCode() == 200) {
                LOGGER.info("Downloaded strings.conf from {} ({} bytes)", url, res.body().length);
                return res.body();
            }
            LOGGER.warn("strings.conf download returned HTTP {}", res.statusCode());
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (Exception e) {
            LOGGER.warn("strings.conf download failed: {}", e.getMessage());
            return null;
        }
    }

    private void parse(String content) {
        for (String rawLine : content.split("\\r?\\n")) {
            String line = rawLine.strip();
            if (line.isEmpty() || line.startsWith("#")) continue;
            if (!line.startsWith("!")) continue;

            // Pattern: !directive <code> <text...>
            // Split on whitespace into at most 3 parts: directive, code, rest-of-line
            int sp1 = line.indexOf(' ');
            if (sp1 < 0) continue;
            int sp2 = line.indexOf(' ', sp1 + 1);
            if (sp2 < 0) continue;

            String directive = line.substring(1, sp1); // skip the leading '!'
            String codeStr = line.substring(sp1 + 1, sp2).strip();
            String text = line.substring(sp2 + 1).strip();

            Integer code = parseCode(codeStr);
            if (code == null) continue;

            switch (directive) {
                case "system"  -> system.put(code, text);
                case "victory" -> victory.put(code, text);
                case "counter" -> counter.put(code, text);
                case "setname" -> setname.put(code, text);
                default        -> { /* unknown directive — ignore */ }
            }
        }
    }

    private static Integer parseCode(String s) {
        try {
            if (s.startsWith("0x") || s.startsWith("0X")) {
                return Integer.parseUnsignedInt(s.substring(2), 16);
            }
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
