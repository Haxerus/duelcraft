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
    /** Last recipient-known identity; nonzero does not imply current face-up permission. */
    public int code;
    public int position;
    public int controller;
    public int location;
    public int sequence;

    /** Authoritative visibility, retained when an incremental query omits either field. */
    public boolean isPublic;
    public boolean isHidden;

    /** Latest query result, or null until an UPDATE_DATA covers this slot. */
    public QueriedCard stats;

    /**
     * Combat ATK/DEF from {@code MSG_BATTLE}, shown in place of the queried stats until the damage
     * step ends. edopro writes them straight onto the card ({@code duelclient.cpp:3805-3825});
     * null means the card is not in a damage calculation.
     */
    public Integer combatAttack;
    public Integer combatDefense;

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

    /**
     * MSG_CARD_HINT's single {@code cHint/chValue} slot (edopro's {@code duelclient.cpp:3977}):
     * the last non-desc {@code CHINT_*} type and its value, 0 when the card carries none.
     */
    public int hintType;
    public long hintValue;

    /** Refcounted {@code CHINT_DESC_ADD}/{@code CHINT_DESC_REMOVE} hints, desc → count. */
    public final Map<Long, Integer> descHints = new LinkedHashMap<>();

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
