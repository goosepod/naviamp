"""Owned simulator-only audio app: public APIs, muted PCM, bounded session and no user data."""
import json
import math
import os
from pathlib import Path
import plistlib
import struct
import subprocess
import threading
import time
import wave

APPLICATION = 'app.naviamp.tvos.audio-lifecycle-fixture'


def build(root, output):
    app = output/'AudioInterrupter.app'
    app.mkdir()
    sdk = subprocess.check_output(['xcrun', '--sdk', 'appletvsimulator', '--show-sdk-path'], text=True).strip()
    with (output/'interrupter-build.log').open('x') as log:
        subprocess.run(['xcrun', '--sdk', 'appletvsimulator', 'clang', '-target', 'arm64-apple-tvos27.0-simulator',
            '-isysroot', sdk, '-fobjc-arc', str(root/'scripts/fixtures/tvos-audio-interrupter.m'),
            '-framework', 'UIKit', '-framework', 'AVFAudio', '-framework', 'Foundation', '-o', str(app/'AudioInterrupter')],
            stdout=log, stderr=subprocess.STDOUT, timeout=120, check=True)
    (app/'Info.plist').write_bytes(plistlib.dumps(dict(
        CFBundleIdentifier=APPLICATION, CFBundleExecutable='AudioInterrupter', CFBundleName='AudioInterrupter',
        CFBundlePackageType='APPL', CFBundleVersion='1', CFBundleShortVersionString='1.0', MinimumOSVersion='27.0',
        UIDeviceFamily=[3], UILaunchScreen={}, UIApplicationSceneManifest=dict(
            UIApplicationSupportsMultipleScenes=False, UISceneConfigurations=dict(UIWindowSceneSessionRoleApplication=[dict(
                UISceneConfigurationName='Audio fixture', UISceneDelegateClassName='NaviampAudioInterrupterScene')])))))
    with wave.open(str(app/'audio.wav'), 'wb') as audio:
        audio.setnchannels(1); audio.setsampwidth(2); audio.setframerate(16000)
        audio.writeframes(b''.join(struct.pack('<h', int(math.sin(i * 2 * math.pi * 440 / 16000) * 3000)) for i in range(16000)))
    subprocess.run(['codesign', '--force', '--sign', '-', str(app)], capture_output=True, timeout=30, check=True)
    return app


class Coordinator:
    def __init__(self, device, output, token):
        self.device, self.output, self.token = device, output, token
        self.request = output/'system-interruption.request'
        self.stop = threading.Event()
        self.errors = []
        self.completed = False
        self.process = None
        self.thread = threading.Thread(target=self._run, daemon=True)

    def _run(self):
        try:
            while not self.stop.wait(0.05):
                if not self.request.exists(): continue
                if self.request.read_text() != self.token: raise ValueError('Unowned system-interruption request')
                with (self.output/'interrupter-execution.log').open('x') as log:
                    self.process = subprocess.Popen(['xcrun', 'simctl', 'launch', '--console', self.device, APPLICATION],
                        stdin=subprocess.PIPE, stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
                    code = self.process.wait(timeout=25)
                text = (self.output/'interrupter-execution.log').read_text()
                if code or not all(s in text for s in ('OS27 interrupter playing', 'OS27 interrupter released')):
                    raise RuntimeError('System audio fixture did not play/release cleanly: '+str(code))
                self.completed = True
                return
        except Exception as error: self.errors.append(str(error))

    def start(self): self.thread.start()

    def close(self):
        self.stop.set()
        if self.process is not None and self.process.poll() is None:
            subprocess.run(['xcrun', 'simctl', 'terminate', self.device, APPLICATION], capture_output=True, timeout=15)
            try: self.process.wait(timeout=10)
            except subprocess.TimeoutExpired:
                import signal
                os.killpg(self.process.pid, signal.SIGKILL); self.process.wait(timeout=5)
        self.thread.join(timeout=30)
        report = dict(passed=self.completed and not self.errors and not self.thread.is_alive(),
                      completed=self.completed, errors=self.errors)
        (self.output/'system-interruption.json').write_text(json.dumps(report, indent=2)+'\n')
        return report
