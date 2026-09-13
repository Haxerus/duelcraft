#include "script_provider.h"
#include <fstream>
#include "java_log.h"

void ScriptProvider::setSearchPaths(const std::vector<std::string>& paths) {
    searchPaths_ = paths;
    resolved_.clear();
}

int ScriptProvider::scriptReader(void* payload, OCG_Duel duel, const char* name) {
    auto* provider = static_cast<ScriptProvider*>(payload);
    // A card without a script is playable but effect-less, so this is a warning, not an error.
    auto content = provider->readFile(name, LOG_TYPE_BRIDGE_WARN);
    if (!content.has_value()) {
        return 0; // script not found
    }
    return OCG_LoadScript(duel, content->data(),
                          static_cast<uint32_t>(content->size()), name);
}

bool ScriptProvider::loadScript(OCG_Duel duel, const std::string& name) {
    // Bootstrap scripts: without them no card script can run at all.
    auto content = readFile(name, OCG_LOG_TYPE_ERROR);
    if (!content.has_value()) {
        return false;
    }
    return OCG_LoadScript(duel, content->data(),
                          static_cast<uint32_t>(content->size()),
                          name.c_str()) != 0;
}

std::optional<std::vector<char>> ScriptProvider::readFile(const std::string& name, int missLogType) {
    auto cached = resolved_.find(name);
    if (cached != resolved_.end()) {
        if (cached->second.empty()) {
            return std::nullopt; // known missing, already logged
        }
        return readPath(cached->second);
    }

    for (const auto& dir : searchPaths_) {
        std::string path = dir + "/" + name;
        auto buffer = readPath(path);
        if (buffer.has_value()) {
            resolved_.emplace(name, path);
            return buffer;
        }
    }

    resolved_.emplace(name, std::string());
    javaLog(missLogType, "script " + name + " not found in " + searchPathList());
    return std::nullopt;
}

std::optional<std::vector<char>> ScriptProvider::readPath(const std::string& path) {
    std::ifstream file(path, std::ios::binary | std::ios::ate);
    if (!file.is_open()) {
        return std::nullopt;
    }
    auto size = file.tellg();
    if (size <= 0) {
        return std::nullopt;
    }
    std::vector<char> buffer(static_cast<size_t>(size));
    file.seekg(0);
    file.read(buffer.data(), size);
    if (file.gcount() != size) {
        return std::nullopt;
    }
    return buffer;
}

std::string ScriptProvider::searchPathList() const {
    std::string list;
    for (const auto& dir : searchPaths_) {
        if (!list.empty()) list += ", ";
        list += dir;
    }
    return list.empty() ? "(no script paths configured)" : list;
}
