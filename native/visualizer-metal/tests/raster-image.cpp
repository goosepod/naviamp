#include "../src/naviamp_raster_image.hpp"
#include <cstdio>
#include <cstdlib>

static void require(bool condition, const char* message) {
    if (!condition) { std::fprintf(stderr, "%s\n", message); std::exit(1); }
}

int main() {
    require(naviampCreatePremultipliedRasterImage(nullptr) == nullptr, "Null image must be rejected");
    // The shared bytes are sRGB even if a decoder supplies another color-space tag. Include
    // opaque color and transparent nonzero RGB to catch conversion instead of retagging.
    const unsigned char bytes[] = {255, 0, 0, 128, 12, 34, 56, 255, 255, 128, 64, 0,
        255, 0, 0, 1, 0, 255, 0, 254};
    CGColorSpaceRef sourceSpace = CGColorSpaceCreateWithName(kCGColorSpaceDisplayP3);
    CGDataProviderRef provider = CGDataProviderCreateWithData(nullptr, bytes, sizeof(bytes), nullptr);
    CGImageRef source = CGImageCreate(5, 1, 8, 32, 20, sourceSpace,
        kCGImageAlphaLast | kCGBitmapByteOrder32Big, provider, nullptr, false, kCGRenderingIntentDefault);
    CGImageRef result = naviampCreatePremultipliedRasterImage(source);
    require(result != nullptr, "Raster normalization failed");
    require(CGImageGetWidth(result) == 5 && CGImageGetHeight(result) == 1, "Image dimensions changed");
    require(CGImageGetAlphaInfo(result) == kCGImageAlphaPremultipliedLast, "Contents must be premultiplied");
    CGColorSpaceRef space = CGColorSpaceCreateWithName(kCGColorSpaceSRGB);
    require(CFEqual(CGImageGetColorSpace(result), space), "Contents must use named sRGB");
    CFDataRef data = CGDataProviderCopyData(CGImageGetDataProvider(result));
    const auto pixels = CFDataGetBytePtr(data);
    const unsigned char expected[] = {128, 0, 0, 128, 12, 34, 56, 255, 0, 0, 0, 0,
        1, 0, 0, 1, 0, 254, 0, 254};
    for (size_t i = 0; i < sizeof(expected); ++i)
        require(pixels[i] == expected[i], "Raster bytes changed beyond alpha premultiplication");
    CFRelease(data);
    CGColorSpaceRelease(space);
    CGImageRelease(result);
    CGImageRelease(source);
    CGDataProviderRelease(provider);
    CGColorSpaceRelease(sourceSpace);
    std::puts("RASTER_IMAGE_TEST passed: profile, alpha, bytes and dimensions");
}
