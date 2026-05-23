cmake_minimum_required(VERSION 3.22)

project(naviamp_bass_jni LANGUAGES CXX)

add_library(naviamp_bass SHARED
    src/naviamp_bass_jni.cpp
)

target_compile_features(naviamp_bass PRIVATE cxx_std_17)

target_include_directories(naviamp_bass
    PRIVATE
        include
        vendor/include
)

if(ANDROID)
    target_link_options(naviamp_bass PRIVATE
        "-Wl,-z,max-page-size=16384"
        "-Wl,-z,common-page-size=16384"
    )

    add_library(bass SHARED IMPORTED)
    set_target_properties(bass PROPERTIES
        IMPORTED_LOCATION "${CMAKE_CURRENT_SOURCE_DIR}/vendor/android/${ANDROID_ABI}/libbass.so"
    )
    add_library(bassmix SHARED IMPORTED)
    set_target_properties(bassmix PROPERTIES
        IMPORTED_LOCATION "${CMAKE_CURRENT_SOURCE_DIR}/vendor/android/${ANDROID_ABI}/libbassmix.so"
    )
    target_link_libraries(naviamp_bass PRIVATE bass bassmix log)
else()
    find_package(JNI REQUIRED)
    set(BASS_LIBRARY_DIR "" CACHE PATH "Directory containing the platform BASS library")
    if(WIN32)
        set(BASS_LIBRARY_NAME "bass.dll")
    elseif(APPLE)
        set(BASS_LIBRARY_NAME "libbass.dylib")
    else()
        set(BASS_LIBRARY_NAME "libbass.so")
    endif()
    set(BASS_LIBRARY_PATH "${BASS_LIBRARY_DIR}/${BASS_LIBRARY_NAME}")
    if(NOT EXISTS "${BASS_LIBRARY_PATH}")
        message(FATAL_ERROR "BASS library not found at ${BASS_LIBRARY_PATH}")
    endif()
    if(WIN32)
        set(BASSMIX_LIBRARY_NAME "bassmix.dll")
    elseif(APPLE)
        set(BASSMIX_LIBRARY_NAME "libbassmix.dylib")
    else()
        set(BASSMIX_LIBRARY_NAME "libbassmix.so")
    endif()
    set(BASSMIX_LIBRARY_PATH "${BASS_LIBRARY_DIR}/${BASSMIX_LIBRARY_NAME}")
    if(NOT EXISTS "${BASSMIX_LIBRARY_PATH}")
        message(FATAL_ERROR "BASSmix library not found at ${BASSMIX_LIBRARY_PATH}")
    endif()
    target_link_libraries(naviamp_bass PRIVATE JNI::JNI "${BASS_LIBRARY_PATH}" "${BASSMIX_LIBRARY_PATH}")
    if(APPLE)
        set_target_properties(naviamp_bass PROPERTIES
            BUILD_RPATH "@loader_path"
            INSTALL_RPATH "@loader_path"
        )
    endif()
endif()

set_target_properties(naviamp_bass PROPERTIES
    OUTPUT_NAME "naviamp_bass"
)
