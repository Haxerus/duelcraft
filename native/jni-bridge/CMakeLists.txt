cmake_minimum_required(VERSION 3.20)
project(duelcraft_jni CXX C)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# JNI
find_package(JNI REQUIRED)

# SQLite (amalgamation, built as part of this project)
add_library(sqlite3 STATIC deps/sqlite3.c)
target_include_directories(sqlite3 PUBLIC deps/)

# ocgcore (pre-built shared library from the ygopro-core submodule)
set(OCGCORE_DIR "${CMAKE_SOURCE_DIR}/../ygopro-core")

add_library(ocgcore SHARED IMPORTED)
if(WIN32)
    set_target_properties(ocgcore PROPERTIES
        IMPORTED_IMPLIB "${OCGCORE_DIR}/bin/x64/release/ocgcore.lib"
        IMPORTED_LOCATION "${OCGCORE_DIR}/bin/x64/release/ocgcore.dll"
    )
else()
    set_target_properties(ocgcore PROPERTIES
        IMPORTED_LOCATION "${OCGCORE_DIR}/bin/release/libocgcore.so"
    )
endif()

# Bridge library
add_library(duelcraft_jni SHARED
    src/duel_engine.cpp
    src/card_database.cpp
    src/script_provider.cpp
    src/jni_interface.cpp
)
target_include_directories(duelcraft_jni PRIVATE
    include/
    "${OCGCORE_DIR}/"
    ${JNI_INCLUDE_DIRS}
)
target_link_libraries(duelcraft_jni PRIVATE ocgcore sqlite3)
