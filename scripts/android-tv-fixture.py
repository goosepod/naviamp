#!/usr/bin/env python3
"""Loopback-only, credential-free Subsonic/audio fixture for disposable TV emulators.

Use adb reverse tcp:18080 tcp:18080. Never forwards traffic to another server.
GET /_test/off and /_test/on simulate transport loss/recovery; /_test/status is telemetry.
"""
import json
import math
import socket
import struct
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, urlsplit

TRACK = dict(id="fixture-track", title="TV lifecycle fixture", artist="Naviamp Test", album="Recovery",
             albumId="fixture-album", artistId="fixture-artist", duration=600, suffix="wav",
             contentType="audio/wav", bitRate=256, isDir=False, size=19_200_044)
ALBUM = dict(id="fixture-album", name="Recovery", artist="Naviamp Test", artistId="fixture-artist", songCount=1, duration=600)
RATE = 16000
PCM = b"".join(struct.pack("<h", int(1200 * math.sin(2 * math.pi * 440 * i / RATE))) for i in range(RATE))
DATA = struct.pack('<4sI4s4sIHHIIHH4sI', b'RIFF', 36+len(PCM)*600, b'WAVE', b'fmt ',16,1,1,RATE,RATE*2,2,16,b'data',len(PCM)*600) + PCM*600
lock = threading.Lock()
offline = False
streams = set()
counts = {}

class Handler(BaseHTTPRequestHandler):
    def log_message(self, *_): pass
    def do_CONNECT(self): self.send_error(403, "Fixture never proxies traffic")
    def do_GET(self):
        global offline
        parsed = urlsplit(self.path)
        if parsed.hostname and parsed.hostname not in ('127.0.0.1', 'localhost'):
            self.send_error(403); return
        path = parsed.path
        if path.startswith('/_test/'):
            with lock:
                if path.endswith('/off'):
                    offline = True
                    for stream in list(streams):
                        try: stream.shutdown(socket.SHUT_RDWR)
                        except OSError: pass
                elif path.endswith('/on'): offline = False
                payload = dict(offline=offline, activeStreams=len(streams), requests=dict(counts))
            return self.json(payload)
        action = path.rsplit('/',1)[-1].removesuffix('.view')
        with lock:
            counts[action] = counts.get(action, 0)+1
            unavailable = offline
        if unavailable: self.send_error(503); return
        if action == 'stream': return self.audio()
        response = dict(status='ok', version='1.16.1', type='fixture', serverVersion='1.0', openSubsonic=False)
        responses = {
            'getMusicFolders': {'musicFolders': {'musicFolder': [dict(id=1,name='Fixture')]}},
            'getArtists': {'artists': {'index': [dict(name='N',artist=[dict(id='fixture-artist',name='Naviamp Test',albumCount=1)])]}},
            'getArtist': {'artist': dict(id='fixture-artist',name='Naviamp Test',album=[ALBUM])},
            'getAlbum': {'album': dict(ALBUM,song=[TRACK])},
            'getAlbumList2': {'albumList2': {'album': [ALBUM]}},
            'getSong': {'song': TRACK},
            'search3': {'searchResult3': {'song':[TRACK], 'album':[ALBUM], 'artist':[]}},
            'getRandomSongs': {'randomSongs': {'song':[TRACK]}},
            'getStarred2': {'starred2': {'song':[], 'album':[], 'artist':[]}},
            'getPlaylists': {'playlists': {'playlist':[]}},
            'getGenres': {'genres': {'genre':[]}},
            'getInternetRadioStations': {'internetRadioStations': {'internetRadioStation':[]}},
            'getOpenSubsonicExtensions': {'openSubsonicExtensions':[]},
        }
        query = parse_qs(parsed.query)
        if action == 'getAlbumList2' and int(query.get('offset', ['0'])[0]) > 0:
            responses[action]['albumList2']['album'] = []
        if action == 'search3':
            for kind in ('song', 'album', 'artist'):
                if int(query.get(kind + 'Offset', ['0'])[0]) > 0 or query.get(kind + 'Count') == ['0']:
                    responses[action]['searchResult3'][kind] = []
        response.update(responses.get(action, {}))
        self.json({'subsonic-response': response})
    def json(self, value):
        data = json.dumps(value).encode()
        self.send_response(200); self.send_header('Content-Type','application/json')
        self.send_header('Content-Length',str(len(data))); self.end_headers(); self.wfile.write(data)
    def audio(self):
        start = 0
        if self.headers.get('Range','').startswith('bytes='):
            start = int(self.headers['Range'][6:].split('-')[0] or 0)
        if start >= len(DATA): self.send_error(416); return
        self.send_response(206 if start else 200)
        self.send_header('Content-Type','audio/wav'); self.send_header('Accept-Ranges','bytes')
        if start: self.send_header('Content-Range',f'bytes {start}-{len(DATA)-1}/{len(DATA)}')
        self.send_header('Content-Length',str(len(DATA)-start)); self.end_headers()
        with lock: streams.add(self.connection)
        try:
            for offset in range(start,len(DATA),8192):
                self.wfile.write(DATA[offset:offset+8192]); self.wfile.flush()
                if offset - start >= 4_000_000: time.sleep(0.25)
        except (OSError, ConnectionError): pass
        finally:
            with lock: streams.discard(self.connection)

if __name__ == '__main__':
    ThreadingHTTPServer(('127.0.0.1',18080),Handler).serve_forever()
