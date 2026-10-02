#pragma once

#include <algorithm>
#include <cmath>
#include <limits>
#include <vector>

namespace naviamp::x11 {
struct Motion {
    double value = 0.0;
    std::vector<double> values;
    std::vector<double> times;
    bool repeat = false;

    double at(double elapsedMillis) const {
        if (values.empty()) return value;
        const double duration = times.back();
        double time = std::max(0.0, elapsedMillis);
        if (repeat && duration > 0.0) time = std::fmod(time, duration);
        else time = std::min(time, duration);
        const auto end = std::lower_bound(times.begin(), times.end(), time);
        if (end == times.begin()) return values.front();
        if (end == times.end()) return values.back();
        const size_t index = static_cast<size_t>(end - times.begin());
        const double span = times[index] - times[index - 1];
        const double fraction = span > 0.0 ? (time - times[index - 1]) / span : 0.0;
        return values[index - 1] + (values[index] - values[index - 1]) * fraction;
    }

    bool active(double elapsedMillis) const {
        return !values.empty() && (repeat || elapsedMillis < times.back());
    }

    double untilKeyframe(double elapsedMillis) const {
        if (!active(elapsedMillis)) return std::numeric_limits<double>::infinity();
        const double time = repeat ? std::fmod(std::max(0.0, elapsedMillis), times.back()) : std::max(0.0, elapsedMillis);
        const auto next = std::upper_bound(times.begin(), times.end(), time);
        return next == times.end() ? 1.0 : *next - time;
    }
};

// XMoveWindow/XMoveResizeWindow accept integral geometry. Sleep only while their rounded
// arguments remain identical; Core's keyframes, playback cadence, and interpolation are unchanged.
inline double untilIntegerChange(const Motion& a, const Motion& b, double elapsedMillis) {
    const double span = std::min(a.untilKeyframe(elapsedMillis), b.untilKeyframe(elapsedMillis));
    if (!std::isfinite(span)) return span;
    const double current = a.at(elapsedMillis) - b.at(elapsedMillis);
    const double sampleSpan = std::max(0.0, span - 0.000001);
    const double end = a.at(elapsedMillis + sampleSpan) - b.at(elapsedMillis + sampleSpan);
    if (end == current) return span;
    const double boundary = std::lround(current) + (end > current ? 0.5 : -0.5);
    const double crossing = (boundary - current) * sampleSpan / (end - current);
    return std::min(span, std::max(0.0, crossing));
}

inline double untilVisibilityChange(const Motion& right, const Motion& left, double elapsedMillis) {
    const double span = std::min(right.untilKeyframe(elapsedMillis), left.untilKeyframe(elapsedMillis));
    if (!std::isfinite(span)) return span;
    const double sampleSpan = std::max(0.0, span - 0.000001);
    const double current = right.at(elapsedMillis) - left.at(elapsedMillis);
    const double end = right.at(elapsedMillis + sampleSpan) - left.at(elapsedMillis + sampleSpan);
    if ((current <= 0 && end > 0) || (current > 0 && end <= 0))
        return std::min(span, std::max(0.0, -current * sampleSpan / (end - current)));
    return span;
}

} // namespace naviamp::x11
