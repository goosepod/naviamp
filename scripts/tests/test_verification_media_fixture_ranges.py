"""Exercise real loopback HTTP responses used by visible playback acceptance."""
import http.client
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

    def request(self, range_header=None):
        connection = http.client.HTTPConnection('127.0.0.1', self.server.server_port, timeout=5)
        try:
            connection.request('GET', '/rest/stream.view', headers={'Range': range_header} if range_header else {})
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


if __name__ == '__main__':
    unittest.main()
