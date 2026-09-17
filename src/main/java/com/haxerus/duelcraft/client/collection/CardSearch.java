package com.haxerus.duelcraft.client.collection;

import com.haxerus.duelcraft.client.carddata.CardInfo;
import com.haxerus.duelcraft.collection.DeckList;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class CardSearch {
    private CardSearch() {}

    public enum Ownership { ALL, OWNED, MISSING, EXTRAS }
    public enum Measure { ANY, LEVEL, RANK, LINK }
    public enum Sort { NAME, PASSCODE, ATK, DEF }
    public record Range(int min, int max) {}
    public record Filters(int categoryAny, int subtypeAny, int requiredProperties,
                          long raceAny, int attributeAny, Measure measure,
                          Range measureRange, Range atkRange, Range defRange, Range scaleRange,
                          Ownership ownership, Sort sort) {
        // Zero masks and null ranges are unrestricted.
        public static final Filters ALL = new Filters(0, 0, 0, 0, 0,
                Measure.ANY, null, null, null, null, Ownership.ALL, Sort.NAME);
    }
    public static List<CardInfo> search(List<CardInfo> cards, String query,
                                       Filters filters, Map<Integer, Long> owned, DeckList draft) {
        String text = query.strip().toLowerCase(Locale.ROOT);
        var required = draft.requiredCopies();
        Comparator<CardInfo> sort = switch (filters.sort()) {
            case NAME -> Comparator.comparing(card -> card.name().toLowerCase(Locale.ROOT));
            case PASSCODE -> Comparator.comparingInt(CardInfo::code);
            case ATK -> Comparator.comparingInt(CardInfo::atk);
            case DEF -> Comparator.comparingInt(CardInfo::def);
        };
        var order = Comparator.comparingInt((CardInfo card) -> matchTier(card, text))
                .thenComparing(sort).thenComparingInt(CardInfo::code);
        return cards.stream()
                .filter(card -> matchTier(card, text) < 3)
                .filter(card -> matches(card, filters, owned.getOrDefault(card.code(), 0L),
                        required.getOrDefault(card.code(), 0)))
                .sorted(order).toList();
    }

    private static int matchTier(CardInfo card, String text) {
        if (text.isEmpty()) return 2;
        if (Integer.toString(card.code()).equals(text)) return 0;
        String name = card.name().toLowerCase(Locale.ROOT);
        if (name.equals(text)) return 1;
        return name.contains(text) || card.desc().toLowerCase(Locale.ROOT).contains(text) ? 2 : 3;
    }

    private static boolean matches(CardInfo card, Filters filters, long owned, int required) {
        if (!any(card.type(), filters.categoryAny()) || !any(card.type(), filters.subtypeAny())
                || (card.type() & filters.requiredProperties()) != filters.requiredProperties()
                || !any(card.race(), filters.raceAny()) || !any(card.attribute(), filters.attributeAny())) {
            return false;
        }
        boolean measureMatches = switch (filters.measure()) {
            case ANY -> filters.measureRange() == null
                    || card.isMonster() && inRange(card.levelOrRank(), filters.measureRange());
            case LEVEL -> card.isMonster() && !card.isXyz() && !card.isLink()
                    && inRange(card.levelOrRank(), filters.measureRange());
            case RANK -> card.isMonster() && card.isXyz() && inRange(card.levelOrRank(), filters.measureRange());
            case LINK -> card.isMonster() && card.isLink() && inRange(card.linkRating(), filters.measureRange());
        };
        if (!measureMatches) return false;
        if (filters.atkRange() != null && (!card.isMonster() || card.atk() < 0
                || !inRange(card.atk(), filters.atkRange()))) return false;
        if (filters.defRange() != null && (!card.isMonster() || card.isLink() || card.def() < 0
                || !inRange(card.def(), filters.defRange()))) return false;
        if (filters.scaleRange() != null && (!card.isPendulum()
                || !(inRange((card.level() >>> 24) & 0xff, filters.scaleRange())
                || inRange((card.level() >>> 16) & 0xff, filters.scaleRange())))) return false;
        return switch (filters.ownership()) {
            case ALL -> true;
            case OWNED -> owned > 0;
            case MISSING -> required > owned;
            case EXTRAS -> owned > required;
        };
    }

    private static boolean any(long value, long mask) {
        return mask == 0 || (value & mask) != 0;
    }

    private static boolean inRange(int value, Range range) {
        return range == null || value >= range.min() && value <= range.max();
    }
}
