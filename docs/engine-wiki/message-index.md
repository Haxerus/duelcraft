# Message index

This index covers every `MSG_*` constant declared by ygopro-core 11.0 at commit `122e0d091a0f399221a4510cc98a406ae485905f`. The declaration block is [`ocgapi_constants.h`](../../native/ygopro-core/ocgapi_constants.h#L207). “Declared only” means this core has no `new_message` producer and EDOPro has no active client case; reserve the ID but do not invent a payload.

The producer links identify a representative write site. The consumer links enter EDOPro's primary message switch; several host-generated control messages have no core producer.

## Control, setup, and prompts

| ID | Constant | Producer | EDOPro consumer |
|---:|---|---|---|
| 1 | `MSG_RETRY` | [`SelectBattleCmd` validation](../../native/ygopro-core/playerop.cpp#L56) | [`ClientAnalyze`](../../../edopro/gframe/duelclient.cpp#L1348) |
| 2 | `MSG_HINT` | [`Duel.Hint`](../../native/ygopro-core/libduel.cpp#L3063) | [`ClientAnalyze`](../../../edopro/gframe/duelclient.cpp#L1390) |
| 3 | `MSG_WAITING` | host-generated | [`ClientAnalyze`](../../../edopro/gframe/duelclient.cpp#L1631) |
| 4 | `MSG_START` | host-generated | [`ClientAnalyze`](../../../edopro/gframe/duelclient.cpp#L1638) |
| 5 | `MSG_WIN` | [`Processor`](../../native/ygopro-core/processor.cpp#L4420) | [`ClientAnalyze`](../../../edopro/gframe/duelclient.cpp#L1602) |
| 6 | `MSG_UPDATE_DATA` | host wraps `OCG_DuelQueryLocation` | [`ClientAnalyze`](../../../edopro/gframe/duelclient.cpp#L1687) |
| 7 | `MSG_UPDATE_CARD` | host wraps `OCG_DuelQuery` | [`ClientAnalyze`](../../../edopro/gframe/duelclient.cpp#L1694) |
| 8 | `MSG_REQUEST_DECK` | declared only | declared only |
| 10 | `MSG_SELECT_BATTLECMD` | [`SelectBattleCmd`](../../native/ygopro-core/playerop.cpp#L18) | [`ClientAnalyze`](../../../edopro/gframe/duelclient.cpp#L1702) |
| 11 | `MSG_SELECT_IDLECMD` | [`SelectIdleCmd`](../../native/ygopro-core/playerop.cpp#L69) | [`ClientAnalyze`](../../../edopro/gframe/duelclient.cpp#L1778) |
| 12 | `MSG_SELECT_EFFECTYN` | [`SelectEffectYesNo`](../../native/ygopro-core/playerop.cpp#L166) | [`ClientAnalyze`](../../../edopro/gframe/duelclient.cpp#L1927) |
| 13 | `MSG_SELECT_YESNO` | [`SelectYesNo`](../../native/ygopro-core/playerop.cpp#L189) | [`ClientAnalyze`](../../../edopro/gframe/duelclient.cpp#L1956) |
| 14 | `MSG_SELECT_OPTION` | [`SelectOption`](../../native/ygopro-core/playerop.cpp#L205) | [`ClientAnalyze`](../../../edopro/gframe/duelclient.cpp#L1965) |
| 15 | `MSG_SELECT_CARD` | [`SelectCard`](../../native/ygopro-core/playerop.cpp#L279) | [`ClientAnalyze`](../../../edopro/gframe/duelclient.cpp#L1976) |
| 16 | `MSG_SELECT_CHAIN` | [`SelectChain`](../../native/ygopro-core/playerop.cpp#L454) | [`ClientAnalyze`](../../../edopro/gframe/duelclient.cpp#L2115) |
| 18 | `MSG_SELECT_PLACE` | [`SelectPlace`](../../native/ygopro-core/playerop.cpp#L506) | [`ClientAnalyze`](../../../edopro/gframe/duelclient.cpp#L2228) |
| 19 | `MSG_SELECT_POSITION` | [`SelectPosition`](../../native/ygopro-core/playerop.cpp#L604) | [`ClientAnalyze`](../../../edopro/gframe/duelclient.cpp#L2314) |
| 20 | `MSG_SELECT_TRIBUTE` | [`SelectTributeP`](../../native/ygopro-core/playerop.cpp#L641) | [`ClientAnalyze`](../../../edopro/gframe/duelclient.cpp#L2356) |
| 21 | `MSG_SORT_CHAIN` | [`SortCard`](../../native/ygopro-core/playerop.cpp#L878) | [`ClientAnalyze`](../../../edopro/gframe/duelclient.cpp#L2480) |
| 22 | `MSG_SELECT_COUNTER` | [`SelectCounter`](../../native/ygopro-core/playerop.cpp#L708) | [`ClientAnalyze`](../../../edopro/gframe/duelclient.cpp#L2391) |
| 23 | `MSG_SELECT_SUM` | [`SelectSum`](../../native/ygopro-core/playerop.cpp#L787) | [`ClientAnalyze`](../../../edopro/gframe/duelclient.cpp#L2417) |
| 24 | `MSG_SELECT_DISFIELD` | [`SelectPlace`](../../native/ygopro-core/playerop.cpp#L506) | [`ClientAnalyze`](../../../edopro/gframe/duelclient.cpp#L2229) |
| 25 | `MSG_SORT_CARD` | [`SortCard`](../../native/ygopro-core/playerop.cpp#L878) | [`ClientAnalyze`](../../../edopro/gframe/duelclient.cpp#L2479) |
| 26 | `MSG_SELECT_UNSELECT_CARD` | [`SelectUnselectCard`](../../native/ygopro-core/playerop.cpp#L391) | [`ClientAnalyze`](../../../edopro/gframe/duelclient.cpp#L2033) |

## Confirmation, deck, and turn state

| ID | Constant | Producer | EDOPro consumer |
|---:|---|---|---|
| 30 | `MSG_CONFIRM_DECKTOP` | [`Duel.ConfirmDecktop`](../../native/ygopro-core/libduel.cpp#L832) | [`ClientAnalyze`](../../../edopro/gframe/duelclient.cpp#L2513) |
| 31 | `MSG_CONFIRM_CARDS` | [`Duel.ConfirmCards`](../../native/ygopro-core/libduel.cpp#L891) | [`ClientAnalyze`](../../../edopro/gframe/duelclient.cpp#L2582) |
| 32 | `MSG_SHUFFLE_DECK` | [`field::shuffle`](../../native/ygopro-core/field.cpp#L990) | [`ClientAnalyze`](../../../edopro/gframe/duelclient.cpp#L2691) |
| 33 | `MSG_SHUFFLE_HAND` | [`field::shuffle`](../../native/ygopro-core/field.cpp#L970) | [`ClientAnalyze`](../../../edopro/gframe/duelclient.cpp#L2733) |
| 34 | `MSG_REFRESH_DECK` | host-generated | [`ClientAnalyze`](../../../edopro/gframe/duelclient.cpp#L2807) |
| 35 | `MSG_SWAP_GRAVE_DECK` | [`field::swap_grave_deck`](../../native/ygopro-core/field.cpp#L1051) | [`ClientAnalyze`](../../../edopro/gframe/duelclient.cpp#L2811) |
| 36 | `MSG_SHUFFLE_SET_CARD` | [`Duel.ShuffleSetCard`](../../native/ygopro-core/libduel.cpp#L1423) | [`ClientAnalyze`](../../../edopro/gframe/duelclient.cpp#L2881) |
| 37 | `MSG_REVERSE_DECK` | [`Processor`](../../native/ygopro-core/processor.cpp#L4926) | [`ClientAnalyze`](../../../edopro/gframe/duelclient.cpp#L2852) |
| 38 | `MSG_DECK_TOP` | [`Processor`](../../native/ygopro-core/processor.cpp#L4930) | [`ClientAnalyze`](../../../edopro/gframe/duelclient.cpp#L2862) |
| 39 | `MSG_SHUFFLE_EXTRA` | [`field::shuffle`](../../native/ygopro-core/field.cpp#L970) | [`ClientAnalyze`](../../../edopro/gframe/duelclient.cpp#L2776) |
| 40 | `MSG_NEW_TURN` | [`Processor`](../../native/ygopro-core/processor.cpp#L3334) | [`ClientAnalyze`](../../../edopro/gframe/duelclient.cpp#L2928) |
| 41 | `MSG_NEW_PHASE` | [`Processor`](../../native/ygopro-core/processor.cpp#L2787) | [`ClientAnalyze`](../../../edopro/gframe/duelclient.cpp#L2960) |
| 42 | `MSG_CONFIRM_EXTRATOP` | [`Duel.ConfirmExtratop`](../../native/ygopro-core/libduel.cpp#L862) | [`ClientAnalyze`](../../../edopro/gframe/duelclient.cpp#L2548) |

## Card, summon, and chain state

| ID | Constant | Producer | EDOPro consumer |
|---:|---|---|---|
| 50 | `MSG_MOVE` | [`field::add_card`](../../native/ygopro-core/field.cpp#L280) | [`ClientAnalyze`](../../../edopro/gframe/duelclient.cpp#L3044) |
| 53 | `MSG_POS_CHANGE` | [`ChangePosition`](../../native/ygopro-core/operations.cpp#L5278) | [`ClientAnalyze`](../../../edopro/gframe/duelclient.cpp#L3217) |
| 54 | `MSG_SET` | [`MSet`](../../native/ygopro-core/operations.cpp#L2742) | [`ClientAnalyze`](../../../edopro/gframe/duelclient.cpp#L3240) |
| 55 | `MSG_SWAP` | [`field::swap_card`](../../native/ygopro-core/field.cpp#L464) | [`ClientAnalyze`](../../../edopro/gframe/duelclient.cpp#L3247) |
| 56 | `MSG_FIELD_DISABLED` | [`Processor`](../../native/ygopro-core/processor.cpp#L4476) | [`ClientAnalyze`](../../../edopro/gframe/duelclient.cpp#L3273) |
| 60 | `MSG_SUMMONING` | [`Summon`](../../native/ygopro-core/operations.cpp#L2239) | [`ClientAnalyze`](../../../edopro/gframe/duelclient.cpp#L3280) |
| 61 | `MSG_SUMMONED` | [`Summon`](../../native/ygopro-core/operations.cpp#L2309) | [`ClientAnalyze`](../../../edopro/gframe/duelclient.cpp#L3298) |
| 62 | `MSG_SPSUMMONING` | [`SpecialSummon`](../../native/ygopro-core/operations.cpp#L3143) | [`ClientAnalyze`](../../../edopro/gframe/duelclient.cpp#L3302) |
| 63 | `MSG_SPSUMMONED` | [`SpecialSummon`](../../native/ygopro-core/operations.cpp#L3223) | [`ClientAnalyze`](../../../edopro/gframe/duelclient.cpp#L3321) |
| 64 | `MSG_FLIPSUMMONING` | [`FlipSummon`](../../native/ygopro-core/operations.cpp#L2373) | [`ClientAnalyze`](../../../edopro/gframe/duelclient.cpp#L3325) |
| 65 | `MSG_FLIPSUMMONED` | [`FlipSummon`](../../native/ygopro-core/operations.cpp#L2408) | [`ClientAnalyze`](../../../edopro/gframe/duelclient.cpp#L3349) |
| 70 | `MSG_CHAINING` | [`Processor`](../../native/ygopro-core/processor.cpp#L3700) | [`ClientAnalyze`](../../../edopro/gframe/duelclient.cpp#L3353) |
| 71 | `MSG_CHAINED` | [`Processor`](../../native/ygopro-core/processor.cpp#L3893) | [`ClientAnalyze`](../../../edopro/gframe/duelclient.cpp#L3416) |
| 72 | `MSG_CHAIN_SOLVING` | [`Processor`](../../native/ygopro-core/processor.cpp#L4113) | [`ClientAnalyze`](../../../edopro/gframe/duelclient.cpp#L3426) |
| 73 | `MSG_CHAIN_SOLVED` | [`Processor`](../../native/ygopro-core/processor.cpp#L4279) | [`ClientAnalyze`](../../../edopro/gframe/duelclient.cpp#L3444) |
| 74 | `MSG_CHAIN_END` | [`Processor`](../../native/ygopro-core/processor.cpp#L4339) | [`ClientAnalyze`](../../../edopro/gframe/duelclient.cpp#L3448) |
| 75 | `MSG_CHAIN_NEGATED` | [`NegateEffect`](../../native/ygopro-core/operations.cpp#L36) | [`ClientAnalyze`](../../../edopro/gframe/duelclient.cpp#L3457) |
| 76 | `MSG_CHAIN_DISABLED` | [`DisableEffect`](../../native/ygopro-core/operations.cpp#L58) | [`ClientAnalyze`](../../../edopro/gframe/duelclient.cpp#L3458) |
| 80 | `MSG_CARD_SELECTED` | [`Processor`](../../native/ygopro-core/processor.cpp#L2031) | [`ClientAnalyze`](../../../edopro/gframe/duelclient.cpp#L3493) |
| 81 | `MSG_RANDOM_SELECTED` | [`Group.RandomSelect`](../../native/ygopro-core/libgroup.cpp#L326) | [`ClientAnalyze`](../../../edopro/gframe/duelclient.cpp#L3470) |
| 83 | `MSG_BECOME_TARGET` | [`BecomeTarget`](../../native/ygopro-core/operations.cpp#L93) | [`ClientAnalyze`](../../../edopro/gframe/duelclient.cpp#L3494) |

## LP, battle, randomness, and declarations

| ID | Constant | Producer | EDOPro consumer |
|---:|---|---|---|
| 90 | `MSG_DRAW` | [`Draw`](../../native/ygopro-core/operations.cpp#L482) | [`ClientAnalyze`](../../../edopro/gframe/duelclient.cpp#L3529) |
| 91 | `MSG_DAMAGE` | [`Damage`](../../native/ygopro-core/operations.cpp#L603) | [`ClientAnalyze`](../../../edopro/gframe/duelclient.cpp#L3559) |
| 92 | `MSG_RECOVER` | [`Recover`](../../native/ygopro-core/operations.cpp#L674) | [`ClientAnalyze`](../../../edopro/gframe/duelclient.cpp#L3583) |
| 93 | `MSG_EQUIP` | [`card::equip`](../../native/ygopro-core/card.cpp#L1557) | [`ClientAnalyze`](../../../edopro/gframe/duelclient.cpp#L3605) |
| 94 | `MSG_LPUPDATE` | [`Duel.SetLP`](../../native/ygopro-core/libduel.cpp#L48) | [`ClientAnalyze`](../../../edopro/gframe/duelclient.cpp#L3628) |
| 95 | `MSG_UNEQUIP` | declared only in this core | [`ClientAnalyze`](../../../edopro/gframe/duelclient.cpp#L3642) |
| 96 | `MSG_CARD_TARGET` | [`card::create_relation`](../../native/ygopro-core/card.cpp#L2351) | [`ClientAnalyze`](../../../edopro/gframe/duelclient.cpp#L3656) |
| 97 | `MSG_CANCEL_TARGET` | [`card::release_relation`](../../native/ygopro-core/card.cpp#L2364) | [`ClientAnalyze`](../../../edopro/gframe/duelclient.cpp#L3672) |
| 100 | `MSG_PAY_LPCOST` | [`PayLpCost`](../../native/ygopro-core/operations.cpp#L749) | [`ClientAnalyze`](../../../edopro/gframe/duelclient.cpp#L3688) |
| 101 | `MSG_ADD_COUNTER` | [`card::add_counter`](../../native/ygopro-core/card.cpp#L2247) | [`ClientAnalyze`](../../../edopro/gframe/duelclient.cpp#L3711) |
| 102 | `MSG_REMOVE_COUNTER` | [`card::remove_counter`](../../native/ygopro-core/card.cpp#L1877) | [`ClientAnalyze`](../../../edopro/gframe/duelclient.cpp#L3732) |
| 110 | `MSG_ATTACK` | [`Processor`](../../native/ygopro-core/processor.cpp#L2118) | [`ClientAnalyze`](../../../edopro/gframe/duelclient.cpp#L3753) |
| 111 | `MSG_BATTLE` | [`Processor`](../../native/ygopro-core/processor.cpp#L2444) | [`ClientAnalyze`](../../../edopro/gframe/duelclient.cpp#L3792) |
| 112 | `MSG_ATTACK_DISABLED` | [`Processor`](../../native/ygopro-core/processor.cpp#L2174) | [`ClientAnalyze`](../../../edopro/gframe/duelclient.cpp#L3828) |
| 113 | `MSG_DAMAGE_STEP_START` | [`Processor`](../../native/ygopro-core/processor.cpp#L2296) | [`ClientAnalyze`](../../../edopro/gframe/duelclient.cpp#L3832) |
| 114 | `MSG_DAMAGE_STEP_END` | [`Processor`](../../native/ygopro-core/processor.cpp#L2720) | [`ClientAnalyze`](../../../edopro/gframe/duelclient.cpp#L3835) |
| 120 | `MSG_MISSED_EFFECT` | [`Processor`](../../native/ygopro-core/processor.cpp#L4374) | [`ClientAnalyze`](../../../edopro/gframe/duelclient.cpp#L3838) |
| 121 | `MSG_BE_CHAIN_TARGET` | declared only | declared only |
| 122 | `MSG_CREATE_RELATION` | declared only | declared only |
| 123 | `MSG_RELEASE_RELATION` | declared only | declared only |
| 130 | `MSG_TOSS_COIN` | [`TossCoin`](../../native/ygopro-core/operations.cpp#L6034) | [`ClientAnalyze`](../../../edopro/gframe/duelclient.cpp#L3847) |
| 131 | `MSG_TOSS_DICE` | [`TossDice`](../../native/ygopro-core/operations.cpp#L6112) | [`ClientAnalyze`](../../../edopro/gframe/duelclient.cpp#L3865) |
| 132 | `MSG_ROCK_PAPER_SCISSORS` | [`RockPaperScissors`](../../native/ygopro-core/playerop.cpp#L1134) | [`ClientAnalyze`](../../../edopro/gframe/duelclient.cpp#L3883) |
| 133 | `MSG_HAND_RES` | [`RockPaperScissors`](../../native/ygopro-core/playerop.cpp#L1151) | [`ClientAnalyze`](../../../edopro/gframe/duelclient.cpp#L3891) |
| 140 | `MSG_ANNOUNCE_RACE` | [`AnnounceRace`](../../native/ygopro-core/playerop.cpp#L916) | [`ClientAnalyze`](../../../edopro/gframe/duelclient.cpp#L3909) |
| 141 | `MSG_ANNOUNCE_ATTRIB` | [`AnnounceAttribute`](../../native/ygopro-core/playerop.cpp#L951) | [`ClientAnalyze`](../../../edopro/gframe/duelclient.cpp#L3920) |
| 142 | `MSG_ANNOUNCE_CARD` | [`AnnounceCard`](../../native/ygopro-core/playerop.cpp#L1075) | [`ClientAnalyze`](../../../edopro/gframe/duelclient.cpp#L3935) |
| 143 | `MSG_ANNOUNCE_NUMBER` | [`AnnounceNumber`](../../native/ygopro-core/playerop.cpp#L1099) | [`ClientAnalyze`](../../../edopro/gframe/duelclient.cpp#L3949) |

## Hints, reload, and extensions

| ID | Constant | Producer | EDOPro consumer |
|---:|---|---|---|
| 160 | `MSG_CARD_HINT` | [`card` hint](../../native/ygopro-core/card.cpp#L1799) | [`ClientAnalyze`](../../../edopro/gframe/duelclient.cpp#L3964) |
| 161 | `MSG_TAG_SWAP` | [`field::tag_swap`](../../native/ygopro-core/field.cpp#L1164) | [`ClientAnalyze`](../../../edopro/gframe/duelclient.cpp#L4053) |
| 162 | `MSG_RELOAD_FIELD` | [`field::reload_field_info`](../../native/ygopro-core/field.cpp#L78) | [`ClientAnalyze`](../../../edopro/gframe/duelclient.cpp#L4136) |
| 163 | `MSG_AI_NAME` | [`Debug.SetAIName`](../../native/ygopro-core/libdebug.cpp#L212) | [`SingleMode`](../../../edopro/gframe/single_mode.cpp#L395) |
| 164 | `MSG_SHOW_HINT` | [`Debug.ShowHint`](../../native/ygopro-core/libdebug.cpp#L213) | [`SingleMode`](../../../edopro/gframe/single_mode.cpp#L396) |
| 165 | `MSG_PLAYER_HINT` | [`field` hint](../../native/ygopro-core/field.cpp#L1335) | [`ClientAnalyze`](../../../edopro/gframe/duelclient.cpp#L3999) |
| 170 | `MSG_MATCH_KILL` | [`Damage`](../../native/ygopro-core/operations.cpp#L609) | [`ClientAnalyze`](../../../edopro/gframe/duelclient.cpp#L4013) |
| 180 | `MSG_CUSTOM_MSG` | declared only | declared only |
| 190 | `MSG_REMOVE_CARDS` | [`Duel.RemoveCards`](../../native/ygopro-core/libduel.cpp#L536) | [`ClientAnalyze`](../../../edopro/gframe/duelclient.cpp#L4017) |

## Finding an exact payload

Start at the producer link and read every `message->write<T>` call until the branch ends. Then compare EDOPro's consumer reads. The producer governs this pinned ABI; the consumer often contains `CompatRead<old,new>` branches which reveal historical widths but do not change the v11 stream. Record framing remains `u32 length + u8 ID + payload` for every category.
