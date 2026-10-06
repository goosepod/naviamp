import AppKit

// Validate screen ownership before a physical Robot capture. Focus is not visibility.
let args = CommandLine.arguments
guard (args.count == 3 && args[2] == "--rectangles") || args.count == 6,
      let pid = Int(args[1]), pid > 0 else {
    print("Usage: guard PID --rectangles | PID X Y WIDTH HEIGHT")
    exit(2)
}
let windows = CGWindowListCopyWindowInfo([.optionOnScreenOnly, .excludeDesktopElements], kCGNullWindowID) as? [[String: Any]] ?? []
if args[2] == "--rectangles" {
    let owned = windows.filter { ($0[kCGWindowOwnerPID as String] as? Int) == pid && ($0[kCGWindowLayer as String] as? Int) == 0 }
        .sorted { ($0[kCGWindowName as String] as? String == "Naviamp" ? 0 : 1) < ($1[kCGWindowName as String] as? String == "Naviamp" ? 0 : 1) }
    for window in owned {
        guard let bounds = window[kCGWindowBounds as String] as? NSDictionary,
              let rect = CGRect(dictionaryRepresentation: bounds), rect.width > 24, rect.height > 24 else { continue }
        print("\(Int(rect.minX)),\(Int(rect.minY)),\(Int(rect.width)),\(Int(rect.height))")
    }
    exit(owned.isEmpty ? 2 : 0)
}
guard let x = Double(args[2]), let y = Double(args[3]),
      let width = Double(args[4]), let height = Double(args[5]),
      [x, y, width, height].allSatisfy({ $0.isFinite }), width > 0, height > 0 else {
    print("Rejected: invalid test rectangle")
    exit(2)
}
let rect = CGRect(x: x, y: y, width: width, height: height)
// Walk front-to-back and cover every part of the client rectangle, not just sample points.
func subtract(_ source: CGRect, _ cover: CGRect) -> [CGRect] {
    let overlap = source.intersection(cover)
    if overlap.isNull || overlap.isEmpty { return [source] }
    return [
        CGRect(x: source.minX, y: source.minY, width: source.width, height: overlap.minY - source.minY),
        CGRect(x: source.minX, y: overlap.maxY, width: source.width, height: source.maxY - overlap.maxY),
        CGRect(x: source.minX, y: overlap.minY, width: overlap.minX - source.minX, height: overlap.height),
        CGRect(x: overlap.maxX, y: overlap.minY, width: source.maxX - overlap.maxX, height: overlap.height)
    ].filter { !$0.isEmpty }
}
var uncovered = [rect]
for window in windows {
    guard let bounds = window[kCGWindowBounds as String] as? NSDictionary,
          let windowRect = CGRect(dictionaryRepresentation: bounds),
          (window[kCGWindowAlpha as String] as? Double ?? 1) > 0,
          (window[kCGWindowLayer as String] as? Int ?? 0) >= 0 else { continue }
    let intersects = uncovered.contains { !$0.intersection(windowRect).isNull && !$0.intersection(windowRect).isEmpty }
    if !intersects { continue }
    if window[kCGWindowOwnerPID as String] as? Int != pid {
        print("Rejected: test rectangle is obscured")
        exit(2)
    }
    uncovered = uncovered.flatMap { subtract($0, windowRect) }
    if uncovered.isEmpty { print("Visible test-owned rectangle"); exit(0) }
}
print("Rejected: test rectangle is off screen")
exit(2)
