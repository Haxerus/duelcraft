#include "card_database.h"
#include <cstring>
#include "sqlite3.h"

bool CardDatabase::open(const std::vector<std::string>& dbPaths) {
    for (const auto& path : dbPaths) {
        if (!loadFromFile(path)) {
            return false;
        }
    }
    return !dbPaths.empty();
}

void CardDatabase::close() {
    cards_.clear();
}

bool CardDatabase::loadFromFile(const std::string& path) {
    sqlite3* db = nullptr;
    if (sqlite3_open_v2(path.c_str(), &db, SQLITE_OPEN_READONLY, nullptr) != SQLITE_OK) {
        if (db) sqlite3_close(db);
        return false;
    }

    const char* sql = "SELECT id, alias, setcode, type, level, attribute, race, atk, def FROM datas";
    sqlite3_stmt* stmt = nullptr;
    if (sqlite3_prepare_v2(db, sql, -1, &stmt, nullptr) != SQLITE_OK) {
        sqlite3_close(db);
        return false;
    }

    while (sqlite3_step(stmt) == SQLITE_ROW) {
        CardDataEntry entry{};
        entry.code      = static_cast<uint32_t>(sqlite3_column_int64(stmt, 0));
        entry.alias     = static_cast<uint32_t>(sqlite3_column_int64(stmt, 1));

        // setcode is a 64-bit int packing up to 4 uint16_t archetype codes
        uint64_t setcode = static_cast<uint64_t>(sqlite3_column_int64(stmt, 2));
        for (int i = 0; i < 4; i++) {
            uint16_t sc = static_cast<uint16_t>((setcode >> (i * 16)) & 0xFFFF);
            if (sc != 0) {
                entry.setcodes.push_back(sc);
            }
        }
        entry.setcodes.push_back(0); // zero-terminated

        entry.type      = static_cast<uint32_t>(sqlite3_column_int64(stmt, 3));

        // level column encodes: bits 0-15 = level/rank, 16-23 = lscale, 24-31 = rscale
        uint32_t levelRaw = static_cast<uint32_t>(sqlite3_column_int64(stmt, 4));
        entry.level     = levelRaw & 0xFFFF;
        entry.lscale    = (levelRaw >> 24) & 0xFF;
        entry.rscale    = (levelRaw >> 16) & 0xFF;

        entry.attribute = static_cast<uint32_t>(sqlite3_column_int64(stmt, 5));
        entry.race      = static_cast<uint64_t>(sqlite3_column_int64(stmt, 6));
        entry.attack    = static_cast<int32_t>(sqlite3_column_int(stmt, 7));
        entry.defense   = static_cast<int32_t>(sqlite3_column_int(stmt, 8));
        entry.link_marker = 0; // not stored in standard datas table

        cards_[entry.code] = std::move(entry);
    }

    sqlite3_finalize(stmt);
    sqlite3_close(db);
    return true;
}

void CardDatabase::cardReader(void* payload, uint32_t code, OCG_CardData* data) {
    auto* db = static_cast<CardDatabase*>(payload);
    std::memset(data, 0, sizeof(OCG_CardData));

    auto it = db->cards_.find(code);
    if (it != db->cards_.end()) {
        const auto& entry = it->second;
        data->code        = entry.code;
        data->alias       = entry.alias;
        data->setcodes    = const_cast<uint16_t*>(entry.setcodes.data());
        data->type        = entry.type;
        data->level       = entry.level;
        data->attribute   = entry.attribute;
        data->race        = entry.race;
        data->attack      = entry.attack;
        data->defense     = entry.defense;
        data->lscale      = entry.lscale;
        data->rscale      = entry.rscale;
        data->link_marker = entry.link_marker;
    }
}

void CardDatabase::cardReaderDone(void* /*payload*/, OCG_CardData* /*data*/) {
    // No-op: setcodes point into our persistent map entries, no cleanup needed
}
