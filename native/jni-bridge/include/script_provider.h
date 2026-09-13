#ifndef SCRIPT_PROVIDER_H
#define SCRIPT_PROVIDER_H

#include <optional>
#include <string>
#include <unordered_map>
#include <vector>
#include "ocgapi_types.h"

// Forward-declare the OCG functions we call (loaded from the DLL)
extern "C" {
    int OCG_LoadScript(OCG_Duel ocg_duel, const char* buffer, uint32_t length, const char* name);
}

class ScriptProvider {
public:
    // Configure search paths (checked in order, first match wins)
    void setSearchPaths(const std::vector<std::string>& paths);

    // OCG_ScriptReader callback target
    static int scriptReader(void* payload, OCG_Duel duel, const char* name);

    // Explicitly load a script by name (used for bootstrap scripts)
    bool loadScript(OCG_Duel duel, const std::string& name);

private:
    std::vector<std::string> searchPaths_;

    // Resolved script name -> full path; an empty path records a miss already logged
    std::unordered_map<std::string, std::string> resolved_;

    // Read a file from the first matching search path. A name that resolves for the first
    // time is remembered; a first miss is logged once at `missLogType` with the paths searched.
    std::optional<std::vector<char>> readFile(const std::string& name, int missLogType);

    static std::optional<std::vector<char>> readPath(const std::string& path);
    std::string searchPathList() const;
};

#endif // SCRIPT_PROVIDER_H
