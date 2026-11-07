"""Diagnostic failures must remain visible and capture must stay on owned loopback ports."""
from pathlib import Path
import runpy
import tempfile
import unittest

HELPER = runpy.run_path(str(Path(__file__).resolve().parents[1] / 'diagnose-ios-fixture-lifetime.py'))


class FixtureLifetimeDiagnosticsTest(unittest.TestCase):
    def report(self, text):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory)
            (path / 'TEST-native.xml').write_text(text)
            return HELPER['read_results'](path)

    case = ('<testcase classname="app.naviamp.ios.playback.IosMutualTlsPlaybackIntegrationTest" '
            'name="realMutualTlsAuthenticatesAndSeeksThroughNormalPlayback">{}</testcase>')

    def test_failure_is_retained_instead_of_becoming_a_pass(self):
        cases = self.report('<testsuite tests="1">' + self.case.format(
            '<failure message="fixture bootstrap timeout"/>') + '</testsuite>')
        self.assertEqual('failure', cases[0]['outcome'])
        self.assertEqual('fixture bootstrap timeout', cases[0]['message'])

    def test_skipped_native_case_is_not_a_pass(self):
        cases = self.report('<testsuite tests="1">' + self.case.format('<skipped/>') + '</testsuite>')
        self.assertEqual('skipped', cases[0]['outcome'])

    def test_missing_duplicate_and_incomplete_results_are_rejected(self):
        for text in ('<testsuite tests="0"/>',
                     '<testsuite tests="2">' + self.case.format('') + '</testsuite>',
                     '<testsuite tests="2">' + self.case.format('') * 2 + '</testsuite>'):
            with self.subTest(text=text), self.assertRaises(RuntimeError):
                self.report(text)

    def test_complete_native_pass_is_distinguished(self):
        cases = self.report('<testsuite tests="1">' + self.case.format('') + '</testsuite>')
        self.assertEqual('passed', cases[0]['outcome'])

    def test_packet_filter_cannot_expand_beyond_four_owned_loopback_ports(self):
        expected = 'tcp and host 127.0.0.1 and (port 41001 or port 41002 or port 41003 or port 41004)'
        self.assertEqual(expected, HELPER['packet_filter']([41001, 41002, 41003, 41004]))
        for ports in ([0, 1, 2, 3], [1, 1, 2, 3], [1, 2, 3], [1, 2, 3, '4 or host example.test']):
            with self.subTest(ports=ports), self.assertRaises(ValueError):
                HELPER['packet_filter'](ports)
