package com.haxerus.duelcraft.collection;

import com.haxerus.duelcraft.core.DeckLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeckListLoaderTest {
    @Test
    void retainsMainExtraAndSide() {
        var list = DeckListLoader.parseYdk("#main\n1\n#extra\n2\n!side\n3\n");

        assertEquals(List.of(1), list.main());
        assertEquals(List.of(2), list.extra());
        assertEquals(List.of(3), list.side());
    }

    @Test
    void loadsCompleteListFromFile(@TempDir Path dir) throws IOException {
        var file = dir.resolve("complete.ydk");
        Files.writeString(file, "#main\n1\n#extra\n2\n!side\n3\n");

        assertEquals(new DeckList(List.of(1), List.of(2), List.of(3)), DeckListLoader.loadFromFile(file));
    }

    @Test
    void malformedInputReportsItsLine() {
        var error = assertThrows(DeckLoader.DeckParseException.class,
                () -> DeckListLoader.parseYdk("#main\n1\nnot-a-passcode\n"));

        assertEquals(3, error.getLine());
        assertTrue(error.getMessage().contains("line 3"));
    }

    @Test
    void rejectsTheFiveHundredThirteenthCard() {
        var ydk = new StringBuilder("#main\n");
        IntStream.rangeClosed(1, 513).forEach(code -> ydk.append(code).append('\n'));

        var error = assertThrows(DeckLoader.DeckParseException.class,
                () -> DeckListLoader.parseYdk(ydk.toString()));

        assertEquals(514, error.getLine());
        assertTrue(error.getMessage().contains("512-card editor limit"));
    }
}
