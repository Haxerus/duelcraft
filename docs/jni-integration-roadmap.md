# Duelcraft JNI Integration Roadmap

A development roadmap for bridging the ygopro-core C++ duel simulator into the Duelcraft Minecraft mod via JNI. The end goal is fully functional, multiplayer-capable Yu-Gi-Oh! dueling controlled from within Minecraft.

## Architecture Overview

The integration uses a **thick C++ bridge** design. Rather than mirroring the raw ygopro-core C API directly into Java and bouncing callbacks across JNI, the C++ bridge layer (`DuelEngine`) absorbs the "plumbing" work — SQLite card database loading, Lua script file I/O, and callback wiring — entirely in native code. Java sees a streamlined, higher-level API focused on duel lifecycle, message consumption, and response submission.

```
┌──────────────────────────────────────────────────────────┐
│  Java (Minecraft Mod)                                    │
│  ┌────────────┐  ┌──────────────┐  ┌──────────────────┐ │
│  │ DuelSession │  │ MessageParser│  │ ResponseBuilder  │ │
│  │ DuelManager │  │ DuelMessage  │  │ (binary encode)  │ │
│  └──────┬──────┘  └──────────────┘  └──────────────────┘ │
│         │ JNI (thin: handle + byte[] in/out)              │
├─────────┼────────────────────────────────────────────────┤
│  C++ Bridge (libduelcraft_jni)                           │
│  ┌──────┴──────┐                                         │
│  │ DuelEngine  │──→ SQLite card DB (native C API)        │
│  │             │──→ Script filesystem I/O (C++ fstream)  │
│  │             │──→ Bootstrap script injection            │
│  │             │──→ DataReader / ScriptReader callbacks   │
│  │             │──→ LogHandler → optional Java forwarding │
│  └──────┬──────┘                                         │
│         │ C API calls                                    │
│  ┌──────┴──────┐                                         │
│  │  ocgcore    │  (ygopro-core shared library)           │
│  └─────────────┘                                         │
└──────────────────────────────────────────────────────────┘
```

**Why this design:**
- **Fewer JNI crossings.** `DataReader` and `ScriptReader` fire thousands of times per duel. Handling them in C++ avoids `AttachCurrentThread`/`DetachCurrentThread` overhead on every callback.
- **Simpler Java API.** No `DuelCallbackHandler` interface, no `CardDatabase.java`, no `ScriptProvider.java`. Java just passes paths at engine init.
- **Natural fit.** SQLite is a C library; reading it through sqlite-jdbc just to feed data back to C++ via JNI is a pointless round trip.
- **Easier threading.** Only the `LogHandler` optionally crosses into Java. The hot-path callbacks stay entirely native.

---

## Table of Contents

1. [Phase 1: Native Library Build Pipeline](#phase-1-native-library-build-pipeline)
2. [Phase 2: C++ DuelEngine Bridge](#phase-2-c-duelengine-bridge)
3. [Phase 3: Java Native Interface](#phase-3-java-native-interface)
4. [Phase 4: Duel Session Manager](#phase-4-duel-session-manager)
5. [Phase 5: Message Protocol Parser](#phase-5-message-protocol-parser)
6. [Phase 6: Player Response System](#phase-6-player-response-system)
7. [Phase 7: Minecraft Integration](#phase-7-minecraft-integration)
8. [Phase 8: Multiplayer & Networking](#phase-8-multiplayer--networking)
9. [Phase 9: Frontend UI](#phase-9-frontend-ui)
10. [Appendix A: OCG API Reference Summary](#appendix-a-ocg-api-reference-summary)
11. [Appendix B: Message Types Reference](#appendix-b-message-types-reference)
12. [Appendix C: Thread Safety Model](#appendix-c-thread-safety-model)

---

## Phase 1: Native Library Build Pipeline

**Goal:** Compile ygopro-core as a shared library (`.dll` / `.so` / `.dylib`) and integrate it into the Gradle build so the mod JAR bundles the correct native binary.

### 1.1 Build ocgcore as a Shared Library

The ygopro-core submodule already supports building a shared library via Premake5 (`ocgcoreshared` target). The build must produce platform-specific binaries.

**Windows (primary target):**
```
cd native/ygopro-core/scripts
generate.bat          # generates Visual Studio solution in build/
```
Then build the `ocgcoreshared` project in Release/x64 configuration. Output: `ocgcore.dll`.

**Linux (for CI):**
```
cd native/ygopro-core
./premake5 gmake2
make -Cbuild ocgcoreshared config=release
```
Output: `libocgcore.so`.

**Alternative: CMake/Meson wrapper.** The submodule also includes `meson.build`. Either build system works — pick whichever integrates best with CI.

### 1.2 Integrate Native Build with Gradle

Create a Gradle task (or use the `cpp-library` plugin) to:

1. Invoke the native build system as a build step.
2. Copy the resulting shared library into `src/main/resources/natives/<platform>/` (e.g., `natives/windows-x86_64/ocgcore.dll`).
3. Ensure the library is bundled inside the mod JAR.

Alternatively, use a pre-build script and commit platform binaries to a `libs/` directory for simplicity during early development, then automate later.

### 1.3 Native Library Loader (Java)

Create `com.haxerus.duelcraft.native.NativeLoader`:

- On mod initialization, extract the platform-appropriate shared libraries from JAR resources to a temp directory.
- Call `System.load(extractedPath)` to load both `ocgcore` and `duelcraft_jni` (the bridge library from Phase 2).
- Detect platform via `System.getProperty("os.name")` and `os.arch`.
- `ocgcore` must be loaded before `duelcraft_jni` since the bridge links against it.

### 1.4 CI Pipeline Update

Update `.github/workflows/build.yml` to:

- Install C++ build tools (GCC/Premake5 on Ubuntu).
- Build the native shared library before `./gradlew build`.
- Optionally, set up a matrix build for Windows/Linux.

### Deliverables
- [ ] `ocgcore.dll` / `libocgcore.so` built from submodule
- [ ] Gradle task or script that builds native library
- [ ] `NativeLoader.java` that extracts and loads libraries at runtime
- [ ] CI builds native + Java in one pipeline

---

## Phase 2: C++ DuelEngine Bridge

**Goal:** Build a C++ bridge layer that wraps ygopro-core's C API behind a higher-level `DuelEngine` abstraction, handling card data, script loading, and callbacks entirely in native code. Only the `LogHandler` optionally forwards to Java.

### 2.1 Project Structure

```
native/
  ygopro-core/              # existing submodule (don't modify)
  jni-bridge/
    include/
      duel_engine.h          # DuelEngine C++ class
      card_database.h         # SQLite card data provider
      script_provider.h       # Filesystem script resolver
    src/
      duel_engine.cpp
      card_database.cpp
      script_provider.cpp
      jni_interface.cpp       # JNI function implementations
      jni_interface.h         # generated by javac -h (Phase 3)
    deps/
      sqlite3.c              # SQLite amalgamation (single-file C build)
      sqlite3.h
    CMakeLists.txt            # builds libduelcraft_jni, links ocgcore + sqlite3
```

Build this as a single shared library (`duelcraft_jni.dll` / `libduelcraft_jni.so`) that statically links SQLite and dynamically links `ocgcore`.

### 2.2 CardDatabase (C++)

Handles the `OCG_DataReader` callback entirely in native code.

```cpp
class CardDatabase {
public:
    // Open one or more .cdb files and load all card data into memory
    bool open(const std::vector<std::string>& dbPaths);
    void close();

    // OCG_DataReader callback target — called by ygopro-core
    static void cardReader(void* payload, uint32_t code, OCG_CardData* data);

private:
    // code -> CardData, populated from the SQLite "datas" table
    std::unordered_map<uint32_t, CardDataEntry> cards_;
};
```

**Database schema:** The standard `cards.cdb` has a `datas` table:

| Column | Maps to | Notes |
|---|---|---|
| id | code | Card passcode |
| alias | alias | Alternate printing code |
| setcode | setcodes | 64-bit packed: up to 4 `uint16_t` archetype codes |
| type | type | Card type bitmask |
| level | level | bits 0-7: level/rank, 16-23: lscale, 24-31: rscale |
| attribute | attribute | Attribute bitmask |
| race | race | Race/type bitmask |
| atk | attack | ATK value (-2 = ?) |
| def | defense | DEF value (-2 = ?) |

The `open()` method queries all rows at startup and builds the in-memory map. At ~15,000 cards, this is a few MB — trivial. No per-callback SQL queries needed.

**Setcode unpacking:** The 64-bit `setcode` column packs up to 4 archetype codes as 16-bit values. Unpack into a zero-terminated `uint16_t` array for `OCG_CardData::setcodes`.

**Multiple databases:** Support loading multiple `.cdb` files (base + expansions) by calling `open()` with a list of paths, merging into the same map.

### 2.3 ScriptProvider (C++)

Handles the `OCG_ScriptReader` callback entirely in native code.

```cpp
class ScriptProvider {
public:
    // Configure search paths (checked in order, first match wins)
    void setSearchPaths(const std::vector<std::string>& paths);

    // OCG_ScriptReader callback target
    // Reads the script file, calls OCG_LoadScript, returns success
    static int scriptReader(void* payload, OCG_Duel duel, const char* name);

    // Explicitly load a script (used for bootstrap scripts)
    bool loadScript(OCG_Duel duel, const std::string& name);

private:
    std::vector<std::string> searchPaths_;

    // Read a file from the first matching search path
    std::optional<std::vector<char>> readFile(const std::string& name);
};
```

**Search path order** (matching edopro conventions):
1. `<expansion_scripts>/` — user overrides
2. `<base_scripts>/` — main script repository

**Required scripts loaded after duel creation:**
1. `constant.lua` — core Lua constants
2. `utility.lua` — core Lua helper functions

These are loaded explicitly by `DuelEngine::createDuel()` after calling `OCG_CreateDuel` and before any cards are added, matching edopro's `Game::SetupDuel` pattern.

### 2.4 DuelEngine (C++)

The central C++ class that owns the card database, script provider, and manages duel handles.

```cpp
class DuelEngine {
public:
    // Initialize with paths to resources
    bool init(const std::vector<std::string>& dbPaths,
              const std::vector<std::string>& scriptPaths);
    void shutdown();

    // Create a new duel, returns opaque handle (OCG_Duel cast to intptr_t)
    // Automatically wires DataReader, ScriptReader, and LogHandler
    // Automatically loads bootstrap scripts (constant.lua, utility.lua)
    intptr_t createDuel(const uint64_t seed[4], uint64_t flags,
                        int team1LP, int team1StartHand, int team1DrawPerTurn,
                        int team2LP, int team2StartHand, int team2DrawPerTurn);
    void destroyDuel(intptr_t duel);

    // Card setup
    void addCard(intptr_t duel, uint8_t team, uint8_t duelist, uint32_t code,
                 uint8_t controller, uint32_t location, uint32_t sequence, uint32_t position);
    void startDuel(intptr_t duel);

    // Processing — these return data as byte arrays to be copied into Java byte[]
    int process(intptr_t duel);
    std::pair<const uint8_t*, uint32_t> getMessages(intptr_t duel);
    void setResponse(intptr_t duel, const uint8_t* data, uint32_t len);

    // Querying
    int queryCount(intptr_t duel, uint8_t team, uint32_t location);
    std::pair<const uint8_t*, uint32_t> query(intptr_t duel, uint32_t flags,
                                               uint8_t controller, uint32_t location,
                                               uint32_t sequence, uint32_t overlaySeq);
    std::pair<const uint8_t*, uint32_t> queryLocation(intptr_t duel, uint32_t flags,
                                                       uint8_t controller, uint32_t location);
    std::pair<const uint8_t*, uint32_t> queryField(intptr_t duel);

    // Info
    std::pair<int, int> getVersion();

    // Optional: set a Java log handler (see 2.5)
    void setLogHandler(JavaVM* jvm, jobject handler);

private:
    CardDatabase cardDb_;
    ScriptProvider scriptProvider_;

    // Per-duel context for the LogHandler callback
    struct DuelContext {
        DuelEngine* engine;
        OCG_Duel duel;
    };
    std::unordered_map<intptr_t, std::unique_ptr<DuelContext>> contexts_;

    // Optional Java log forwarding
    JavaVM* jvm_ = nullptr;
    jobject logHandler_ = nullptr;  // GlobalRef
    jmethodID logMethodId_ = nullptr;

    static void logHandler(void* payload, const char* message, int type);
};
```

### 2.5 Log Forwarding (Only JNI Callback)

The `LogHandler` is the only callback that optionally crosses into Java, and it's low-frequency (errors/debug only, not per-card).

```cpp
void DuelEngine::logHandler(void* payload, const char* message, int type) {
    auto* ctx = static_cast<DuelContext*>(payload);
    auto* engine = ctx->engine;
    if (engine->jvm_ && engine->logHandler_) {
        JNIEnv* env;
        bool attached = false;
        if (engine->jvm_->GetEnv((void**)&env, JNI_VERSION_1_8) == JNI_EDETACHED) {
            engine->jvm_->AttachCurrentThread((void**)&env, nullptr);
            attached = true;
        }
        jstring jmsg = env->NewStringUTF(message);
        env->CallVoidMethod(engine->logHandler_, engine->logMethodId_, jmsg, (jint)type);
        env->DeleteLocalRef(jmsg);
        if (attached) {
            engine->jvm_->DetachCurrentThread();
        }
    }
}
```

If no Java log handler is set, log messages are silently discarded (or written to stderr for debugging).

### 2.6 JNI Function Implementations

The JNI layer in `jni_interface.cpp` is thin — it simply translates JNI types to/from `DuelEngine` calls:

```cpp
// Engine lifecycle
JNIEXPORT jlong JNICALL Java_com_haxerus_duelcraft_core_OcgCore_nCreateEngine(
    JNIEnv* env, jclass, jobjectArray dbPaths, jobjectArray scriptPaths) {
    auto* engine = new DuelEngine();
    auto dbs = jstringArrayToVector(env, dbPaths);
    auto scripts = jstringArrayToVector(env, scriptPaths);
    if (!engine->init(dbs, scripts)) {
        delete engine;
        return 0;
    }
    return reinterpret_cast<jlong>(engine);
}

JNIEXPORT void JNICALL Java_com_haxerus_duelcraft_core_OcgCore_nDestroyEngine(
    JNIEnv*, jclass, jlong engineHandle) {
    auto* engine = reinterpret_cast<DuelEngine*>(engineHandle);
    engine->shutdown();
    delete engine;
}

// Duel lifecycle — delegates to DuelEngine methods
JNIEXPORT jlong JNICALL Java_com_haxerus_duelcraft_core_OcgCore_nCreateDuel(
    JNIEnv* env, jclass, jlong engineHandle, jlongArray seed, jlong flags,
    jint t1LP, jint t1Hand, jint t1Draw, jint t2LP, jint t2Hand, jint t2Draw) {
    auto* engine = reinterpret_cast<DuelEngine*>(engineHandle);
    jlong* seedElems = env->GetLongArrayElements(seed, nullptr);
    uint64_t nativeSeed[4] = {
        (uint64_t)seedElems[0], (uint64_t)seedElems[1],
        (uint64_t)seedElems[2], (uint64_t)seedElems[3]
    };
    env->ReleaseLongArrayElements(seed, seedElems, 0);
    return (jlong)engine->createDuel(nativeSeed, (uint64_t)flags,
        t1LP, t1Hand, t1Draw, t2LP, t2Hand, t2Draw);
}

// getMessages — copies the native buffer into a Java byte[]
JNIEXPORT jbyteArray JNICALL Java_com_haxerus_duelcraft_core_OcgCore_nDuelGetMessage(
    JNIEnv* env, jclass, jlong engineHandle, jlong duelHandle) {
    auto* engine = reinterpret_cast<DuelEngine*>(engineHandle);
    auto [data, len] = engine->getMessages(duelHandle);
    if (!data || len == 0) return nullptr;
    jbyteArray result = env->NewByteArray(len);
    env->SetByteArrayRegion(result, 0, len, reinterpret_cast<const jbyte*>(data));
    return result;
}
```

### 2.7 CMake Build Configuration

```cmake
cmake_minimum_required(VERSION 3.20)
project(duelcraft_jni CXX C)

set(CMAKE_CXX_STANDARD 17)

# JNI
find_package(JNI REQUIRED)

# SQLite (amalgamation, built as part of this project)
add_library(sqlite3 STATIC deps/sqlite3.c)
target_include_directories(sqlite3 PUBLIC deps/)

# ocgcore (pre-built shared library)
add_library(ocgcore SHARED IMPORTED)
set_target_properties(ocgcore PROPERTIES
    IMPORTED_LOCATION "${CMAKE_SOURCE_DIR}/../ygopro-core/build/release/ocgcore${CMAKE_SHARED_LIBRARY_SUFFIX}"
    INTERFACE_INCLUDE_DIRECTORIES "${CMAKE_SOURCE_DIR}/../ygopro-core/"
)

# Bridge library
add_library(duelcraft_jni SHARED
    src/duel_engine.cpp
    src/card_database.cpp
    src/script_provider.cpp
    src/jni_interface.cpp
)
target_include_directories(duelcraft_jni PRIVATE
    include/
    ${JNI_INCLUDE_DIRS}
)
target_link_libraries(duelcraft_jni PRIVATE ocgcore sqlite3)
```

### Deliverables
- [ ] `native/jni-bridge/` project with CMake build
- [ ] `CardDatabase` — loads `.cdb` files, serves `DataReader` callback natively
- [ ] `ScriptProvider` — serves `ScriptReader` callback from filesystem
- [ ] `DuelEngine` — wraps full OCG API with bootstrap script auto-loading
- [ ] JNI interface functions (thin translation layer)
- [ ] Optional Java log forwarding (only JNI callback)
- [ ] `JNI_OnLoad` with method ID caching for log handler
- [ ] CMake build producing `libduelcraft_jni`

---

## Phase 3: Java Native Interface

**Goal:** Define the Java-side native method declarations and constants. The API is higher-level than the raw OCG API — Java never touches card data callbacks, script loading, or SQLite.

### 3.1 Core Native Class

`com.haxerus.duelcraft.core.OcgCore`:

```java
public final class OcgCore {

    static {
        NativeLoader.load(); // loads ocgcore + duelcraft_jni
    }

    // Engine lifecycle
    public static native long nCreateEngine(String[] dbPaths, String[] scriptPaths);
    public static native void nDestroyEngine(long engine);
    public static native void nSetLogHandler(long engine, LogHandler handler);

    // Duel lifecycle
    public static native long nCreateDuel(long engine, long[] seed, long flags,
        int team1LP, int team1StartHand, int team1DrawPerTurn,
        int team2LP, int team2StartHand, int team2DrawPerTurn);
    public static native void nDestroyDuel(long engine, long duel);
    public static native void nDuelNewCard(long engine, long duel,
        int team, int duelist, int code, int controller,
        int location, int sequence, int position);
    public static native void nStartDuel(long engine, long duel);

    // Processing
    public static native int nDuelProcess(long engine, long duel);
    public static native byte[] nDuelGetMessage(long engine, long duel);
    public static native void nDuelSetResponse(long engine, long duel, byte[] response);

    // Querying
    public static native int nDuelQueryCount(long engine, long duel, int team, int location);
    public static native byte[] nDuelQuery(long engine, long duel,
        int flags, int controller, int location, int sequence, int overlaySequence);
    public static native byte[] nDuelQueryLocation(long engine, long duel,
        int flags, int controller, int location);
    public static native byte[] nDuelQueryField(long engine, long duel);

    // Info
    public static native int[] nGetVersion();
}
```

Key differences from the raw API mirror approach:
- **Two handle levels:** `engine` (owns card DB + scripts, shared across duels) and `duel` (single duel instance).
- **No callback handler parameter** on `nCreateDuel` — card data and scripts are handled internally by the engine.
- **No `nLoadScript`** exposed — bootstrap scripts are loaded automatically; card scripts are loaded by the native `ScriptReader` callback.
- Only `LogHandler` optionally crosses into Java.

### 3.2 Log Handler Interface

The only callback interface Java needs to implement:

```java
@FunctionalInterface
public interface LogHandler {
    void onLogMessage(String message, int type);
}
```

Typical usage: forward to `org.slf4j.Logger` or Minecraft's logging system.

### 3.3 Constants Classes

Mirror `ocgapi_constants.h` into Java. These are used by the message parser and response builder:

- `com.haxerus.duelcraft.core.Location` — `DECK`, `HAND`, `MZONE`, `SZONE`, `GRAVE`, `REMOVED`, `EXTRA`, etc.
- `com.haxerus.duelcraft.core.Position` — `FACEUP_ATTACK`, `FACEDOWN_DEFENSE`, etc.
- `com.haxerus.duelcraft.core.CardType` — `MONSTER`, `SPELL`, `TRAP`, `FUSION`, etc.
- `com.haxerus.duelcraft.core.DuelMode` — `MR5`, `SPEED`, `RUSH`, etc. (composed flag sets)
- `com.haxerus.duelcraft.core.DuelStatus` — `END` (0), `AWAITING` (1), `CONTINUE` (2)
- `com.haxerus.duelcraft.core.MsgType` — all `MSG_*` constants
- `com.haxerus.duelcraft.core.QueryFlag` — all `QUERY_*` constants
- `com.haxerus.duelcraft.core.Phase` — `DRAW`, `STANDBY`, `MAIN1`, `BATTLE`, `MAIN2`, `END`

### 3.4 Generate JNI Headers

After compiling the Java native class:

```bash
javac -h native/jni-bridge/src/ src/main/java/com/haxerus/duelcraft/core/OcgCore.java
```

This generates the C header (`com_haxerus_duelcraft_core_OcgCore.h`) that `jni_interface.cpp` implements.

### Deliverables
- [ ] `OcgCore.java` with all native method declarations
- [ ] `LogHandler.java` functional interface
- [ ] Constants classes mirroring `ocgapi_constants.h`
- [ ] Generated JNI header for C++ implementation

---

## Phase 4: Duel Session Manager

**Goal:** Create the Java-side duel lifecycle manager that orchestrates creating, running, and cleaning up duels. With the thick C++ bridge, this layer is simpler — no callback implementation needed.

### 4.1 DuelEngine (Java Wrapper)

A thin Java wrapper around the native engine handle:

```java
public class DuelEngine implements AutoCloseable {
    private final long handle;

    public DuelEngine(List<String> dbPaths, List<String> scriptPaths) {
        this.handle = OcgCore.nCreateEngine(
            dbPaths.toArray(String[]::new),
            scriptPaths.toArray(String[]::new)
        );
        if (this.handle == 0) {
            throw new IllegalStateException("Failed to initialize DuelEngine");
        }
    }

    public void setLogHandler(LogHandler handler) {
        OcgCore.nSetLogHandler(handle, handler);
    }

    public long getHandle() { return handle; }

    @Override
    public void close() {
        OcgCore.nDestroyEngine(handle);
    }
}
```

Created once during server startup, shared by all duels.

### 4.2 DuelSession

Wraps a single duel instance:

```java
public class DuelSession implements AutoCloseable {
    private final DuelEngine engine;
    private final long duelHandle;
    private final DuelEventListener listener;

    public DuelSession(DuelEngine engine, DuelOptions options, DuelEventListener listener) {
        this.engine = engine;
        this.listener = listener;
        this.duelHandle = OcgCore.nCreateDuel(
            engine.getHandle(),
            options.seed(), options.flags(),
            options.team1().lp(), options.team1().startHand(), options.team1().drawPerTurn(),
            options.team2().lp(), options.team2().startHand(), options.team2().drawPerTurn()
        );
        // Bootstrap scripts (constant.lua, utility.lua) are loaded
        // automatically by the C++ DuelEngine during createDuel
    }

    @Override
    public void close() {
        OcgCore.nDestroyDuel(engine.getHandle(), duelHandle);
    }
}
```

### 4.3 Duel Setup Sequence

Following the pattern established by edopro's `GenericDuel::TPResult`:

```java
public void setupDuel(Deck team1Deck, Deck team2Deck) {
    long eng = engine.getHandle();

    // Team 1 main deck (reverse order to match stack behavior)
    var main1 = team1Deck.main();
    for (int i = main1.size() - 1; i >= 0; i--) {
        OcgCore.nDuelNewCard(eng, duelHandle,
            0, 0, main1.get(i), 0, Location.DECK, 0, Position.FACEDOWN_DEFENSE);
    }
    // Team 1 extra deck
    for (int code : team1Deck.extra()) {
        OcgCore.nDuelNewCard(eng, duelHandle,
            0, 0, code, 0, Location.EXTRA, 0, Position.FACEDOWN_DEFENSE);
    }
    // Team 2 main deck
    var main2 = team2Deck.main();
    for (int i = main2.size() - 1; i >= 0; i--) {
        OcgCore.nDuelNewCard(eng, duelHandle,
            1, 0, main2.get(i), 1, Location.DECK, 0, Position.FACEDOWN_DEFENSE);
    }
    // Team 2 extra deck
    for (int code : team2Deck.extra()) {
        OcgCore.nDuelNewCard(eng, duelHandle,
            1, 0, code, 1, Location.EXTRA, 0, Position.FACEDOWN_DEFENSE);
    }

    OcgCore.nStartDuel(eng, duelHandle);
}
```

### 4.4 Duel Processing Loop

```java
public void process() {
    long eng = engine.getHandle();
    int status;
    do {
        status = OcgCore.nDuelProcess(eng, duelHandle);
        byte[] messageBuffer = OcgCore.nDuelGetMessage(eng, duelHandle);
        if (messageBuffer != null && messageBuffer.length > 0) {
            List<DuelMessage> messages = MessageParser.parse(messageBuffer);
            for (DuelMessage msg : messages) {
                int result = listener.onMessage(msg);
                if (result != 0) {
                    if (result == 2 || status == DuelStatus.END) {
                        listener.onDuelEnd();
                    }
                    return;
                }
            }
        }
    } while (status == DuelStatus.CONTINUE);

    if (status == DuelStatus.END) {
        listener.onDuelEnd();
    }
}

public void setResponse(byte[] response) {
    OcgCore.nDuelSetResponse(engine.getHandle(), duelHandle, response);
    process(); // resume processing
}
```

### 4.5 Supporting Records

```java
public record DuelOptions(
    long[] seed,       // 4-element seed array (xoshiro256**)
    long flags,        // duel rule flags (e.g., DuelMode.MR5)
    PlayerOptions team1,
    PlayerOptions team2
) {}

public record PlayerOptions(
    int lp,            // starting LP (typically 8000)
    int startHand,     // initial hand size (typically 5)
    int drawPerTurn    // cards drawn per turn (typically 1)
) {}

public record Deck(
    List<Integer> main,   // main deck card codes
    List<Integer> extra    // extra deck card codes
) {}
```

### 4.6 Thread Model

Each `DuelSession` must be accessed from a **single thread at a time**. Ygopro-core is not thread-safe within a single duel instance. Options:

- **Dedicated thread per duel:** Each `DuelSession` owns a `SingleThreadExecutor`. All calls (`process`, `setResponse`, `query`) are dispatched to this executor.
- **Server tick integration:** Process duels on the server tick thread and use a queue for incoming responses. Simpler but blocks the tick during processing.

### Deliverables
- [ ] `DuelEngine.java` — Java wrapper for native engine handle
- [ ] `DuelSession.java` — full duel lifecycle
- [ ] `DuelOptions.java`, `PlayerOptions.java`, `Deck.java` records
- [ ] `DuelEventListener.java` interface for message dispatch
- [ ] Setup sequence (add cards → start → process loop)
- [ ] Thread-safe executor wrapper

---

## Phase 5: Message Protocol Parser

**Goal:** Parse the binary message buffer from `OCG_DuelGetMessage` into structured Java objects.

### 5.1 Message Buffer Format

`OCG_DuelGetMessage` returns a single buffer containing **zero or more concatenated messages**. Each message is structured as:

```
[uint32  message_length]  (length of the rest, NOT including this uint32)
[uint8   message_type]    (MSG_* constant)
[...     message_body]    (type-specific binary data)
```

edopro's `CoreUtils::ParseMessages` reads a `uint32_t` length prefix, then the message type byte, then the remaining body.

### 5.2 Buffer Reader Utility

```java
public class BufferReader {
    private final byte[] data;
    private int pos;

    public int readUint8()  { ... }
    public int readUint16() { ... } // little-endian
    public int readInt32()  { ... } // little-endian
    public long readUint32() { ... }
    public long readInt64() { ... }
    public int remaining() { ... }
    public void skip(int bytes) { ... }
}
```

All ygopro-core binary data is **little-endian**.

### 5.3 Message Class Hierarchy

Create a sealed interface with records for each message type. Prioritize the messages needed for basic gameplay first:

**Phase 5a — Critical messages (minimum viable duel):**

| Message | Description | Key Fields |
|---|---|---|
| `MSG_START` | Duel started | player type, LP values, deck/extra counts |
| `MSG_NEW_TURN` | New turn begins | player |
| `MSG_NEW_PHASE` | Phase change | phase |
| `MSG_DRAW` | Cards drawn | player, count, card codes |
| `MSG_MOVE` | Card moved | code, from (loc_info), to (loc_info), reason |
| `MSG_SELECT_IDLECMD` | Main phase action menu | summonable/activatable/repositionable cards |
| `MSG_SELECT_BATTLECMD` | Battle phase action menu | attackable/activatable cards |
| `MSG_SELECT_CARD` | Select card(s) from list | min, max, card list |
| `MSG_SELECT_CHAIN` | Select chain activation | activatable cards |
| `MSG_SELECT_EFFECTYN` | Activate effect yes/no | card code, description |
| `MSG_SELECT_YESNO` | Generic yes/no | description |
| `MSG_SELECT_OPTION` | Select from options | option list |
| `MSG_SELECT_PLACE` | Select field zone | player, count, selectable zones |
| `MSG_SELECT_POSITION` | Select card position | code, available positions |
| `MSG_SELECT_TRIBUTE` | Select tributes | min, max, card list |
| `MSG_SUMMONING` | Card being summoned | code, position |
| `MSG_SPSUMMONING` | Special summon | code, position |
| `MSG_CHAINING` | Chain link activated | code, triggering card |
| `MSG_CHAIN_END` | Chain fully resolved | — |
| `MSG_DAMAGE` | LP damage | player, amount |
| `MSG_RECOVER` | LP recovery | player, amount |
| `MSG_LPUPDATE` | LP changed | player, amount |
| `MSG_ATTACK` | Attack declaration | attacker loc, target loc |
| `MSG_BATTLE` | Battle result | attacker/defender stats |
| `MSG_WIN` | Duel result | winner, reason |
| `MSG_HINT` | UI hint | type, player, data |
| `MSG_POS_CHANGE` | Position changed | code, previous, current |
| `MSG_SET` | Card set (face-down) | code, location |
| `MSG_SWAP` | Cards swapped | two locations |
| `MSG_SHUFFLE_DECK` | Deck shuffled | player |
| `MSG_SHUFFLE_HAND` | Hand shuffled | player, new hand codes |

**Phase 5b — Extended messages (full feature parity):**

All remaining `MSG_*` types from `ocgapi_constants.h` (coin toss, dice, announce race/attribute/card/number, equip, counter, tag swap, sort, etc.).

### 5.4 Message Parser Implementation

```java
public class MessageParser {
    public static List<DuelMessage> parse(byte[] buffer) {
        List<DuelMessage> messages = new ArrayList<>();
        BufferReader reader = new BufferReader(buffer);
        while (reader.remaining() > 0) {
            int length = reader.readInt32();
            int type = reader.readUint8();
            // length includes the type byte, so body is length-1 bytes
            switch (type) {
                case MsgType.START -> messages.add(parseStart(reader));
                case MsgType.NEW_TURN -> messages.add(parseNewTurn(reader));
                case MsgType.DRAW -> messages.add(parseDraw(reader));
                case MsgType.SELECT_IDLECMD -> messages.add(parseSelectIdleCmd(reader));
                // ... etc
                default -> reader.skip(length - 1); // skip unknown
            }
        }
        return messages;
    }
}
```

### 5.5 Location Info

Many messages reference card locations with the structure:

```java
public record LocInfo(
    int controller,  // uint8 — 0 or 1
    int location,    // uint8 — DECK, HAND, MZONE, etc.
    int sequence,    // uint32 — index within location
    int position     // uint32 — FACEUP_ATTACK, etc.
) {
    public static LocInfo read(BufferReader reader) {
        return new LocInfo(
            reader.readUint8(),
            reader.readUint8(),
            reader.readInt32(),
            reader.readInt32()
        );
    }
}
```

### 5.6 Query Response Parser

The `OCG_DuelQuery*` functions return binary data in a similar format. Implement `QueryParser` to decode card query responses into structured objects, using `QUERY_*` flags to know which fields are present.

### Deliverables
- [ ] `BufferReader.java` utility for little-endian binary parsing
- [ ] `DuelMessage.java` sealed interface + records for each MSG type
- [ ] `MessageParser.java` — parses raw buffer into message objects
- [ ] `LocInfo.java` record for card location references
- [ ] `QueryParser.java` for card query responses
- [ ] Unit tests with known message byte sequences

---

## Phase 6: Player Response System

**Goal:** Encode player decisions as binary responses that `OCG_DuelSetResponse` accepts.

### 6.1 Response Format

Responses are binary buffers whose format depends on the message being responded to. The response system must construct the correct byte sequence for each selection type.

### 6.2 Response Builder

```java
public class ResponseBuilder {
    private final ByteArrayOutputStream buf = new ByteArrayOutputStream();

    public ResponseBuilder writeInt8(int value) { buf.write(value & 0xFF); return this; }
    public ResponseBuilder writeInt32(int value) { /* little-endian 4 bytes */ return this; }

    public byte[] build() { return buf.toByteArray(); }
}
```

### 6.3 Response Types

| Prompt Message | Response Format |
|---|---|
| `MSG_SELECT_IDLECMD` | `int32 action_type + int32 index` |
| `MSG_SELECT_BATTLECMD` | `int32 action_type + int32 index` |
| `MSG_SELECT_CARD` | `int32 count + int32[] indices` |
| `MSG_SELECT_CHAIN` | `int32 index` (-1 to decline) |
| `MSG_SELECT_EFFECTYN` | `int32` (1=yes, 0=no) |
| `MSG_SELECT_YESNO` | `int32` (1=yes, 0=no) |
| `MSG_SELECT_OPTION` | `int32 index` |
| `MSG_SELECT_PLACE` | `uint8 player + uint8 location + uint8 sequence` |
| `MSG_SELECT_POSITION` | `int32 position` |
| `MSG_SELECT_TRIBUTE` | `int32 count + int32[] indices` |
| `MSG_SELECT_SUM` | `int32 count + int32[] indices` |
| `MSG_SELECT_COUNTER` | `int16[] counts` (per card) |
| `MSG_SORT_CARD` | `int8[] order` |
| `MSG_SORT_CHAIN` | `int8[] order` |
| `MSG_SELECT_UNSELECT_CARD` | `int32 index` (-1 to finish) |
| `MSG_SELECT_DISFIELD` | Same as `MSG_SELECT_PLACE` |
| `MSG_ROCK_PAPER_SCISSORS` | `int32` (1=rock, 2=paper, 3=scissors) |
| `MSG_ANNOUNCE_RACE` | `int64 race_flags` |
| `MSG_ANNOUNCE_ATTRIB` | `int32 attribute_flags` |
| `MSG_ANNOUNCE_CARD` | Depends on opcode filter |
| `MSG_ANNOUNCE_NUMBER` | `int32 index` |

### 6.4 Response Validation

Before sending, validate that:
- Selected indices are within the valid range provided in the prompt message.
- The count of selected items is between `min` and `max`.
- The selected position is among the available positions.
- The selected zone is among the selectable zones.

This prevents sending invalid responses that would cause ygopro-core to request the same prompt again (it sends `MSG_RETRY` on invalid responses).

### Deliverables
- [ ] `ResponseBuilder.java` utility
- [ ] Response encoding methods for each `MSG_SELECT_*` type
- [ ] Validation logic matching prompt constraints
- [ ] Unit tests pairing each prompt message with its expected response

---

## Phase 7: Minecraft Integration

**Goal:** Wire the duel system into the Minecraft mod so players can initiate and play duels.

### 7.1 Server-Side Duel Manager

`DuelManager` — a singleton managed by the server lifecycle:

- Owns a single `DuelEngine` instance (created during `ServerStartingEvent` with configured DB/script paths).
- Maintains a `Map<UUID, DuelSession>` of active duels (keyed by a unique duel ID).
- On server stop, destroys all active sessions and then the engine.
- Provides `startDuel(player1, player2, options)` → creates a `DuelSession` and begins processing.

### 7.2 Resource Configuration

Card databases and scripts need to be configured. Use the NeoForge config system to specify paths:

```java
// In mod config
public static String cardDatabasePath = "config/duelcraft/cards.cdb";
public static List<String> scriptSearchPaths = List.of(
    "config/duelcraft/scripts/expansions",
    "config/duelcraft/scripts"
);
```

Alternatively, auto-detect an existing EDOPro installation and use its `script/` directory and `cards.cdb` — many users will already have these files.

### 7.3 Duel Trigger

How players initiate a duel — options:

- **Command:** `/duel challenge <player>` — sends a duel request. The target accepts with `/duel accept`.
- **Item:** A custom item (e.g., "Duel Disk") that right-clicks to challenge the nearest player or opens a duel configuration screen.
- **Block:** A "Duel Arena" block that both players right-click to start.

For the initial implementation, a simple command is fastest.

### 7.4 Deck Management

Players need decks. Implementation:

- **Deck format:** Use the standard `.ydk` text format (lines of card codes, separated by `#main`, `#extra`, `!side` headers).
- **Storage:** Per-player data — files in `config/duelcraft/decks/<uuid>.ydk` or world-scoped storage.
- **Commands:** `/duel deck set <name>`, `/duel deck list`.
- **Future:** GUI deck editor.

### 7.5 Duel State Synchronization

The server processes the duel. The client needs to display the game state. Communication uses NeoForge's network system:

**Server → Client packets:**
- `DuelStartPacket` — duel ID, opponent name, initial state
- `DuelMessagePacket` — forwarded `DuelMessage` (serialized)
- `DuelEndPacket` — result

**Client → Server packets:**
- `DuelResponsePacket` — player's response (serialized)

Use NeoForge's `PayloadRegistrar` for packet registration.

**Information hiding:** Like edopro, the server must NOT send the opponent's hidden card codes to a player. When forwarding messages, zero out codes for:
- Face-down cards the player doesn't control
- Cards in the opponent's hand
- Cards in the opponent's deck

### 7.6 Server Tick Integration (Alternative to Dedicated Threads)

Instead of dedicated threads, process duels during the server tick:

- Each tick, check if any duel has a pending response to process.
- Call `setResponse` + `process` on the server thread.
- This avoids thread-safety concerns with Minecraft's own state.
- Downside: duel processing blocks the tick. Mitigate by limiting processing time per tick.

For a first implementation, the simpler tick-based approach may be preferable. Move to dedicated threads later if performance requires it.

### Deliverables
- [ ] `DuelManager.java` — server-side singleton owning `DuelEngine` + active sessions
- [ ] NeoForge config for card DB and script paths
- [ ] `/duel` command tree (challenge, accept, deck management)
- [ ] Deck loading from `.ydk` files
- [ ] Network packet definitions (NeoForge channel)
- [ ] Information hiding in message forwarding
- [ ] Server lifecycle hooks (start/stop)

---

## Phase 8: Multiplayer & Networking

**Goal:** Ensure duels work correctly in a multiplayer Minecraft server with multiple concurrent duels.

### 8.1 Concurrency Model

- All duels share a single `DuelEngine` (card DB and scripts are read-only after init).
- Each `DuelSession` gets its own `OCG_Duel` handle. Separate handles are fully independent in ygopro-core.
- Use a thread pool (`Executors.newCachedThreadPool()`) where each duel processes on a pooled thread, or use the server tick approach from 7.6.

### 8.2 Player State Tracking

Track which players are currently in a duel to prevent:
- Starting a second duel while already in one.
- Interacting with the Minecraft world during a duel (optional restriction).
- Disconnecting mid-duel (pause or forfeit).

### 8.3 Disconnect Handling

- If a player disconnects, pause their duel (with a timeout).
- On reconnect, re-sync the full duel state by querying all zones and re-sending.
- If timeout expires, the disconnected player forfeits.

### 8.4 Spectating

- Other players can watch a duel (receive broadcast messages but not selection prompts).
- Both players' hidden information is hidden from spectators (spectators see what edopro calls the "observer" view).

### 8.5 Timeout / Timer

- Add a per-turn timer (configurable, e.g., 180 seconds).
- If a player doesn't respond in time, auto-forfeit or auto-select a default response.

### Deliverables
- [ ] Thread pool for concurrent duel processing
- [ ] Player-in-duel state tracking
- [ ] Disconnect/reconnect handling with state re-sync
- [ ] Spectator support
- [ ] Turn timer with auto-forfeit

---

## Phase 9: Frontend UI

**Goal:** Render the duel visually in Minecraft and provide interactive controls for player decisions.

### 9.1 UI Approach Options

1. **Custom GUI screens** — NeoForge `Screen` subclasses rendering a 2D duel board. Familiar to ygopro users. Fastest to implement.
2. **In-world rendering** — Cards as entities or block displays on a 3D duel mat in the world. More immersive but much more complex.
3. **Hybrid** — In-world duel mat for aesthetics, 2D overlay GUI for card inspection and selections.

**Recommendation:** Start with option 1 (custom GUI screens) for rapid iteration. Layer in-world elements later.

### 9.2 Core UI Components

- **Duel Board Screen** — The main duel view showing:
  - Both players' Monster Zones (5 Main + 2 Extra Monster Zones)
  - Both players' Spell/Trap Zones (5 + Field Zone)
  - Both players' Graveyard, Banished, Extra Deck, Main Deck (as pile icons with counts)
  - Both players' hands (your cards face-up, opponent's face-down)
  - LP displays
  - Current phase indicator
  - Chain display

- **Card Info Panel** — Hovering over a card shows its full details (name, type, ATK/DEF, effect text). Card text comes from the `texts` table in `cards.cdb`. This data can either be:
  - Queried from the C++ bridge (add a `nGetCardText(engine, code)` native method that reads the `texts` table), or
  - Loaded into a separate Java-side read-only map at startup (since it's display-only data, not needed by the core engine).

- **Selection Overlays** — When the server sends a selection prompt:
  - Highlight selectable cards/zones.
  - Show confirm/cancel buttons.
  - For `MSG_SELECT_CARD`, show a card list popup.
  - For `MSG_SELECT_PLACE`, highlight valid zones on the board.
  - For `MSG_SELECT_YESNO` / `MSG_SELECT_EFFECTYN`, show a dialog.

### 9.3 Card Art

- Card images are separate assets, not part of ygopro-core.
- Options: user-provided image directory, download on demand, or placeholder art.
- Display card art as textures on GUI elements.

### 9.4 Animation

- Animate card movements (draw, summon, attack, destroy) based on `MSG_MOVE`, `MSG_ATTACK`, `MSG_DAMAGE`, etc.
- Phase transitions, chain link indicators, LP change animations.
- Start simple (instant state changes) and add animations incrementally.

### 9.5 Sound

- Optional sound effects for summon, attack, LP damage, phase change, chain activation.
- Use Minecraft's `SoundEvent` system.

### Deliverables
- [ ] Duel board GUI screen with zone layout
- [ ] Card rendering (placeholder or with art)
- [ ] Card info tooltip / detail panel (with card text from DB)
- [ ] Selection prompt UI for each `MSG_SELECT_*` type
- [ ] LP display and phase indicator
- [ ] Chain visualization
- [ ] Basic animations for card movement

---

## Appendix A: OCG API Reference Summary

The complete C API consists of 13 functions:

```c
// Info
void OCG_GetVersion(int* major, int* minor);

// Lifecycle
int  OCG_CreateDuel(OCG_Duel* duel, OCG_DuelOptions options);
void OCG_DestroyDuel(OCG_Duel duel);
void OCG_DuelNewCard(OCG_Duel duel, OCG_NewCardInfo info);
void OCG_StartDuel(OCG_Duel duel);

// Processing
int   OCG_DuelProcess(OCG_Duel duel);
void* OCG_DuelGetMessage(OCG_Duel duel, uint32_t* length);
void  OCG_DuelSetResponse(OCG_Duel duel, const void* buffer, uint32_t length);
int   OCG_LoadScript(OCG_Duel duel, const char* buffer, uint32_t length, const char* name);

// Querying
uint32_t OCG_DuelQueryCount(OCG_Duel duel, uint8_t team, uint32_t loc);
void*    OCG_DuelQuery(OCG_Duel duel, uint32_t* length, OCG_QueryInfo info);
void*    OCG_DuelQueryLocation(OCG_Duel duel, uint32_t* length, OCG_QueryInfo info);
void*    OCG_DuelQueryField(OCG_Duel duel, uint32_t* length);
```

**Key types:**
- `OCG_Duel` — opaque `void*` handle
- `OCG_DuelOptions` — seed[4], flags, team1, team2, cardReader, scriptReader, logHandler, payloads
- `OCG_NewCardInfo` — team, duelist, code, controller, location, sequence, position
- `OCG_QueryInfo` — flags, controller, location, sequence, overlay_sequence
- `OCG_CardData` — code, alias, setcodes, type, level, attribute, race, attack, defense, lscale, rscale, link_marker

**Duel lifecycle (as wrapped by DuelEngine):**
1. `DuelEngine::init(dbPaths, scriptPaths)` — load card database(s), configure script search paths
2. `DuelEngine::createDuel(seed, flags, teams)` — calls `OCG_CreateDuel` with internal callbacks, then auto-loads `constant.lua` and `utility.lua`
3. `DuelEngine::addCard(...)` — calls `OCG_DuelNewCard` for each card in both decks
4. `DuelEngine::startDuel(...)` — calls `OCG_StartDuel`
5. Loop: `process()` → `getMessages()` → parse in Java → send to players → wait for response → `setResponse()` → repeat
6. `DuelEngine::destroyDuel(...)` → `OCG_DestroyDuel`
7. `DuelEngine::shutdown()` — cleanup

---

## Appendix B: Message Types Reference

Full list of `MSG_*` constants from `ocgapi_constants.h` with their numeric values:

| Constant | Value | Category |
|---|---|---|
| MSG_RETRY | 1 | Error |
| MSG_HINT | 2 | UI |
| MSG_WAITING | 3 | UI |
| MSG_START | 4 | Lifecycle |
| MSG_WIN | 5 | Lifecycle |
| MSG_UPDATE_DATA | 6 | Query |
| MSG_UPDATE_CARD | 7 | Query |
| MSG_REQUEST_DECK | 8 | Setup |
| MSG_SELECT_BATTLECMD | 10 | Selection |
| MSG_SELECT_IDLECMD | 11 | Selection |
| MSG_SELECT_EFFECTYN | 12 | Selection |
| MSG_SELECT_YESNO | 13 | Selection |
| MSG_SELECT_OPTION | 14 | Selection |
| MSG_SELECT_CARD | 15 | Selection |
| MSG_SELECT_CHAIN | 16 | Selection |
| MSG_SELECT_PLACE | 18 | Selection |
| MSG_SELECT_POSITION | 19 | Selection |
| MSG_SELECT_TRIBUTE | 20 | Selection |
| MSG_SORT_CHAIN | 21 | Selection |
| MSG_SELECT_COUNTER | 22 | Selection |
| MSG_SELECT_SUM | 23 | Selection |
| MSG_SELECT_DISFIELD | 24 | Selection |
| MSG_SORT_CARD | 25 | Selection |
| MSG_SELECT_UNSELECT_CARD | 26 | Selection |
| MSG_CONFIRM_DECKTOP | 30 | Info |
| MSG_CONFIRM_CARDS | 31 | Info |
| MSG_SHUFFLE_DECK | 32 | Action |
| MSG_SHUFFLE_HAND | 33 | Action |
| MSG_REFRESH_DECK | 34 | Action |
| MSG_SWAP_GRAVE_DECK | 35 | Action |
| MSG_SHUFFLE_SET_CARD | 36 | Action |
| MSG_REVERSE_DECK | 37 | Action |
| MSG_DECK_TOP | 38 | Action |
| MSG_SHUFFLE_EXTRA | 39 | Action |
| MSG_NEW_TURN | 40 | Lifecycle |
| MSG_NEW_PHASE | 41 | Lifecycle |
| MSG_CONFIRM_EXTRATOP | 42 | Info |
| MSG_MOVE | 50 | Action |
| MSG_POS_CHANGE | 53 | Action |
| MSG_SET | 54 | Action |
| MSG_SWAP | 55 | Action |
| MSG_FIELD_DISABLED | 56 | Info |
| MSG_SUMMONING | 60 | Action |
| MSG_SUMMONED | 61 | Action |
| MSG_SPSUMMONING | 62 | Action |
| MSG_SPSUMMONED | 63 | Action |
| MSG_FLIPSUMMONING | 64 | Action |
| MSG_FLIPSUMMONED | 65 | Action |
| MSG_CHAINING | 70 | Chain |
| MSG_CHAINED | 71 | Chain |
| MSG_CHAIN_SOLVING | 72 | Chain |
| MSG_CHAIN_SOLVED | 73 | Chain |
| MSG_CHAIN_END | 74 | Chain |
| MSG_CHAIN_NEGATED | 75 | Chain |
| MSG_CHAIN_DISABLED | 76 | Chain |
| MSG_CARD_SELECTED | 80 | Info |
| MSG_RANDOM_SELECTED | 81 | Info |
| MSG_BECOME_TARGET | 83 | Info |
| MSG_DRAW | 90 | Action |
| MSG_DAMAGE | 91 | Action |
| MSG_RECOVER | 92 | Action |
| MSG_EQUIP | 93 | Action |
| MSG_LPUPDATE | 94 | Action |
| MSG_UNEQUIP | 95 | Action |
| MSG_CARD_TARGET | 96 | Action |
| MSG_CANCEL_TARGET | 97 | Action |
| MSG_PAY_LPCOST | 100 | Action |
| MSG_ADD_COUNTER | 101 | Action |
| MSG_REMOVE_COUNTER | 102 | Action |
| MSG_ATTACK | 110 | Battle |
| MSG_BATTLE | 111 | Battle |
| MSG_ATTACK_DISABLED | 112 | Battle |
| MSG_DAMAGE_STEP_START | 113 | Battle |
| MSG_DAMAGE_STEP_END | 114 | Battle |
| MSG_MISSED_EFFECT | 120 | Chain |
| MSG_BE_CHAIN_TARGET | 121 | Chain |
| MSG_CREATE_RELATION | 122 | Info |
| MSG_RELEASE_RELATION | 123 | Info |
| MSG_TOSS_COIN | 130 | Action |
| MSG_TOSS_DICE | 131 | Action |
| MSG_ROCK_PAPER_SCISSORS | 132 | Selection |
| MSG_HAND_RES | 133 | Info |
| MSG_ANNOUNCE_RACE | 140 | Selection |
| MSG_ANNOUNCE_ATTRIB | 141 | Selection |
| MSG_ANNOUNCE_CARD | 142 | Selection |
| MSG_ANNOUNCE_NUMBER | 143 | Selection |
| MSG_CARD_HINT | 160 | UI |
| MSG_TAG_SWAP | 161 | Action |
| MSG_RELOAD_FIELD | 162 | Action |
| MSG_AI_NAME | 163 | Info |
| MSG_SHOW_HINT | 164 | UI |
| MSG_PLAYER_HINT | 165 | UI |
| MSG_MATCH_KILL | 170 | Lifecycle |
| MSG_CUSTOM_MSG | 180 | Custom |
| MSG_REMOVE_CARDS | 190 | Action |

---

## Appendix C: Thread Safety Model

### ygopro-core guarantees

- A single `OCG_Duel` instance is **NOT thread-safe**. All calls to a given duel handle must be serialized.
- **Separate `OCG_Duel` instances are fully independent** and can be used concurrently from different threads without synchronization.
- Callbacks (`DataReader`, `ScriptReader`, `LogHandler`) are invoked synchronously during `OCG_DuelProcess`, `OCG_DuelNewCard`, and `OCG_CreateDuel`. They execute on the calling thread.

### Threading with the DuelEngine design

Since `DataReader` and `ScriptReader` are handled entirely in C++, the JNI threading concerns are dramatically simplified:

- **`CardDatabase`** is read-only after `init()`. Multiple duel threads can invoke the `cardReader` callback concurrently with no synchronization needed — each callback just does a `std::unordered_map::find` on immutable data.
- **`ScriptProvider`** only does filesystem reads. Multiple duel threads calling `scriptReader` concurrently is safe (filesystem reads are inherently thread-safe; the OS handles it).
- **`LogHandler`** is the only callback that optionally crosses into Java. It uses `AttachCurrentThread`/`DetachCurrentThread` only when invoked from a non-Java thread, which is rare (ygopro-core calls callbacks synchronously on the caller's thread, and the caller is a JNI method invoked from Java).
- **No `GlobalRef` per duel** needed for DataReader/ScriptReader — they use the engine-level `CardDatabase` and `ScriptProvider` directly via the `payload` pointer.

### Recommended architecture for Duelcraft

```
Server Tick Thread
  └─ DuelManager
       │
       ├─ DuelEngine (shared, created once)
       │    ├─ CardDatabase (read-only after init, thread-safe)
       │    └─ ScriptProvider (filesystem reads, thread-safe)
       │
       ├─ DuelSession A ──→ OCG_Duel handle A  (processed on Thread-Pool-1)
       ├─ DuelSession B ──→ OCG_Duel handle B  (processed on Thread-Pool-2)
       └─ DuelSession C ──→ OCG_Duel handle C  (processed on Thread-Pool-3)
```

Each session's `process()` and `setResponse()` must be dispatched to the **same single thread** (or protected by a per-session lock). A `SingleThreadExecutor` per session is the simplest correct approach.

Network packet handling (receiving player responses) happens on Netty threads — these must enqueue responses to the correct session's executor rather than calling `setResponse` directly.
