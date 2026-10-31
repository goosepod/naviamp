"""Exercise real loopback HTTP responses used by visible playback acceptance."""
import http.client
import contextlib
import io
import json
from pathlib import Path
import runpy
import sys
import threading
import unittest
from unittest.mock import patch


class MediaFixtureRangesTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        script = Path(__file__).resolve().parents[1] / 'android-tv-fixture.py'
        with patch.object(sys, 'argv', [str(script), '--track-seconds', '30']):
            cls.fixture = runpy.run_path(str(script))
        cls.server = cls.fixture['ThreadingHTTPServer'](('127.0.0.1', 0), cls.fixture['Handler'])
        cls.worker = threading.Thread(target=cls.server.serve_forever, daemon=True)
        cls.worker.start()

    @classmethod
    def tearDownClass(cls):
        cls.server.shutdown()
        cls.server.server_close()
        cls.worker.join(5)

    def request(self, range_header=None, path='/rest/stream.view'):
        connection = http.client.HTTPConnection('127.0.0.1', self.server.server_port, timeout=5)
        try:
            connection.request('GET', path, headers={'Range': range_header} if range_header else {})
            response = connection.getresponse()
            return response.status, dict(response.getheaders()), response.read()
        finally:
            connection.close()

    def test_single_byte_probe_is_partial_and_bounded(self):
        status, headers, body = self.request('bytes=0-0')
        self.assertEqual(206, status)
        self.assertEqual('1', headers['Content-Length'])
        self.assertEqual(f"bytes 0-0/{len(self.fixture['DATA'])}", headers['Content-Range'])
        self.assertEqual(self.fixture['DATA'][:1], body)

    def test_far_range_returns_exact_requested_bytes(self):
        status, headers, body = self.request('bytes=800000-900000')
        self.assertEqual(206, status)
        self.assertEqual('100001', headers['Content-Length'])
        self.assertEqual(self.fixture['DATA'][800000:900001], body)

    def test_unsatisfiable_or_malformed_ranges_are_rejected(self):
        for value in ['bytes=99999999-', 'bytes=10-5', 'bytes=bad-']:
            with self.subTest(value=value):
                status, headers, body = self.request(value)
                self.assertEqual(416, status)
                self.assertEqual(f"bytes */{len(self.fixture['DATA'])}", headers['Content-Range'])
                self.assertEqual(b'', body)

    def test_full_request_still_serves_the_complete_wave(self):
        status, headers, body = self.request()
        self.assertEqual(200, status)
        self.assertEqual(str(len(self.fixture['DATA'])), headers['Content-Length'])
        self.assertEqual(self.fixture['DATA'], body)

    def test_seek_audit_excludes_authentication_parameters(self):
        self.request('bytes=0-0', '/rest/stream.view?id=fixture-track&timeOffset=12&u=private-user&t=private-token&p=private-password')
        status, _, body = self.request(path='/_test/status')
        self.assertEqual(200, status)
        audit = json.loads(body)['streamRequests'][-1]
        self.assertEqual('bytes=0-0', audit['range'])
        self.assertEqual({'id':['fixture-track'], 'timeOffset':['12']}, audit['query'])
        self.assertNotIn(b'private-', body)

    def test_server_fault_rejects_stream_then_recovers(self):
        try:
            self.request(path='/_test/off')
            self.assertEqual(503, self.request('bytes=0-0')[0])
        finally:
            self.request(path='/_test/on')
        self.assertEqual(206, self.request('bytes=0-0')[0])

    def test_unattended_fixture_and_bind_constraints(self):
        script = Path(__file__).resolve().parents[1] / 'android-tv-fixture.py'
        with patch.object(sys, 'argv', [str(script), '--track-seconds','30','--silent','--bind-address','192.168.1.10']):
            fixture = runpy.run_path(str(script))
        self.assertEqual(b'RIFF', fixture['DATA'][:4])
        self.assertEqual(bytes(30 * 32000), fixture['DATA'][44:])
        for address in ('0.0.0.0','8.8.8.8','224.0.0.1'):
            with self.subTest(address=address), patch.object(sys, 'argv', [str(script),'--bind-address',address]), contextlib.redirect_stderr(io.StringIO()):
                with self.assertRaises(SystemExit):
                    runpy.run_path(str(script))

    def test_fault_is_triggered_by_the_selected_range_request(self):
        try:
            self.assertEqual(200,self.request(path='/_test/fail-range?minimum=800000&id=fixture-track')[0])
            self.assertEqual(206,self.request('bytes=0-0','/rest/stream.view?id=fixture-track')[0])
            self.assertEqual(503,self.request('bytes=800000-800001','/rest/stream.view?id=fixture-track')[0])
            audit = json.loads(self.request(path='/_test/status')[2])
            self.assertTrue(audit['offline'])
            self.assertTrue(audit['streamRequests'][-1]['triggeredFault'])
            self.assertIsNone(audit['armedRangeFailure'])
        finally:
            self.request(path='/_test/on')
        self.assertEqual(206,self.request('bytes=800000-800001','/rest/stream.view?id=fixture-track')[0])


if __name__ == '__main__':
    unittest.main()
