# Card data and script providers

Use this page to implement the two required creation callbacks: `OCG_DataReader` and `OCG_ScriptReader`. The core accepts decoded card records and Lua source; it does not open SQLite databases or search a script tree. Scope: Duelcraft core `7471af3c`; EDOPro examples come from host `48ec006c` and its vendored core `158aebe7`. See [source versions](source-versions.md), [core API](core-api.md), and [EDOPro host](edopro-host.md).

## Task lookup

| Task | Start here | Answer |
|---|---|---|
| Define callback ABI | [`OCG_CardData` and callback typedefs](../../native/ygopro-core/ocgapi_types.h#L38) | Fixed-width fields, zero-terminated `uint16_t* setcodes`, synchronous callbacks. |
| Map a `.cdb` row | [`DataManager::ParseDB`](../../../edopro/gframe/data_manager.cpp#L96) | Exact select order and packed-field decoding. |
| Supply a card to the core | [`DataManager::CardReader`](../../../edopro/gframe/data_manager.cpp#L553) | Look up by code and fill the core-facing prefix. |
| Manage setcode memory | [`duel::read_card`](../../native/ygopro-core/duel.cpp#L148), [`card_data::card_data`](../../native/ygopro-core/duel.cpp#L174) | Core copies through the zero sentinel, then calls `cardReaderDone`. |
| Load required base scripts | [`Game::SetupDuel`](../../../edopro/gframe/game.cpp#L3930) | EDOPro loads `constant.lua`, `utility.lua`, then repository `init.lua` files. |
| Resolve an on-demand script | [`Game::FindScript`](../../../edopro/gframe/game.cpp#L3876) | Search configured directories in order, then the supplied path. |
| Implement reader return value | [`Game::ScriptReader`](../../../edopro/gframe/game.cpp#L3949), [`interpreter::load_script`](../../native/ygopro-core/interpreter.cpp#L220) | Return nonzero only when bytes were found and `OCG_LoadScript` executed them successfully. |
| Understand card script naming | [`interpreter::load_card_script`](../../native/ygopro-core/interpreter.cpp#L235) | Requests `c<decimal-code>.lua`; nearby artwork aliases may select the alias script. |
| Diagnose script failures | [`Game::MessageHandler`](../../../edopro/gframe/game.cpp#L3952) | Capture log callback messages; loading returns false after compile/runtime error. |
| Understand caches | [`duel::read_card`](../../native/ygopro-core/duel.cpp#L148), [`Duel.LoadScript`](../../native/ygopro-core/libduel.cpp#L4025) | Card records and Lua load state live per duel; host file caches are separate. |

## Callback boundary

`OCG_CreateDuel` requires both readers. The relevant declarations are:

```c
void card_reader(void *payload, uint32_t code, OCG_CardData *out);
void card_reader_done(void *payload, OCG_CardData *out);
int script_reader(void *payload, OCG_Duel duel, const char *name);
```

The core retains each function pointer and its paired payload from `OCG_DuelOptions`; keep them valid until `OCG_DestroyDuel`. It invokes both readers synchronously on the thread executing the core API call. [`duel` stores the callbacks and payloads](../../native/ygopro-core/duel.h#L106).

`cardReader` receives a zero-initialized output struct. If the host cannot find a code and leaves it untouched, the core caches an all-zero definition under the requested code, then sets the in-duel object's code to that request. There is no callback status value. Validate deck IDs before insertion and log missing definitions in the host.

`scriptReader` does have a status: return zero for missing/failed load and nonzero for success. The callback normally finds bytes and calls `OCG_LoadScript` on the supplied duel. Returning success without calling `OCG_LoadScript` tells the interpreter a script loaded when it did not.

## `.cdb` query and field mapping

EDOPro joins `datas` and `texts` by ID with this fixed select list in [`SELECT_STMT`](../../../edopro/gframe/data_manager.cpp#L23):

```sql
datas.id, datas.ot, datas.alias, datas.setcode, datas.type,
datas.atk, datas.def, datas.level, datas.race, datas.attribute, datas.category,
texts.name, texts.desc, texts.str1 ... texts.str16
```

Only the rule-data prefix reaches `OCG_CardData`. `ot`, `category`, names, descriptions, and display strings remain host metadata.

| `.cdb` value | Core field | EDOPro conversion |
|---|---|---|
| `datas.id` | `code: uint32_t` | Read with `sqlite3_column_int64`, cast to `uint32_t`. |
| `datas.alias` | `alias: uint32_t` | Cast to `uint32_t`. |
| `datas.setcode` | `setcodes: uint16_t*` | Split one 64-bit integer into up to four little significance-order 16-bit values; discard zero slots; append a zero terminator. |
| `datas.type` | `type: uint32_t` | Cast the stored bit field. |
| `datas.level` low byte | `level: uint32_t` | Use low 8 bits; EDOPro preserves a negative stored level by negating the masked low byte. |
| `datas.level` bits 16..23 | `rscale: uint32_t` | `(packed >> 16) & 0xff`. |
| `datas.level` bits 24..31 | `lscale: uint32_t` | `(packed >> 24) & 0xff`. |
| `datas.race` | `race: uint64_t` | Read as SQLite int64 and retain all 64 bits. |
| `datas.attribute` | `attribute: uint32_t` | Cast the stored bit field. |
| `datas.atk` | `attack: int32_t` | Read as signed int. |
| `datas.def` | `defense` or `link_marker` | For a link card, move DEF into `link_marker` and set `defense=0`; otherwise keep DEF and set `link_marker=0`. |

The implementation is [`DataManager::ParseDB`](../../../edopro/gframe/data_manager.cpp#L108). The core-facing and extended host structs appear together in [`data_manager.h`](../../../edopro/gframe/data_manager.h#L43), which makes the prefix copied by `CardReader` explicit.

### Packed-field traps

`race` is 64-bit in current API 11 data and query records. Truncating it to 32 bits loses newer race flags. EDOPro's current query parser reads `uint64_t`, while its compatibility path can read a 32-bit legacy race at [`CoreUtils::Query::Parse`](../../../edopro/gframe/core_utils.cpp#L11). Database ingestion itself uses 64-bit storage.

`setcodes` is not the address of the packed SQLite integer. It points to separate 16-bit values ending in zero. Given packed value `0x00001234056700ab`, EDOPro supplies `{0x00ab, 0x0567, 0x1234, 0}`. The order follows shifts by 0, 16, 32, and 48 in [`ParseDB`](../../../edopro/gframe/data_manager.cpp#L125); the core stores them in a set.

The `level` database column carries three values. Do not pass the packed 32-bit column directly as `OCG_CardData.level`. Extract level and both scales exactly as above.

Link cards reuse the database DEF column for link arrows. Determine `TYPE_LINK` before mapping DEF. The core expects ordinary `defense` and `link_marker` as separate fields.

## Setcode and card-data lifetime

On a cache miss, [`duel::read_card`](../../native/ygopro-core/duel.cpp#L148) performs this sequence:

1. Zero-initialize a temporary `OCG_CardData`.
2. Invoke `cardReader(payload, code, &temporary)`.
3. Construct the core's cached `card_data` from the temporary.
4. Invoke `cardReaderDone(payload, &temporary)`.
5. Return the cached record for this and later reads in the duel.

[`card_data::card_data`](../../native/ygopro-core/duel.cpp#L174) copies every scalar and walks `setcodes` until a zero value, copying each code into an internal `std::set<uint16_t>`. Therefore:

- setcode memory must remain readable until `cardReader` returns and the constructor finishes;
- a `cardReaderDone` callback may release per-read memory because the core calls it after the copy;
- a persistent database-owned vector also works; EDOPro uses this approach and leaves `cardReaderDone` null;
- every non-null setcode array must contain a zero terminator, including the maximum four values decoded from `.cdb`;
- the core calls the reader once per distinct code per duel, then uses its own cache.

The current core replaces a null `cardReaderDone` with a no-op in [`OCG_CreateDuel`](../../native/ygopro-core/ocgapi.cpp#L23). The data reader itself remains mandatory.

Do not mutate shared card definitions during a duel and expect the core cache to change. Create a new duel to pick up replacements, or use a deliberately versioned data provider and document that policy.

## Texts and layered databases

The engine needs rules data, not display text. EDOPro stores the `texts` row beside the rule record for names, main description, and 16 auxiliary strings at [`DataManager::ParseDB`](../../../edopro/gframe/data_manager.cpp#L156). Its client resolves encoded description IDs through [`DataManager::GetDesc`](../../../edopro/gframe/data_manager.cpp#L377).

EDOPro loads `./cards.cdb`, expansion databases, then databases found in archives in [`DataHandler::LoadDatabases`](../../../edopro/gframe/data_handler.cpp#L28). `cards[code]` means a later parsed row replaces the stored entry for that code. Repository data can also add databases and `strings.conf` content in [`Game::ParseGithubRepositories`](../../../edopro/gframe/game.cpp#L2678). If your host supports overlays, define and test the precedence rather than depending on filesystem enumeration order.

`strings.conf` is host UI data. [`DataManager::LoadStrings`](../../../edopro/gframe/data_manager.cpp#L227) recognizes `!system`, `!victory`, `!counter`, and `!setname` records. ID mappings in `mappings.json` are another EDOPro host feature at [`LoadIdsMapping`](../../../edopro/gframe/data_manager.cpp#L303); the core does not read either format.

## Script search order

EDOPro builds `script_dirs` in [`Game::PopulateResourcesDirectories`](../../../edopro/gframe/game.cpp#L3992):

1. `./expansions/script/`;
2. its discovered subdirectories;
3. the logical `archives` source;
4. `./script/`;
5. its discovered subdirectories.

Loaded repositories insert their script path and discovered subdirectories at the front in [`Game::ParseGithubRepositories`](../../../edopro/gframe/game.cpp#L2778). `Game::FindScript` checks `script_dirs` in vector order, searches archives under `script/`, then accepts the requested path itself if it exists. First match wins.

That behavior is a host policy, not a core contract. A server can use an immutable manifest, database blob store, classpath, archive, or network-fetched cache as long as its synchronous callback supplies the right bytes. Normalize script names and define duplicate precedence. Do not let untrusted duel input choose arbitrary host paths.

The current Duelcraft core's Lua-facing `Duel.LoadScript` rejects `/` and `\` in a requested name at [`libduel.cpp`](../../native/ygopro-core/libduel.cpp#L4025). Card-script requests generated by the interpreter also use bare names.

## Base scripts and initialization order

The core creates a Lua state with its native `Card`, `Effect`, `Group`, `Duel`, and `Debug` libraries, but it does not load a gameplay script pack. EDOPro's [`Game::SetupDuel`](../../../edopro/gframe/game.cpp#L3930) follows this order:

1. Create the duel with callbacks.
2. Load `constant.lua` explicitly.
3. Load `utility.lua` explicitly.
4. Load each discovered repository/root `init.lua` explicitly.
5. Insert cards or load the selected puzzle script.
6. Start the duel.

Treat the base-script list and order as part of the script pack version. Fail setup if a required load returns zero. EDOPro's shown implementation does not check `OCG_CreateDuel`, `constant.lua`, or `utility.lua` results before returning the handle, so copy its intended ordering rather than its unchecked failure behavior.

`OCG_LoadScript` compiles and executes the provided buffer immediately in [`interpreter::load_script`](../../native/ygopro-core/interpreter.cpp#L220). The diagnostic `name` becomes the Lua chunk name; pass a stable logical filename for useful errors. Since the call is synchronous, the host may release or reuse the source buffer after it returns.

## On-demand card scripts

When the core registers a card, [`interpreter::register_card`](../../native/ygopro-core/interpreter.cpp#L134) selects its script code. If `alias` and `code` differ by less than 10, it loads the alias script; otherwise it loads the card's own script. This lets nearby artwork variants share logic.

The script provider must already be usable inside `OCG_CreateDuel`. During construction, [`duel::duel`](../../native/ygopro-core/duel.cpp#L15) creates a temporary card with code zero; [`duel::new_card`](../../native/ygopro-core/duel.cpp#L75) registers it with Lua, which can request `c0.lua`. This occurs before the host regains control to load `constant.lua` and `utility.lua`. Build callback context, search roots, synchronization, and logging before calling create.

[`interpreter::load_card_script`](../../native/ygopro-core/interpreter.cpp#L235) creates global table `c<code>`, gives it `Card` as metatable, sets temporary globals `self_code` and `self_table`, then requests `c<code>.lua` from the host. The callback must call `OCG_LoadScript` during that request. The interpreter clears the temporary globals afterward.

The presence of global `c<code>` is the card-script cache check. The table is created before the reader runs, so a failed first read still leaves a non-nil table; subsequent `load_card_script` calls return early in this snapshot. A zero return is not universally fatal: `c0.lua` may be absent, and a normal card with no scripted effect may need no file. Define which base, init, puzzle, and effect-card scripts your pack requires; report failures for that required set. Do not expect an automatic retry inside the same duel.

Script source bytes are not retained by the core after compilation/execution. Lua definitions, functions, and per-duel load state live until duel destruction. A host-level byte cache can outlive duels, but invalidate it when the script pack changes.

## `Duel.LoadScript` cache behavior

Card scripts and supporting scripts use different checks. The Lua binding [`Duel.LoadScript`](../../native/ygopro-core/libduel.cpp#L4025) accepts a name and an optional truthy cache argument.

With caching enabled, it stores one of `LOADING`, `LOAD_SUCCEEDED`, or `LOAD_FAILED` in a registry table owned by that interpreter. A recursive request for the same name while `LOADING` raises a Lua error. Later calls return the stored success/failure without invoking the host reader again. Without caching, every call invokes `scriptReader`.

This cache belongs to one duel's Lua state; [`interpreter::~interpreter`](../../native/ygopro-core/interpreter.cpp#L121) closes the state at destruction. It does not cache file bytes across duels and does not observe files changing on disk.

## Script logs and library exposure

Compile/runtime failures in `interpreter::load_script` go to `OCG_LogHandler` with `OCG_LOG_TYPE_ERROR` and return false. EDOPro's [`Game::MessageHandler`](../../../edopro/gframe/game.cpp#L3952) splits lines, adds them to its debug UI, and prints selected types. Capture type, logical script name where available, duel/session ID, and pack version in a server host.

The `enableUnsafeLibraries` option changes Lua library exposure. Current core always opens base, string, table, and math libraries; it opens `io` and retains `dofile`/`loadfile` only when that option is nonzero at [`interpreter::interpreter`](../../native/ygopro-core/interpreter.cpp#L60). EDOPro sets it to `1` in `Game::SetupDuel`. Choose this setting as part of your execution environment and script-pack contract; the callback mechanism itself works without filesystem functions inside Lua.

## Provider checklist

- Pin database and script versions to the tested core snapshot.
- Populate every scalar with the declared width; keep `race` at 64 bits.
- Decode packed level/scales, setcodes, and link DEF before filling `OCG_CardData`.
- Terminate every non-null setcode array with `uint16_t(0)` and keep it alive through the copy/done callback.
- Reject missing card IDs during host deck validation.
- Load base scripts in a fixed order and check every result.
- Resolve `c<decimal-code>.lua` and supporting bare script names deterministically.
- Return nonzero from `scriptReader` only after a successful nested `OCG_LoadScript`.
- Do not retain core-owned buffer pointers; copy bytes into host-owned storage first.
- Record data/core/script versions with reproducibility artifacts such as legacy replays.

Raw license texts for the inspected snapshots are at [EDOPro `LICENSE`](../../../edopro/LICENSE) and [ygopro-core `LICENSE`](../../native/ygopro-core/LICENSE).
