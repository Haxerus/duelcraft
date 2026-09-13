package com.haxerus.duelcraft.core;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Cross-checks every {@link OcgConstants} field whose name carries one of the wire-format
 * prefixes below against the value {@code #define}'d in the ygopro-core submodule header, so a
 * hand-transcription slip or an engine bump cannot silently drift the two apart.
 *
 * <p>The header ({@code native/ygopro-core/ocgapi_constants.h}) is entirely {@code #define NAME
 * value}; most values are plain decimal or hex literals, a handful (e.g. {@code POS_FACEUP},
 * {@code LOCATION_ONFIELD}, {@code ATTRIBUTE_ALL}) are a parenthesised {@code A | B | ...} of
 * already-defined names. {@link #eval} resolves both shapes; anything it cannot evaluate (casts,
 * shifts, subtraction, e.g. {@code RACE_ALL}) is left out of the comparison rather than failing —
 * none of those are mirrored as a same-named {@code OcgConstants} field anyway.
 */
class OcgConstantsHeaderTest {

    private static final Path HEADER_RELATIVE = Path.of("native/ygopro-core/ocgapi_constants.h");

    /**
     * Gradle's NeoForge {@code unitTest} feature runs the {@code test} task from the {@code run/}
     * directory (like {@code runClient}), not the project root, so the relative path above only
     * resolves by walking upward from the working directory to find it.
     */
    private static Path resolveHeader() {
        Path dir = Path.of("").toAbsolutePath();
        for (int i = 0; i < 6 && dir != null; i++) {
            Path candidate = dir.resolve(HEADER_RELATIVE);
            if (Files.exists(candidate)) return candidate;
            dir = dir.getParent();
        }
        return HEADER_RELATIVE;
    }

    private static final Path HEADER = resolveHeader();

    private static final String[] PREFIXES = {
            "MSG_", "QUERY_", "LOCATION_", "POS_", "HINT_", "CHINT_", "PHINT_",
            "OPCODE_", "TYPE_", "RACE_", "ATTRIBUTE_"
    };

    private static final Pattern DEFINE = Pattern.compile("^#define\\s+(\\w+)(?:\\s+(.+))?$");

    @Test
    void everyMatchingConstantHasTheHeadersValue() throws IOException {
        assumeTrue(Files.exists(HEADER), "ygopro-core submodule not checked out; skipping header cross-check");

        Map<String, String> rawDefines = new LinkedHashMap<>();
        for (String rawLine : Files.readAllLines(HEADER)) {
            String line = rawLine.strip();
            Matcher m = DEFINE.matcher(line);
            if (!m.matches()) continue;
            String value = m.group(2);
            if (value == null) continue; // bare include-guard style define
            int comment = value.indexOf("//");
            if (comment >= 0) value = value.substring(0, comment);
            rawDefines.put(m.group(1), value.strip());
        }
        assertFalse(rawDefines.isEmpty(), "Regex found no #define lines; has the header's format changed?");

        int checked = 0;
        List<String> mismatches = new ArrayList<>();
        for (String name : rawDefines.keySet()) {
            if (!hasMatchingPrefix(name)) continue;

            Field field;
            try {
                field = OcgConstants.class.getField(name);
            } catch (NoSuchFieldException e) {
                continue; // not every header constant is mirrored in Java
            }

            Long headerValue = eval(name, rawDefines, new LinkedHashMap<>(), new HashSet<>());
            if (headerValue == null) continue; // an expression this test does not evaluate

            long javaValue;
            try {
                javaValue = field.getLong(null);
            } catch (IllegalAccessException e) {
                throw new AssertionError(e);
            }
            if (field.getType() == int.class) javaValue &= 0xFFFFFFFFL; // header ints are unsigned 32-bit

            checked++;
            if (javaValue != headerValue) {
                mismatches.add(String.format("%s: header=0x%x java=0x%x", name, headerValue, javaValue));
            }
        }

        assertTrue(checked > 100, "Expected to cross-check well over 100 constants, only checked " + checked);
        assertEquals(List.of(), mismatches, "OcgConstants values differ from ocgapi_constants.h");
    }

    private static boolean hasMatchingPrefix(String name) {
        for (String p : PREFIXES) {
            if (name.startsWith(p)) return true;
        }
        return false;
    }

    /** Resolve one #define's value, memoising into {@code cache}; null if unresolvable. */
    private static Long eval(String name, Map<String, String> raw, Map<String, Long> cache, Set<String> inProgress) {
        if (cache.containsKey(name)) return cache.get(name);
        String expr = raw.get(name);
        if (expr == null || !inProgress.add(name)) return null;
        Long value = evalExpr(expr, raw, cache, inProgress);
        inProgress.remove(name);
        if (value != null) cache.put(name, value);
        return value;
    }

    private static Long evalExpr(String expr, Map<String, String> raw, Map<String, Long> cache, Set<String> inProgress) {
        expr = expr.strip();
        while (expr.startsWith("(") && expr.endsWith(")") && isFullyParenthesised(expr)) {
            expr = expr.substring(1, expr.length() - 1).strip();
        }

        List<String> orParts = splitTopLevel(expr, '|');
        if (orParts.size() > 1) {
            long acc = 0;
            for (String part : orParts) {
                Long v = evalExpr(part, raw, cache, inProgress);
                if (v == null) return null;
                acc |= v;
            }
            return acc;
        }

        try {
            if (expr.startsWith("0x") || expr.startsWith("0X")) {
                return Long.parseLong(expr.substring(2), 16);
            }
            return Long.parseLong(expr);
        } catch (NumberFormatException ignored) {
            // not a literal — fall through
        }

        if (expr.matches("\\w+")) {
            return eval(expr, raw, cache, inProgress);
        }
        return null; // casts, shifts, arithmetic — not handled, e.g. RACE_ALL
    }

    private static boolean isFullyParenthesised(String s) {
        int depth = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '(') depth++;
            else if (c == ')') {
                depth--;
                if (depth == 0 && i != s.length() - 1) return false;
            }
        }
        return depth == 0;
    }

    private static List<String> splitTopLevel(String expr, char sep) {
        List<String> parts = new ArrayList<>();
        int depth = 0;
        int start = 0;
        for (int i = 0; i < expr.length(); i++) {
            char c = expr.charAt(i);
            if (c == '(') depth++;
            else if (c == ')') depth--;
            else if (c == sep && depth == 0) {
                parts.add(expr.substring(start, i));
                start = i + 1;
            }
        }
        parts.add(expr.substring(start));
        return parts;
    }
}
