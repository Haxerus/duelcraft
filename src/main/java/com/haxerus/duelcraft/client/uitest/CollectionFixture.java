package com.haxerus.duelcraft.client.uitest;

import com.haxerus.duelcraft.client.carddata.CardInfo;
import com.haxerus.duelcraft.client.collection.DeckEditorModel;
import com.haxerus.duelcraft.collection.DeckList;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static com.haxerus.duelcraft.core.OcgConstants.*;

/** Sample data only: no database, image service, inventory, or server state. */
public final class CollectionFixture {
    public static final int FIRST = 10001;
    private CollectionFixture() {}

    public static List<CardInfo> cards() {
        var cards = new ArrayList<CardInfo>();
        cards.add(new CardInfo(FIRST, "Amber Dragon", "Draw two cards.\n" +
                "This sample effect explains the selected card. Scroll this description while the deck and collection stay in place. ".repeat(32),
                TYPE_MONSTER | TYPE_EFFECT, 2400, 1800, 6, RACE_DRAGON, ATTRIBUTE_DARK));
        cards.add(new CardInfo(10002, "Beacon Spell", "Draw two cards.", TYPE_SPELL, 0, 0, 0, 0, 0));
        cards.add(new CardInfo(10003, "Cinder Dragon", "Draw two cards.", TYPE_MONSTER | TYPE_EFFECT,
                1600, 1200, 4, RACE_DRAGON, ATTRIBUTE_FIRE));
        cards.add(new CardInfo(10004, "Dusk Guard", "Protect a card.", TYPE_TRAP | TYPE_COUNTER, 0, 0, 0, 0, 0));
        for (int i = 5; i <= 80; i++) {
            int type = i > 65 ? TYPE_MONSTER | TYPE_FUSION : switch (i % 4) {
                case 0 -> TYPE_SPELL;
                case 1 -> TYPE_TRAP;
                default -> TYPE_MONSTER | TYPE_EFFECT;
            };
            boolean monster = (type & TYPE_MONSTER) != 0;
            cards.add(new CardInfo(10000 + i, "Sample " + String.format("%02d", i),
                    "A deterministic sample card for deck editing.", type, monster ? 1000 + i * 10 : 0,
                    monster ? 800 : 0, monster ? 4 : 0, monster ? RACE_WARRIOR : 0, monster ? ATTRIBUTE_LIGHT : 0));
        }
        return List.copyOf(cards);
    }

    public static DeckEditorModel model(boolean overflow) {
        var main = IntStream.rangeClosed(1, overflow ? 60 : 40).map(i -> 10000 + i).boxed().toList();
        var extra = IntStream.rangeClosed(66, overflow ? 80 : 70).map(i -> 10000 + i).boxed().toList();
        var side = overflow ? IntStream.rangeClosed(41, 55).map(i -> 10000 + i).boxed().toList() : List.<Integer>of();
        return new DeckEditorModel(new DeckList(main, extra, side), owned());
    }

    public static Map<Integer, Long> owned() {
        var owned = new HashMap<Integer, Long>();
        for (int i = 2; i <= 80; i++) owned.put(10000 + i, 3L);
        return Map.copyOf(owned);
    }

    public static List<CardInfo> largeCatalog() {
        return IntStream.rangeClosed(1, 16000).mapToObj(i -> new CardInfo(20000 + i,
                "Catalog " + String.format("%05d", i), "Synthetic catalog entry.", TYPE_MONSTER | TYPE_EFFECT,
                i % 4000, i % 3000, 4, RACE_DRAGON, ATTRIBUTE_LIGHT)).toList();
    }
}
