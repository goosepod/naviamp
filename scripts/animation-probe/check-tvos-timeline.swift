import CoreGraphics
import Foundation
import ImageIO

// Coordinates are screenshot pixels for the timeline alone, between the two time labels.
// Pass a second capture of the same region to verify that the raster actually moves.
let arguments = Array(CommandLine.arguments.dropFirst())

func fail(_ message: String) -> Never {
    FileHandle.standardError.write(Data((message + "\n").utf8))
    exit(1)
}

guard (5...6).contains(arguments.count) else {
    fail("Usage: swift check-tvos-timeline.swift screenshot.png x y width height [later.png]")
}
let coordinates = arguments[1...4].compactMap(Int.init)
guard coordinates.count == 4, coordinates[0] >= 0, coordinates[1] >= 0,
      coordinates[2] >= 32, coordinates[3] >= 16 else { fail("Invalid pixel rectangle") }
let width = coordinates[2]
let height = coordinates[3]
let region = CGRect(x: coordinates[0], y: coordinates[1], width: width, height: height)

func timelineProfile(_ path: String) -> [Int] {
    guard let source = CGImageSourceCreateWithURL(URL(fileURLWithPath: path) as CFURL, nil),
          let image = CGImageSourceCreateImageAtIndex(source, 0, nil),
          CGRect(x: 0, y: 0, width: image.width, height: image.height).contains(region),
          let crop = image.cropping(to: region) else { fail("Could not read the timeline rectangle in \(path)") }
    var pixels = [UInt8](repeating: 0, count: width * height * 4)
    pixels.withUnsafeMutableBytes { bytes in
        guard let context = CGContext(data: bytes.baseAddress, width: width, height: height,
                                     bitsPerComponent: 8, bytesPerRow: width * 4,
                                     space: CGColorSpaceCreateDeviceRGB(),
                                     bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue) else {
            fail("Could not read the screenshot pixels")
        }
        context.draw(crop, in: CGRect(x: 0, y: 0, width: width, height: height))
    }
    func channel(_ x: Int, _ y: Int, _ c: Int) -> Int { Int(pixels[(y * width + x) * 4 + c]) }
    return stride(from: 8, to: width - 8, by: 4).map { x in
        var strongest = 0
        for y in (height / 8)..<(height - height / 8) {
            // Subtract the local background gradient before looking for the painted raster.
            let contrast = (0..<3).reduce(0) { sum, c in
                let background = (channel(x, 0, c) * (height - 1 - y) + channel(x, height - 1, c) * y) / (height - 1)
                return sum + abs(channel(x, y, c) - background)
            }
            strongest = max(strongest, contrast)
        }
        return strongest
    }
}

func assertVisible(_ profile: [Int]) {
    let midpoint = profile.count / 2
    for half in [profile[..<midpoint], profile[midpoint...]] {
        let painted = half.filter { $0 >= 24 }.count
        print("Painted timeline columns \(painted)/\(half.count)")
        guard painted * 2 >= half.count else { fail("The timeline does not paint across both halves") }
    }
}

let first = timelineProfile(arguments[0])
assertVisible(first)
if arguments.count == 6 {
    let second = timelineProfile(arguments[5])
    assertVisible(second)
    let changed = zip(first, second).filter { abs($0.0 - $0.1) >= 24 }.count
    print("Moved timeline columns \(changed)/\(first.count)")
    guard changed >= 4 else { fail("The timeline did not visibly advance between captures") }
}
