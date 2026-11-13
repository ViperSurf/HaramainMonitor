cmake_minimum_required(VERSION 3.22.1)
project("haramain_audio")

# Google Oboe via Prefab (enabled with buildFeatures.prefab in build.gradle.kts
# and the com.google.oboe:oboe AAR dependency).
find_package(oboe REQUIRED CONFIG)

add_library(haramain_audio SHARED
        AudioEngine.cpp
        native-lib.cpp
        )

target_include_directories(haramain_audio PRIVATE
        ${CMAKE_CURRENT_SOURCE_DIR}
        ${CMAKE_CURRENT_SOURCE_DIR}/dsp
        )

# -O3 for the DSP callback; deliberately NOT -ffast-math so NaN/denormal
# handling in the DSP chain stays predictable.
target_compile_options(haramain_audio PRIVATE -O3 -Wall -Wextra)

target_link_libraries(haramain_audio
        oboe::oboe
        android
        log
        )
