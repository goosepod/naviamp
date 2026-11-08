cmake_minimum_required(VERSION 3.22)

project(naviamp_visualizer_metal LANGUAGES CXX OBJCXX)

find_package(JNI REQUIRED)

add_library(naviamp_visualizer_metal SHARED
    src/naviamp_visualizer_metal.mm
    src/naviamp_raster_compositor.mm
    src/naviamp_owned_window.mm
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

include(CTest)
if(BUILD_TESTING)
    add_executable(naviamp_raster_image_test tests/raster-image.cpp)
    target_compile_features(naviamp_raster_image_test PRIVATE cxx_std_17)
    target_link_libraries(naviamp_raster_image_test PRIVATE "-framework CoreGraphics" "-framework CoreFoundation")
    add_test(NAME macos_raster_image COMMAND naviamp_raster_image_test)
    add_executable(naviamp_owned_window_test tests/owned-window.mm)
    target_compile_options(naviamp_owned_window_test PRIVATE "-fobjc-arc" "-UNDEBUG")
    target_link_libraries(naviamp_owned_window_test PRIVATE "-framework AppKit")
    add_test(NAME macos_owned_window COMMAND naviamp_owned_window_test)
endif()
