# Duel UI Scaling and Rule-Driven Field Layout: Design

**Date:** 2026-09-05
**Status:** approved in brainstorming, awaiting implementation plan
**Branch:** new feature branch after `deck-loader-and-seed-control` merges

## Motivation

Two defects in `LDLibDuelScreen` block further UI work.

**The screen overflows above GUI scale 2.** Every dimension in `duel_screen.xml` is an absolute minimum in logical pixels (`.square-slot` 48, `.card-slot` 36x48, hand viewports 42) and `#field-area` shrink-wraps its content. Minecraft's logical viewport is the framebuffer divided by the GUI scale, so the field needs about 400 logical pixels of height while a 1080p window offers 360 at scale 3 and 270 at scale 4. `dev/gui_3.png` shows the player's hand and bottom piles cut off at scale 3. The two side overlays are 210 pixels each and cover 420 of the 640 pixels available at scale 3.

**One rule set is hard-coded.** The XML contains 70 hand-written zone elements with ids like `plr-mon-3`, and `FieldRenderer` binds fixed arrays of five, assumes two Extra Monster Zones, and maps pendulum zones onto S/T 0 and 4. That is Master Rule 5 and nothing else. ygopro-core describes a field with three switches (`DUEL_3_COLUMNS_FIELD`, `DUEL_EMZONE`, `DUEL_PZONE` plus `DUEL_SEPARATE_PZONE`), and edopro picks its geometry from the same three at duel start (`duelclient.cpp:830`, `matManager.SetActiveVertices`). Duelcraft cannot start an MR3 or Speed duel today without the client drawing the wrong field.

## Scope

**In scope**

- One fixed 960x540 design canvas scaled to fit any viewport from GUI scale 1 to 4, centered, letterboxed on other aspect ratios.
- A pure-Java `FieldLayout` model derived from the duel flags that owns zone visibility and the zone-to-slot mapping.
- A superset `duel_screen.xml` with the extra slots MR3 needs, shown or hidden per rule.
- `duelFlags` on `DuelStartPayload`, a `DuelRule` preset enum, and an optional trailing rule argument on `/duel test` and `/duel challenge`.
- JUnit coverage of the layout model and the enum.
- Five LDLib2 in-client harness scenarios with Gradle wiring.
- The current visual style stays. No new textures, colors, or spacing beyond what the restructuring requires.

**Out of scope, recorded under Deferred Work**

- Starting LP, hand size, and draw rules for Speed and Rush presets.
- A player-facing multiplier for the canvas scale.
- Any visual restyle.
- Rule-specific forbidden card types.
- Headless harness runs.

## Decisions Made During Brainstorming

| Question | Decision | Reason |
|---|---|---|
| How far does the pass go? | Layout mechanics only, overlays included, current look kept | Overlays break at scale 3 too; restyling doubles the verification surface |
| Which viewports? | GUI scale 1 to 4, fit and fill | Scale 4 on 1080p is a 480x270 viewport and must still show the whole field |
| Which rule sets? | All eight presets modeled, three geometries verified | The engine exposes three switches; modeling all combinations costs one record |
| How to start other rules? | Optional trailing rule argument on `test` and `challenge` | Harness-only testing would leave the feature unreachable in play |
| Scaling strategy | Fixed design canvas with a uniform transform | Smallest stylesheet change, proportions identical at every scale, matches edopro and the LDLib2 editor preview |
| Field structure | Superset XML plus Java layout model | Keeps the XML-first, editor-previewable workflow; the variant set is small and finite |

The canvas approach makes the GUI scale setting stop affecting the duel screen. On a 1080p window the field looks the same at scale 1 and scale 4. A config multiplier can restore player control later.

## Architecture

Three layers, each with one job.

**Presentation canvas (client).** The XML root is a fixed 960x540 canvas. The size provider returns that constant, so `ModularUIScreen.init()` centers the root using its existing `leftPos = (width - uiWidth) / 2` math, which goes negative at small viewports without clamping. A single wrapper element `#duel-canvas` inside the root carries a uniform scale transform. `DuelScreen`, a subclass of `ModularUIScreen`, overrides `init()`, which Minecraft calls on open and on every resize, to compute `k = min(width / 960f, height / 540f)` and apply it. Existing stylesheet numbers become design pixels and survive unchanged. LDLib2 runs hit-testing through the inverse transform (`UIElement.hitTest` calls `transform2D.inversePoint`) and pushes scissor rectangles through the current pose (`GUIContext.enableScissor`), so clicks, hovers, and scroll-view clipping keep working under the scale.

**Field layout model (client, plain Java).** `FieldLayout` holds three values from the duel flags: columns (5 or 3), whether Extra Monster Zones exist, and the pendulum mode (NONE, SHARED, SEPARATE). It answers which slot ids are visible and which slot id an engine zone maps to, expressed relative to the viewer. `FieldRenderer`, `PromptController`, and `ClickDispatcher` resolve zones only through it.

**Rule plumbing (core and server).** `DuelRule` maps the eight `DUEL_MODE_*` presets to flags. `DuelOptions.of(seed, rule)` feeds the engine, `DuelStartPayload.duelFlags` feeds the client, and `/duel` commands accept the rule id.

## Components

### Files

| Layer | File | Change | Job |
|---|---|---|---|
| Client | `client/FieldLayout.java` | new | Pure model: visible slots, zone to slot mapping, pendulum sequences |
| Client | `client/DuelScreen.java` | new | `ModularUIScreen` subclass; `init()` applies the scale factor to the canvas wrapper |
| Client | `client/LDLibDuelScreen.java` | modify | `create(payload)` returns a `DuelScreen`; constant size provider; builds `FieldLayout` from the state's flags |
| Client | `client/FieldRenderer.java` | modify | Slot map keyed by id replaces eight arrays; every lookup goes through `FieldLayout` |
| Client | `client/ClickDispatcher.java` | modify | Context menu converts screen mouse coordinates to canvas coordinates |
| Client | `client/ClientDuelState.java` | modify | Stores `duelFlags` from the start payload |
| Client | `assets/duelcraft/ui/duel_screen.xml` | modify | Fixed root, canvas wrapper, four separate pendulum slots, pendulum markers on S/T 0, 1, 3, 4 |
| Core | `core/DuelRule.java` | new | Enum of the eight presets with flags and a command id |
| Core | `core/DuelOptions.java` | modify | `of(seed, rule)`; `standard(seed)` delegates with MR5 |
| Server | `server/DuelStartPayload.java` | modify | Adds `duelFlags`; hand-written `FriendlyByteBuf` codec, since `StreamCodec.composite` stops at six fields |
| Server | `server/DuelManager.java` | modify | Start methods take a `DuelRule`, put its flags in the payload, log it |
| Server | `server/DuelCommand.java` | modify | Optional trailing rule on `test` and `challenge`; `PendingChallenge` carries it |
| Test | `src/test/.../client/FieldLayoutTest.java` | new | Every preset, every mapping, both sides |
| Test | `src/test/.../core/DuelRuleTest.java` | new | Parse round trip; flags equal the constants |
| Test | `client/uitest/DuelScreenScenario.java` and five subclasses | new, dev-only | Harness scenarios opening the real screen |
| Test | `client/uitest/DuelScreenFixture.java` | new, dev-only | Synthetic start payload and messages that populate every zone type |
| Build | `gradle/ldlib2-uitest.gradle`, `build.gradle` | new, modify | LDLib2's wiring ported from NeoGradle's `runs` to ModDevGradle's `neoForge.runs` |

### FieldLayout

```java
public record FieldLayout(int columns, boolean emz, PendulumMode pendulum) {
    public enum PendulumMode { NONE, SHARED, SEPARATE }
    public enum Side { PLR, OPP }                       // relative to the viewer
    public record Zone(Side side, int location, int sequence) {}

    public static FieldLayout fromFlags(long flags);

    public Optional<String> slotId(Zone zone);          // empty when the rule has no such zone
    public List<Zone> zonesOf(String slotId);           // EMZ slots list two zones, viewer's first
    public Set<String> hiddenSlotIds();                 // slots the XML has but this rule hides
    public int[] pendulumSequences();                   // {}, {0,4}, {1,3}, or {6,7}
    public static List<String> allSlotIds();            // the superset, for binding
}
```

`fromFlags` is total and never throws:

- `columns = (flags & DUEL_3_COLUMNS_FIELD) != 0 ? 3 : 5`
- `emz = (flags & DUEL_EMZONE) != 0 && columns == 5` (the engine has no EMZ on a 3-column field: `field.cpp:1616` empties the range)
- `pendulum = (flags & DUEL_PZONE) == 0 ? NONE : (flags & DUEL_SEPARATE_PZONE) != 0 ? SEPARATE : SHARED`

Preset expectations, from `OcgConstants.DUEL_MODE_*`:

| Presets | columns | emz | pendulum |
|---|---|---|---|
| MR1, GOAT, MR2 | 5 | no | NONE |
| MR3 | 5 | no | SEPARATE |
| MR4, MR5 | 5 | yes | SHARED |
| SPEED, RUSH | 3 | no | NONE |

The model still resolves SHARED on a 3-column field to sequences 1 and 3 (`field.cpp:1265`) for custom flag combinations.

Slot id vocabulary. Ids stay in the owner's frame, so `opp-st-0` is the opponent's zone 0 and the existing `row_reverse` CSS mirrors it. EMZ ids are in the viewer's frame because the two physical slots are shared.

| Zone | Slot id |
|---|---|
| Monster zones | `plr-mon-0` to `plr-mon-4`, `opp-mon-0` to `opp-mon-4` |
| Spell/Trap zones | `plr-st-0` to `plr-st-4`, `opp-st-0` to `opp-st-4` |
| Field spell | `plr-field-spell`, `opp-field-spell` |
| Extra Monster Zones | `emz-left`, `emz-right` |
| Separate pendulum zones (new) | `plr-pz-left`, `plr-pz-right`, `opp-pz-left`, `opp-pz-right` |
| Piles | `plr-deck`, `plr-extra-deck`, `plr-graveyard`, `plr-banished`, and the `opp-` four |

`slotId(Zone)` rules:

| location | sequence | condition | result |
|---|---|---|---|
| MZONE | 0 to 4 | column visible (all on 5 columns, 1 to 3 on 3 columns) | `{side}-mon-{seq}` |
| MZONE | 5 | emz | PLR: `emz-left`, OPP: `emz-right` |
| MZONE | 6 | emz | PLR: `emz-right`, OPP: `emz-left` |
| SZONE | 0 to 4 | column visible | `{side}-st-{seq}` |
| SZONE | 5 | always | `{side}-field-spell` |
| SZONE | 6 | pendulum is SEPARATE | `{side}-pz-left` |
| SZONE | 7 | pendulum is SEPARATE | `{side}-pz-right` |
| EXTRA, GRAVE, REMOVED, DECK | any | always | `{side}-extra-deck`, `-graveyard`, `-banished`, `-deck` |
| anything else | | | empty |

`zonesOf` is the inverse. `emz-left` returns `[(PLR, MZONE, 5), (OPP, MZONE, 6)]` and `emz-right` returns `[(PLR, MZONE, 6), (OPP, MZONE, 5)]`. Every other slot returns one zone.

`hiddenSlotIds`: on 3 columns, `mon` and `st` 0 and 4 on both sides; without EMZ, both `emz-` slots; unless SEPARATE, the four `pz-` slots.

### FieldRenderer after the change

- Binds every id from `allSlotIds()` into `Map<String, UIElement>`. Ids the XML lacks log one warning and count as hidden.
- Adds class `rule-hidden` to `hiddenSlotIds()`. Adds class `pendulum` to the S/T slots named by `pendulumSequences()` when the mode is SHARED.
- `wireFieldClicks` is one loop over visible zone slots. `zonesOf(slotId)` gives the candidates. The candidate whose `mzone`/`szone` entry is occupied wins; otherwise the first, which is the viewer's own zone, so placement prompts target it. The winner converts back to an absolute player before `callbacks.onCardClicked`.
- `refreshMonsterZones(player)` and `refreshSpellZones(player)` iterate sequences 0 to 6 and 0 to 7, converting `player` to a `Side`, and skip zones with no slot. EMZ slots refresh from whichever of their two zones is occupied.
- `findSlotForLocation(CardLocation)` is `slots.get(layout.slotId(toZone(loc)))`.
- `highlightValidPlaces(field)` loops both sides (bit offsets 0 and 16), monster bits 0 to 6 and spell bits 8 to 15, and highlights every clear bit that has a slot. This also covers the field spell bit and separate pendulum bits the current code skips.
- `getFieldBit` is unchanged.
- The `emz-opp` rotation class still marks an opponent-owned card in a shared EMZ slot.
- The eight public slot arrays are removed. `ClickDispatcher` uses `getFieldBit` and `PromptController` uses `findSlotForLocation`; neither touches the arrays today.

### DuelScreen and the canvas

```java
public final class DuelScreen extends ModularUIScreen {
    public static final int DESIGN_WIDTH = 960;
    public static final int DESIGN_HEIGHT = 540;
    private static final float MIN_SCALE = 0.25f;

    @Override
    public void init() {
        super.init();                                   // LDLib2 centers the 960x540 root
        if (width == 0 || height == 0) return;          // minimized window
        float k = Math.max(MIN_SCALE, Math.min(width / (float) DESIGN_WIDTH, height / (float) DESIGN_HEIGHT));
        canvas.transform(t -> t.scale(k));              // default pivot is the center
    }
}
```

`LDLibDuelScreen.create` looks up `#duel-canvas` with `ui.selectId` and passes it to the `DuelScreen` constructor along with the `ModularUI` and title. `Transform2D.scale` assigns rather than multiplies, so repeated resizes are idempotent. The root element's own transform is ignored by LDLib2 (`computeLocalToWorldPose` returns the ModularUI pose for a parentless element), which is why the scale lives on the wrapper. `LDLibDuelScreen.loadFromXml` passes `screen -> Size.of(960, 540)` as the size provider.

`ClickDispatcher` receives the canvas element. `event.x` and `event.y`, which LDLib2 fills with screen coordinates (`ModularUI.java:848`), go through `canvas.getWorldToLocalPose()`. That undoes the scale but yields root-layout coordinates, which still carry the root's centering offset, so `showContextMenu` receives them minus `canvas.getPositionX()` and `canvas.getPositionY()`. That makes them canvas-relative, which is what `left` and `top` on an absolute child are measured from. The existing flip-and-nudge logic then applies unchanged, already measuring against the canvas's 960x540.

### XML restructuring

- `#duel-root`: `width: 960; height: 540;` replacing the percent sizes. Its only child is `#duel-canvas`.
- `#duel-canvas`: `width: 100%; height: 100%; flex-direction: column;`. Every current child of the root moves under it unchanged, including the absolute-positioned overlays, which then position against the canvas.
- Separate pendulum slots: each is the middle child of an inner pile column, in EDOPro's order — field spell / pendulum (left) / extra deck, and graveyard / pendulum (right) / deck. Class `card-slot zone-slot pz-slot`, card-proportioned like the piles they sit between, each holding a pendulum `zone-icon`. The opponent's columns keep their vertical mirroring.
- Pendulum markers: the lapis and redstone icons on S/T 0 and 4 become `zone-icon pendulum-marker` children, and S/T 1 and 3 gain the same children. Rules: `.pendulum-marker { display: none; }` and `.pendulum > .pendulum-marker { display: flex; }`. `FieldRenderer` owns the `pendulum` class.
- `.rule-hidden { display: none; }`. Hidden slots leave the flex row, so a 3-column field renders three zones wide.
- `#center-row` holds only the phase buttons and the EMZ slots; hidden EMZ slots collapse and the buttons stay.
- Each banished pile lives in its own outer `pile-column`, level with that side's graveyard, with an invisible spacer column of the same width at the opposite end of the row so both zone grids stay aligned.
- `.hand-row` has no max width: both hands stretch to the field's content width.
- Nothing else in the stylesheet changes. `#field-area` keeps its centering within the canvas.

### DuelRule, DuelOptions, payload, commands

```java
public enum DuelRule {
    MR1(DUEL_MODE_MR1), GOAT(DUEL_MODE_GOAT), MR2(DUEL_MODE_MR2), MR3(DUEL_MODE_MR3),
    MR4(DUEL_MODE_MR4), MR5(DUEL_MODE_MR5), SPEED(DUEL_MODE_SPEED), RUSH(DUEL_MODE_RUSH);

    public long flags();
    public String id();                                 // lowercase name, used by commands
    public static Optional<DuelRule> parse(String id);  // case-insensitive
    public static List<String> ids();                   // for tab completion
}
```

- `DuelOptions.of(long seed, DuelRule rule)` builds options with `rule.flags()`. `standard(seed)` becomes `of(seed, DuelRule.MR5)` and stays for existing callers and tests.
- `DuelStartPayload(int localPlayer, String opponentName, int lp0, int lp1, int deckSize, int extraSize, long duelFlags)`. The codec becomes `StreamCodec<FriendlyByteBuf, DuelStartPayload>` written with `writeVarInt`, `writeUtf`, `writeLong` and the matching reads, the same shape `DuelMessagePayload` already uses.
- `DuelManager.startSoloDuel(player, seed, rule, playerDeck, aiDeck, names...)` and `startDuel(p1, p2, seed, rule, decks..., names...)`. The convenience overloads pass `DuelRule.MR5`. Both put `options.flags()` into the payload and add `rule=<id>` to the start log line.
- `DuelCommand`: `test [aiDeck [seed [rule]]]` and `challenge <player> [seed [rule]]`, rule as a `StringArgumentType.word()` with suggestions from `DuelRule.ids()`. `PendingChallenge(UUID challengerUUID, long seed, DuelRule rule)`. An unknown id fails with the list of valid ids.

### Harness wiring

`gradle/ldlib2-uitest.gradle` is LDLib2's script (LGPL-3.0, published for copying per its header) ported to ModDevGradle:

- `runs.matching { it.name == 'client' }.configureEach` becomes `neoForge.runs.matching { it.name == 'client' }.configureEach`; `run.systemProperty(k, v)` is the same call.
- The system property names (`ldlib2.uitest.run`, `.out`, `.exclude`, `.guiScale`, `.window`, `.inputMode`, `.watchdogSec`, `.keepOpen`) are what the LDLib2 runner reads and stay verbatim.
- `verifyUiTest` and `ldlib2CleanUiTestReport` are kept as written. The headless block is dropped.
- `build.gradle` adds `apply from: rootProject.file('gradle/ldlib2-uitest.gradle')` after the `neoForge { }` block. The script is inert without `-PldTest`.

Scenario classes live in `src/main/java/com/haxerus/duelcraft/client/uitest/`, because the harness discovers them by annotation scan across loaded mods and `src/test` has no Minecraft on its classpath. Each is registered with `@LDLRegisterClient(name, group = "duelcraft", registry = UIScenario.REGISTRY, environment = RegistrationEnvironment.DEV_ONLY)`, which keeps them out of production builds.

## Data Flow

**1. Starting a duel with a rule.** `/duel test alpha 42 mr3` parses the trailing word through `DuelRule.parse`, defaulting to MR5 when absent. `DuelCommand` passes the rule to `DuelManager.startSoloDuel`, which builds `DuelOptions.of(seed, rule)`. The same flags go to the engine in `OCG_CreateDuel` and into `DuelStartPayload.duelFlags`, so client and engine cannot disagree about the field shape. For a challenge the rule rides in `PendingChallenge` next to the seed and applies on accept. The start log line reads `seed=42, rule=mr3, player=alpha, aiDeck=alpha`.

**2. Opening and resizing.** `ClientPayloadHandler.handleStart` calls `LDLibDuelScreen.open`, which builds `ClientDuelState`, derives `FieldLayout.fromFlags(state.duelFlags)`, loads the XML, and hands the layout to `FieldRenderer`. FieldRenderer binds the superset slots, hides what the rule excludes, and tags pendulum markers. `create` returns a `DuelScreen`; Minecraft calls its `init()` now and on every window resize. That `init()` runs LDLib2's setup with the constant 960x540 size, so the root is centered, then applies the scale factor to the wrapper. The layout tree does not change on resize. Only the factor does.

**3. Engine zone to screen.** A `Move` or `UpdateData` message lands in `ClientDuelState`, which keeps its arrays indexed by absolute player and marks a dirty flag. On tick, `FieldRenderer.refreshMonsterZones(player)` converts the player to a `Side`, asks the layout for the slot of each sequence, and renders into that element. Sequences the rule lacks return empty and are skipped, so a 3-column field never touches sequence 0 or 4 even though the arrays hold them.

**4. Screen to engine.** A click on a slot runs `zonesOf(slotId)`. Ordinary slots yield one zone. An EMZ slot yields two, and FieldRenderer picks the occupied one, falling back to the viewer's own zone for placement. The winner converts back to an absolute player for `onCardClicked`, so `ClickDispatcher`, `PromptController`, and `ResponseBuilder` see the same `(player, location, sequence)` triple they see today. The one new step is the context menu converting screen coordinates to canvas coordinates before positioning itself.

**5. Placement prompts.** A `SelectPlace` bitmask is relative to the asking player. `highlightValidPlaces` walks both sides, monster bits 0 to 6 and spell bits 8 to 15, asks the layout for a slot for each clear bit, and highlights whatever exists. A click on a highlighted slot goes through flow 4, and `getFieldBit` turns the triple back into the response bit.

**6. Harness run.** `gradlew runClient -PldTest=group:duelcraft` launches a client and a world. Each scenario asks `DuelScreenFixture` for a start payload with the rule's flags, opens the screen through `LDLibDuelScreen.create`, waits for the ModularUI, feeds fixture messages through `applyMessage`, and waits two ticks for dirty flags to drain. It asserts the canvas bounds sit inside the viewport, that hidden slots are hidden and rule-specific slots visible, takes a screenshot, and closes the screen. Output lands in `build/ldlib2-uitest/`; `verifyUiTest` fails the build on any failed check.

## Error Handling

- **Rule parsing.** An unknown trailing word fails the command with the valid ids. Parsing is case-insensitive.
- **Flag combinations.** `fromFlags` never throws. Three columns force `emz = false`. A separate-pendulum bit without the pendulum bit resolves to NONE. Unknown bits are ignored.
- **Zones the rule lacks.** A message naming a sequence with no slot is skipped with a debug log. Today's code returns null for the same case and relies on downstream null checks.
- **Missing XML ids.** Binding logs one warning per missing id and treats the slot as hidden.
- **Degenerate windows.** `k` has a floor of 0.25, and `init()` skips the transform when either dimension is zero.
- **Async card data.** The renderer already shows card backs and codes without the database and images. `showCardInfo` removes the banner's `hidden` class before it consults the database, so the hover check in the harness passes on a fresh checkout without the cache. Fixtures and assertions never depend on names or artwork.

## Testing Strategy

### JUnit, in `./gradlew test`, no Minecraft on the classpath

`FieldLayoutTest`

- Each of the eight presets produces the columns, EMZ flag, and pendulum mode in the table above.
- For MR5, MR3, and Speed: a table of zone to slot expectations on both sides, covering both EMZ cross-mappings, the four separate pendulum slots, the field spell, the 3-column hidden set, and empties for zones the rule lacks.
- `pendulumSequences()` for all four modes, including SHARED on 3 columns.
- Round trip over every visible slot: each zone in `zonesOf(slot)` maps back to `slot`.
- `hiddenSlotIds()` per geometry.

`DuelRuleTest`

- `parse` round-trips every id, accepts mixed case, and returns empty on garbage.
- Every enum's `flags()` equals the matching `DUEL_MODE_*` constant.

### LDLib2 harness, `gradlew runClient -PldTest=group:duelcraft`

| Scenario | GUI scale | Asserts beyond the shared checks |
|---|---|---|
| duel_mr5_scale2 | 2 | EMZ slots visible, pendulum slots hidden |
| duel_mr5_scale3 | 3 | Same, plus hovering the `.card` inside an occupied monster zone shows `#card-info-banner` |
| duel_mr5_scale4 | 4 | Same as scale 2 |
| duel_mr3_scale3 | 3 | Pendulum slots visible with a card in `plr-pz-left`, EMZ hidden |
| duel_speed_scale3 | 3 | `mon` and `st` 0 and 4 hidden on both sides, EMZ hidden, pendulum markers absent |

Shared checks in every scenario: `#duel-canvas` bounds inside the viewport (read from `Minecraft.getInstance().getWindow()` scaled size), `#opponent-hand` and `#player-hand` bounds inside the canvas, a screenshot named `<rule>-scale<n>`, and a teardown that closes the screen.

The hover check at scale 3 proves hit-testing survives the transform. The banner opens only through a real mouse-enter on the card element.

`DuelScreenFixture` provides `startPayload(DuelRule)` with 8000 LP, 40-card decks, 15-card extra decks, and the rule's flags, and `populate(DuelRule)`, which sends through `LDLibDuelScreen.applyMessage`: a ten-card draw for each player so the hand never runs dry, a face-up monster moved from hand into every visible monster zone on both sides, one monster into `(PLR, MZONE, 5)` when the rule has EMZ, one spell into `(PLR, SZONE, 1)`, one card into `(PLR, SZONE, 6)` when the mode is SEPARATE, and one card into the graveyard. Card codes come from `Deck.standard()`.

### Manual, with the decks in `run/duelcraft/decks/`

```
/duel test alpha 42           GUI scale 3, the case from dev/gui_3.png: nothing clipped
/duel test alpha 42 mr3       separate pendulum zones flank both S/T rows
/duel test alpha 42 speed     three columns, no EMZ
click a monster               context menu opens under the cursor at scale 3
runClient2 challenge          player 2 sees EMZ and pendulum zones mirrored correctly
```

### Success criteria

1. All JUnit suites pass, existing 173 tests included.
2. All five harness scenarios pass and their screenshots at scale 3 and 4 show the whole field.
3. The three manual duels render fully at GUI scale 3 and the context menu opens under the cursor.
4. Player 2's mirrored view behaves as it does today.

## Deferred Work

- **Speed and Rush game rules.** edopro's presets also set starting LP (4000 for Speed), starting hand, and draw count. `DuelRule` carries flags only in this pass; a `PlayerOptions` per rule is the natural extension.
- **Canvas scale multiplier.** A client config value multiplied into `k` would give players back a size control.
- **Visual restyle.** Field textures, zone markers, colors.
- **Forbidden card types per rule.** `DUEL_MODE_*_FORB` constants exist in `OcgConstants`; enforcement belongs with deck validation.
- **Headless harness runs.** The dropped block from LDLib2's script patches `config/fml.toml` in the run directory; port it when CI needs it.

## Reference Facts

- ygopro-core, `native/ygopro-core/field.cpp`: 3-column fields use `list_mzone[sequence + 1]` (line 526), `get_pzone_index` returns `seq + 6` for separate zones, `seq * 2 + 1` on 3 columns, else `seq * 4` (lines 1263 to 1267), and the EMZ range is empty on 3 columns (line 1616).
- edopro, `gframe/duelclient.cpp:830` and `drawing.cpp:158-160`: geometry and hover targets derive from `DUEL_3_COLUMNS_FIELD`, `DUEL_SEPARATE_PZONE`, and `DUEL_EMZONE` at runtime.
- LDLib2 2.2.39.a, `ModularUIScreen.init()`: centers the root with unclamped `leftPos`/`topPos`. `ModularUI.init` applies `ui.dynamicSize` to the root's width and height.
- LDLib2, `UIElement.computeLocalToWorldPose`: a parentless element's own transform is not applied.
- LDLib2, `GUIContext.enableScissor`: scissor rectangles pass through the pose matrix. `UIElement.hitTest`: mouse points pass through `transform2D.inversePoint`.
- LDLib2, `Transform2D.scale(float)`: assigns the scale. Default pivot is the center.
- LDLib2, `uitest.md` and `gradle/ldlib2-uitest.gradle`: scenario API, `ScenarioOptions.guiScale`, `checkBounds(selector, Predicate<ElementBounds>)`, `openScreen(name, Function<TestContext, Screen>)`.
- Memory notes `reference_pendulum_emz_zones.md` and `reference_selectplace_bitmask.md` describe the sequence and bitmask conventions the mapping table encodes.
