#include <jni.h>
#include <cstring>
#include <string>
#include <vector>
#include "duel_engine.h"

// Helper: convert a Java String[] to std::vector<std::string>
static std::vector<std::string> jstringArrayToVector(JNIEnv* env, jobjectArray arr) {
    std::vector<std::string> result;
    if (arr == nullptr) return result;
    jsize len = env->GetArrayLength(arr);
    result.reserve(len);
    for (jsize i = 0; i < len; i++) {
        auto jstr = static_cast<jstring>(env->GetObjectArrayElement(arr, i));
        if (jstr == nullptr) continue;
        const char* chars = env->GetStringUTFChars(jstr, nullptr);
        result.emplace_back(chars);
        env->ReleaseStringUTFChars(jstr, chars);
        env->DeleteLocalRef(jstr);
    }
    return result;
}

// Helper: copy a native byte buffer into a new Java byte[]
static jbyteArray nativeBufToJbyteArray(JNIEnv* env, const uint8_t* data, uint32_t len) {
    if (data == nullptr || len == 0) return nullptr;
    jbyteArray result = env->NewByteArray(static_cast<jsize>(len));
    env->SetByteArrayRegion(result, 0, static_cast<jsize>(len),
                            reinterpret_cast<const jbyte*>(data));
    return result;
}

// --- Engine lifecycle ---

extern "C" JNIEXPORT jlong JNICALL
Java_com_haxerus_duelcraft_core_OcgCore_nCreateEngine(
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

extern "C" JNIEXPORT void JNICALL
Java_com_haxerus_duelcraft_core_OcgCore_nDestroyEngine(
        JNIEnv*, jclass, jlong engineHandle) {
    auto* engine = reinterpret_cast<DuelEngine*>(engineHandle);
    if (engine) {
        engine->shutdown();
        delete engine;
    }
}

// --- Duel lifecycle ---

extern "C" JNIEXPORT jlong JNICALL
Java_com_haxerus_duelcraft_core_OcgCore_nCreateDuel(
        JNIEnv* env, jclass, jlong engineHandle, jlongArray seed, jlong flags,
        jint t1LP, jint t1Hand, jint t1Draw, jint t2LP, jint t2Hand, jint t2Draw) {
    auto* engine = reinterpret_cast<DuelEngine*>(engineHandle);
    if (!engine) return 0;

    jlong* seedElems = env->GetLongArrayElements(seed, nullptr);
    uint64_t nativeSeed[4] = {
        static_cast<uint64_t>(seedElems[0]),
        static_cast<uint64_t>(seedElems[1]),
        static_cast<uint64_t>(seedElems[2]),
        static_cast<uint64_t>(seedElems[3])
    };
    env->ReleaseLongArrayElements(seed, seedElems, JNI_ABORT);

    return static_cast<jlong>(engine->createDuel(
        nativeSeed, static_cast<uint64_t>(flags),
        t1LP, t1Hand, t1Draw, t2LP, t2Hand, t2Draw));
}

extern "C" JNIEXPORT void JNICALL
Java_com_haxerus_duelcraft_core_OcgCore_nDestroyDuel(
        JNIEnv*, jclass, jlong engineHandle, jlong duelHandle) {
    auto* engine = reinterpret_cast<DuelEngine*>(engineHandle);
    if (engine) engine->destroyDuel(static_cast<intptr_t>(duelHandle));
}

extern "C" JNIEXPORT void JNICALL
Java_com_haxerus_duelcraft_core_OcgCore_nDuelNewCard(
        JNIEnv*, jclass, jlong engineHandle, jlong duelHandle,
        jint team, jint duelist, jint code, jint controller,
        jint location, jint sequence, jint position) {
    auto* engine = reinterpret_cast<DuelEngine*>(engineHandle);
    if (engine) {
        engine->addCard(static_cast<intptr_t>(duelHandle),
                        static_cast<uint8_t>(team),
                        static_cast<uint8_t>(duelist),
                        static_cast<uint32_t>(code),
                        static_cast<uint8_t>(controller),
                        static_cast<uint32_t>(location),
                        static_cast<uint32_t>(sequence),
                        static_cast<uint32_t>(position));
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_haxerus_duelcraft_core_OcgCore_nStartDuel(
        JNIEnv*, jclass, jlong engineHandle, jlong duelHandle) {
    auto* engine = reinterpret_cast<DuelEngine*>(engineHandle);
    if (engine) engine->startDuel(static_cast<intptr_t>(duelHandle));
}

// --- Processing ---

extern "C" JNIEXPORT jint JNICALL
Java_com_haxerus_duelcraft_core_OcgCore_nDuelProcess(
        JNIEnv*, jclass, jlong engineHandle, jlong duelHandle) {
    auto* engine = reinterpret_cast<DuelEngine*>(engineHandle);
    if (!engine) return 0;
    return static_cast<jint>(engine->process(static_cast<intptr_t>(duelHandle)));
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_haxerus_duelcraft_core_OcgCore_nDuelGetMessage(
        JNIEnv* env, jclass, jlong engineHandle, jlong duelHandle) {
    auto* engine = reinterpret_cast<DuelEngine*>(engineHandle);
    if (!engine) return nullptr;
    auto [data, len] = engine->getMessages(static_cast<intptr_t>(duelHandle));
    return nativeBufToJbyteArray(env, data, len);
}

extern "C" JNIEXPORT void JNICALL
Java_com_haxerus_duelcraft_core_OcgCore_nDuelSetResponse(
        JNIEnv* env, jclass, jlong engineHandle, jlong duelHandle, jbyteArray response) {
    auto* engine = reinterpret_cast<DuelEngine*>(engineHandle);
    if (!engine || !response) return;

    jsize len = env->GetArrayLength(response);
    jbyte* data = env->GetByteArrayElements(response, nullptr);
    engine->setResponse(static_cast<intptr_t>(duelHandle),
                        reinterpret_cast<const uint8_t*>(data),
                        static_cast<uint32_t>(len));
    env->ReleaseByteArrayElements(response, data, JNI_ABORT);
}

// --- Querying ---

extern "C" JNIEXPORT jint JNICALL
Java_com_haxerus_duelcraft_core_OcgCore_nDuelQueryCount(
        JNIEnv*, jclass, jlong engineHandle, jlong duelHandle,
        jint team, jint location) {
    auto* engine = reinterpret_cast<DuelEngine*>(engineHandle);
    if (!engine) return 0;
    return static_cast<jint>(engine->queryCount(
        static_cast<intptr_t>(duelHandle),
        static_cast<uint8_t>(team),
        static_cast<uint32_t>(location)));
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_haxerus_duelcraft_core_OcgCore_nDuelQuery(
        JNIEnv* env, jclass, jlong engineHandle, jlong duelHandle,
        jint flags, jint controller, jint location, jint sequence, jint overlaySequence) {
    auto* engine = reinterpret_cast<DuelEngine*>(engineHandle);
    if (!engine) return nullptr;
    auto [data, len] = engine->query(
        static_cast<intptr_t>(duelHandle),
        static_cast<uint32_t>(flags),
        static_cast<uint8_t>(controller),
        static_cast<uint32_t>(location),
        static_cast<uint32_t>(sequence),
        static_cast<uint32_t>(overlaySequence));
    return nativeBufToJbyteArray(env, data, len);
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_haxerus_duelcraft_core_OcgCore_nDuelQueryLocation(
        JNIEnv* env, jclass, jlong engineHandle, jlong duelHandle,
        jint flags, jint controller, jint location) {
    auto* engine = reinterpret_cast<DuelEngine*>(engineHandle);
    if (!engine) return nullptr;
    auto [data, len] = engine->queryLocation(
        static_cast<intptr_t>(duelHandle),
        static_cast<uint32_t>(flags),
        static_cast<uint8_t>(controller),
        static_cast<uint32_t>(location));
    return nativeBufToJbyteArray(env, data, len);
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_haxerus_duelcraft_core_OcgCore_nDuelQueryField(
        JNIEnv* env, jclass, jlong engineHandle, jlong duelHandle) {
    auto* engine = reinterpret_cast<DuelEngine*>(engineHandle);
    if (!engine) return nullptr;
    auto [data, len] = engine->queryField(static_cast<intptr_t>(duelHandle));
    return nativeBufToJbyteArray(env, data, len);
}

// --- Info ---

extern "C" JNIEXPORT jintArray JNICALL
Java_com_haxerus_duelcraft_core_OcgCore_nGetVersion(JNIEnv* env, jclass) {
    DuelEngine engine; // version query doesn't need init
    auto [major, minor] = engine.getVersion();
    jintArray result = env->NewIntArray(2);
    jint buf[2] = { major, minor };
    env->SetIntArrayRegion(result, 0, 2, buf);
    return result;
}
