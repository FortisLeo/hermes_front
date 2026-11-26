# Native part of the VRM renderer: the ETC2 texture encoder (vendored etcpak) behind a small JNI wrapper.
cmake_minimum_required(VERSION 3.22.1)
project(vrmtex CXX)
set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
add_library(vrmtex SHARED
    vrmtex_jni.cpp
    etcpak/ProcessRGB.cpp
    etcpak/Tables.cpp
    etcpak/Dither.cpp)
target_include_directories(vrmtex PRIVATE etcpak)
# Fast even in debug builds: encoding runs in the background but should not take seconds.
target_compile_options(vrmtex PRIVATE -O3 -fno-exceptions -fno-rtti -Wno-unused-function -Wno-unused-variable)
target_link_options(vrmtex PRIVATE "-Wl,-z,max-page-size=16384")
