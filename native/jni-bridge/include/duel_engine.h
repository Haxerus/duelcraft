#ifndef DUEL_ENGINE_H
#define DUEL_ENGINE_H

#include <cstdint>
#include <memory>
#include <string>
#include <unordered_map>
#include <utility>
#include <vector>
#include "card_database.h"
#include "script_provider.h"
#include "ocgapi_types.h"

class DuelEngine {
public:
    // Initialize with paths to card databases and script directories
    bool init(const std::vector<std::string>& dbPaths,
              const std::vector<std::string>& scriptPaths);
    void shutdown();

    // Create a new duel. Returns opaque handle (OCG_Duel cast to intptr_t), or 0 on failure.
    // Automatically wires DataReader, ScriptReader, LogHandler.
    // Automatically loads bootstrap scripts (constant.lua, utility.lua).
    intptr_t createDuel(const uint64_t seed[4], uint64_t flags,
                        int team1LP, int team1StartHand, int team1DrawPerTurn,
                        int team2LP, int team2StartHand, int team2DrawPerTurn);
    void destroyDuel(intptr_t duel);

    // Card setup
    void addCard(intptr_t duel, uint8_t team, uint8_t duelist, uint32_t code,
                 uint8_t controller, uint32_t location, uint32_t sequence, uint32_t position);
    void startDuel(intptr_t duel);

    // Processing
    int process(intptr_t duel);
    std::pair<const uint8_t*, uint32_t> getMessages(intptr_t duel);
    void setResponse(intptr_t duel, const uint8_t* data, uint32_t len);

    // Querying
    uint32_t queryCount(intptr_t duel, uint8_t team, uint32_t location);
    std::pair<const uint8_t*, uint32_t> query(intptr_t duel, uint32_t flags,
                                               uint8_t controller, uint32_t location,
                                               uint32_t sequence, uint32_t overlaySeq);
    std::pair<const uint8_t*, uint32_t> queryLocation(intptr_t duel, uint32_t flags,
                                                       uint8_t controller, uint32_t location);
    std::pair<const uint8_t*, uint32_t> queryField(intptr_t duel);

    // Info
    std::pair<int, int> getVersion();

private:
    // Per-duel context, passed as callback payload
    struct DuelContext {
        DuelEngine* engine;
        OCG_Duel duel;
    };

    static OCG_Duel toDuel(intptr_t handle);

    static void logHandler(void* payload, const char* message, int type);

    CardDatabase cardDb_;
    ScriptProvider scriptProvider_;
    std::unordered_map<intptr_t, std::unique_ptr<DuelContext>> contexts_;
};

#endif // DUEL_ENGINE_H
