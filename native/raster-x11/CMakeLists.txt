cmake_minimum_required(VERSION 3.22)

project(naviamp_raster_x11 LANGUAGES CXX)

find_package(JNI REQUIRED)
find_package(Threads REQUIRED)
find_package(X11 REQUIRED COMPONENTS Xrender)

add_library(naviamp_raster_x11 SHARED
    src/naviamp_raster_x11.cpp
)

target_compile_features(naviamp_raster_x11 PRIVATE cxx_std_17)
target_include_directories(naviamp_raster_x11 PRIVATE ${JNI_INCLUDE_DIRS} ${X11_INCLUDE_DIR} ${X11_Xrender_INCLUDE_PATH})
target_link_libraries(naviamp_raster_x11 PRIVATE ${JNI_LIBRARIES} ${X11_LIBRARIES} ${X11_Xrender_LIB} Threads::Threads)

set_target_properties(naviamp_raster_x11 PROPERTIES
    OUTPUT_NAME "naviamp_raster_x11"
)

include(CTest)
if(BUILD_TESTING)
    add_executable(naviamp_x11_geometry_timing tests/geometry-timing.cpp)
    target_compile_features(naviamp_x11_geometry_timing PRIVATE cxx_std_17)
    add_test(NAME x11_geometry_timing COMMAND naviamp_x11_geometry_timing)
endif()
