#include "script_provider.h"
#include <fstream>

void ScriptProvider::setSearchPaths(const std::vector<std::string>& paths) {
    searchPaths_ = paths;
}

int ScriptProvider::scriptReader(void* payload, OCG_Duel duel, const char* name) {
    auto* provider = static_cast<ScriptProvider*>(payload);
    auto content = provider->readFile(name);
    if (!content.has_value()) {
        return 0; // script not found
    }
    return OCG_LoadScript(duel, content->data(),
                          static_cast<uint32_t>(content->size()), name);
}

bool ScriptProvider::loadScript(OCG_Duel duel, const std::string& name) {
    auto content = readFile(name);
    if (!content.has_value()) {
        return false;
    }
    return OCG_LoadScript(duel, content->data(),
                          static_cast<uint32_t>(content->size()),
                          name.c_str()) != 0;
}

std::optional<std::vector<char>> ScriptProvider::readFile(const std::string& name) {
    for (const auto& dir : searchPaths_) {
        std::string path = dir + "/" + name;
        std::ifstream file(path, std::ios::binary | std::ios::ate);
        if (!file.is_open()) {
            continue;
        }
        auto size = file.tellg();
        if (size <= 0) {
            continue;
        }
        std::vector<char> buffer(static_cast<size_t>(size));
        file.seekg(0);
        file.read(buffer.data(), size);
        if (file.gcount() == size) {
            return buffer;
        }
    }
    return std::nullopt;
}
