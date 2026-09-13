package com.haxerus.duelcraft.client;

import com.haxerus.duelcraft.duel.message.QueriedCard;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.haxerus.duelcraft.core.OcgConstants.POS_FACEDOWN;

/**
 * One card object per card, moved between the containers of {@link ClientDuelState} as the engine
 * reports moves — edopro's {@code ClientCard} model. Counters, equip links, targets and XYZ
 * materials live on the object, so they travel with the card instead of being lost whenever it
 * changes zone.
 *
 * Mutable and identity-compared on purpose: the link sets hold the very objects they point at.
 * Minecraft-free so {@link ClientDuelState} stays testable under plain JUnit.
 */
public class ClientCard {
    /** 0 when the card is hidden from this client (opponent's face-down cards, deck contents). */
    public int code;
    public int position;
    public int controller;
    public int location;
    public int sequence;

    /** Latest query result, or null until an UPDATE_DATA covers this slot. */
    public QueriedCard stats;

    /** XYZ materials in engine order; a material's {@link #sequence} is its index here. */
    public final List<ClientCard> materials = new ArrayList<>();

    /** Counter type → count. A type is removed once its count reaches zero. */
    public final Map<Integer, Integer> counters = new LinkedHashMap<>();

    /** The card this one is equipped to, and the cards equipped to it. */
    public ClientCard equipTarget;
    public final Set<ClientCard> equippedBy = new LinkedHashSet<>();

    /** The cards this one targets, and the cards that target it. */
    public final Set<ClientCard> targets = new LinkedHashSet<>();
    public final Set<ClientCard> targetedBy = new LinkedHashSet<>();

    public ClientCard(int code, int controller, int location, int sequence, int position) {
        this.code = code;
        this.controller = controller;
        this.location = location;
        this.sequence = sequence;
        this.position = position;
    }

    public boolean isFaceDown() {
        return (position & POS_FACEDOWN) != 0;
    }

    /** Total counters across every type, for the badge drawn on the card. */
    public int counterTotal() {
        int total = 0;
        for (int count : counters.values()) total += count;
        return total;
    }
}
