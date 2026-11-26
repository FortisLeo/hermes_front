# etcpak (vendored subset)

ETC2 encoder used to build the compressed texture cache (`ModelCache`). Only the encoder files are copied, unchanged.

- Source: https://github.com/wolfpld/etcpak
- Commit: 3d716e1550023dc5ff8b602636af8b76584e0b66 (2026-09-13)
- License: BSD-3-Clause, see `LICENSE.txt` (Copyright (c) 2013-2026, Bartosz Taudul)
- Files: ProcessRGB.cpp/.hpp, Tables.cpp/.hpp, Dither.cpp/.hpp, ForceInline.hpp, Math.hpp, ProcessCommon.hpp, Vector.hpp

Input pixels are BGRA in memory and width/height must be multiples of 4; `../vrmtex_jni.cpp` swaps channels and pads edges.
