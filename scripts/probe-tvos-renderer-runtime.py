#!/usr/bin/env python3
"""Launch #590's shared synthetic renderer with actual Compose resource sync in an owned simulator."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import plistlib
import re
import signal
import subprocess
import time
import uuid


def numeric_version(value):
    if not re.fullmatch(r"\d+\.\d+(?:\.\d+)?", value):
        raise ValueError(f"Invalid OS version: {value}")
    parts = tuple(int(part) for part in value.split("."))
    return parts + (0,) * (3 - len(parts))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--consumer", type=Path, required=True)
    parser.add_argument("--minimum-os", required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    if not re.fullmatch(r"\d+\.\d+(?:\.\d+)?", args.minimum_os):
        parser.error("Supply a numeric probe deployment target; this does not establish app support")
    consumer = args.consumer.resolve(strict=True)
    out = args.output.resolve()
    if out.exists():
        parser.error("Choose a fresh evidence directory")
    framework = consumer / "build/bin/tvosSimulatorArm64/debugFramework/NaviampRendererProbe.framework"
    binary = framework / "NaviampRendererProbe"
    digest = hashlib.sha256(binary.read_bytes()).hexdigest()
    out.mkdir(parents=True)
    app = out / "RendererProbe.app"
    app.mkdir()
    identifier = "app.naviamp.feasibility.rendererdeployment"
    info = dict(CFBundleExecutable="RendererProbe", CFBundleIdentifier=identifier,
                CFBundleName="RendererProbe", CFBundlePackageType="APPL", CFBundleVersion="1",
                CFBundleShortVersionString="1.0", CFBundleSupportedPlatforms=["AppleTVSimulator"],
                LSRequiresIPhoneOS=True, MinimumOSVersion=args.minimum_os, UIDeviceFamily=[3], UILaunchScreen={},
                UIApplicationSceneManifest=dict(UIApplicationSupportsMultipleScenes=False,
                    UISceneConfigurations={"UIWindowSceneSessionRoleApplication": [dict(
                        UISceneConfigurationName="Probe", UISceneDelegateClassName="RendererSceneDelegate")]}))
    (app / "Info.plist").write_bytes(plistlib.dumps(info))
    source = out / "RendererProbe.m"
    source.write_text('''#import <UIKit/UIKit.h>
#import <NaviampRendererProbe/NaviampRendererProbe.h>
@interface RendererSceneDelegate : UIResponder <UIWindowSceneDelegate>
@property(strong, nonatomic) UIWindow *window;
@end
@implementation RendererSceneDelegate
- (void)scene:(UIScene *)scene willConnectToSession:(UISceneSession *)session options:(UISceneConnectionOptions *)options {
    self.window = [[UIWindow alloc] initWithWindowScene:(UIWindowScene *)scene];
    self.window.rootViewController = [NRPRendererControllerKt MainViewController];
    [self.window makeKeyAndVisible];
}
@end
@interface RendererAppDelegate : UIResponder <UIApplicationDelegate>
@end
@implementation RendererAppDelegate
- (BOOL)application:(UIApplication *)application didFinishLaunchingWithOptions:(NSDictionary *)options { return YES; }
@end
int main(int argc, char **argv) {
    @autoreleasepool { return UIApplicationMain(argc, argv, nil, NSStringFromClass(RendererAppDelegate.class)); }
}
''')
    env = os.environ.copy()
    env.update(BUILT_PRODUCTS_DIR=str(out), UNLOCALIZED_RESOURCES_FOLDER_PATH=app.name,
               PLATFORM_NAME="appletvsimulator", ARCHS="arm64", CONFIGURATION="Debug")
    commands = []
    device = None
    report = dict(passed=False, screenshotInspected=False, minimumOs=args.minimum_os,
                  frameworkSha256=digest, cleanup={})

    def run(command, timeout=180, check=True):
        log = out / f"command-{len(commands):02d}.log"
        with log.open("x") as stream:
            result = subprocess.run(command, cwd=consumer, env=env, stdout=stream,
                                    stderr=subprocess.STDOUT, timeout=timeout)
        commands.append(dict(command=command, exit=result.returncode, log=log.name))
        (out / "commands.json").write_text(json.dumps(commands, indent=2) + "\n")
        if check and result.returncode:
            raise RuntimeError(f"Command failed: see {log}")
        return log.read_text(), result.returncode

    def interrupted(signum, frame):
        raise InterruptedError("Owned renderer probe interrupted")

    signal.signal(signal.SIGTERM, interrupted)
    try:
        run([str(consumer / "gradlew"), "--console=plain", "--no-configuration-cache",
             "--no-configure-on-demand", "--max-workers=2", "syncComposeResourcesForIos"], timeout=600)
        resources = list((app / "compose-resources").rglob("*.cvr"))
        if not resources:
            raise RuntimeError("Actual Compose resource sync did not produce the string bundle")
        report["resourceFiles"] = [str(path.relative_to(app)) for path in resources]
        sdk = run(["xcrun", "--sdk", "appletvsimulator", "--show-sdk-path"])[0].strip()
        command = ["xcrun", "--sdk", "appletvsimulator", "clang", "-fobjc-arc", "-target",
                   "arm64-apple-tvos" + args.minimum_os + "-simulator", "-isysroot", sdk,
                   str(source), "-F" + str(framework.parent), "-framework", "NaviampRendererProbe"]
        for name in ("UIKit", "Foundation", "Metal", "QuartzCore", "CoreGraphics", "CoreText", "ImageIO", "GameController"):
            command += ["-framework", name]
        command += ["-lc++", "-liconv", "-lz", "-o", str(app / "RendererProbe")]
        output, _ = run(command)
        if "built for newer" in output:
            raise RuntimeError("The final host still links an object above its declared deployment floor")
        run(["xcrun", "vtool", "-arch", "arm64", "-show-build", str(app / "RendererProbe")])
        run(["codesign", "--force", "--sign", "-", str(app)])
        runtimes = json.loads(run(["xcrun", "simctl", "list", "runtimes", "--json"])[0])["runtimes"]
        minimum = numeric_version(args.minimum_os)
        runtime = max((r for r in runtimes if r["isAvailable"] and "tvOS" in r["name"] and
                       numeric_version(r["version"]) >= minimum),
                      key=lambda r: numeric_version(r["version"]))
        types = json.loads(run(["xcrun", "simctl", "list", "devicetypes", "--json"])[0])["devicetypes"]
        number = sum(int(p) << shift for p, shift in zip(runtime["version"].split("."), (16, 8, 0)))
        kind = next(t for t in types if t["productFamily"] == "Apple TV" and
                    t["minRuntimeVersion"] <= number <= t["maxRuntimeVersion"])
        device = run(["xcrun", "simctl", "create", "Naviamp590Renderer-" + uuid.uuid4().hex[:8],
                      kind["identifier"], runtime["identifier"]])[0].strip()
        uuid.UUID(device)
        report.update(device=device, runtime=runtime)
        run(["xcrun", "simctl", "boot", device])
        run(["xcrun", "simctl", "bootstatus", device, "-b"], timeout=300)
        run(["xcrun", "simctl", "install", device, str(app)])
        run(["xcrun", "simctl", "launch", device, identifier])
        time.sleep(4)
        processes = run(["xcrun", "simctl", "spawn", device, "launchctl", "list"])[0]
        if identifier not in processes:
            raise RuntimeError("Renderer process did not remain alive")
        run(["xcrun", "simctl", "io", device, "screenshot", str(out / "renderer.png")])
        if hashlib.sha256(binary.read_bytes()).hexdigest() != digest:
            raise RuntimeError("Framework changed during execution")
        report["passed"] = True
    finally:
        if device:
            for action in ("shutdown", "delete"):
                report["cleanup"][action] = run(["xcrun", "simctl", action, device], check=False)[1]
        (out / "summary.json").write_text(json.dumps(report, indent=2) + "\n")
    if not report["passed"] or any(report["cleanup"].values()):
        raise RuntimeError("Renderer probe or owned simulator cleanup failed")
    print(json.dumps({key: value for key, value in report.items() if key != "runtime"}, indent=2))


if __name__ == "__main__":
    main()
