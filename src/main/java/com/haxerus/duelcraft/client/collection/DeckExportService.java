package com.haxerus.duelcraft.client.collection;

import com.haxerus.duelcraft.collection.DeckList;
import com.haxerus.duelcraft.collection.DeckListWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Locale;

/** Client-local YDK output, contained in the configured deck directory. */
public final class DeckExportService {
    private final Path directory;

    public DeckExportService(Path directory) { this.directory = directory; }

    public static String suggestFileName(String deckName) {
        String base = deckName.replaceAll("[\\x00-\\x1f<>:\"/\\\\|?*]", "_");
        int end = Math.min(base.length(), 96);
        if (end > 0 && Character.isHighSurrogate(base.charAt(end - 1))) end--;
        base = trimEnd(base.substring(0, end));
        if (base.isBlank() || reserved(base)) base = "deck";
        return base + ".ydk";
    }

    public Path export(String fileName, DeckList cards, boolean replace) throws IOException {
        validate(fileName);
        String content = DeckListWriter.toYdk(cards);
        Files.createDirectories(directory);
        Path parent = directory.toRealPath();
        Path target = parent.resolve(fileName).normalize();
        if (!parent.equals(target.getParent())) throw new IOException("Choose a filename, not a path");
        checkTarget(target);
        if (!replace && Files.exists(target, LinkOption.NOFOLLOW_LINKS))
            throw new FileAlreadyExistsException(fileName);
        Path temporary = Files.createTempFile(parent, ".duelcraft-export-", ".tmp");
        try {
            Files.writeString(temporary, content, StandardCharsets.UTF_8);
            checkTarget(target);
            if (replace) {
                try {
                    Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                } catch (AtomicMoveNotSupportedException error) {
                    throw new IOException("This filesystem cannot safely replace an existing deck", error);
                }
            } else {
                Files.move(temporary, target);
            }
            return target;
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static void checkTarget(Path target) throws IOException {
        if (Files.isSymbolicLink(target) || Files.exists(target, LinkOption.NOFOLLOW_LINKS)
                && !Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS))
            throw new IOException("Export target must be a regular file, not a link or directory");
    }

    private static void validate(String name) throws IOException {
        if (name == null || !name.endsWith(".ydk") || name.length() <= 4 || name.length() > 100
                || name.chars().anyMatch(c -> c < 32 || "<>:\"/\\|?*".indexOf(c) >= 0))
            throw new IOException("Use a single .ydk filename with at most 96 characters before the extension");
        String base = name.substring(0, name.length() - 4);
        if (base.isBlank() || !base.equals(trimEnd(base)) || reserved(base))
            throw new IOException("Choose a filename without a reserved name or trailing dots/spaces");
    }

    private static String trimEnd(String name) { return name.replaceFirst("[. ]+$", ""); }

    private static boolean reserved(String base) {
        String stem = trimEnd(base.split("\\.", 2)[0]).toUpperCase(Locale.ROOT);
        return stem.matches("CON|PRN|AUX|NUL|COM[1-9¹²³]|LPT[1-9¹²³]");
    }
}
