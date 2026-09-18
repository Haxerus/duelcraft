package com.haxerus.duelcraft.client.collection;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.sql.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class CollectionCatalogTest {
    @TempDir Path directory;
    private Path database(boolean card) throws Exception {
        Class.forName("org.sqlite.JDBC");
        var path = directory.resolve("cards.cdb");
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + path); var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE datas(id INTEGER PRIMARY KEY, type INTEGER, atk INTEGER, def INTEGER, level INTEGER, race INTEGER, attribute INTEGER)");
            statement.execute("CREATE TABLE texts(id INTEGER PRIMARY KEY, name TEXT, desc TEXT)");
            if (card) {
                statement.execute("INSERT INTO datas VALUES(12, 1, 2000, 1500, 4, 4294967296, 16)");
                statement.execute("INSERT INTO texts VALUES(12, 'Dragon', 'Draw cards')");
            }
        }
        return path;
    }

    @Test void loadsImmutableMetadataAndClosesItsOwnConnection() throws Exception {
        var path = database(true);
        var cards = CollectionCatalog.load(path);
        assertEquals(1, cards.size());
        var card = cards.getFirst();
        assertEquals(12, card.code()); assertEquals("Dragon", card.name()); assertEquals("Draw cards", card.desc());
        assertEquals(2000, card.atk()); assertEquals(1500, card.def()); assertEquals(4, card.level());
        assertEquals(4294967296L, card.race()); assertEquals(16, card.attribute()); assertEquals(1, card.type());
        assertThrows(UnsupportedOperationException.class, () -> cards.clear());
        Files.delete(path);
        assertEquals("Dragon", cards.getFirst().name());
    }

    @Test void emptyDatabaseSucceedsAndMissingPathFailsWithoutCreatingAFile() throws Exception {
        assertEquals(List.of(), CollectionCatalog.load(database(false)));
        var missing = directory.resolve("missing.cdb");
        assertThrows(SQLException.class, () -> CollectionCatalog.load(missing));
        assertFalse(Files.exists(missing));
    }

    @Test void brokenSchemaFailsInsteadOfPretendingCatalogIsEmpty() throws Exception {
        var path = directory.resolve("broken.cdb");
        Files.writeString(path, "invalid sqlite");
        assertThrows(SQLException.class, () -> CollectionCatalog.load(path));
    }

    @Test void nullableTextRemainsUsableByPureSearch() throws Exception {
        var path = database(false);
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + path); var statement = connection.createStatement()) {
            statement.execute("INSERT INTO datas VALUES(19, 1, 0, 0, 4, 1, 1)");
            statement.execute("INSERT INTO texts VALUES(19, NULL, NULL)");
        }
        var cards = CollectionCatalog.load(path);
        assertEquals("19", cards.getFirst().name());
        assertEquals("", cards.getFirst().desc());
        assertEquals(cards, CardSearch.search(cards, "19", CardSearch.Filters.ALL, java.util.Map.of(),
                new com.haxerus.duelcraft.collection.DeckList(List.of(), List.of(), List.of())));
    }
}
