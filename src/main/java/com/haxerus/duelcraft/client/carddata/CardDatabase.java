package com.haxerus.duelcraft.client.carddata;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Reads card data from a ygopro-format SQLite card database (cards.cdb).
 * Point queries by card code, results cached in memory.
 */
public class CardDatabase implements AutoCloseable {

    private static final Logger LOGGER = LoggerFactory.getLogger(CardDatabase.class);

    // Explicitly load the SQLite JDBC driver. NeoForge's modular classloader
    // prevents automatic SPI discovery (META-INF/services), so DriverManager
    // won't find the driver without this.
    static {
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            LOGGER.error("sqlite-jdbc driver not found — is it bundled via JarJar?", e);
        }
    }

    private static final String QUERY = """
            SELECT d.id, t.name, t.desc, d.type, d.atk, d.def, d.level, d.race, d.attribute, d.ot
            FROM datas d JOIN texts t ON d.id = t.id
            WHERE d.id = ?
            """;

    private static final String SEARCH_SELECT = """
            SELECT d.id, t.name, t.desc, d.type, d.atk, d.def, d.level, d.race, d.attribute, d.ot, d.alias, d.setcode
            FROM datas d JOIN texts t ON d.id = t.id
            """;
    /** Indexed point lookup, so a passcode hit never depends on the name scan. */
    private static final String SEARCH_BY_ID = SEARCH_SELECT + "WHERE d.id = ?";
    private static final String SEARCH_BY_NAME = SEARCH_SELECT + "WHERE t.name LIKE ? ESCAPE '\\'";

    private final Connection connection;
    private final Map<Integer, CardInfo> cache = new ConcurrentHashMap<>();
    // Cache for card option strings (desc + str1..str16). Key: (code << 8) | offset.
    // Values stored as Optional to cache misses (null columns) efficiently.
    private final Map<Long, String> stringCache = new ConcurrentHashMap<>();
    private static final String MISSING_STRING = "\u0000MISSING\u0000"; // sentinel, NUL-delimited so no real string collides

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

    /**
     * Look up an option/effect string for a card by zero-based offset.
     * Offset {@code N} maps to column {@code str(N+1)} in the {@code texts} table,
     * matching the ygopro/edopro {@code aux.Stringid(code, N)} convention: the
     * {@code desc} column holds the card's full effect description (tooltip text)
     * and is NOT addressed by this method. Returns null if the string is empty
     * or the card/offset is out of range.
     */
    public String getCardString(int code, int offset) {
        if (code == 0 || offset < 0 || offset > 15) return null;
        long key = ((long) code << 8) | (offset & 0xFF);
        String cached = stringCache.get(key);
        if (cached != null) return cached == MISSING_STRING ? null : cached;
        String result = queryCardString(code, offset);
        stringCache.put(key, result != null ? result : MISSING_STRING);
        return result;
    }

    private String queryCardString(int code, int offset) {
        // Offset 0..15 maps to columns str1..str16
        String column = "str" + (offset + 1);
        String sql = "SELECT " + column + " FROM texts WHERE id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, code);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    String text = rs.getString(1);
                    return (text == null || text.isEmpty()) ? null : text;
                }
            }
        } catch (SQLException e) {
            LOGGER.warn("Failed to query card string (code={}, offset={}): {}", code, offset, e.getMessage());
        }
        return null;
    }

    /**
     * Cards an {@code MSG_ANNOUNCE_CARD} prompt would accept, matching {@code query} against the
     * passcode or as a name substring and filtering with {@link DeclarableFilter}. Exact name (or
     * passcode) matches come first, then partial ones, capped at {@code limit} — the same ordering
     * edopro uses in {@code client_field.cpp:1320-1370}. Case-insensitivity is sqlite's ASCII
     * {@code LIKE}; unlike edopro this is not accent-insensitive.
     */
    public List<CardInfo> searchDeclarable(String query, List<Long> opcodes, int limit) {
        String trimmed = query == null ? "" : query.trim();
        List<CardInfo> exact = new ArrayList<>();
        List<CardInfo> partial = new ArrayList<>();

        int passcode = parsePasscode(trimmed);
        if (passcode != 0) {
            CardInfo hit = declarableById(passcode, opcodes);
            if (hit != null) exact.add(hit);
        }

        // A blank query can never have an exact name match, and once one has been found there is no
        // reason to keep looking for another — either way the scan stops as soon as the partial list
        // is full, instead of filtering the whole table on every keystroke.
        boolean exactNamePossible = !trimmed.isEmpty();
        try (PreparedStatement stmt = connection.prepareStatement(SEARCH_BY_NAME)) {
            stmt.setString(1, "%" + escapeLike(trimmed) + "%");
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    if (partial.size() >= limit && !exactNamePossible) break;
                    CardInfo info = declarable(rs, opcodes);
                    if (info == null || info.code() == passcode) continue;
                    if (trimmed.equalsIgnoreCase(info.name())) {
                        exact.add(info);
                        exactNamePossible = false;
                    } else if (partial.size() < limit) {
                        partial.add(info);
                    }
                }
            }
        } catch (SQLException e) {
            LOGGER.warn("Declarable card search failed for '{}': {}", trimmed, e.getMessage());
            return List.of();
        }
        exact.addAll(partial);
        return exact.size() > limit ? List.copyOf(exact.subList(0, limit)) : exact;
    }

    private CardInfo declarableById(int code, List<Long> opcodes) {
        try (PreparedStatement stmt = connection.prepareStatement(SEARCH_BY_ID)) {
            stmt.setInt(1, code);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) return declarable(rs, opcodes);
            }
        } catch (SQLException e) {
            LOGGER.warn("Declarable lookup failed for passcode {}: {}", code, e.getMessage());
        }
        return null;
    }

    /** The row as a {@link CardInfo}, or null when the opcodes reject it. Feeds the code cache. */
    private CardInfo declarable(ResultSet rs, List<Long> opcodes) throws SQLException {
        var facts = new DeclarableFilter.CardFacts(
                rs.getInt("id"),
                rs.getInt("alias"),
                rs.getLong("setcode"),
                rs.getInt("type"),
                rs.getLong("race"),
                rs.getInt("attribute"));
        if (!DeclarableFilter.matches(opcodes, facts)) return null;
        CardInfo info = readCard(rs);
        cache.putIfAbsent(info.code(), info);
        return cache.get(info.code());
    }

    /** The passcode a query denotes, or 0 (never a real card code) when it is not a passcode. */
    private static int parsePasscode(String query) {
        try {
            return Integer.parseInt(query);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String escapeLike(String query) {
        return query.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private CardInfo queryCard(int code) {
        try (PreparedStatement stmt = connection.prepareStatement(QUERY)) {
            stmt.setInt(1, code);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return readCard(rs);
                }
            }
        } catch (SQLException e) {
            LOGGER.warn("Failed to query card {}: {}", code, e.getMessage());
        }
        return null;
    }

    private static CardInfo readCard(ResultSet rs) throws SQLException {
        return new CardInfo(
                rs.getInt("id"),
                rs.getString("name"),
                rs.getString("desc"),
                rs.getInt("type"),
                rs.getInt("atk"),
                rs.getInt("def"),
                rs.getInt("level"),
                rs.getLong("race"),
                rs.getInt("attribute"),
                rs.getInt("ot")
        );
    }

    @Override
    public void close() throws SQLException {
        if (connection != null && !connection.isClosed()) {
            connection.close();
        }
    }
}
