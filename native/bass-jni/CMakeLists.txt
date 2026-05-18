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

find_package(JNI REQUIRED)
target_link_libraries(naviamp_bass PRIVATE JNI::JNI)

set_target_properties(naviamp_bass PROPERTIES
    OUTPUT_NAME "naviamp_bass"
)
