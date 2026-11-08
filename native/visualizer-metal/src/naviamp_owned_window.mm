#include <jni.h>
#include "naviamp_owned_window.hpp"
#import <CoreFoundation/CoreFoundation.h>

extern "C" JNIEXPORT void JNICALL Java_app_naviamp_ui_DesktopOwnedWindowNative_attach(
    JNIEnv*, jobject, jlong childHandle, jlong ownerHandle) {
    // Skiko publishes NSWindow pointers. Retain both resources before crossing into AppKit.
    NSWindow* child = (__bridge NSWindow*)reinterpret_cast<void*>(childHandle);
    NSWindow* owner = (__bridge NSWindow*)reinterpret_cast<void*>(ownerHandle);
    if (!child || !owner) return;
    void (^work)(void) = ^{
        // A popup may have been disposed while this operation waited for the AppKit run loop.
        if (child.visible && owner.visible) attachNaviampOwnedWindow(child, owner);
    };
    // Never synchronously wait for AppKit from AWT: accessibility can wait in the other direction.
    if ([NSThread isMainThread]) work();
    else {
        CFRunLoopPerformBlock(CFRunLoopGetMain(), kCFRunLoopCommonModes, work);
        CFRunLoopWakeUp(CFRunLoopGetMain());
    }
}
