import os
from pathlib import Path
import subprocess
import tempfile
import unittest

SCRIPT = Path(__file__).resolve().parents[1] / 'install-android-sdk-packages.sh'

FAKE_SDKMANAGER = '''#!/usr/bin/env python3
import os, sys
from pathlib import Path
calls = Path(os.environ['CALLS'])
count = len(calls.read_text().splitlines()) if calls.exists() else 0
with calls.open('a') as log: log.write(' '.join(sys.argv[1:]) + '\\n')
outcome = os.environ['OUTCOMES'].split(',')[min(count, len(os.environ['OUTCOMES'].split(',')) - 1)]
if outcome == 'corrupt-exit':
    print('Warning: An error occurred while preparing SDK package NDK: Error reading Zip content from a SeekableByteChannel.')
    sys.exit(1)
if outcome == 'corrupt-zero':
    print('Warning: An error occurred while preparing SDK package Android Emulator: Error reading Zip content.')
    sys.exit(0)
print('[=======================================] 100% Unzipping...')
'''


class AndroidSdkInstallTest(unittest.TestCase):
    def run_install(self, outcomes):
        directory = tempfile.TemporaryDirectory()
        self.addCleanup(directory.cleanup)
        root = Path(directory.name)
        sdkmanager = root / 'sdkmanager'
        sdkmanager.write_text(FAKE_SDKMANAGER)
        sdkmanager.chmod(0o755)
        calls, log = root / 'calls', root / 'install.log'
        env = dict(os.environ, PATH=f'{root}:{os.environ["PATH"]}', CALLS=str(calls),
                   OUTCOMES=outcomes, NAVIAMP_SDK_INSTALL_RETRY_DELAY='0')
        result = subprocess.run(['bash', str(SCRIPT), '--log', str(log), 'ndk;27.0.12077973', 'emulator'],
                                env=env, capture_output=True, text=True, timeout=30)
        return result, calls.read_text().splitlines(), log.read_text()

    def test_clean_install_runs_once(self):
        result, calls, log = self.run_install('ok')
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        self.assertEqual(['ndk;27.0.12077973 emulator'], calls)
        self.assertIn('100% Unzipping', log)

    def test_corrupted_download_is_retried_with_the_same_packages(self):
        for first in ('corrupt-exit', 'corrupt-zero'):
            with self.subTest(first=first):
                result, calls, log = self.run_install(f'{first},ok')
                self.assertEqual(0, result.returncode, result.stdout + result.stderr)
                self.assertEqual(['ndk;27.0.12077973 emulator'] * 2, calls)
                self.assertIn('attempt 1 of 3', log)

    def test_persistent_failure_fails_after_bounded_attempts(self):
        result, calls, log = self.run_install('corrupt-exit')
        self.assertEqual(1, result.returncode)
        self.assertEqual(3, len(calls))
        self.assertIn('failed after 3 attempts', log)


if __name__ == '__main__':
    unittest.main()
