#include "../src/naviamp_owned_window.hpp"
#include <cassert>

int main() {
    @autoreleasepool {
        [NSApplication sharedApplication];
        auto makeWindow = [] {
            NSWindow* window = [[NSWindow alloc] initWithContentRect:NSMakeRect(0, 0, 100, 100)
                styleMask:NSWindowStyleMaskBorderless backing:NSBackingStoreBuffered defer:NO];
            window.releasedWhenClosed = NO;
            return window;
        };
        NSWindow* owner = makeWindow();
        NSWindow* child = makeWindow();
        NSWindow* nested = makeWindow();
        NSWindow* unrelated = makeWindow();
        [owner orderFront:nil];
        [unrelated orderFront:nil];
        [child orderFront:nil];
        child.level = NSFloatingWindowLevel;
        unrelated.level = NSNormalWindowLevel;
        attachNaviampOwnedWindow(child, owner);
        attachNaviampOwnedWindow(nested, child);
        attachNaviampOwnedWindow(child, owner); // Idempotent: no duplicate child relationship.
        assert(child.parentWindow == owner);
        assert(nested.parentWindow == child);
        assert(owner.childWindows.count == 1);
        assert(child.level == owner.level);
        assert(nested.level == owner.level);
        assert(unrelated.parentWindow == nil);
        assert(unrelated.level == NSNormalWindowLevel);
        // The child was previously above an unrelated window. It must join its owner's stack.
        NSArray<NSWindow*>* ordered = NSApp.orderedWindows;
        assert([ordered indexOfObject:child] < [ordered indexOfObject:owner]);
        assert([ordered indexOfObject:unrelated] < [ordered indexOfObject:child]);
        [child close];
        assert(child.parentWindow == nil);
        [nested orderOut:nil]; [child orderOut:nil]; [owner orderOut:nil]; [unrelated orderOut:nil];
    }
}
