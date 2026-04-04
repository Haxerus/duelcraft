#ifndef SCRIPT_PROVIDER_H
#define SCRIPT_PROVIDER_H

#include <optional>
#include <string>
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

    // Read a file from the first matching search path
    std::optional<std::vector<char>> readFile(const std::string& name);
};

#endif // SCRIPT_PROVIDER_H
