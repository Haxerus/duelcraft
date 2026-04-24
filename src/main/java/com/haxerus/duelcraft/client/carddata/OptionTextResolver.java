package com.haxerus.duelcraft.client.carddata;

/**
 * Resolves the opaque 64-bit description codes that ygopro-core uses for
 * SelectOption / SelectEffectYn / SelectYesNo / activatable card hints into
 * displayable strings.
 *
 * <p>Two encoding regimes:
 * <ul>
 *   <li><b>Packed card description</b> ({@code desc >= (1L << 20)}): upper 44 bits
 *       are the card code, lower 20 bits are a zero-based string offset. This
 *       matches {@code aux.Stringid(code, N)} in the Lua scripts — offset N
 *       maps to the card's {@code str(N+1)} column (so offset 0 → str1). The
 *       {@code desc} column (full card text) is NOT used for option resolution.
 *   <li><b>System string code</b> ({@code desc < (1L << 20)}): refers to a
 *       shared string in {@code strings.conf}, loaded into a {@link SystemStringTable}.
 * </ul>
 */
public class OptionTextResolver {

    /** Bit layout: {@code desc = (cardCode << 20) | offset}. */
    private static final int OFFSET_BITS = 20;
    private static final long OFFSET_MASK = (1L << OFFSET_BITS) - 1;

    private final CardDatabase cardDb;
    private final SystemStringTable systemStrings;

    public OptionTextResolver(CardDatabase cardDb, SystemStringTable systemStrings) {
        this.cardDb = cardDb;
        this.systemStrings = systemStrings;
    }

    /** Resolve a desc to a displayable option label. Never returns null. */
    public String resolve(long desc) {
        if (desc == 0) return "(no description)";

        if (desc >= (1L << OFFSET_BITS)) {
            return resolveCardString(desc);
        }
        return resolveSystemString((int) desc);
    }

    private String resolveCardString(long desc) {
        int code = (int) (desc >>> OFFSET_BITS);
        int offset = (int) (desc & OFFSET_MASK);

        if (cardDb != null) {
            String text = cardDb.getCardString(code, offset);
            if (text != null && !text.isBlank()) return text;
            // Fallback: card name + offset hint for debugging unknown strings
            CardInfo card = cardDb.getCard(code);
            if (card != null) {
                return card.name() + " (effect " + offset + ")";
            }
        }
        return "Effect " + code + "#" + offset;
    }

    private String resolveSystemString(int code) {
        if (systemStrings != null) {
            String hit = systemStrings.getSystem(code);
            if (hit != null) return hit;
        }
        return "System string #" + code;
    }
}
