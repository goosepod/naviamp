"""A prebuilt native run must never turn missing, skipped, stale or crashed work into acceptance."""
from pathlib import Path
import json
import os
import runpy
import subprocess
import sys
import tempfile
import unittest
import xml.etree.ElementTree as ET

HELPER = runpy.run_path(str(Path(__file__).resolve().parents[1] / 'run-prebuilt-ios-native-tests.py'))
OWNER = HELPER['TLS_OWNER']
NAME = HELPER['TLS_CASE']
EXPECTED = {(OWNER, NAME)}
DEVICE = '12345678-1234-1234-1234-123456789abc'
LISTING = f'{OWNER}.\n  {NAME}\n'
OUTPUT = f'''##teamcity[testSuiteStarted name='{OWNER}']
##teamcity[testStarted name='{NAME}' locationHint='ktest:test://{OWNER}.{NAME}']
mTLS runner bootstrap supplied
##teamcity[testFinished name='{NAME}' duration='123']
##teamcity[testSuiteFinished name='{OWNER}']
'''


class PrebuiltNativeProtocolTest(unittest.TestCase):
    def test_requires_exact_inventory_and_bootstrap_provenance(self):
        self.assertEqual(EXPECTED, HELPER['inventory'](LISTING))
        self.assertEqual({(OWNER, NAME): .123}, HELPER['results'](OUTPUT, EXPECTED))
        for text in ('', LISTING * 2, '  orphan\n', 'unexpected output\n', 'other.Suite.\n  unrelated\n'):
            with self.subTest(text=text), self.assertRaises(RuntimeError):
                HELPER['inventory'](text)

    def test_preserves_teamcity_escaping(self):
        event, fields = HELPER['service_message']("##teamcity[testFailed name='a|[|]|||'b|n|r' message='failure']")
        self.assertEqual('testFailed', event)
        self.assertEqual("a[]|'b\n\r", fields['name'])
        for line in ("##teamcity[testStarted name='bad|x']", "##teamcity[testStarted name='unterminated]",
                     "##teamcity[testStarted name='a' name='b']", '##teamcity[]'):
            with self.subTest(line=line), self.assertRaises(RuntimeError):
                HELPER['service_message'](line)

    def test_rejects_failed_ignored_duplicate_truncated_and_missing_cases(self):
        changes = [
            OUTPUT.replace('testFinished', 'testFailed'),
            OUTPUT.replace('testFinished', 'testIgnored'),
            OUTPUT * 2,
            OUTPUT.rsplit('##teamcity[testSuiteFinished', 1)[0],
            OUTPUT.replace("duration='123'", "duration='-1'"),
            OUTPUT.replace('mTLS runner bootstrap supplied', 'no bootstrap'),
            OUTPUT.replace(f"name='{NAME}'", "name='unlisted'"),
            OUTPUT.replace('testStarted', 'unknownEvent'),
            '',
        ]
        for output in changes:
            with self.subTest(output=output), self.assertRaises(RuntimeError):
                HELPER['results'](output, EXPECTED)
        with self.assertRaises(RuntimeError):
            HELPER['results'](OUTPUT, EXPECTED | {(OWNER, 'missing')})

    def test_committed_required_manifest_is_unique_and_contains_native_boundaries(self):
        path = Path(__file__).resolve().parents[1] / 'ios-native-required-tests.json'
        cases = json.loads(path.read_text())
        self.assertEqual(17, len(cases))
        self.assertEqual(17, len(set(map(tuple, cases))))
        self.assertIn([OWNER, NAME], cases)
        self.assertTrue(any('IosBassDecodeIntegrationTest' in owner for owner, _ in cases))
        self.assertTrue(any('IosAudioStorageTest' in owner for owner, _ in cases))


class PrebuiltNativeExecutionTest(unittest.TestCase):
    def fixture(self, root, listing=LISTING, output=OUTPUT, native_exit=0):
        executable = root / 'test.kexe'
        executable.write_bytes(b'owned compiled native test placeholder')
        fake = root / 'simctl.py'
        fake.write_text('import sys\n'
                        'assert sys.argv[-2] == "--", "Match the KotlinNativeSimulatorTest argument boundary"\n'
                        f'listing={listing!r}\noutput={output!r}\n'
                        'print(listing if sys.argv[-1] == "--ktest_list_tests" else output, end="")\n'
                        f'sys.exit(0 if sys.argv[-1] == "--ktest_list_tests" else {native_exit})\n')
        return executable, [sys.executable, str(fake)]

    def test_executes_fresh_commands_and_writes_only_validated_junit(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            executable, prefix = self.fixture(root)
            target = root / 'run'
            self.assertEqual({(OWNER, NAME): .123}, HELPER['run'](executable, DEVICE, target, prefix=prefix, required=EXPECTED))
            report = json.loads((target / 'execution.json').read_text())
            self.assertEqual('passed', report['status'])
            self.assertEqual([[OWNER, NAME]], report['executedCases'])
            xml = ET.parse(target / f'results/TEST-{OWNER}.xml').getroot()
            self.assertEqual('1', xml.get('tests'))
            self.assertEqual(NAME, xml.find('testcase').get('name'))
            with self.assertRaises(FileExistsError):
                HELPER['run'](executable, DEVICE, target, prefix=prefix, required=EXPECTED)

    def test_manifest_mismatch_stops_before_native_execution(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            executable, prefix = self.fixture(root)
            with self.assertRaisesRegex(RuntimeError, 'required source manifest'):
                HELPER['run'](executable, DEVICE, root / 'run', prefix=prefix, required=EXPECTED | {(OWNER, 'missing')})
            self.assertFalse((root / 'run/native.log').exists())
            self.assertEqual('failed', json.loads((root / 'run/execution.json').read_text())['status'])

    def test_nonzero_native_exit_cannot_pass_despite_success_messages(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            executable, prefix = self.fixture(root, native_exit=7)
            with self.assertRaisesRegex(RuntimeError, 'exited 7'):
                HELPER['run'](executable, DEVICE, root / 'run', prefix=prefix, required=EXPECTED)
            self.assertFalse((root / 'run/results').exists())
            self.assertEqual('failed', json.loads((root / 'run/execution.json').read_text())['status'])

    @unittest.skipUnless(os.name == 'posix', 'Actual process-group cleanup is a POSIX adapter boundary')
    def test_timeout_kills_only_the_group_created_for_its_command(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            child_file = root / 'child.pid'
            fake = root / 'hung.py'
            fake.write_text('import subprocess,sys,time\nfrom pathlib import Path\n'
                            'child=subprocess.Popen([sys.executable,"-c","import time; time.sleep(60)"])\n'
                            f'Path({str(child_file)!r}).write_text(str(child.pid))\n'
                            'time.sleep(60)\n')
            with self.assertRaises(subprocess.TimeoutExpired):
                HELPER['execute']([sys.executable, str(fake)], root / 'hung.log', HELPER['time'].monotonic() + 1)
            child = int(child_file.read_text())
            result = subprocess.run(['ps', '-p', str(child), '-o', 'stat='], text=True, capture_output=True)
            self.assertTrue(result.returncode != 0 or result.stdout.strip().startswith('Z'), result.stdout)


if __name__ == '__main__':
    unittest.main()
