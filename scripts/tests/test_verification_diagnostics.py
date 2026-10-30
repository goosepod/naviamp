import os
from pathlib import Path
import subprocess
import tempfile
import time
import unittest

SCRIPT = Path(__file__).resolve().parents[1] / 'capture-android-diagnostics.py'


class AndroidDiagnosticsTest(unittest.TestCase):
    def test_absent_offline_online_and_hung_devices(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            adb = root / 'adb'
            adb.write_text('''#!/usr/bin/env python3
import os, sys, time
from pathlib import Path
with Path(os.environ['CALLS']).open('a') as log: log.write(' '.join(sys.argv[1:]) + '\\n')
mode = os.environ['MODE']
if mode == 'hung-discovery' or (mode == 'hung-shell' and sys.argv[1] != 'devices'):
    time.sleep(60)
if sys.argv[1] == 'devices':
    print('List of devices attached')
    if mode == 'offline': print('emulator-5554 offline')
    elif mode in ('online', 'hung-shell'): print('emulator-5554 device product:fixture')
else: print('fixture diagnostics')
''')
            adb.chmod(0o755)
            for mode in ['absent', 'offline', 'online', 'hung-discovery', 'hung-shell']:
                with self.subTest(mode=mode):
                    output = root / mode
                    calls = root / f'{mode}.calls'
                    started = time.monotonic()
                    subprocess.run(['python3', str(SCRIPT), '--output', str(output),
                                    '--timeout-seconds', '1'], check=True,
                                   env={**os.environ, 'PATH': f'{root}{os.pathsep}{os.environ["PATH"]}',
                                        'MODE': mode, 'CALLS': str(calls)})
                    self.assertLess(time.monotonic() - started, 8)
                    commands = calls.read_text().splitlines()
                    self.assertEqual(5 if mode in ['online', 'hung-shell'] else 1, len(commands))
                    if mode in ['absent', 'offline', 'hung-discovery']:
                        self.assertIn('skipped', (output / 'status.txt').read_text())
                    else:
                        self.assertTrue(all('-s emulator-5554' in c for c in commands[1:]))
                        self.assertTrue((output / 'logcat.txt').exists())
                    if mode == 'hung-shell':
                        self.assertIn('timed out', (output / 'properties.txt').read_text())


if __name__ == '__main__':
    unittest.main()
