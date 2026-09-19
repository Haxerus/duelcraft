package com.haxerus.duelcraft.client.collection;
import com.haxerus.duelcraft.client.carddata.CardInfo;
import org.sqlite.SQLiteConfig;
import java.nio.file.*;
import java.sql.*;
import java.util.*;

/** Immutable metadata copied with an independent, short-lived read-only connection. */
public final class CollectionCatalog {
    private CollectionCatalog() {}

    public static List<CardInfo> load(Path database) throws SQLException {
        if (!Files.isRegularFile(database)) throw new SQLException("Card database does not exist: " + database);
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException exception) {
            throw new SQLException("SQLite driver unavailable", exception);
        }
        var config = new SQLiteConfig();
        config.setReadOnly(true);
        var cards = new ArrayList<CardInfo>();
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + database.toAbsolutePath(), config.toProperties());
             var statement = connection.createStatement();
             var rows = statement.executeQuery("""
                     SELECT d.id, t.name, t.desc, d.type, d.atk, d.def, d.level, d.race, d.attribute, d.ot
                     FROM datas d JOIN texts t ON d.id = t.id
                     """)) {
            while (rows.next()) {
                int code = rows.getInt("id");
                cards.add(new CardInfo(code, Objects.requireNonNullElse(rows.getString("name"), Integer.toString(code)),
                        Objects.requireNonNullElse(rows.getString("desc"), ""), rows.getInt("type"),
                        rows.getInt("atk"), rows.getInt("def"), rows.getInt("level"), rows.getLong("race"), rows.getInt("attribute"), rows.getInt("ot")));
            }
        }
        return List.copyOf(cards);
    }
}
