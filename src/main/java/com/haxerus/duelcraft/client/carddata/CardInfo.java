package com.haxerus.duelcraft.client.carddata;

import static com.haxerus.duelcraft.core.OcgConstants.*;

/**
 * Immutable card data read from the SQLite card database.
 * Fields mirror the datas + texts tables in cards.cdb.
 */
public record CardInfo(
        int code,
        String name,
        String desc,
        int type,
        int atk,
        int def,
        int level,
        long race,
        int attribute
) {
    /** Monster level or Xyz rank (lower 8 bits of level field). */
    public int levelOrRank() {
        return level & 0xFF;
    }

    /** Left pendulum scale (bits 24-31 of level field). */
    public int leftScale() {
        return (level >> 24) & 0xFF;
    }

    /** Right pendulum scale (bits 16-23 of level field). */
    public int rightScale() {
        return (level >> 16) & 0xFF;
    }

    public boolean isMonster() { return (type & TYPE_MONSTER) != 0; }
    public boolean isSpell()   { return (type & TYPE_SPELL) != 0; }
    public boolean isTrap()    { return (type & TYPE_TRAP) != 0; }
    public boolean isLink()    { return (type & TYPE_LINK) != 0; }
    public boolean isXyz()     { return (type & TYPE_XYZ) != 0; }
    public boolean isPendulum() { return (type & TYPE_PENDULUM) != 0; }

    /** For Link monsters, the level field stores the Link rating. */
    public int linkRating() { return level & 0xFF; }

    /** For Link monsters, the def field stores link arrow bitmask. */
    public int linkArrows() { return def; }
}
