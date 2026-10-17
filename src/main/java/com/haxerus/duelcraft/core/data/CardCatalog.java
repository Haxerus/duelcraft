package com.haxerus.duelcraft.core.data;

import org.sqlite.SQLiteConfig;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

/** Host-policy facts copied from the managed database, without retaining a connection. */
public final class CardCatalog {
    public record Facts(int code, int type) {}

    private CardCatalog() {}

    public static Map<Integer, Facts> load(Path database) throws SQLException {
        try {
            // NeoForge's module loader does not discover the JDBC service automatically.
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException exception) {
            throw new SQLException("SQLite driver unavailable", exception);
        }
        var config = new SQLiteConfig();
        config.setReadOnly(true);
        var facts = new HashMap<Integer, Facts>();
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + database.toAbsolutePath(), config.toProperties());
             var statement = connection.createStatement();
             var rows = statement.executeQuery("SELECT id,type FROM datas")) {
            while (rows.next()) {
                int code = rows.getInt(1);
                facts.put(code, new Facts(code, rows.getInt(2)));
            }
        }
        return Map.copyOf(facts);
    }
}
