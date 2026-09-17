# Building and matching a core binary

Use this page when compiling the engine, loading it from another language, or upgrading a working integration. This documents the [local snapshots](source-versions.md), not the latest upstream toolchain. [Index](README.md) · [C API](core-api.md).

## Build inputs

| Input | Source-backed requirement |
|---|---|
| Engine sources | Use one pinned core checkout with its matching public headers. |
| Compiler | The core's [Premake project](../../native/ygopro-core/premake5.lua#L5) requests C++17 and disables RTTI. |
| Lua | The checked-out submodule is Lua 5.4, commit `6e22fedb74cf0c9b6656e9fce8b7331db847c605`. Use the core's build settings, including C++ compilation and custom configuration. |
| Build generator | Upstream's documented primary path uses Premake 5. [scripts/generate.bat](../../native/ygopro-core/scripts/generate.bat) prepares Visual Studio output. |
| Card database and scripts | Runtime inputs supplied by the host; not part of the standalone core build. |

The core's [.gitmodules](../../native/ygopro-core/.gitmodules) points `lua/src` at the Lua repository. A checkout with empty submodules cannot reproduce the documented build. The snapshot manifest records the actual checked-out revisions.

## Upstream build paths

The following commands reproduce the commands documented by the repository; this wiki task did not execute these builds. Run from the core checkout with the required tools installed.

| Platform / output | Procedure | Artifact contract |
|---|---|---|
| Visual Studio | Run `scripts/generate.bat`, select the installed VS version, open `build/ocgcore.sln`, choose architecture/configuration and build the desired project. | Project `ocgcore` is static; `ocgcoreshared` is shared and has target name `ocgcore`. |
| POSIX or MinGW | Install Premake as described by `scripts/install-premake5.sh`; run `./premake5 gmake2`, then `make -Cbuild ocgcoreshared config=release` (or target `ocgcore`). | Consult generated configurations for architecture-specific MinGW names. |
| Android | Configure NDK and invoke `ndk-build`; inspect `jni/Android.mk` and `jni/Application.mk`. | Android is a separate upstream build path, not the Duelcraft Windows JNI packaging task. |

Sources: [upstream build instructions](../../native/ygopro-core/README.md#L9), [static/shared projects](../../native/ygopro-core/premake5.lua#L124), [platform output directories](../../native/ygopro-core/premake5.lua#L72), [Android build](../../native/ygopro-core/jni/Android.mk).

The repository also contains [meson.build](../../native/ygopro-core/meson.build). It declares project version `11.0` and builds the vendored Lua sources as C++ with the custom configuration. Duelcraft's 2026-09-17 upgrade was built with the existing VS2022/Premake pipeline; Meson was not exercised.

## Lua is a compatibility constraint

[interpreter.cpp](../../native/ygopro-core/interpreter.cpp#L31) raises a Lua error inside a C++ scope and checks whether a destructor ran. `OCG_CreateDuel` returns status 5 if this test fails. The source explains the reason: setjmp/longjmp error handling can skip cleanup of C++ objects used by the engine. The core's [Lua Premake file](../../native/ygopro-core/lua/premake5.lua#L43) compiles `.c` sources as C++ and [force-includes its custom configuration](../../native/ygopro-core/lua/premake5.lua#L23).

Do not substitute a Lua DLL solely because its version says 5.4. Reproduce the build configuration and require successful duel creation. Some MSVC runtime configurations may satisfy the probe through structured exception unwinding; the probe's actual outcome determines acceptance.

The normal interpreter opens base, string, table and math libraries. Enabling `enableUnsafeLibraries` additionally opens I/O and preserves `dofile`/`loadfile`; normal mode removes them. This option does not establish a resource quota or an isolation boundary. Source: [interpreter initialization](../../native/ygopro-core/interpreter.cpp#L72).

## Static linking, dynamic loading, and FFI

- A native host may link the static target and call the C API directly.
- A dynamic host must resolve the exact `OCG_*` exports, retain the loaded library while any duel/callback can execute, and use compatible headers. The shared project defines `OCGCORE_EXPORT_FUNCTIONS`; [ocgapi.h](../../native/ygopro-core/ocgapi.h#L17) controls symbol visibility.
- A foreign-language host must marshal native structs and callbacks separately from binary messages. Java/JNI may hide these structs behind a small native adapter, as [Duelcraft](duelcraft-jni.md) does.

For a dynamic-loader reference, inspect [EDOPro dllinterface.cpp](../../../edopro/gframe/dllinterface.cpp) and [ocgcore_functions.inl](../../../edopro/gframe/ocgcore_functions.inl), with the declaration mismatch noted in [the EDOPro page](edopro-host.md#dynamic-loading-and-snapshot-compatibility). For Duelcraft's library-loading/build details, use [the JNI page](duelcraft-jni.md) and [native CMake project](../../native/jni-bridge/CMakeLists.txt).

## Upgrade checklist

These are host verification steps, not claims that any untested pair of binaries is compatible.

1. Record old/new core commits, public-header hashes, Lua revision/build settings, script pack revision, database hashes, host adapter version and target architecture.
2. Compare `ocgapi.h`, `ocgapi_types.h`, `ocgapi_constants.h`; verify exported signatures, enum additions, struct fields/alignment, and widths. Check `OCG_GetVersion` against the adapter.
3. Diff message producers (`duel.cpp`, `playerop.cpp`, `operations.cpp`, `processor.cpp`, `field.cpp`, `libduel.cpp`) and query producers (`card.cpp`, `ocgapi.cpp`) against the parser/encoder. Use [message index](message-index.md) to limit the search.
4. Check Lua loader behavior and script library changes. Replay a known fixture with identical data/scripts/options/deck order/responses and compare output.
5. Run host integration checks for creation failures, message framing, prompt families, query decoding, hidden information, and lifecycle cleanup. See [recipes](integration-recipes.md).
6. Update the wiki claims and [source manifest](sources.json) after verification. Matching API version numbers alone do not replace these checks.

This checkout demonstrates the last point: both EDOPro's core and Duelcraft's core report 11.0, while the newer core adds creation statuses 5 and 6. See [version evidence](source-versions.md).

## Distribution source pointers

The core files carry `SPDX-License-Identifier: AGPL-3.0-or-later`; consult its [LICENSE](../../native/ygopro-core/LICENSE) and [COPYING](../../native/ygopro-core/COPYING). EDOPro has its own [COPYING](../../../edopro/COPYING) and [README](../../../edopro/README.md). These are repository-source pointers, not a determination of obligations for a particular product or distribution.
