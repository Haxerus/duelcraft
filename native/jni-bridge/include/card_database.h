#ifndef CARD_DATABASE_H
#define CARD_DATABASE_H

#include <cstdint>
#include <string>
#include <unordered_map>
#include <vector>
#include "ocgapi_types.h"

struct CardDataEntry {
    uint32_t code;
    uint32_t alias;
    std::vector<uint16_t> setcodes; // zero-terminated when passed to OCG
    uint32_t type;
    uint32_t level;
    uint32_t attribute;
    uint64_t race;
    int32_t attack;
    int32_t defense;
    uint32_t lscale;
    uint32_t rscale;
    uint32_t link_marker;
};

class CardDatabase {
public:
    bool open(const std::vector<std::string>& dbPaths);
    void close();

    // OCG_DataReader callback — looks up card data from the in-memory map
    static void cardReader(void* payload, uint32_t code, OCG_CardData* data);

    // OCG_DataReaderDone callback — no-op since setcodes point into our map
    static void cardReaderDone(void* payload, OCG_CardData* data);

private:
    bool loadFromFile(const std::string& path);

    std::unordered_map<uint32_t, CardDataEntry> cards_;
};

#endif // CARD_DATABASE_H
