package com.haxerus.duelcraft.client.collection;

import com.haxerus.duelcraft.collection.DeckList;
import com.haxerus.duelcraft.collection.DeckListLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class DeckExportServiceTest {
    @TempDir Path root;
    private static final DeckList CARDS = new DeckList(List.of(7, 7, 7, 7), List.of(8), List.of(9));

    @Test void newFileIsCompleteUtf8AndLeavesNoTemporaryFile() throws IOException {
        Path dir = root.resolve("decks");
        Path output = new DeckExportService(dir).export("Étoile.ydk", CARDS, false);
        assertEquals(dir.resolve("Étoile.ydk"), output);
        assertEquals("#created by Duelcraft\n#main\n7\n7\n7\n7\n#extra\n8\n!side\n9\n",
                Files.readString(output, StandardCharsets.UTF_8));
        assertEquals(CARDS, DeckListLoader.loadFromFile(output));
        try (var files = Files.list(dir)) { assertEquals(List.of(output), files.toList()); }
    }

    @Test void collisionRequiresConfirmationAndPreservesOriginalBytes() throws IOException {
        Path output = Files.write(root.resolve("existing.ydk"), new byte[]{0, 1, 2, -1});
        assertThrows(FileAlreadyExistsException.class, () -> new DeckExportService(root).export("existing.ydk", CARDS, false));
        assertArrayEquals(new byte[]{0, 1, 2, -1}, Files.readAllBytes(output));
        try (var files = Files.list(root)) { assertEquals(List.of(output), files.toList()); }
    }

    @Test void confirmedReplacementIsCompleteAndLeavesNoTemporaryFile() throws IOException {
        Path output = Files.writeString(root.resolve("existing.ydk"), "old");
        assertEquals(output, new DeckExportService(root).export("existing.ydk", CARDS, true));
        assertEquals(CARDS, DeckListLoader.loadFromFile(output));
        try (var files = Files.list(root)) { assertEquals(List.of(output), files.toList()); }
    }

    @ParameterizedTest @ValueSource(strings = {"", "../escape.ydk", "..\\escape.ydk", "/absolute.ydk",
            "C:\\absolute.ydk", "C:drive.ydk", "folder/file.ydk", "folder\\file.ydk", "deck.txt", ".ydk",
            "CON.ydk", "con.more.ydk", "PRN.ydk", "AUX.ydk", "NUL.ydk", "COM1.ydk", "LPT9.ydk",
            "COM¹.ydk", "LPT².ydk", "bad?.ydk", "bad*.ydk", "bad|.ydk", "bad<.ydk", "bad>.ydk",
            "bad\".ydk", "name.ydk.", "name.ydk ", "name .ydk", "name..ydk", "\n.ydk", "a\nb\n.ydk"})
    void rejectsUnsafeSubmittedNamesWithoutCreatingAnything(String name) throws IOException {
        Path dir = root.resolve("decks");
        assertThrows(IOException.class, () -> new DeckExportService(dir).export(name, CARDS, false));
        try (var files = Files.list(root)) { assertEquals(0, files.count()); }
    }

    @Test void rejectsOverlongBaseName() {
        assertThrows(IOException.class, () -> new DeckExportService(root).export("a".repeat(97) + ".ydk", CARDS, false));
    }

    @Test void suggestionsReplaceInvalidCharactersTrimBoundAndAvoidDevices() {
        assertEquals("a_b_c_d_e_f_g_h_i_.ydk", DeckExportService.suggestFileName("a<b>c:d\"e/f\\g|h?i*"));
        assertEquals("Étoile.ydk", DeckExportService.suggestFileName("Étoile.  "));
        assertEquals("a".repeat(96) + ".ydk", DeckExportService.suggestFileName("a".repeat(128)));
        for (String name : List.of("", " . ", "CON", "con.txt", "LPT1", "COM²"))
            assertEquals("deck.ydk", DeckExportService.suggestFileName(name));
    }

    @Test void suggestionDoesNotSplitASupplementaryCharacterAtTheLengthLimit() {
        assertEquals("a".repeat(95) + ".ydk", DeckExportService.suggestFileName("a".repeat(95) + "\uD83D\uDE00"));
    }

    @Test void rejectsDirectoryTargetEvenWhenReplacementWasConfirmed() throws IOException {
        Path target = Files.createDirectory(root.resolve("folder.ydk"));
        assertThrows(IOException.class, () -> new DeckExportService(root).export("folder.ydk", CARDS, true));
        assertTrue(Files.isDirectory(target));
        try (var files = Files.list(root)) { assertEquals(List.of(target), files.toList()); }
    }

    @Test void invalidParentDoesNotModifyExistingFile() throws IOException {
        Path parent = Files.writeString(root.resolve("parent"), "unchanged");
        assertThrows(IOException.class, () -> new DeckExportService(parent.resolve("decks")).export("draft.ydk", CARDS, false));
        assertEquals("unchanged", Files.readString(parent));
    }

    @Test void preparationFailureDoesNotTouchExistingDeck() throws IOException {
        Path target = Files.writeString(root.resolve("old.ydk"), "unchanged");
        // Invalid draft input cannot complete serialization, so replacement must never begin.
        assertThrows(NullPointerException.class, () -> new DeckExportService(root).export("old.ydk", null, true));
        assertEquals("unchanged", Files.readString(target));
        try (var files = Files.list(root)) { assertEquals(List.of(target), files.toList()); }
    }

    @Test void lockedTargetFailureLeavesOldBytesAndCleansTemporaryFile() throws IOException {
        Path target = Files.writeString(root.resolve("locked.ydk"), "unchanged");
        assumeTrue(System.getProperty("os.name").startsWith("Windows"), "Windows file lock behavior");
        try (var channel = FileChannel.open(target, StandardOpenOption.WRITE); var lock = channel.lock()) {
            assertThrows(IOException.class, () -> new DeckExportService(root).export("locked.ydk", CARDS, true));
        }
        assertEquals("unchanged", Files.readString(target));
        try (var files = Files.list(root)) { assertEquals(List.of(target), files.toList()); }
    }

    @Test void deniedTemporaryFileCreationLeavesExistingDeckUntouched() throws IOException {
        Path target = Files.writeString(root.resolve("old.ydk"), "unchanged");
        var acl = Files.getFileAttributeView(root, AclFileAttributeView.class);
        assumeTrue(acl != null, "Filesystem has no ACL support");
        var original = acl.getAcl();
        var denied = new java.util.ArrayList<>(original);
        denied.addFirst(AclEntry.newBuilder().setType(AclEntryType.DENY).setPrincipal(acl.getOwner())
                .setPermissions(AclEntryPermission.ADD_FILE).build());
        try {
            acl.setAcl(denied);
            assertThrows(AccessDeniedException.class, () -> Files.createTempFile(root, "probe", ".tmp"));
            assertThrows(IOException.class, () -> new DeckExportService(root).export("old.ydk", CARDS, true));
            assertEquals("unchanged", Files.readString(target));
        } finally {
            acl.setAcl(original);
        }
        try (var files = Files.list(root)) { assertEquals(List.of(target), files.toList()); }
    }

    @Test void rejectsSymlinkTargetWithoutChangingOutsideFile() throws IOException {
        Path outside = Files.writeString(root.resolve("outside.ydk"), "unchanged");
        Path dir = Files.createDirectory(root.resolve("decks"));
        try { Files.createSymbolicLink(dir.resolve("link.ydk"), outside); }
        catch (IOException | UnsupportedOperationException | SecurityException error) {
            assumeTrue(false, "Symlink creation unavailable: " + error.getMessage());
        }
        assertThrows(IOException.class, () -> new DeckExportService(dir).export("link.ydk", CARDS, true));
        assertEquals("unchanged", Files.readString(outside));
    }
}
