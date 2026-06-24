cmake_minimum_required(VERSION 3.22)

project(naviamp_visualizer_metal LANGUAGES CXX OBJCXX)

find_package(JNI REQUIRED)

add_library(naviamp_visualizer_metal SHARED
    src/naviamp_visualizer_metal.mm
)

target_compile_features(naviamp_visualizer_metal PRIVATE cxx_std_17)

target_include_directories(naviamp_visualizer_metal PRIVATE ${JNI_INCLUDE_DIRS})

target_compile_options(naviamp_visualizer_metal PRIVATE
    "-fobjc-arc"
)

target_link_libraries(naviamp_visualizer_metal PRIVATE
    ${JNI_LIBRARIES}
    "-framework AppKit"
    "-framework Metal"
    "-framework QuartzCore"
)

set_target_properties(naviamp_visualizer_metal PROPERTIES
    OUTPUT_NAME "naviamp_visualizer_metal"
    BUILD_RPATH "@loader_path"
    INSTALL_RPATH "@loader_path"
)
