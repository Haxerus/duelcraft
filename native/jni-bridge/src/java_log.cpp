#include "java_log.h"
#include <jni.h>

namespace {

JavaVM* g_vm = nullptr;
jclass g_ocgCore = nullptr; // global ref
jmethodID g_onNativeLog = nullptr;

// Resolved lazily from inside a native method: FindClass then uses OcgCore's own class
// loader, which JNI_OnLoad (called from a system class) could not reach.
bool resolveHook(JNIEnv* env) {
    if (g_onNativeLog != nullptr) return true;
    jclass local = env->FindClass("com/haxerus/duelcraft/core/OcgCore");
    if (local == nullptr) {
        env->ExceptionClear();
        return false;
    }
    jmethodID method = env->GetStaticMethodID(local, "onNativeLog", "(ILjava/lang/String;)V");
    if (method == nullptr) {
        env->ExceptionClear();
        env->DeleteLocalRef(local);
        return false;
    }
    g_ocgCore = static_cast<jclass>(env->NewGlobalRef(local));
    env->DeleteLocalRef(local);
    g_onNativeLog = method;
    return true;
}

} // namespace

extern "C" JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void*) {
    g_vm = vm;
    return JNI_VERSION_1_8;
}

void javaLog(int type, const std::string& message) {
    if (g_vm == nullptr) return;

    // The engine invokes its handlers synchronously from the Java thread that entered the
    // bridge, so the thread is always attached; anything else is dropped rather than attached.
    JNIEnv* env = nullptr;
    if (g_vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_8) != JNI_OK || env == nullptr) return;
    if (!resolveHook(env)) return;

    jstring jmsg = env->NewStringUTF(message.c_str());
    if (jmsg == nullptr) {
        env->ExceptionClear();
        return;
    }
    env->CallStaticVoidMethod(g_ocgCore, g_onNativeLog, static_cast<jint>(type), jmsg);
    env->DeleteLocalRef(jmsg);
    // A Java exception must never unwind back into the engine's call stack.
    if (env->ExceptionCheck()) {
        env->ExceptionClear();
    }
}
