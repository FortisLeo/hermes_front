// JNI wrapper around etcpak's ETC2 encoder for the texture cache (see etcpak/README.md for the vendored code).
#include <jni.h>
#include <cstdint>
#include <cstring>
#include <vector>
#include "ProcessRGB.hpp"

namespace {
// Size in bytes of one compressed level: 8 bytes per 4x4 block for RGB8, 16 for RGBA8 (EAC alpha block + RGB block).
inline size_t levelBytes(int w, int h, bool alpha) { return size_t((w + 3) / 4) * size_t((h + 3) / 4) * (alpha ? 16 : 8); }
}

extern "C" JNIEXPORT jint JNICALL
Java_com_rishitgoklani_hermes_1front_1mobile_engine_vrmgl_Etc2Encoder_nativeEncode(
        JNIEnv* env, jclass, jobject src, jint width, jint height, jint stride, jboolean alpha, jobject dst) {
    // src: RGBA8888 rows (Android bitmap memory order), [stride] bytes apart. dst: receives the blocks in the byte order GL expects.
    if (width <= 0 || height <= 0 || stride < width * 4) return -1;
    auto* in = static_cast<const uint8_t*>(env->GetDirectBufferAddress(src));
    auto* out = static_cast<uint8_t*>(env->GetDirectBufferAddress(dst));
    if (in == nullptr || out == nullptr) return -1;
    const bool a = alpha == JNI_TRUE;
    const size_t need = levelBytes(width, height, a);
    if (env->GetDirectBufferCapacity(dst) < jlong(need) || env->GetDirectBufferCapacity(src) < jlong(stride) * height) return -1;

    // etcpak wants BGRA pixels and dimensions that are multiples of 4: swap red/blue and repeat the last row/column into the padding.
    const int pw = (width + 3) & ~3, ph = (height + 3) & ~3;
    std::vector<uint32_t> px(size_t(pw) * ph);
    for (int y = 0; y < ph; y++) {
        const uint8_t* row = in + size_t(y < height ? y : height - 1) * stride;
        uint32_t* o = px.data() + size_t(y) * pw;
        for (int x = 0; x < pw; x++) {
            const uint8_t* p = row + size_t(x < width ? x : width - 1) * 4;
            o[x] = uint32_t(p[2]) | (uint32_t(p[1]) << 8) | (uint32_t(p[0]) << 16) | (uint32_t(p[3]) << 24);
        }
    }
    const uint32_t blocks = uint32_t(pw / 4) * uint32_t(ph / 4);
    if (a) CompressEtc2Rgba(px.data(), reinterpret_cast<uint64_t*>(out), blocks, size_t(pw), true);
    else CompressEtc2Rgb(px.data(), reinterpret_cast<uint64_t*>(out), blocks, size_t(pw), true);
    return jint(need);
}
