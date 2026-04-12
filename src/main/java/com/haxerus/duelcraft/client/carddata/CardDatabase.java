package com.haxerus.duelcraft.client.carddata;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.sql.*;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Reads card data from a ygopro-format SQLite card database (cards.cdb).
 * Point queries by card code, results cached in memory.
 */
public class CardDatabase implements AutoCloseable {

    private static final Logger LOGGER = LoggerFactory.getLogger(CardDatabase.class);

    private static final String QUERY = """
            SELECT d.id, t.name, t.desc, d.type, d.atk, d.def, d.level, d.race, d.attribute
            FROM datas d JOIN texts t ON d.id = t.id
            WHERE d.id = ?
            """;

    private final Connection connection;
    private final Map<Integer, CardInfo> cache = new ConcurrentHashMap<>();

    public CardDatabase(Path dbPath) throws SQLException {
        this.connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath.toAbsolutePath());
    }

    /**
     * Look up a card by code. Returns null if not found.
     * Results are cached — repeated calls for the same code return the same instance.
     */
    public CardInfo getCard(int code) {
        if (code == 0) return null;
        CardInfo cached = cache.get(code);
        if (cached != null) return cached;
        CardInfo result = queryCard(code);
        if (result != null) {
            cache.putIfAbsent(code, result);
            return cache.get(code);
        }
        return null;
    }

    private CardInfo queryCard(int code) {
        try (PreparedStatement stmt = connection.prepareStatement(QUERY)) {
            stmt.setInt(1, code);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return new CardInfo(
                            rs.getInt("id"),
                            rs.getString("name"),
                            rs.getString("desc"),
                            rs.getInt("type"),
                            rs.getInt("atk"),
                            rs.getInt("def"),
                            rs.getInt("level"),
                            rs.getLong("race"),
                            rs.getInt("attribute")
                    );
                }
            }
        } catch (SQLException e) {
            LOGGER.warn("Failed to query card {}: {}", code, e.getMessage());
        }
        return null;
    }

    @Override
    public void close() throws SQLException {
        if (connection != null && !connection.isClosed()) {
            connection.close();
        }
    }
}
