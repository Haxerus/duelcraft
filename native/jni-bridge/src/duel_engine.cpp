#include "duel_engine.h"
#include <cstring>
#include <cstdio>
#include "ocgapi.h"

OCG_Duel DuelEngine::toDuel(intptr_t handle) {
    return reinterpret_cast<OCG_Duel>(handle);
}

bool DuelEngine::init(const std::vector<std::string>& dbPaths,
                      const std::vector<std::string>& scriptPaths) {
    if (!cardDb_.open(dbPaths)) {
        return false;
    }
    scriptProvider_.setSearchPaths(scriptPaths);
    return true;
}

void DuelEngine::shutdown() {
    // Destroy any duels still alive
    for (auto& [handle, ctx] : contexts_) {
        OCG_DestroyDuel(ctx->duel);
    }
    contexts_.clear();
    cardDb_.close();
}

intptr_t DuelEngine::createDuel(const uint64_t seed[4], uint64_t flags,
                                int team1LP, int team1StartHand, int team1DrawPerTurn,
                                int team2LP, int team2StartHand, int team2DrawPerTurn) {
    OCG_DuelOptions opts{};
    std::memcpy(opts.seed, seed, sizeof(opts.seed));
    opts.flags = flags;

    opts.team1.startingLP        = static_cast<uint32_t>(team1LP);
    opts.team1.startingDrawCount = static_cast<uint32_t>(team1StartHand);
    opts.team1.drawCountPerTurn  = static_cast<uint32_t>(team1DrawPerTurn);

    opts.team2.startingLP        = static_cast<uint32_t>(team2LP);
    opts.team2.startingDrawCount = static_cast<uint32_t>(team2StartHand);
    opts.team2.drawCountPerTurn  = static_cast<uint32_t>(team2DrawPerTurn);

    // Wire callbacks — card data and scripts stay entirely in C++
    opts.cardReader     = CardDatabase::cardReader;
    opts.payload1       = &cardDb_;
    opts.scriptReader   = ScriptProvider::scriptReader;
    opts.payload2       = &scriptProvider_;
    opts.logHandler     = DuelEngine::logHandler;
    opts.payload3       = this;
    opts.cardReaderDone = CardDatabase::cardReaderDone;
    opts.payload4       = &cardDb_;

    opts.enableUnsafeLibraries = 0;

    OCG_Duel duel = nullptr;
    int status = OCG_CreateDuel(&duel, &opts);
    if (status != OCG_DUEL_CREATION_SUCCESS || duel == nullptr) {
        return 0;
    }

    auto handle = reinterpret_cast<intptr_t>(duel);

    // Store context for this duel
    auto ctx = std::make_unique<DuelContext>();
    ctx->engine = this;
    ctx->duel = duel;
    contexts_[handle] = std::move(ctx);

    // Load bootstrap scripts
    scriptProvider_.loadScript(duel, "constant.lua");
    scriptProvider_.loadScript(duel, "utility.lua");

    return handle;
}

void DuelEngine::destroyDuel(intptr_t handle) {
    auto it = contexts_.find(handle);
    if (it != contexts_.end()) {
        OCG_DestroyDuel(it->second->duel);
        contexts_.erase(it);
    }
}

void DuelEngine::addCard(intptr_t handle, uint8_t team, uint8_t duelist, uint32_t code,
                         uint8_t controller, uint32_t location, uint32_t sequence, uint32_t position) {
    OCG_NewCardInfo info{};
    info.team     = team;
    info.duelist  = duelist;
    info.code     = code;
    info.con      = controller;
    info.loc      = location;
    info.seq      = sequence;
    info.pos      = position;
    OCG_DuelNewCard(toDuel(handle), &info);
}

void DuelEngine::startDuel(intptr_t handle) {
    OCG_StartDuel(toDuel(handle));
}

int DuelEngine::process(intptr_t handle) {
    return OCG_DuelProcess(toDuel(handle));
}

std::pair<const uint8_t*, uint32_t> DuelEngine::getMessages(intptr_t handle) {
    uint32_t length = 0;
    auto* data = static_cast<const uint8_t*>(OCG_DuelGetMessage(toDuel(handle), &length));
    return {data, length};
}

void DuelEngine::setResponse(intptr_t handle, const uint8_t* data, uint32_t len) {
    OCG_DuelSetResponse(toDuel(handle), data, len);
}

uint32_t DuelEngine::queryCount(intptr_t handle, uint8_t team, uint32_t location) {
    return OCG_DuelQueryCount(toDuel(handle), team, location);
}

std::pair<const uint8_t*, uint32_t> DuelEngine::query(intptr_t handle, uint32_t flags,
                                                       uint8_t controller, uint32_t location,
                                                       uint32_t sequence, uint32_t overlaySeq) {
    OCG_QueryInfo info{};
    info.flags       = flags;
    info.con         = controller;
    info.loc         = location;
    info.seq         = sequence;
    info.overlay_seq = overlaySeq;
    uint32_t length = 0;
    auto* data = static_cast<const uint8_t*>(OCG_DuelQuery(toDuel(handle), &length, &info));
    return {data, length};
}

std::pair<const uint8_t*, uint32_t> DuelEngine::queryLocation(intptr_t handle, uint32_t flags,
                                                               uint8_t controller, uint32_t location) {
    OCG_QueryInfo info{};
    info.flags = flags;
    info.con   = controller;
    info.loc   = location;
    uint32_t length = 0;
    auto* data = static_cast<const uint8_t*>(OCG_DuelQueryLocation(toDuel(handle), &length, &info));
    return {data, length};
}

std::pair<const uint8_t*, uint32_t> DuelEngine::queryField(intptr_t handle) {
    uint32_t length = 0;
    auto* data = static_cast<const uint8_t*>(OCG_DuelQueryField(toDuel(handle), &length));
    return {data, length};
}

std::pair<int, int> DuelEngine::getVersion() {
    int major = 0, minor = 0;
    OCG_GetVersion(&major, &minor);
    return {major, minor};
}

void DuelEngine::logHandler(void* payload, const char* message, int type) {
    // For now, log to stderr. Java log forwarding will be added later.
    if (message) {
        fprintf(stderr, "[ocgcore] %s\n", message);
    }
}
