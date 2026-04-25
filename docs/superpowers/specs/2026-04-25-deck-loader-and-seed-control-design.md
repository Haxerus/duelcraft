# Deck Loader and Seed Control — Design

**Status:** Draft for review
**Date:** 2026-04-25
**Goal:** Eliminate the need to recompile and relaunch Minecraft to swap test decks, and make duels reproducible by exposing a seed parameter that drives both the initial deck shuffle and the in-duel RNG.

## Motivation

Two pain points block fast iteration during manual testing:

1. **Hardcoded deck.** `Deck.standard()` returns a fixed `Integer[] MAIN` literal. Trying a different deck means editing the file, rebuilding, and relaunching — minutes of friction per iteration.
2. **Non-shuffled deck and unreproducible duels.** `OCG_StartDuel` does not shuffle the deck (verified in `native/ygopro-core/processor.cpp:5009`, `Processors::Startup`). The first cards drawn are always the top of `MAIN[]` in the order they were inserted, so the same opening hand appears every duel. The seed currently passed to `OCG_CreateDuel` only feeds the in-duel RNG — it has no effect on draw order. There is also no way to specify a seed when starting a duel, and the auto-generated seed is not logged, so a duel cannot be reproduced after the fact.

A future Duel Lobby UI is planned but is large work; this design unblocks testing without it.

## Scope

**In scope:**

- Loading decks from `.ydk` files (EDOPro's native format) at `<gameDir>/duelcraft/decks/*.ydk`
- Per-player "current deck" state, in-memory only, on the server
- Hot reload (re-read files on each duel start; no caching beyond a tab-completion scan)
- Deterministic deck shuffle driven by a single user-input `long` seed
- Optional seed argument on `/duel test` and `/duel challenge`
- Logging the seed at duel start so it can be recovered from logs

**Out of scope (deferred):**

- Persistent per-player deck state (NeoForge `AttachmentType` / playerdata NBT)
- YDKE URL parsing (waits for Duel Lobby UI; chat input length cap rules out command-line URLs)
- Setting the starting hand directly
- Replay save/load
- Side-deck UI (parser tolerates the `!side` section but ignores it)

## File format and location

**Path convention.** Decks live at `<gameDir>/duelcraft/decks/*.ydk`, mirroring the existing `<gameDir>/duelcraft/cache/` pattern from `DuelcraftClient.java:39`. On a dedicated server this resolves via `MinecraftServer#getServerDirectory()`.

**Format.** Standard EDOPro `.ydk`:

```
#created by ...        ← comments, ignored
#main                  ← section header
89631139
55144522
...
#extra
7391448
...
!side                  ← parsed, ignored
```

Parsing rules:

- Lines starting with `#` are either section headers (`#main`, `#extra`) or comments. Non-recognized `#`-lines are treated as comments.
- The line `!side` switches into a section that is parsed and discarded (so a future side-deck pass can pick it up cheaply).
- Lines that parse as a positive integer are passcodes for the current section.
- Blank lines and trailing whitespace are tolerated.
- Files with no `#main` section are treated as malformed.
- Empty `extra` is allowed.

The parser does **not** validate against the card database. Unknown passcodes propagate to ygopro-core, which will reject them at duel setup with a clearer error than we could synthesize.

## Components

### `core/DeckLoader.java` (new)

```
public final class DeckLoader {
    public static Deck loadFromFile(Path path) throws IOException, DeckParseException;
    public static Deck parseYdk(String content) throws DeckParseException;
}
```

- `parseYdk` is pure (string in, `Deck` out). Easy to unit-test.
- `loadFromFile` is the IO wrapper.
- `DeckParseException` extends `RuntimeException` with the offending line number for log clarity.

### `core/DeckRegistry.java` (new)

```
public final class DeckRegistry {
    public DeckRegistry(Path decksDir);
    public List<String> listDeckNames();   // re-scans on call
    public Deck load(String name) throws IOException, DeckParseException;
}
```

- `listDeckNames` re-globs `*.ydk` on every call. Used for tab completion.
- `load(name)` re-reads the file every call. This is the "hot reload" mechanism — no watchers, no cached `Deck` objects, no staleness window.
- Names are filenames without the `.ydk` extension.

The registry instance is owned by `DuelManager` (created in `init`).

### `Deck.java` (modified)

Add a deterministic-shuffle method:

```
public Deck shuffled(long seed) {
    var rng = new Random(seed);
    var shuffledMain = new ArrayList<>(main);
    Collections.shuffle(shuffledMain, rng);
    // extra deck is NOT shuffled — its order does not affect draws
    return new Deck(List.copyOf(shuffledMain), extra);
}
```

`Deck.standard()` is preserved as the no-setup fallback path.

### `core/SeedExpander.java` (new, small)

```
public final class SeedExpander {
    public static long[] toFourLongs(long seed);   // SplitMix64 expansion
}
```

`OCG_CreateDuel` takes `long[4]` — 256 bits of state for ygopro-core's xoshiro RNG. Expanding one user `long` via SplitMix64 gives a deterministic, well-distributed expansion. Used both when a seed is user-specified and when it is auto-generated.

### `DuelOptions.java` (modified)

Add a factory that takes an explicit seed:

```
public static DuelOptions standard(long seed) {
    return new DuelOptions(SeedExpander.toFourLongs(seed),
        OcgConstants.DUEL_MODE_MR5,
        PlayerOptions.standard(), PlayerOptions.standard());
}
```

The existing `standard()` (no-arg) keeps generating a random seed but is implemented via `standard(ThreadLocalRandom.current().nextLong())` so all paths funnel through SeedExpander.

### `DuelManager.java` (modified)

- Add `private DeckRegistry deckRegistry;` initialized in `init()` from `server.getServerDirectory().resolve("duelcraft/decks")`. Creates the directory if missing.
- Add `private final Map<UUID, String> playerCurrentDeck = new HashMap<>();` and getter/setter/clear.
- Add a helper `Deck resolveDeck(ServerPlayer player) throws DeckUnavailableException` that:
  - returns `Deck.standard()` if the player has not set a current deck (silent fallback);
  - loads from the registry if a current deck name is set;
  - throws if the set deck name no longer resolves to a valid file (caller turns this into a command-level error).
- `startDuel` and `startSoloDuel` are extended to accept the resolved decks plus the seed. They:
  1. Call `deck.shuffled(seed)` on the inputs before passing to `DuelSession.setupDuel`.
  2. Log `LOGGER.info("Duel {}: seed={}, decks=[{}, {}]", duelId, seed, name1, name2)`.

### `DuelCommand.java` (modified)

New command tree:

```
/duel
  challenge <player>                         opt. trailing <seed>
  accept                                     no extra args
  forfeit
  test                                       opt. trailing <aiDeck> <seed>
  deck
    list                                     prints available .ydk names
    set <name>                               with tab-completion suggestion provider
    get
    clear
```

Suggestion provider for `<name>` calls `DeckRegistry.listDeckNames()`. Tab-completion thus reflects file system changes immediately.

The `<seed>` argument is `LongArgumentType.longArg()`. The `<aiDeck>` argument is `StringArgumentType.string()` with the same suggestion provider as `set`.

For `/duel test`:

- `/duel test` → uses player's current deck for both sides, random seed
- `/duel test <aiDeck>` → player's current deck vs. `<aiDeck>`, random seed
- `/duel test <aiDeck> <seed>` → fully specified

Trade-off: there is no way to specify a seed without also specifying an AI deck. To work around this, the user types the same name for both args (`/duel test myDeck 42`). This redundancy is acceptable for keeping a positional-only command shape.

For `/duel challenge`:

- `/duel challenge <player>` → both players' current decks, random seed
- `/duel challenge <player> <seed>` → seed locked here, accepter cannot override

Each command path resolves player decks via `DuelManager.resolveDeck(...)`.

## Data flow

```
User types: /duel test combo_deck 42
    ↓
DuelCommand.test(ctx)
    ↓
DuelManager.startSoloDuel(player,
    DuelOptions.standard(42),                  // 42 → SplitMix64 → long[4]
    deckRegistry.load("<player's current>"),   // hot read from disk
    deckRegistry.load("combo_deck"),           // hot read from disk
    42                                          // pass seed for shuffle
)
    ↓
LOGGER.info("Duel <uuid>: seed=42, decks=[<player>, combo_deck]")
    ↓
session.setupDuel(
    playerDeck.shuffled(42),
    aiDeck.shuffled(42)
)
    ↓
OCG_DuelNewCard for each card in shuffled order
    ↓
OCG_StartDuel (no built-in shuffle, but our deck is already shuffled)
```

## Error handling

- **Decks directory missing:** auto-created on `DuelManager.init()`.
- **Deck file missing or unreadable** (e.g., user deleted a file after `/duel deck set`): command fails with `Component.literal("Deck '<name>' not found")`. No duel started, no fallback.
- **Deck file malformed:** command fails with the parser's error message including the offending line number. No duel started.
- **No current deck set** when calling `/duel test`/`challenge`/`accept`: silently falls back to `Deck.standard()`. The duel-start log line names the deck used.
- **Unknown passcodes in the deck:** propagated to ygopro-core, which will fail duel setup. The user sees the engine's error in the server log.
- **`/duel deck set <name>`:** validates that the file exists and parses successfully at set-time. Invalid input rejects the command with the parser error; the player's current deck is not changed.

## Testing strategy

**Unit tests** (no native or Minecraft dependencies):

- `DeckLoaderTest`: parses fixture strings (well-formed, comments-only, malformed, empty extra, side-deck-with-comments-after, leading/trailing whitespace, blank lines, non-integer line in main section).
- `DeckShuffleTest`: same seed produces same output; different seeds produce different outputs; extra deck is unchanged; original `Deck` is not mutated.
- `SeedExpanderTest`: expansion is deterministic; same input → same `long[4]`; different inputs produce different outputs.

**Integration tests:** none. The deck-load → shuffle → JNI path requires the EDOPro test data files; existing JNI tests already cover the post-setup path. Touching them adds no signal here.

**Manual verification in `runClient`:**

- Drop two `.ydk` files in `<gameDir>/duelcraft/decks/`.
- `/duel deck list` shows both.
- `/duel deck set` tab-completes both.
- `/duel test` with no current deck → starts duel with `Deck.standard()`, log line confirms.
- `/duel test <name>` with same seed twice → identical opening hand.
- `/duel test <name> <seed>` → log shows the seed, opening hand reproducible across mod relaunches.
- Edit a `.ydk` file, then `/duel test <name>` again → new contents take effect (no relaunch).

## Notes

- **Concurrent file edits:** if a user edits a `.ydk` mid-duel, the change is picked up on the *next* duel start. The currently running duel keeps its already-shuffled `Deck` instance. This is the correct behavior — no race, no special handling needed.
- **Suggestion provider:** `DeckRegistry` lives on the server. Tab completion uses `SuggestionProvider` and is sent to the client over Brigadier's existing wire protocol; no client-side registration is needed.

## Future work that this enables

- Once the Duel Lobby UI exists, deck registration becomes "import from YDKE URL → write a new `.ydk` file" — the loader continues to read from disk regardless.
- Persistent per-player deck state can be layered on by replacing the `Map<UUID, String>` with a NeoForge `AttachmentType`; no other component needs to change.
- Replay save/load can record the seed alongside response bytes; the seed plumbing is already in place.
