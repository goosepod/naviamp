// Exercises only the integer geometry accepted by XMoveWindow/XMoveResizeWindow.
#include "../src/naviamp_x11_geometry_timing.hpp"
using naviamp::x11::Motion;
using naviamp::x11::untilIntegerChange;
using naviamp::x11::untilVisibilityChange;
#undef NDEBUG // Keep assertions enabled in the Release build used by packaging and CI.
#include <cassert>
#include <iostream>

int main() {
    Motion zero;
    assert(std::isinf(untilIntegerChange(zero, zero, 0)));
    Motion marquee;
    marquee.values = {0, 0, -300, -300, 0};
    marquee.times = {0, 2000, 12000, 14000, 24000};
    marquee.repeat = true;
    assert(untilIntegerChange(marquee, zero, 0) == 2000);
    assert(untilIntegerChange(marquee, zero, 12000) == 2000);
    assert(untilIntegerChange(marquee, zero, 2001) < 17);
    Motion reveal;
    reveal.values = {0, 206}; reveal.times = {0, 600000};
    assert(untilIntegerChange(reveal, zero, 0) > 1400);
    assert(untilVisibilityChange(reveal, zero, 0) == 0);
    assert(std::isinf(untilIntegerChange(reveal, zero, 600000)));
    for (double time = 0; time < 72000; time += 7) {
        const double deadline = untilIntegerChange(marquee, zero, time);
        // Every skipped millisecond has identical native coordinates, including reverse motion,
        // holds, negative half-pixel boundaries, and repetition.
        for (double delta = 1; delta < deadline - 0.000001; delta++) {
            if (std::lround(marquee.at(time)) != std::lround(marquee.at(time + delta))) {
                std::cerr << "time=" << time << " delta=" << delta << " deadline=" << deadline << " value=" << marquee.at(time) << " later=" << marquee.at(time+delta) << "\n";
                return 1;
            }
        }
    }
    assert(untilIntegerChange(reveal, reveal, 0) == 600000);
    std::cout << "X11 integer geometry timing passed\n";
}
