"""The recovery proof must fail when either synthetic account loses its own credentials."""
import hashlib
import http.client
import json
from pathlib import Path
import runpy
import threading
import unittest


class TvosSceneFixtureTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.fixture = runpy.run_path(str(Path(__file__).resolve().parents[1] / 'tvos-scene-fixture.py'))
        cls.server = cls.fixture['ThreadingHTTPServer'](('127.0.0.1', 0), cls.fixture['Handler'])
        cls.thread = threading.Thread(target=cls.server.serve_forever, daemon=True)
        cls.thread.start()

    @classmethod
    def tearDownClass(cls):
        cls.server.shutdown()
        cls.server.server_close()
        cls.thread.join(5)

    def request(self, path, body=None, headers=None):
        connection = http.client.HTTPConnection('127.0.0.1', self.server.server_port, timeout=5)
        try:
            connection.request('POST' if body is not None else 'GET', path,
                               json.dumps(body) if body is not None else None, headers or {})
            response = connection.getresponse()
            body = response.read()
            return response.status, json.loads(body) if response.getheader('Content-Type') == 'application/json' else body
        finally:
            connection.close()

    def test_primary_requires_its_own_password_and_salt(self):
        for password, status in [('Main!590', 'ok'), ('Companion!590', 'failed'), ('', 'failed')]:
            token = hashlib.md5((password+'salt590').encode()).hexdigest()
            _, body = self.request('/main/rest/ping.view?u=primary590&s=salt590&t='+token)
            self.assertEqual(status, body['subsonic-response']['status'])
        _, body = self.request('/main/rest/ping.view')
        self.assertEqual('failed', body['subsonic-response']['status'])

    def test_companion_requires_separate_credentials_and_bearer(self):
        for username, password, status in [('secondary590', 'Companion!590', 200), ('primary590', 'Main!590', 401)]:
            result, body = self.request('/companion/api/auth/login', dict(username=username, password=password))
            self.assertEqual(status, result)
        self.assertEqual(401, self.request('/companion/api/discover')[0])
        token = self.fixture['COMPANION_TOKEN']
        status, body = self.request('/companion/api/discover', headers={'Authorization': 'Bearer '+token})
        self.assertEqual(200, status)
        self.assertEqual('Recovered Aurral discovery', body['recommendations'][0]['name'])

    def test_telemetry_retains_authentication_results_without_secrets(self):
        self.request('/companion/api/auth/login', dict(username='secondary590', password='Companion!590'))
        _, telemetry = self.request('/_test/status')
        self.assertTrue(telemetry['events'])
        self.assertTrue(all(set(event) == {'route', 'authenticated'} for event in telemetry['events']))
        for secret in ('Companion!590', 'Main!590', self.fixture['COMPANION_TOKEN'], 'primary590'):
            self.assertNotIn(secret, json.dumps(telemetry))

    def test_cover_lookup_and_image_require_the_companion_session(self):
        cover = '/companion/api/artists/550e8400-e29b-41d4-a716-446655440590/cover'
        headers = {'Authorization': 'Bearer '+self.fixture['COMPANION_TOKEN']}
        self.assertEqual(401, self.request(cover)[0])
        status, body = self.request(cover, headers=headers)
        self.assertEqual(200, status)
        self.assertEqual('/api/image-proxy/fixture.png', body['images'][0]['image'])
        image = '/companion/api/image-proxy/fixture.png'
        self.assertEqual(401, self.request(image)[0])
        status, data = self.request(image, headers=headers)
        self.assertEqual(200, status)
        self.assertTrue(data.startswith(b'\x89PNG\r\n\x1a\n'))

    def test_preflight_cannot_inspect_a_simulator_without_explicit_runner_ownership(self):
        self.assertEqual(503, self.request('/_test/control', dict(action='purge'))[0])


if __name__ == '__main__': unittest.main()
