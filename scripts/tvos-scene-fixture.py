#!/usr/bin/env python3
"""Loopback-only, synthetic main/Aurral servers for owned tvOS scene recovery tests.

The two accounts deliberately have different credentials. Telemetry contains only route names,
authentication results and counters, never passwords, tokens or authentication query strings.
"""
import argparse
import hashlib
import json
import threading
import time
import subprocess
import plistlib
import uuid
import shutil
import io
import re
import wave
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import parse_qs, urlsplit

MAIN_USER, MAIN_PASSWORD = 'primary590', 'Main!590'
COMPANION_USER, COMPANION_PASSWORD = 'secondary590', 'Companion!590'
COMPANION_TOKEN = 'synthetic-companion-session-590'
lock = threading.Lock()
events = []
acceptance = dict(current=None, stages={})
ARTIST = dict(id='590-artist', name='Recovered main library', albumCount=1)
ALBUM = dict(id='590-album', name='Recovery 590', artist=ARTIST['name'], artistId=ARTIST['id'], songCount=2, duration=360)
SONG = dict(id='590-song', title='Recovery proof', albumId=ALBUM['id'], album=ALBUM['name'],
            artistId=ARTIST['id'], artist=ARTIST['name'], duration=180, suffix='wav', contentType='audio/wav', isDir=False)

SONGS = (SONG, dict(SONG, id='590-song-2', title='Recovery second track', track=2))
SONGS_BY_ID = {song['id']: song for song in SONGS}

# Real, finite PCM/WAV decoded by BASS; silent so unattended simulator tests do not play sound.
with io.BytesIO() as output:
    with wave.open(output, 'wb') as wav:
        wav.setparams((1, 2, 16000, 0, 'NONE', 'not compressed'))
        wav.writeframes(bytes(16000 * 2 * SONG['duration']))
    AUDIO = output.getvalue()


class Handler(BaseHTTPRequestHandler):
    def log_message(self, *_): pass

    def respond(self, value, status=200):
        data = json.dumps(value).encode()
        self.send_response(status)
        self.send_header('Content-Type', 'application/json')
        self.send_header('Content-Length', str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def artwork(self):
        data = (Path(__file__).parent/'animation-probe/fixtures/artwork-0.png').read_bytes()
        self.send_response(200)
        self.send_header('Content-Type', 'image/png')
        self.send_header('Content-Length', str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def record(self, route, authenticated):
        with lock: events.append(dict(route=route, authenticated=authenticated))

    def audio(self):
        start, end = 0, len(AUDIO) - 1
        requested = self.headers.get('Range')
        if requested is not None:
            match = re.fullmatch(r'bytes=(\d*)-(\d*)', requested) if len(requested) < 128 else None
            valid = match is not None and bool(match[1] or match[2])
            if valid:
                if match[1]:
                    start = int(match[1])
                    end = min(int(match[2]), end) if match[2] else end
                    valid = start <= end and start < len(AUDIO)
                else:
                    count = int(match[2])
                    valid = count > 0
                    start = max(0, len(AUDIO) - count)
            if not valid:
                self.send_response(416)
                self.send_header('Content-Range', f'bytes */{len(AUDIO)}')
                self.send_header('Content-Length', '0')
                self.end_headers()
                return
        self.send_response(206 if requested is not None else 200)
        self.send_header('Content-Type', 'audio/wav')
        self.send_header('Accept-Ranges', 'bytes')
        self.send_header('Content-Length', str(end - start + 1))
        if requested is not None: self.send_header('Content-Range', f'bytes {start}-{end}/{len(AUDIO)}')
        self.end_headers()
        try: self.wfile.write(AUDIO[start:end+1])
        except (BrokenPipeError, ConnectionResetError): pass  # BASS may cancel a read after probing or seeking.

    def do_CONNECT(self): self.send_error(403)

    def do_POST(self):
        path = urlsplit(self.path).path
        length = int(self.headers.get('Content-Length', '0'))
        if not 0 <= length <= 8192: return self.send_error(413)
        try: body = json.loads(self.rfile.read(length))
        except (ValueError, TypeError): return self.send_error(400)
        if not isinstance(body, dict): return self.send_error(400)
        if path == '/_test/control':
            if getattr(self.server, 'owned_device', None) is None: return self.send_error(503)
            try: return self.respond(self.control(body.get('action')))
            except (AssertionError, OSError, ValueError, subprocess.SubprocessError) as failure:
                import traceback
                traceback.print_exc()
                return self.respond(dict(error=type(failure).__name__), 500)
        if path in ('/main/auth/login', '/companion/api/auth/login'):
            companion = path.startswith('/companion/')
            valid = body == dict(username=COMPANION_USER if companion else MAIN_USER,
                                 password=COMPANION_PASSWORD if companion else MAIN_PASSWORD)
            self.record(path, valid)
            return self.respond(dict(token=COMPANION_TOKEN if companion else 'synthetic-main-session-590',
                                     expiresAt=int(time.time()*1000)+3600000), 200 if valid else 401)
        self.send_error(404)

    def owned_probe(self, allow_missing=False):
        device = self.server.owned_device
        result = subprocess.run(['xcrun','simctl','get_app_container',device,'app.naviamp.tvos','data'],
                                capture_output=True, text=True, timeout=10)
        if result.returncode:
            assert allow_missing and result.returncode == 2, 'App container must exist'
            return dict(databaseExists=False, recoveryRecordExists=False, installed=False), None
        container = Path(result.stdout.strip()).resolve()
        parent = (Path.home()/'Library/Developer/CoreSimulator/Devices'/device/'data/Containers/Data/Application').resolve()
        assert container.parent == parent
        uuid.UUID(container.name)
        preference = container/'Library/Preferences/app.naviamp.tvos.plist'
        values = plistlib.loads(preference.read_bytes()) if preference.exists() else {}
        return dict(databaseExists=(container/'Library/Caches/Naviamp/naviamp-storage.db').is_file(),
                    recoveryRecordExists='naviamp.recovery.v1' in values, installed=True, container=container.name), container

    def control(self, action):
        def finish_stage():
            with lock:
                acceptance['stages'][acceptance['current']] = dict(preflight=acceptance['preflight'], events=list(events))
        if action in ('create', 'relaunch', 'purged'):
            previous = {'create':None, 'relaunch':'create', 'purged':'relaunch'}[action]
            assert acceptance['current'] == previous
            if action == 'purged': assert 'purge' in acceptance
            probe, _ = self.owned_probe(allow_missing=action == 'create')
            probe['expectedDatabaseExists'] = action == 'relaunch'
            assert probe['databaseExists'] == probe['expectedDatabaseExists']
            assert probe['recoveryRecordExists'] == (action != 'create')
            if previous is not None: finish_stage()
            with lock:
                events.clear()
                acceptance.update(current=action, preflight=probe)
            return probe
        if action == 'purge':
            assert acceptance['current'] == 'relaunch'
            before, container = self.owned_probe()
            assert before['databaseExists'] and before['recoveryRecordExists']
            preference = container/'Library/Preferences/app.naviamp.tvos.plist'
            encoded = plistlib.loads(preference.read_bytes())['naviamp.recovery.v1']
            assert encoded.startswith('naviamp-keychain-aead-v1:')
            for secret in ('primary590','secondary590','Main!590','Companion!590','/companion'):
                assert secret not in encoded
            cache = (container/'Library/Caches/Naviamp').resolve()
            assert cache.parent == container/'Library/Caches' and cache.is_relative_to(container)
            files = sorted(str(p.relative_to(cache)) for p in cache.rglob('*') if p.is_file())
            assert 'naviamp-storage.db' in files
            shutil.rmtree(cache)
            assert not cache.exists()
            assert plistlib.loads(preference.read_bytes())['naviamp.recovery.v1'] == encoded
            acceptance['purge'] = dict(removedFiles=files, encryptedBytes=len(encoded.encode()),
                encryptedSha256=hashlib.sha256(encoded.encode()).hexdigest(), preferencesPreserved=True)
            return dict(databaseExists=False, recoveryRecordExists=True)
        if action == 'finish':
            assert acceptance['current'] == 'purged'
            probe, _ = self.owned_probe()
            assert probe['databaseExists'] and probe['recoveryRecordExists']
            finish_stage()
            acceptance.update(finished=True, rebuilt=probe)
            return probe
        raise ValueError('Unknown owned test control')

    def do_GET(self):
        request = urlsplit(self.path)
        if request.scheme or request.netloc: return self.send_error(403)
        path = request.path
        if path == '/_test/status':
            with lock: snapshot = list(events)
            return self.respond(dict(events=snapshot, acceptance=acceptance))
        if path == '/companion/api/health/bootstrap':
            self.record(path, None)
            return self.respond(dict(api=dict(version=1, features=['discover'])))
        if path.startswith('/companion/api/'):
            valid = self.headers.get('Authorization') == 'Bearer '+COMPANION_TOKEN
            self.record(path, valid)
            if not valid: return self.respond(dict(error='Unauthorized'), 401)
            if path.endswith('/cover'):
                return self.respond(dict(images=[dict(image='/api/image-proxy/fixture.png', front=True)]))
            if path == '/companion/api/image-proxy/fixture.png': return self.artwork()
            if path == '/companion/api/discover':
                return self.respond(dict(recommendations=[dict(id='550e8400-e29b-41d4-a716-446655440590', name='Recovered Aurral discovery')], globalTop=[]))
            if path == '/companion/api/discover/status': return self.respond(dict(isUpdating=False))
            if path == '/companion/api/discover/blocked-tags': return self.respond(dict(tags=[]))
            return self.respond({})
        if not path.startswith('/main/rest/'): return self.send_error(404)
        query = parse_qs(request.query)
        one = lambda name: query.get(name, [''])[0]
        expected = hashlib.md5((MAIN_PASSWORD+one('s')).encode()).hexdigest()
        valid = one('u') == MAIN_USER and bool(one('s')) and one('t') == expected
        action = path.rsplit('/', 1)[-1].removesuffix('.view')
        self.record('/main/rest/'+action, valid)
        result = dict(status='ok', version='1.16.1', type='fixture', serverVersion='1.0', openSubsonic=False)
        if not valid:
            result.update(status='failed', error=dict(code=40, message='Wrong synthetic account'))
        else:
            if action in ('stream', 'download'):
                if one('id') not in SONGS_BY_ID: return self.send_error(404)
                return self.audio()
            first_page = int(one('offset') or 0) == 0
            responses = {
                'getMusicFolders': dict(musicFolders=dict(musicFolder=[dict(id=1, name='Fixture')])),
                'getArtists': dict(artists=dict(index=[dict(name='R', artist=[ARTIST])])),
                'getArtist': dict(artist=dict(ARTIST, album=[ALBUM])),
                'getAlbumList2': dict(albumList2=dict(album=[ALBUM] if first_page else [])),
                'getAlbum': dict(album=dict(ALBUM, song=SONGS)),
                'getSong': dict(song=SONGS_BY_ID.get(one('id'), SONG)),
                'search3': dict(searchResult3=dict(song=[SONG] if int(one('songOffset') or 0) == 0 and one('songCount') != '0' else [], album=[], artist=[])),
                'getRandomSongs': dict(randomSongs=dict(song=SONGS)),
                'getStarred2': dict(starred2=dict(song=[], album=[], artist=[])),
                'getPlaylists': dict(playlists=dict(playlist=[])),
                'getGenres': dict(genres=dict(genre=[])),
                'getInternetRadioStations': dict(internetRadioStations=dict(internetRadioStation=[])),
                'getOpenSubsonicExtensions': dict(openSubsonicExtensions=[]),
                'getScanStatus': dict(scanStatus=dict(scanning=False, count=1)),
            }
            result.update(responses.get(action, {}))
        self.respond({'subsonic-response': result})


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--port', type=int, default=18790)
    parser.add_argument('--owned-device', help='Read-only pre-launch probe of the runner-created simulator')
    args = parser.parse_args()
    if args.owned_device:
        try: uuid.UUID(args.owned_device)
        except ValueError: parser.error('owned-device must be the runner-created simulator UUID')
    server = ThreadingHTTPServer(('127.0.0.1', args.port), Handler)
    server.owned_device = args.owned_device
    print(json.dumps(dict(port=server.server_port)), flush=True)
    server.serve_forever()
