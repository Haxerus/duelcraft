package com.haxerus.duelcraft.duel.message;

import java.util.List;

/**
 * Holds the result of a card query. Only fields corresponding to the
 * requested QUERY_* flags will be populated; the rest stay at defaults
 * (0, empty list, etc.). Check {@link #flags} to know which fields are valid.
 */
public class QueriedCard {
    /** Bitmask of QUERY_* flags that were actually present in the response. */
    public int flags;

    public int code;
    public int position;
    public int alias;
    public int type;
    public int level;
    public int rank;
    public int attribute;
    public long race;
    public int attack;
    public int defense;
    public int baseAttack;
    public int baseDefense;
    public int reason;
    public int owner;
    public int status;
    public boolean isPublic;
    public boolean isHidden;
    public int lscale;
    public int rscale;
    public int linkRating;
    public int linkMarker;
    public int cover;
    public List<Integer> overlayCards = List.of();
    public List<Integer> counters = List.of();
    // QUERY_REASON_CARD, QUERY_EQUIP_CARD, QUERY_TARGET_CARD are loc_info references
    public LocInfo reasonCard;
    public LocInfo equipCard;
    public List<LocInfo> targetCards = List.of();
}
