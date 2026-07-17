cmake_minimum_required(VERSION 3.22.1)

project(tripflyover LANGUAGES CXX)

# Native Vulkan renderer for the trip flyover. libtripflyover.so is loaded
# opportunistically by the Kotlin side (TripFlyoverNative / TripFlyoverController);
# when it is absent for an ABI, or Vulkan init fails at runtime, the app falls
# back to the 2D Compose renderer — the native layer never crashes the HOME
# launcher.
#
# SPIR-V shaders are precompiled and committed as C headers under generated/
# (regenerate with the NDK's glslc — see shaders/README.md). The build therefore
# has no glslc dependency, so CI needs only the pinned NDK + CMake.

add_library(tripflyover SHARED
        flyover_renderer.cpp
        jni_bridge.cpp)

target_compile_features(tripflyover PRIVATE cxx_std_17)
target_compile_options(tripflyover PRIVATE -Wall -Wextra)

target_include_directories(tripflyover PRIVATE
        ${CMAKE_CURRENT_SOURCE_DIR}
        ${CMAKE_CURRENT_SOURCE_DIR}/generated)

find_library(vulkan-lib vulkan)
find_library(android-lib android)
find_library(log-lib log)

target_link_libraries(tripflyover
        ${vulkan-lib}
        ${android-lib}
        ${log-lib})
