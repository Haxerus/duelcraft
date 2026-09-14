package com.haxerus.duelcraft.core.data;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/** Produces the same complete database for native card reads and client text/search. */
final class CardDatabaseMerger {
    private static final String DATA_COLUMNS = "id,ot,alias,setcode,type,atk,def,level,race,attribute,category";
    private static final String TEXT_COLUMNS = "id,name,desc," + IntStream.rangeClosed(1, 16)
            .mapToObj(i -> "str" + i).collect(Collectors.joining(","));

    static void merge(Path sources, Path destination) throws IOException, SQLException {
        Path official = sources.resolve("cards.cdb");
        validate(official);
        Files.copy(official, destination);
        List<Path> supplements;
        try (var files = Files.list(sources)) {
            supplements = files.filter(p -> p.toString().endsWith(".cdb") && !p.equals(official)).sorted().toList();
        }
        try (var db = open(destination); var sql = db.createStatement()) {
            for (Path source : supplements) {
                validate(source, true);
                try (var attach = db.prepareStatement("ATTACH DATABASE ? AS supplement")) {
                    attach.setString(1, source.toAbsolutePath().toString());
                    attach.execute();
                }
                // Official IDs win; supplementary duplicates use filename order, first wins.
                sql.executeUpdate("INSERT OR IGNORE INTO datas (" + DATA_COLUMNS + ") SELECT "
                        + DATA_COLUMNS + " FROM supplement.datas");
                sql.executeUpdate("INSERT OR IGNORE INTO texts (" + TEXT_COLUMNS + ") SELECT "
                        + TEXT_COLUMNS + " FROM supplement.texts");
                sql.execute("DETACH DATABASE supplement");
            }
        }
        validate(destination);
    }

    static void validate(Path path) throws IOException, SQLException {
        validate(path, false);
    }

    private static void validate(Path path, boolean allowEmpty) throws IOException, SQLException {
        if (!Files.isRegularFile(path) || Files.size(path) == 0) throw new IOException("Missing database: " + path);
        try (var db = open(path); var sql = db.createStatement()) {
            try (var result = sql.executeQuery("PRAGMA quick_check")) {
                if (!result.next() || !"ok".equals(result.getString(1))) throw new IOException("Corrupt database: " + path);
            }
            sql.executeQuery("SELECT " + DATA_COLUMNS + " FROM datas LIMIT 0").close();
            sql.executeQuery("SELECT " + TEXT_COLUMNS + " FROM texts LIMIT 0").close();
            try (var result = sql.executeQuery("SELECT (SELECT count(*) FROM datas), "
                    + "(SELECT count(*) FROM texts), (SELECT count(*) FROM datas JOIN texts USING(id))")) {
                if (!result.next() || (!allowEmpty && result.getLong(1) == 0) || result.getLong(1) != result.getLong(2)
                        || result.getLong(1) != result.getLong(3)) {
                    throw new IOException("Missing or unmatched card rows: " + path);
                }
            }
        }
    }

    private static Connection open(Path path) throws SQLException {
        try {
            // NeoForge's module loader does not discover the JDBC service automatically.
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            throw new SQLException("SQLite driver unavailable", e);
        }
        return DriverManager.getConnection("jdbc:sqlite:" + path.toAbsolutePath());
    }
}
