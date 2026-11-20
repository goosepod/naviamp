#!/usr/bin/env python3
"""Run Core and direct-BASS tvOS tests in an owned simulator; retain evidence and always clean up."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import signal
import subprocess
import time
import uuid
import xml.etree.ElementTree as ET


REQUIRED_NATIVE_CASES = {
    "directOutputClockMatchesTheNativeCursorAcrossPauseAndResume",
    "publicNativeDecoderUsesSharedWaveformAndNetworkPolicy",
    "publicNativeFileCallbacksReadAndSeekCoreRandomAccessSource",
    "coreEnginePlaysPausesSeeksAndFinishesThroughDirectPublicBass",
}

REQUIRED_STORAGE_NATIVE_CASES = {
    "nativeDriverCreatesCanonicalSchemaAndEnforcesForeignKeys",
    "nativeDriverReopensCommittedRowsInTheHostSelectedDirectory",
    "emptyReplacementDirectoryRebuildsSchemaAfterSimulatedCachePurge",
    "atomicBytesAndBoundedReadUseOnlyTheSelectedNativeDirectory",
    "writeAndDeletionRejectTraversalAndMissingReadsRemainFailures",
    "emptyCacheRecoversMainCompanionSecretsScopesSelectionAndPresetsButNoPlaybackQueue",
    "failedDocumentCommitRollsBackConnectionAndCompanionChanges",
    "oversizedBatchRollsBackSettingsConnectionsAndDjPresetsTogether",
    "certificateBytesForMainAndCompanionRecoverIntoRelocatedCacheAndDeletionStaysDeleted",
    "externalCertificateWithRecoveryBasenameDoesNotReplaceAnotherConnectionsCertificate",
    "sqlCommitFailurePreservesNewAuthorityAndRequiresReloadBeforeAnyFurtherMutation",
    "sqlRollbackFailureAlsoRequiresReloadButNormalPreflightFailureDoesNotWrite",
}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--project", type=Path, default=Path(__file__).resolve().parent.parent)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--gradle-arg", action="append", default=[])
    parser.add_argument("--shared-foundation", action="store_true",
                        help="Also run the shared app/storage/Navidrome/Jellyfin suites")
    args = parser.parse_args()
    root = args.project.resolve()
    out = args.output.resolve()
    if out.exists():
        parser.error("Choose a fresh evidence directory")
    if not (root / "platforms/tvos/build.gradle.kts").is_file():
        parser.error("Project does not contain the opt-in tvOS backend")
    out.mkdir(parents=True)
    commands = []
    report = dict(passed=False, audibilityVerified=False, cleanup={})
    source_paths = ["core/domain/src/commonMain/kotlin/app/naviamp/domain/playback/BassPreviewPlayback.kt",
                    "platforms/apple/src/appleMain/kotlin/app/naviamp/apple/playback/AppleBassPlayback.kt",
                    "platforms/tvos/src/tvosMain/kotlin/app/naviamp/tvos/playback/TvosBassPlayback.kt",
                    "platforms/tvos/src/tvosTest/kotlin/app/naviamp/tvos/playback/TvosBassIntegrationTest.kt",
                    "platforms/tvos/src/tvosTest/kotlin/app/naviamp/tvos/playback/TvosCodecFixtures.kt"]
    modules = ["core/domain", "platforms/tvos"]
    if args.shared_foundation:
        modules += ["core/app", "core/storage", "providers/navidrome", "providers/jellyfin"]
        source_paths += [module + "/build.gradle.kts" for module in modules[2:]]
        source_paths += ["providers/navidrome/src/appleMain/kotlin/app/naviamp/provider/navidrome/" + name
                         for name in ("NavidromeApplePlatform.kt", "NavidromeAppleTlsMaterial.kt")]
        source_paths += ["core/storage/src/" + path for path in (
            "nativeMain/kotlin/app/naviamp/storage/NativeStorageDriverFactory.kt",
            "appleTest/kotlin/app/naviamp/storage/NativeStorageDriverFactoryTest.kt",
            "commonMain/kotlin/app/naviamp/storage/StorageDatabaseLocation.kt",
            "appleTest/kotlin/app/naviamp/storage/AppleStorageDriverTest.kt",
        )]
    report["sourceSha256"] = {path: hashlib.sha256((root / path).read_bytes()).hexdigest() for path in source_paths}
    report["sourceLfSha256"] = {
        path: hashlib.sha256((root / path).read_bytes().replace(b"\r\n", b"\n")).hexdigest()
        for path in source_paths
    }
    device = None
    env = os.environ.copy()

    def run(command, timeout=180, check=True):
        number = len(commands)
        log = out / f"command-{number:02d}.log"
        with log.open("x") as stream:
            result = subprocess.run(command, cwd=root, env=env, stdout=stream,
                                    stderr=subprocess.STDOUT, timeout=timeout)
        commands.append(dict(command=command, returncode=result.returncode, log=log.name))
        (out / "commands.json").write_text(json.dumps(commands, indent=2) + "\n")
        if check and result.returncode:
            raise RuntimeError(f"Command failed ({result.returncode}); see {log}")
        return log.read_text(), result.returncode

    def interrupted(signum, frame):
        raise InterruptedError("Owned tvOS test run interrupted")

    signal.signal(signal.SIGTERM, interrupted)
    try:
        runtimes = json.loads(run(["xcrun", "simctl", "list", "runtimes", "--json"])[0])["runtimes"]
        runtime = max((r for r in runtimes if r["isAvailable"] and "tvOS" in r["name"]),
                      key=lambda r: tuple(int(p) for p in r["version"].split(".")))
        types = json.loads(run(["xcrun", "simctl", "list", "devicetypes", "--json"])[0])["devicetypes"]
        version = sum(int(part) << shift for part, shift in zip(runtime["version"].split("."), (16, 8, 0)))
        kind = next(t for t in types if t["productFamily"] == "Apple TV" and
                    t["minRuntimeVersion"] <= version <= t["maxRuntimeVersion"])
        device = run(["xcrun", "simctl", "create", "Naviamp590Bass-" + uuid.uuid4().hex[:8],
                      kind["identifier"], runtime["identifier"]])[0].strip()
        uuid.UUID(device)
        report.update(device=device, runtime=runtime, type=kind)
        run(["xcrun", "simctl", "boot", device])
        run(["xcrun", "simctl", "bootstatus", device, "-b"], timeout=300)
        frameworks = root / "platforms/tvos/build/bin/tvosSimulatorArm64/debugTest/Frameworks"
        env["DYLD_FRAMEWORK_PATH"] = str(frameworks)
        env["SIMCTL_CHILD_DYLD_FRAMEWORK_PATH"] = str(frameworks)
        init = out / "test-device.init.gradle"
        init.write_text('''gradle.projectsEvaluated {
    ''' + json.dumps([":" + module.replace("/", ":") for module in modules]) + '''.each { path ->
        rootProject.project(path).tasks.named("tvosSimulatorArm64Test").configure {
            device.set(gradle.startParameter.projectProperties["naviamp.tvos.testDevice"])
            standalone.set(false)
            outputs.upToDateWhen { false }
        }
    }
}
''')
        started = time.time()
        _, gradle_status = run([str(root / "gradlew"), "--console=plain", "--max-workers=2",
             "-Dorg.gradle.jvmargs=-Xmx4g -Dfile.encoding=UTF-8", *args.gradle_arg,
             "--init-script", str(init), "-Pnaviamp.tvos.enabled=true",
             "-Pnaviamp.tvos.testDevice=" + device,
             *[":" + module.replace("/", ":") + ":tvosSimulatorArm64Test" for module in modules],
             ":platforms:tvos:verifyTvosBassInventory"], timeout=1800, check=False)
        report["suites"] = {}
        for module in modules:
            files = sorted((root / module / "build/test-results/tvosSimulatorArm64Test").glob("TEST-*.xml"))
            if not files or any(p.stat().st_mtime < started for p in files):
                raise RuntimeError(f"Missing or stale XML results: {module}")
            cases = [case for path in files for case in ET.parse(path).getroot().iter("testcase")]
            if module == "platforms/tvos":
                names = {case.attrib["name"].removesuffix("[tvosSimulatorArm64]") for case in cases}
                if names != REQUIRED_NATIVE_CASES or len(cases) != len(REQUIRED_NATIVE_CASES):
                    raise RuntimeError(f"Native test inventory mismatch: {names}")
            elif module == "core/domain" and len(cases) < 1_100:
                raise RuntimeError("Core test inventory unexpectedly small")
            elif not cases:
                raise RuntimeError(f"Empty shared test inventory: {module}")
            if module == "core/storage":
                names = {case.attrib["name"].removesuffix("[tvosSimulatorArm64]") for case in cases}
                if not REQUIRED_STORAGE_NATIVE_CASES <= names:
                    raise RuntimeError(f"Required native SQLite tests did not execute: {names}")
            report["suites"][module] = dict(cases=len(cases),
                                          failures=sum(c.find("failure") is not None for c in cases),
                                          errors=sum(c.find("error") is not None for c in cases),
                                          skips=sum(c.find("skipped") is not None for c in cases),
                                          names=[case.attrib for case in cases])
            destination = out / module
            destination.mkdir(parents=True)
            for path in files:
                (destination / path.name).write_bytes(path.read_bytes())
            binary = root / module / "build/bin/tvosSimulatorArm64/debugTest/test.kexe"
            report["suites"][module]["binarySha256"] = hashlib.sha256(binary.read_bytes()).hexdigest()
            if module == "platforms/tvos":
                libraries, _ = run(["otool", "-L", str(binary)])
                symbols, _ = run(["nm", "-u", str(binary)])
                if "bassmix" in libraries.lower() or "_BASS_Mixer_" in symbols:
                    raise RuntimeError("Direct tvOS backend unexpectedly links BASSmix")
                report["mixerLinked"] = False
        report["seconds"] = time.time() - started
        if any(hashlib.sha256((root / path).read_bytes()).hexdigest() != digest
               for path, digest in report["sourceSha256"].items()):
            raise RuntimeError("Source changed during native tests")
        if gradle_status or any(suite[key] for suite in report["suites"].values()
                                for key in ("failures", "errors", "skips")):
            raise RuntimeError("Failed or skipped native case; see retained logs and XML")
        report["passed"] = True
    finally:
        if device:
            for action in ("shutdown", "delete"):
                report["cleanup"][action] = run(["xcrun", "simctl", action, device], check=False)[1]
        (out / "summary.json").write_text(json.dumps(report, indent=2) + "\n")
    if not report["passed"] or any(report["cleanup"].values()):
        raise RuntimeError("Native test acceptance or owned simulator cleanup failed")
    print(json.dumps({key: value for key, value in report.items() if key != "suites"}, indent=2))
    print({key: value["cases"] for key, value in report["suites"].items()})


if __name__ == "__main__":
    main()
