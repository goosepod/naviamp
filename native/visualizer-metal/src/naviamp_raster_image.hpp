#pragma once
#include <CoreGraphics/CoreGraphics.h>

// Core supplies sRGB bytes; normalize PNG decoder output to premultiplied named-sRGB contents.
// Caller owns the result. Core Animation retains it; the frame loop never repeats conversion.
inline CGImageRef naviampCreatePremultipliedRasterImage(CGImageRef source) {
    if (!source) return nullptr;
    const size_t width = CGImageGetWidth(source), height = CGImageGetHeight(source);
    CGColorSpaceRef space = CGColorSpaceCreateWithName(kCGColorSpaceSRGB);
    if (!space) return nullptr;
    CGImageRef pixels = CGImageCreateCopyWithColorSpace(source, space);
    CGContextRef context = pixels ? CGBitmapContextCreate(nullptr, width, height, 8, width * 4, space,
        kCGImageAlphaPremultipliedLast | kCGBitmapByteOrder32Big) : nullptr;
    CGColorSpaceRelease(space);
    if (!context) { if (pixels) CGImageRelease(pixels); return nullptr; }
    CGContextSetBlendMode(context, kCGBlendModeCopy);
    CGContextDrawImage(context, CGRectMake(0, 0, width, height), pixels);
    CGImageRelease(pixels);
    CGImageRef image = CGBitmapContextCreateImage(context);
    CGContextRelease(context);
    return image;
}
