# Source versions and maintenance

The core snapshot was reviewed and upgraded on **2026-09-17**; the EDOPro and host examples retain their **2026-09-08** baseline. It records implementation facts, EDOPro examples and host recommendations separately. It does not claim that an upstream branch or downloaded binary still matches these snapshots. [Index](README.md).

## Repository identity

Paths below are relative to the Duelcraft root.

| Repository | Local path | Inspected HEAD | Role |
|---|---|---|---|
| Duelcraft | `.` | `b910dd5f5aa3c7faa1ae6e1d3d499ed1b1936463` | Current host/JNI example; existing uncommitted documentation and development files were present. |
| ygopro-core | `native/ygopro-core` | `122e0d091a0f399221a4510cc98a406ae485905f` | Primary engine contract for this wiki; API 11.0. |
| Core's Lua | `native/ygopro-core/lua/src` | `6e22fedb74cf0c9b6656e9fce8b7331db847c605` | Checked-out Lua dependency; matches core gitlink. |
| EDOPro | `../edopro` | `48ec006c49d7899b39f39420b9a71dea5ecdd22a` | Host/client reference implementation. |
| EDOPro's core | `../edopro/ocgcore` | `158aebe758be3c46249c75d602e3f16d63d2ef31` | EDOPro's own pinned core; also API 11.0, a different revision. |

Core and EDOPro worktrees had no main-repository changes in `git status --short --ignore-submodules` during inspection. The table records nested core/Lua HEADs separately; it is not a recursive clean-status assertion for all vendored dependencies. [sources.json](sources.json) records content hashes for cited files so local changes can be detected even when HEAD stays unchanged.

Origins observed in Git: [edo9300/ygopro-core at the inspected commit](https://github.com/edo9300/ygopro-core/tree/122e0d091a0f399221a4510cc98a406ae485905f) and [edo9300/edopro at the inspected commit](https://github.com/edo9300/edopro/tree/48ec006c49d7899b39f39420b9a71dea5ecdd22a). These links identify snapshots; local files supplied the evidence for this wiki.

## Confirmed differences and stale documentation

| Item | Observed fact | Consequence |
|---|---|---|
| API number | Both type headers define 11.0. | Version matching does not prove exact behavior or data-pack compatibility. |
| Creation status enum | Duelcraft core adds `INCOMPATIBLE_LUA_API=5` and `NULL_RNG_SEED=6`; EDOPro's copied header ends at `NULL_SCRIPT_READER=4`. | A host must handle a creation failure it did not previously name. |
| Public constant header | Duelcraft core `ocgapi_constants.h` and EDOPro `gframe/ocgapi_constants.h` had identical bytes at inspection. | Their constants match here; still review producers and consumers on an upgrade. |
| Upstream core README | Several signatures show struct arguments by value; current `ocgapi.h` takes pointers. README says all option pointers are mandatory; implementation defaults optional log/data-done callbacks. | Use header + implementation for bindings. |
| Meson metadata | `meson.build` now declares version 11.0 and builds the vendored Lua as C++. | This upgrade used the existing Windows VS2022/Premake build, not Meson. |

Evidence: [core types](../../native/ygopro-core/ocgapi_types.h), [EDOPro copied types](../../../edopro/gframe/ocgapi_types.h), [core header](../../native/ygopro-core/ocgapi.h), [core implementation](../../native/ygopro-core/ocgapi.cpp#L23), [README signatures](../../native/ygopro-core/README.md#L65), [Meson file](../../native/ygopro-core/meson.build).

## Evidence rules for agents

The 2026-09-17 upgrade from `7471af3c` to `122e0d09` leaves `ocgapi.h`, `ocgapi_types.h`, `ocgapi_constants.h`, framing, prompt producers, and the Lua gitlink unchanged. The reviewed runtime changes include Deck ritual tributes, unconditional `QUERY_IS_PUBLIC`, set-card shuffle ordering, wider Lua fusion flags, additional chain-info values, and upstream material/summon checks. See [script compatibility](data-and-scripts.md#deck-ritual-tribute-compatibility), [queries](queries.md), and [shuffle ordering](protocol.md#set-card-shuffle-ordering).

Only core source fingerprints were refreshed for this upgrade. The wiki checker already reported stale Duelcraft host fingerprints, an invalid host line anchor, and missing host manifest entries before this change; those remain separate documentation maintenance work.

1. Start at [the task router](README.md). Read the page for the task, then the cited producer/consumer if changing behavior. You do not need to rediscover the entire repository each turn.
2. Treat a statement labeled **host recommendation** or **recipe** as design guidance, not an upstream guarantee. Treat the Duelcraft page as one implementation, not the generic API specification.
3. A source link uses a relative path plus a line anchor and names the relevant symbol nearby. Local Markdown viewers may ignore `#L` anchors; search the symbol if they do. When viewing on GitHub, sibling-repository links require the local layout or the pinned repository link above.
4. Prefer the engine's producer and response validator for wire contracts; compare EDOPro's consumer as corroboration. Handle differences explicitly. Do not copy a legacy-client branch because its fields look shorter.
5. Do not infer a guarantee from an absent crash check. Some C exports assume valid arguments; a robust adapter must validate inputs at its own boundary.
6. When hashes differ, identify the changed functions and revalidate their dependent pages before using this wiki as a contract. A passing link/hash check establishes provenance and document structure, not semantic correctness.

## Updating the wiki

Run `python docs/engine-wiki/verify_wiki.py` from Duelcraft with Python 3.9 or newer to check links, line-anchor bounds, indexed message coverage, and recorded source fingerprints. It reads the files and does not build or run a duel. Fingerprints normalize CRLF/CR to LF so a checkout's line-ending setting does not create false changes; all other bytes remain significant.

For an engine update, follow [the compatibility checklist](build-and-compatibility.md). For each changed fact, edit the owning page and its source references; adjust the task router if scope changes. Update the inspected revision table and the corresponding fields in `sources.json`, then refresh the manifest's cited-file hashes only after reviewing the changed sources. The manifest is a snapshot, not an automatic declaration that new sources are compatible.

## Coverage boundary

The wiki covers standalone engine integration, API and ownership, build/runtime dependencies, card/script loading, rule configuration, messages and prompt replies, queries, EDOPro host behavior, JNI adaptation, testing and debugging. [message-index.md](message-index.md) inventories the public `MSG_*` definitions; specialized message layouts remain discoverable through exact producer/consumer links when they are not expanded into prose.

This is not a full EDOPro UI guide, a catalog of all Lua methods/card rulings, a card/script data distribution, or a claim that Duelcraft implements every upstream feature. Neither a native build nor runtime duel tests were required to write these documentation-only changes; the verification record distinguishes source checks from suggested integration tests.
