cmake_minimum_required(VERSION 3.22)

project(naviamp_visualizer_opengl LANGUAGES CXX)

find_package(JNI REQUIRED)

add_library(naviamp_visualizer_opengl SHARED
    src/naviamp_visualizer_opengl.cpp
)

target_compile_features(naviamp_visualizer_opengl PRIVATE cxx_std_17)
target_include_directories(naviamp_visualizer_opengl PRIVATE ${JNI_INCLUDE_DIRS})

if (WIN32)
    target_link_libraries(naviamp_visualizer_opengl PRIVATE ${JNI_LIBRARIES} opengl32 gdi32 user32)
    set(JAWT_LIBRARY_PATH "$ENV{JAVA_HOME}/lib/jawt.lib")
    if(EXISTS "${JAWT_LIBRARY_PATH}")
        target_link_libraries(naviamp_visualizer_opengl PRIVATE "${JAWT_LIBRARY_PATH}")
    endif()
else()
    message(FATAL_ERROR "naviamp_visualizer_opengl currently supports Windows only")
endif()

set_target_properties(naviamp_visualizer_opengl PROPERTIES
    OUTPUT_NAME "naviamp_visualizer_opengl"
)
