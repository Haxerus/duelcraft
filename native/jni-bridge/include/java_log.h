#ifndef JAVA_LOG_H
#define JAVA_LOG_H

#include <string>

// Log type forwarded alongside the message. 0-3 are the engine's OCG_LogTypes;
// LOG_TYPE_BRIDGE_WARN is this bridge's own channel for recoverable data problems
// (unknown card code, missing card script) that the engine itself never reports.
constexpr int LOG_TYPE_BRIDGE_WARN = 4;

// Hand a line to OcgCore.onNativeLog. Dropped if the calling thread is not attached
// to the JVM or the Java hook cannot be resolved; never throws into the caller.
void javaLog(int type, const std::string& message);

#endif // JAVA_LOG_H
