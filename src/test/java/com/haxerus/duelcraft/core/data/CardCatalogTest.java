package com.haxerus.duelcraft.core.data;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.sql.SQLException;
import static org.junit.jupiter.api.Assertions.*;

class CardCatalogTest {
    @TempDir Path directory;

    @Test void loadsAnImmutableSnapshotAndClosesTheDatabase() throws Exception {
        Class.forName("org.sqlite.JDBC");
        var path = directory.resolve("cards.cdb");
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + path);
             var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE datas (id INTEGER PRIMARY KEY, type INTEGER)");
            statement.execute("INSERT INTO datas VALUES (1,1),(2,65),(3,67108865)");
        }
        var bytes = Files.readAllBytes(path);
        var facts = CardCatalog.load(path);
        assertEquals(3, facts.size());
        assertEquals(new CardCatalog.Facts(2, 65), facts.get(2));
        assertEquals(67108865, facts.get(3).type());
        assertThrows(UnsupportedOperationException.class, () -> facts.clear());
        assertArrayEquals(bytes, Files.readAllBytes(path));
        Files.delete(path);
        assertEquals(1, facts.get(1).type());
    }

    @Test void missingDatabaseIsRejectedWithoutCreatingAFile() {
        var path = directory.resolve("missing.cdb");
        assertThrows(SQLException.class, () -> CardCatalog.load(path));
        assertFalse(Files.exists(path));
    }

    @Test void rejectsAnIncompatibleDatabase() throws Exception {
        Class.forName("org.sqlite.JDBC");
        var path = directory.resolve("wrong.cdb");
        try (var ignored = DriverManager.getConnection("jdbc:sqlite:" + path)) {}
        assertThrows(SQLException.class, () -> CardCatalog.load(path));
    }
}
