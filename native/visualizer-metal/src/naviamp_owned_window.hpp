#pragma once
#import <AppKit/AppKit.h>

// AppKit ownership keeps an overlay immediately above its owner as other apps move in front.
// AWT ownership alone only orders a JDialog above its owner when it is first shown.
inline void attachNaviampOwnedWindow(NSWindow* child, NSWindow* owner) {
    if (!child || !owner || child == owner) return;
    if (child.parentWindow != owner) {
        [child.parentWindow removeChildWindow:child];
        [owner addChildWindow:child ordered:NSWindowAbove];
    }
    child.level = owner.level;
    [child orderWindow:NSWindowAbove relativeTo:owner.windowNumber];
}
