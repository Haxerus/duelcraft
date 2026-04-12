package com.haxerus.duelcraft.client.carddata;

import java.util.ArrayList;
import java.util.List;

import static com.haxerus.duelcraft.core.OcgConstants.*;

public final class CardStringHelper {

    private CardStringHelper() {}

    public static String attributeName(int attribute) {
        return switch (Integer.highestOneBit(attribute)) {
            case ATTRIBUTE_EARTH  -> "EARTH";
            case ATTRIBUTE_WATER  -> "WATER";
            case ATTRIBUTE_FIRE   -> "FIRE";
            case ATTRIBUTE_WIND   -> "WIND";
            case ATTRIBUTE_LIGHT  -> "LIGHT";
            case ATTRIBUTE_DARK   -> "DARK";
            case ATTRIBUTE_DIVINE -> "DIVINE";
            default -> "???";
        };
    }

    public static String raceName(long race) {
        long primary = Long.highestOneBit(race);
        if (primary == RACE_WARRIOR)          return "Warrior";
        if (primary == RACE_SPELLCASTER)      return "Spellcaster";
        if (primary == RACE_FAIRY)            return "Fairy";
        if (primary == RACE_FIEND)            return "Fiend";
        if (primary == RACE_ZOMBIE)           return "Zombie";
        if (primary == RACE_MACHINE)          return "Machine";
        if (primary == RACE_AQUA)             return "Aqua";
        if (primary == RACE_PYRO)             return "Pyro";
        if (primary == RACE_ROCK)             return "Rock";
        if (primary == RACE_WINGEDBEAST)      return "Winged Beast";
        if (primary == RACE_PLANT)            return "Plant";
        if (primary == RACE_INSECT)           return "Insect";
        if (primary == RACE_THUNDER)          return "Thunder";
        if (primary == RACE_DRAGON)           return "Dragon";
        if (primary == RACE_BEAST)            return "Beast";
        if (primary == RACE_BEASTWARRIOR)     return "Beast-Warrior";
        if (primary == RACE_DINOSAUR)         return "Dinosaur";
        if (primary == RACE_FISH)             return "Fish";
        if (primary == RACE_SEASERPENT)       return "Sea Serpent";
        if (primary == RACE_REPTILE)          return "Reptile";
        if (primary == RACE_PSYCHIC)          return "Psychic";
        if (primary == RACE_DIVINE)           return "Divine-Beast";
        if (primary == RACE_CREATORGOD)       return "Creator God";
        if (primary == RACE_WYRM)             return "Wyrm";
        if (primary == RACE_CYBERSE)          return "Cyberse";
        if (primary == RACE_ILLUSION)         return "Illusion";
        if (primary == RACE_CYBORG)           return "Cyborg";
        if (primary == RACE_MAGICALKNIGHT)    return "Magical Knight";
        if (primary == RACE_HIGHDRAGON)       return "High Dragon";
        if (primary == RACE_OMEGAPSYCHIC)     return "Omega Psychic";
        if (primary == RACE_CELESTIALWARRIOR) return "Celestial Warrior";
        if (primary == RACE_GALAXY)           return "Galaxy";
        if (primary == RACE_YOKAI)            return "Yokai";
        return "???";
    }

    public static String typeLine(CardInfo card) {
        if (card.isSpell()) return spellTypeLine(card.type());
        if (card.isTrap())  return trapTypeLine(card.type());
        return monsterTypeLine(card);
    }

    public static String atkDefLine(CardInfo card) {
        if (!card.isMonster()) return "";
        String atk = card.atk() == -2 ? "?" : String.valueOf(card.atk());
        if (card.isLink()) {
            return "ATK " + atk + " / Link " + card.linkRating();
        }
        String def = card.def() == -2 ? "?" : String.valueOf(card.def());
        return "ATK " + atk + " / DEF " + def;
    }

    private static String monsterTypeLine(CardInfo card) {
        var sb = new StringBuilder();
        sb.append(attributeName(card.attribute()));
        sb.append(" / ");

        int type = card.type();
        if (card.isLink()) {
            sb.append("Link ").append(card.linkRating());
        } else if (card.isXyz()) {
            sb.append("Rank ").append(card.levelOrRank());
        } else {
            sb.append("Level ").append(card.levelOrRank());
        }

        sb.append(" / ").append(raceName(card.race()));

        List<String> subtypes = new ArrayList<>();
        if ((type & TYPE_FUSION) != 0)    subtypes.add("Fusion");
        if ((type & TYPE_SYNCHRO) != 0)   subtypes.add("Synchro");
        if ((type & TYPE_XYZ) != 0)       subtypes.add("Xyz");
        if ((type & TYPE_LINK) != 0)      subtypes.add("Link");
        if ((type & TYPE_RITUAL) != 0)    subtypes.add("Ritual");
        if ((type & TYPE_PENDULUM) != 0)  subtypes.add("Pendulum");
        if ((type & TYPE_SPIRIT) != 0)    subtypes.add("Spirit");
        if ((type & TYPE_UNION) != 0)     subtypes.add("Union");
        if ((type & TYPE_GEMINI) != 0)    subtypes.add("Gemini");
        if ((type & TYPE_TUNER) != 0)     subtypes.add("Tuner");
        if ((type & TYPE_FLIP) != 0)      subtypes.add("Flip");
        if ((type & TYPE_TOON) != 0)      subtypes.add("Toon");
        if ((type & TYPE_EFFECT) != 0)    subtypes.add("Effect");
        else if ((type & TYPE_NORMAL) != 0) subtypes.add("Normal");

        for (String sub : subtypes) {
            sb.append(" / ").append(sub);
        }
        return sb.toString();
    }

    private static String spellTypeLine(int type) {
        String prefix = "";
        if ((type & TYPE_QUICKPLAY) != 0)       prefix = "Quick-Play ";
        else if ((type & TYPE_CONTINUOUS) != 0) prefix = "Continuous ";
        else if ((type & TYPE_EQUIP) != 0)      prefix = "Equip ";
        else if ((type & TYPE_FIELD) != 0)      prefix = "Field ";
        else if ((type & TYPE_RITUAL) != 0)     prefix = "Ritual ";
        return prefix + "Spell Card";
    }

    private static String trapTypeLine(int type) {
        String prefix = "";
        if ((type & TYPE_CONTINUOUS) != 0)    prefix = "Continuous ";
        else if ((type & TYPE_COUNTER) != 0)  prefix = "Counter ";
        return prefix + "Trap Card";
    }
}
