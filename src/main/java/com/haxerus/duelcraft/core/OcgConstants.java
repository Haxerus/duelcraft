package com.haxerus.duelcraft.core;

/**
 * Constants mirroring native/ygopro-core/ocgapi_constants.h and ocgapi_types.h.
 * All values match the C defines exactly.
 */
public final class OcgConstants {

    private OcgConstants() {}

    // ---- Duel Status (ocgapi_types.h) ----

    public static final int DUEL_STATUS_END       = 0;
    public static final int DUEL_STATUS_AWAITING  = 1;
    public static final int DUEL_STATUS_CONTINUE  = 2;

    // ---- Locations ----

    public static final int LOCATION_DECK    = 0x01;
    public static final int LOCATION_HAND    = 0x02;
    public static final int LOCATION_MZONE   = 0x04;
    public static final int LOCATION_SZONE   = 0x08;
    public static final int LOCATION_GRAVE   = 0x10;
    public static final int LOCATION_REMOVED = 0x20;
    public static final int LOCATION_EXTRA   = 0x40;
    public static final int LOCATION_OVERLAY = 0x80;
    public static final int LOCATION_ONFIELD = LOCATION_MZONE | LOCATION_SZONE;

    // ---- Positions ----

    public static final int POS_FACEUP_ATTACK    = 0x1;
    public static final int POS_FACEDOWN_ATTACK  = 0x2;
    public static final int POS_FACEUP_DEFENSE   = 0x4;
    public static final int POS_FACEDOWN_DEFENSE = 0x8;
    public static final int POS_FACEUP           = POS_FACEUP_ATTACK | POS_FACEUP_DEFENSE;
    public static final int POS_FACEDOWN         = POS_FACEDOWN_ATTACK | POS_FACEDOWN_DEFENSE;
    public static final int POS_ATTACK           = POS_FACEUP_ATTACK | POS_FACEDOWN_ATTACK;
    public static final int POS_DEFENSE          = POS_FACEUP_DEFENSE | POS_FACEDOWN_DEFENSE;

    // ---- Card Types ----

    public static final int TYPE_MONSTER     = 0x1;
    public static final int TYPE_SPELL       = 0x2;
    public static final int TYPE_TRAP        = 0x4;
    public static final int TYPE_NORMAL      = 0x10;
    public static final int TYPE_EFFECT      = 0x20;
    public static final int TYPE_FUSION      = 0x40;
    public static final int TYPE_RITUAL      = 0x80;
    public static final int TYPE_TRAPMONSTER = 0x100;
    public static final int TYPE_SPIRIT      = 0x200;
    public static final int TYPE_UNION       = 0x400;
    public static final int TYPE_GEMINI      = 0x800;
    public static final int TYPE_TUNER       = 0x1000;
    public static final int TYPE_SYNCHRO     = 0x2000;
    public static final int TYPE_TOKEN       = 0x4000;
    public static final int TYPE_MAXIMUM     = 0x8000;
    public static final int TYPE_QUICKPLAY   = 0x10000;
    public static final int TYPE_CONTINUOUS  = 0x20000;
    public static final int TYPE_EQUIP       = 0x40000;
    public static final int TYPE_FIELD       = 0x80000;
    public static final int TYPE_COUNTER     = 0x100000;
    public static final int TYPE_FLIP        = 0x200000;
    public static final int TYPE_TOON        = 0x400000;
    public static final int TYPE_XYZ         = 0x800000;
    public static final int TYPE_PENDULUM    = 0x1000000;
    public static final int TYPE_SPSUMMON    = 0x2000000;
    public static final int TYPE_LINK        = 0x4000000;

    // ---- Attributes ----

    public static final int ATTRIBUTE_EARTH  = 0x01;
    public static final int ATTRIBUTE_WATER  = 0x02;
    public static final int ATTRIBUTE_FIRE   = 0x04;
    public static final int ATTRIBUTE_WIND   = 0x08;
    public static final int ATTRIBUTE_LIGHT  = 0x10;
    public static final int ATTRIBUTE_DARK   = 0x20;
    public static final int ATTRIBUTE_DIVINE = 0x40;
    public static final int ATTRIBUTE_ALL    = ATTRIBUTE_EARTH | ATTRIBUTE_WATER | ATTRIBUTE_FIRE
            | ATTRIBUTE_WIND | ATTRIBUTE_LIGHT | ATTRIBUTE_DARK | ATTRIBUTE_DIVINE;

    // ---- Monster Races ----

    public static final long RACE_WARRIOR         = 0x1L;
    public static final long RACE_SPELLCASTER     = 0x2L;
    public static final long RACE_FAIRY           = 0x4L;
    public static final long RACE_FIEND           = 0x8L;
    public static final long RACE_ZOMBIE          = 0x10L;
    public static final long RACE_MACHINE         = 0x20L;
    public static final long RACE_AQUA            = 0x40L;
    public static final long RACE_PYRO            = 0x80L;
    public static final long RACE_ROCK            = 0x100L;
    public static final long RACE_WINGEDBEAST     = 0x200L;
    public static final long RACE_PLANT           = 0x400L;
    public static final long RACE_INSECT          = 0x800L;
    public static final long RACE_THUNDER         = 0x1000L;
    public static final long RACE_DRAGON          = 0x2000L;
    public static final long RACE_BEAST           = 0x4000L;
    public static final long RACE_BEASTWARRIOR    = 0x8000L;
    public static final long RACE_DINOSAUR        = 0x10000L;
    public static final long RACE_FISH            = 0x20000L;
    public static final long RACE_SEASERPENT      = 0x40000L;
    public static final long RACE_REPTILE         = 0x80000L;
    public static final long RACE_PSYCHIC         = 0x100000L;
    public static final long RACE_DIVINE          = 0x200000L;
    public static final long RACE_CREATORGOD      = 0x400000L;
    public static final long RACE_WYRM            = 0x800000L;
    public static final long RACE_CYBERSE         = 0x1000000L;
    public static final long RACE_ILLUSION        = 0x2000000L;
    public static final long RACE_CYBORG          = 0x4000000L;
    public static final long RACE_MAGICALKNIGHT   = 0x8000000L;
    public static final long RACE_HIGHDRAGON      = 0x10000000L;
    public static final long RACE_OMEGAPSYCHIC    = 0x20000000L;
    public static final long RACE_CELESTIALWARRIOR = 0x40000000L;
    public static final long RACE_GALAXY          = 0x80000000L;
    public static final long RACE_YOKAI           = 0x4000000000000000L;

    // ---- Event Reasons ----

    public static final int REASON_DESTROY     = 0x1;
    public static final int REASON_RELEASE     = 0x2;
    public static final int REASON_TEMPORARY   = 0x4;
    public static final int REASON_MATERIAL    = 0x8;
    public static final int REASON_SUMMON      = 0x10;
    public static final int REASON_BATTLE      = 0x20;
    public static final int REASON_EFFECT      = 0x40;
    public static final int REASON_COST        = 0x80;
    public static final int REASON_ADJUST      = 0x100;
    public static final int REASON_LOST_TARGET = 0x200;
    public static final int REASON_RULE        = 0x400;
    public static final int REASON_SPSUMMON    = 0x800;
    public static final int REASON_DISSUMMON   = 0x1000;
    public static final int REASON_FLIP        = 0x2000;
    public static final int REASON_DISCARD     = 0x4000;
    public static final int REASON_RDAMAGE     = 0x8000;
    public static final int REASON_RRECOVER    = 0x10000;
    public static final int REASON_RETURN      = 0x20000;
    public static final int REASON_FUSION      = 0x40000;
    public static final int REASON_SYNCHRO     = 0x80000;
    public static final int REASON_RITUAL      = 0x100000;
    public static final int REASON_XYZ         = 0x200000;
    public static final int REASON_REPLACE     = 0x1000000;
    public static final int REASON_DRAW        = 0x2000000;
    public static final int REASON_REDIRECT    = 0x4000000;
    public static final int REASON_LINK        = 0x10000000;

    // ---- Card Status ----

    public static final int STATUS_DISABLED           = 0x1;
    public static final int STATUS_TO_ENABLE          = 0x2;
    public static final int STATUS_TO_DISABLE         = 0x4;
    public static final int STATUS_PROC_COMPLETE      = 0x8;
    public static final int STATUS_SET_TURN           = 0x10;
    public static final int STATUS_NO_LEVEL           = 0x20;
    public static final int STATUS_BATTLE_RESULT      = 0x40;
    public static final int STATUS_SPSUMMON_STEP      = 0x80;
    public static final int STATUS_FORM_CHANGED       = 0x100;
    public static final int STATUS_SUMMONING          = 0x200;
    public static final int STATUS_EFFECT_ENABLED     = 0x400;
    public static final int STATUS_SUMMON_TURN        = 0x800;
    public static final int STATUS_DESTROY_CONFIRMED  = 0x1000;
    public static final int STATUS_LEAVE_CONFIRMED    = 0x2000;
    public static final int STATUS_BATTLE_DESTROYED   = 0x4000;
    public static final int STATUS_COPYING_EFFECT     = 0x8000;
    public static final int STATUS_CHAINING           = 0x10000;
    public static final int STATUS_SUMMON_DISABLED    = 0x20000;
    public static final int STATUS_ACTIVATE_DISABLED  = 0x40000;
    public static final int STATUS_EFFECT_REPLACED    = 0x80000;
    public static final int STATUS_FUTURE_FUSION      = 0x100000;
    public static final int STATUS_ATTACK_CANCELED    = 0x200000;
    public static final int STATUS_INITIALIZING       = 0x400000;
    public static final int STATUS_JUST_POS           = 0x1000000;
    public static final int STATUS_CONTINUOUS_POS     = 0x2000000;
    public static final int STATUS_FORBIDDEN          = 0x4000000;
    public static final int STATUS_ACT_FROM_HAND      = 0x8000000;
    public static final int STATUS_OPPO_BATTLE        = 0x10000000;
    public static final int STATUS_FLIP_SUMMON_TURN   = 0x20000000;
    public static final int STATUS_SPSUMMON_TURN      = 0x40000000;

    // ---- Query Flags ----

    public static final int QUERY_CODE         = 0x1;
    public static final int QUERY_POSITION     = 0x2;
    public static final int QUERY_ALIAS        = 0x4;
    public static final int QUERY_TYPE         = 0x8;
    public static final int QUERY_LEVEL        = 0x10;
    public static final int QUERY_RANK         = 0x20;
    public static final int QUERY_ATTRIBUTE    = 0x40;
    public static final int QUERY_RACE         = 0x80;
    public static final int QUERY_ATTACK       = 0x100;
    public static final int QUERY_DEFENSE      = 0x200;
    public static final int QUERY_BASE_ATTACK  = 0x400;
    public static final int QUERY_BASE_DEFENSE = 0x800;
    public static final int QUERY_REASON       = 0x1000;
    public static final int QUERY_REASON_CARD  = 0x2000;
    public static final int QUERY_EQUIP_CARD   = 0x4000;
    public static final int QUERY_TARGET_CARD  = 0x8000;
    public static final int QUERY_OVERLAY_CARD = 0x10000;
    public static final int QUERY_COUNTERS     = 0x20000;
    public static final int QUERY_OWNER        = 0x40000;
    public static final int QUERY_STATUS       = 0x80000;
    public static final int QUERY_IS_PUBLIC    = 0x100000;
    public static final int QUERY_LSCALE       = 0x200000;
    public static final int QUERY_RSCALE       = 0x400000;
    public static final int QUERY_LINK         = 0x800000;
    public static final int QUERY_IS_HIDDEN    = 0x1000000;
    public static final int QUERY_COVER        = 0x2000000;
    public static final int QUERY_END          = 0x80000000;

    // ---- Link Markers ----

    public static final int LINK_MARKER_BOTTOM_LEFT  = 0x001;
    public static final int LINK_MARKER_BOTTOM       = 0x002;
    public static final int LINK_MARKER_BOTTOM_RIGHT = 0x004;
    public static final int LINK_MARKER_LEFT         = 0x008;
    public static final int LINK_MARKER_RIGHT        = 0x020;
    public static final int LINK_MARKER_TOP_LEFT     = 0x040;
    public static final int LINK_MARKER_TOP          = 0x080;
    public static final int LINK_MARKER_TOP_RIGHT    = 0x100;

    // ---- Messages ----

    public static final int MSG_RETRY                = 1;
    public static final int MSG_HINT                 = 2;
    public static final int MSG_WAITING              = 3;
    public static final int MSG_START                = 4;
    public static final int MSG_WIN                  = 5;
    public static final int MSG_UPDATE_DATA          = 6;
    public static final int MSG_UPDATE_CARD          = 7;
    public static final int MSG_REQUEST_DECK         = 8;
    public static final int MSG_SELECT_BATTLECMD     = 10;
    public static final int MSG_SELECT_IDLECMD       = 11;
    public static final int MSG_SELECT_EFFECTYN      = 12;
    public static final int MSG_SELECT_YESNO         = 13;
    public static final int MSG_SELECT_OPTION        = 14;
    public static final int MSG_SELECT_CARD          = 15;
    public static final int MSG_SELECT_CHAIN         = 16;
    public static final int MSG_SELECT_PLACE         = 18;
    public static final int MSG_SELECT_POSITION      = 19;
    public static final int MSG_SELECT_TRIBUTE       = 20;
    public static final int MSG_SORT_CHAIN           = 21;
    public static final int MSG_SELECT_COUNTER       = 22;
    public static final int MSG_SELECT_SUM           = 23;
    public static final int MSG_SELECT_DISFIELD      = 24;
    public static final int MSG_SORT_CARD            = 25;
    public static final int MSG_SELECT_UNSELECT_CARD = 26;
    public static final int MSG_CONFIRM_DECKTOP      = 30;
    public static final int MSG_CONFIRM_CARDS        = 31;
    public static final int MSG_SHUFFLE_DECK         = 32;
    public static final int MSG_SHUFFLE_HAND         = 33;
    public static final int MSG_REFRESH_DECK         = 34;
    public static final int MSG_SWAP_GRAVE_DECK      = 35;
    public static final int MSG_SHUFFLE_SET_CARD     = 36;
    public static final int MSG_REVERSE_DECK         = 37;
    public static final int MSG_DECK_TOP             = 38;
    public static final int MSG_SHUFFLE_EXTRA        = 39;
    public static final int MSG_NEW_TURN             = 40;
    public static final int MSG_NEW_PHASE            = 41;
    public static final int MSG_CONFIRM_EXTRATOP     = 42;
    public static final int MSG_MOVE                 = 50;
    public static final int MSG_POS_CHANGE           = 53;
    public static final int MSG_SET                  = 54;
    public static final int MSG_SWAP                 = 55;
    public static final int MSG_FIELD_DISABLED       = 56;
    public static final int MSG_SUMMONING            = 60;
    public static final int MSG_SUMMONED             = 61;
    public static final int MSG_SPSUMMONING          = 62;
    public static final int MSG_SPSUMMONED           = 63;
    public static final int MSG_FLIPSUMMONING        = 64;
    public static final int MSG_FLIPSUMMONED         = 65;
    public static final int MSG_CHAINING             = 70;
    public static final int MSG_CHAINED              = 71;
    public static final int MSG_CHAIN_SOLVING        = 72;
    public static final int MSG_CHAIN_SOLVED         = 73;
    public static final int MSG_CHAIN_END            = 74;
    public static final int MSG_CHAIN_NEGATED        = 75;
    public static final int MSG_CHAIN_DISABLED       = 76;
    public static final int MSG_CARD_SELECTED        = 80;
    public static final int MSG_RANDOM_SELECTED      = 81;
    public static final int MSG_BECOME_TARGET        = 83;
    public static final int MSG_DRAW                 = 90;
    public static final int MSG_DAMAGE               = 91;
    public static final int MSG_RECOVER              = 92;
    public static final int MSG_EQUIP                = 93;
    public static final int MSG_LPUPDATE             = 94;
    public static final int MSG_UNEQUIP              = 95;
    public static final int MSG_CARD_TARGET          = 96;
    public static final int MSG_CANCEL_TARGET        = 97;
    public static final int MSG_PAY_LPCOST           = 100;
    public static final int MSG_ADD_COUNTER          = 101;
    public static final int MSG_REMOVE_COUNTER       = 102;
    public static final int MSG_ATTACK               = 110;
    public static final int MSG_BATTLE               = 111;
    public static final int MSG_ATTACK_DISABLED      = 112;
    public static final int MSG_DAMAGE_STEP_START    = 113;
    public static final int MSG_DAMAGE_STEP_END      = 114;
    public static final int MSG_MISSED_EFFECT        = 120;
    public static final int MSG_BE_CHAIN_TARGET      = 121;
    public static final int MSG_CREATE_RELATION      = 122;
    public static final int MSG_RELEASE_RELATION     = 123;
    public static final int MSG_TOSS_COIN            = 130;
    public static final int MSG_TOSS_DICE            = 131;
    public static final int MSG_ROCK_PAPER_SCISSORS  = 132;
    public static final int MSG_HAND_RES             = 133;
    public static final int MSG_ANNOUNCE_RACE        = 140;
    public static final int MSG_ANNOUNCE_ATTRIB      = 141;
    public static final int MSG_ANNOUNCE_CARD        = 142;
    public static final int MSG_ANNOUNCE_NUMBER      = 143;
    public static final int MSG_CARD_HINT            = 160;
    public static final int MSG_TAG_SWAP             = 161;
    public static final int MSG_RELOAD_FIELD         = 162;
    public static final int MSG_AI_NAME              = 163;
    public static final int MSG_SHOW_HINT            = 164;
    public static final int MSG_PLAYER_HINT          = 165;
    public static final int MSG_MATCH_KILL           = 170;
    public static final int MSG_CUSTOM_MSG           = 180;
    public static final int MSG_REMOVE_CARDS         = 190;

    // ---- Hints ----

    public static final int HINT_EVENT      = 1;
    public static final int HINT_MESSAGE    = 2;
    public static final int HINT_SELECTMSG  = 3;
    public static final int HINT_OPSELECTED = 4;
    public static final int HINT_EFFECT     = 5;
    public static final int HINT_RACE       = 6;
    public static final int HINT_ATTRIB     = 7;
    public static final int HINT_CODE       = 8;
    public static final int HINT_NUMBER     = 9;
    public static final int HINT_CARD       = 10;
    public static final int HINT_ZONE       = 11;

    // ---- Card Hints ----

    public static final int CHINT_TURN        = 1;
    public static final int CHINT_CARD        = 2;
    public static final int CHINT_RACE        = 3;
    public static final int CHINT_ATTRIBUTE   = 4;
    public static final int CHINT_NUMBER      = 5;
    public static final int CHINT_DESC_ADD    = 6;
    public static final int CHINT_DESC_REMOVE = 7;

    // ---- Player Hints ----

    public static final int PHINT_DESC_ADD    = 6;
    public static final int PHINT_DESC_REMOVE = 7;

    // ---- Effect Client Modes ----

    public static final int EFFECT_CLIENT_MODE_NORMAL  = 0;
    public static final int EFFECT_CLIENT_MODE_RESOLVE = 1;
    public static final int EFFECT_CLIENT_MODE_RESET   = 2;

    // ---- Player Constants ----

    public static final int PLAYER_NONE = 2;
    public static final int PLAYER_ALL  = 3;

    // ---- Duel Phases ----

    public static final int PHASE_DRAW         = 0x01;
    public static final int PHASE_STANDBY      = 0x02;
    public static final int PHASE_MAIN1        = 0x04;
    public static final int PHASE_BATTLE_START = 0x08;
    public static final int PHASE_BATTLE_STEP  = 0x10;
    public static final int PHASE_DAMAGE       = 0x20;
    public static final int PHASE_DAMAGE_CAL   = 0x40;
    public static final int PHASE_BATTLE       = 0x80;
    public static final int PHASE_MAIN2        = 0x100;
    public static final int PHASE_END          = 0x200;

    // ---- Duel Option Flags ----

    public static final long DUEL_TEST_MODE                      = 0x01L;
    public static final long DUEL_ATTACK_FIRST_TURN              = 0x02L;
    public static final long DUEL_USE_TRAPS_IN_NEW_CHAIN         = 0x04L;
    public static final long DUEL_6_STEP_BATTLE_STEP             = 0x08L;
    public static final long DUEL_PSEUDO_SHUFFLE                 = 0x10L;
    public static final long DUEL_TRIGGER_WHEN_PRIVATE_KNOWLEDGE = 0x20L;
    public static final long DUEL_SIMPLE_AI                      = 0x40L;
    public static final long DUEL_RELAY                          = 0x80L;
    public static final long DUEL_OCG_OBSOLETE_IGNITION          = 0x100L;
    public static final long DUEL_1ST_TURN_DRAW                  = 0x200L;
    public static final long DUEL_1_FACEUP_FIELD                 = 0x400L;
    public static final long DUEL_PZONE                          = 0x800L;
    public static final long DUEL_SEPARATE_PZONE                 = 0x1000L;
    public static final long DUEL_EMZONE                         = 0x2000L;
    public static final long DUEL_FSX_MMZONE                     = 0x4000L;
    public static final long DUEL_TRAP_MONSTERS_NOT_USE_ZONE     = 0x8000L;
    public static final long DUEL_RETURN_TO_DECK_TRIGGERS        = 0x10000L;
    public static final long DUEL_TRIGGER_ONLY_IN_LOCATION       = 0x20000L;
    public static final long DUEL_SPSUMMON_ONCE_OLD_NEGATE       = 0x40000L;
    public static final long DUEL_CANNOT_SUMMON_OATH_OLD         = 0x80000L;
    public static final long DUEL_NO_STANDBY_PHASE               = 0x100000L;
    public static final long DUEL_NO_MAIN_PHASE_2                = 0x200000L;
    public static final long DUEL_3_COLUMNS_FIELD                = 0x400000L;
    public static final long DUEL_DRAW_UNTIL_5                   = 0x800000L;
    public static final long DUEL_NO_HAND_LIMIT                  = 0x1000000L;
    public static final long DUEL_UNLIMITED_SUMMONS              = 0x2000000L;
    public static final long DUEL_INVERTED_QUICK_PRIORITY        = 0x4000000L;
    public static final long DUEL_EQUIP_NOT_SENT_IF_MISSING_TARGET = 0x8000000L;
    public static final long DUEL_0_ATK_DESTROYED                = 0x10000000L;
    public static final long DUEL_STORE_ATTACK_REPLAYS           = 0x20000000L;
    public static final long DUEL_SINGLE_CHAIN_IN_DAMAGE_SUBSTEP = 0x40000000L;
    public static final long DUEL_CAN_REPOS_IF_NON_SUMPLAYER     = 0x80000000L;
    public static final long DUEL_TCG_SEGOC_NONPUBLIC            = 0x100000000L;
    public static final long DUEL_TCG_SEGOC_FIRSTTRIGGER         = 0x200000000L;
    public static final long DUEL_TCG_FAST_EFFECT_IGNITION       = 0x400000000L;
    public static final long DUEL_EXTRA_DECK_RITUAL              = 0x800000000L;
    public static final long DUEL_NORMAL_SUMMON_FACEUP_DEF       = 0x1000000000L;

    // ---- Duel Modes (composed flag presets) ----

    public static final long DUEL_MODE_SPEED = DUEL_3_COLUMNS_FIELD | DUEL_NO_MAIN_PHASE_2
            | DUEL_TRAP_MONSTERS_NOT_USE_ZONE | DUEL_TRIGGER_ONLY_IN_LOCATION;

    public static final long DUEL_MODE_RUSH = DUEL_3_COLUMNS_FIELD | DUEL_NO_MAIN_PHASE_2
            | DUEL_NO_STANDBY_PHASE | DUEL_1ST_TURN_DRAW | DUEL_INVERTED_QUICK_PRIORITY
            | DUEL_DRAW_UNTIL_5 | DUEL_NO_HAND_LIMIT | DUEL_UNLIMITED_SUMMONS
            | DUEL_TRAP_MONSTERS_NOT_USE_ZONE | DUEL_TRIGGER_ONLY_IN_LOCATION
            | DUEL_EXTRA_DECK_RITUAL;

    public static final long DUEL_MODE_MR1 = DUEL_OCG_OBSOLETE_IGNITION | DUEL_1ST_TURN_DRAW
            | DUEL_1_FACEUP_FIELD | DUEL_SPSUMMON_ONCE_OLD_NEGATE
            | DUEL_RETURN_TO_DECK_TRIGGERS | DUEL_CANNOT_SUMMON_OATH_OLD;

    public static final long DUEL_MODE_GOAT = DUEL_MODE_MR1 | DUEL_TCG_FAST_EFFECT_IGNITION
            | DUEL_USE_TRAPS_IN_NEW_CHAIN | DUEL_6_STEP_BATTLE_STEP
            | DUEL_TRIGGER_WHEN_PRIVATE_KNOWLEDGE | DUEL_EQUIP_NOT_SENT_IF_MISSING_TARGET
            | DUEL_0_ATK_DESTROYED | DUEL_STORE_ATTACK_REPLAYS
            | DUEL_SINGLE_CHAIN_IN_DAMAGE_SUBSTEP | DUEL_CAN_REPOS_IF_NON_SUMPLAYER
            | DUEL_TCG_SEGOC_NONPUBLIC | DUEL_TCG_SEGOC_FIRSTTRIGGER;

    public static final long DUEL_MODE_MR2 = DUEL_1ST_TURN_DRAW | DUEL_1_FACEUP_FIELD
            | DUEL_SPSUMMON_ONCE_OLD_NEGATE | DUEL_RETURN_TO_DECK_TRIGGERS
            | DUEL_CANNOT_SUMMON_OATH_OLD;

    public static final long DUEL_MODE_MR3 = DUEL_PZONE | DUEL_SEPARATE_PZONE
            | DUEL_SPSUMMON_ONCE_OLD_NEGATE | DUEL_RETURN_TO_DECK_TRIGGERS
            | DUEL_CANNOT_SUMMON_OATH_OLD;

    public static final long DUEL_MODE_MR4 = DUEL_PZONE | DUEL_EMZONE
            | DUEL_SPSUMMON_ONCE_OLD_NEGATE | DUEL_RETURN_TO_DECK_TRIGGERS
            | DUEL_CANNOT_SUMMON_OATH_OLD;

    public static final long DUEL_MODE_MR5 = DUEL_PZONE | DUEL_EMZONE | DUEL_FSX_MMZONE
            | DUEL_TRAP_MONSTERS_NOT_USE_ZONE | DUEL_TRIGGER_ONLY_IN_LOCATION;

    // ---- Duel Mode Forbidden Card Types ----

    public static final int DUEL_MODE_MR1_FORB = TYPE_XYZ | TYPE_PENDULUM | TYPE_LINK;
    public static final int DUEL_MODE_MR2_FORB = TYPE_PENDULUM | TYPE_LINK;
    public static final int DUEL_MODE_MR3_FORB = TYPE_LINK;
    public static final int DUEL_MODE_MR4_FORB = 0;
    public static final int DUEL_MODE_MR5_FORB = 0;

    // ---- Announce Card Opcodes ----

    public static final long OPCODE_ADD           = 0x4000000000000000L;
    public static final long OPCODE_SUB           = 0x4000000100000000L;
    public static final long OPCODE_MUL           = 0x4000000200000000L;
    public static final long OPCODE_DIV           = 0x4000000300000000L;
    public static final long OPCODE_AND           = 0x4000000400000000L;
    public static final long OPCODE_OR            = 0x4000000500000000L;
    public static final long OPCODE_NEG           = 0x4000000600000000L;
    public static final long OPCODE_NOT           = 0x4000000700000000L;
    public static final long OPCODE_BAND          = 0x4000000800000000L;
    public static final long OPCODE_BOR           = 0x4000000900000000L;
    public static final long OPCODE_BNOT          = 0x4000001000000000L;
    public static final long OPCODE_BXOR          = 0x4000001100000000L;
    public static final long OPCODE_LSHIFT        = 0x4000001200000000L;
    public static final long OPCODE_RSHIFT        = 0x4000001300000000L;
    public static final long OPCODE_ALLOW_ALIASES = 0x4000001400000000L;
    public static final long OPCODE_ALLOW_TOKENS  = 0x4000001500000000L;
    public static final long OPCODE_ISCODE        = 0x4000010000000000L;
    public static final long OPCODE_ISSETCARD     = 0x4000010100000000L;
    public static final long OPCODE_ISTYPE        = 0x4000010200000000L;
    public static final long OPCODE_ISRACE        = 0x4000010300000000L;
    public static final long OPCODE_ISATTRIBUTE   = 0x4000010400000000L;
    public static final long OPCODE_GETCODE       = 0x4000010500000000L;
    public static final long OPCODE_GETSETCARD    = 0x4000010600000000L;
    public static final long OPCODE_GETTYPE       = 0x4000010700000000L;
    public static final long OPCODE_GETRACE       = 0x4000010800000000L;
    public static final long OPCODE_GETATTRIBUTE  = 0x4000010900000000L;
}
