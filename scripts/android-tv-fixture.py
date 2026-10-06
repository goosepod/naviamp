#!/usr/bin/env python3
"""Loopback-only, credential-free Subsonic/audio fixture for disposable TV emulators.

Use adb reverse tcp:18080 tcp:18080. Never forwards traffic to another server.
GET /_test/off and /_test/on simulate transport loss/recovery; /_test/status is telemetry.
"""
import argparse
import json
import math
import socket
import struct
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, urlsplit

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--burst-seconds', type=float, default=125)
parser.add_argument('--track-seconds', type=int, default=600)
parser.add_argument('--tracks', type=int, default=1)
parser.add_argument('--albums', type=int, default=1)
parser.add_argument('--unknown-length', action='store_true', help='Omit Content-Length and ranges for finite songs')
parser.add_argument('--long-metadata', action='store_true', help='Overflow all player metadata rows for full-app animation measurements')
parser.add_argument('--mixed-metadata', action='store_true', help='Provide one short track and long metadata on remaining tracks for animation isolation')
parser.add_argument('--accounts', action='store_true', help='Return distinct catalogs for each fixture username')
args = parser.parse_args()
if args.mixed_metadata and (args.long_metadata or args.tracks < 2):
    parser.error('mixed metadata requires at least two tracks and cannot be combined with long metadata')
if not (0 <= args.burst_seconds <= 600 and 30 <= args.track_seconds <= 600 and 1 <= args.tracks <= 240 and 1 <= args.albums <= args.tracks and args.tracks % args.albums == 0):
    parser.error('burst must be 0..600 seconds, track length 30..600 seconds, tracks 1..240, albums must divide track count')

TRACK = dict(id="fixture-track", title="TV lifecycle fixture", artist="Naviamp Test", album="Recovery",
             albumId="fixture-album", artistId="fixture-artist", duration=args.track_seconds, suffix="wav",
             contentType="audio/wav", bitRate=256, isDir=False,
             replayGain=dict(trackGain=-6.0, albumGain=-3.0, trackPeak=1.0, albumPeak=1.0), size=32_000 * args.track_seconds + 44)
ALBUM = dict(id="fixture-album", name="Recovery", artist="Naviamp Test", artistId="fixture-artist", songCount=args.tracks, duration=args.track_seconds * args.tracks)
TRACKS = [dict(TRACK, id="fixture-track" if i == 0 else f"fixture-track-{i+1}",
               title="TV lifecycle fixture" if i == 0 else f"TV lifecycle fixture {i+1}") for i in range(args.tracks)]
PER_ALBUM = args.tracks // args.albums
ALBUMS = [dict(ALBUM, id="fixture-album" if i == 0 else f"fixture-album-{i+1}",
               name=f"Recovery {i+1}", songCount=PER_ALBUM, duration=PER_ALBUM * args.track_seconds) for i in range(args.albums)]
for i, track in enumerate(TRACKS):
    track.update(albumId=ALBUMS[i // PER_ALBUM]['id'], album=ALBUMS[i // PER_ALBUM]['name'], track=i % PER_ALBUM + 1)
if args.long_metadata or args.mixed_metadata:
    for i, track in enumerate(TRACKS):
        if args.mixed_metadata and i == 0:
            track.update(title='Short fixture', artist='Fixture', album='Fixture')
            continue
        track.update(title='Long scrolling player title with enough metadata to overflow its viewport',
                     artist='Long scrolling fixture artist with enough text to overflow its viewport',
                     album='Long scrolling fixture album with enough text to overflow its viewport')
    for i, album in enumerate(ALBUMS):
        track = TRACKS[i * PER_ALBUM]
        album.update(name=track['album'], artist=track['artist'])
RATE = 16000
PCM = b"".join(struct.pack("<h", int(1200 * math.sin(2 * math.pi * 440 * i / RATE))) for i in range(RATE))
DATA = struct.pack('<4sI4s4sIHHIIHH4sI', b'RIFF', 36+len(PCM)*args.track_seconds, b'WAVE', b'fmt ',16,1,1,RATE,RATE*2,2,16,b'data',len(PCM)*args.track_seconds) + PCM*args.track_seconds
lock = threading.Lock()
offline = False
streams = set()
counts = {}
reports = []
bytes_sent = 0
max_active_streams = 0
rejected_accounts = set()

class Handler(BaseHTTPRequestHandler):
    def log_message(self, *_): pass
    def do_CONNECT(self): self.send_error(403, "Fixture never proxies traffic")
    def do_GET(self):
        global offline, bytes_sent
        parsed = urlsplit(self.path)
        if parsed.hostname and parsed.hostname not in ('127.0.0.1', 'localhost'):
            self.send_error(403); return
        path = parsed.path
        if path.startswith('/_test/'):
            with lock:
                account = parse_qs(parsed.query).get('user', [''])[0]
                if args.accounts and path.endswith('/reject'): rejected_accounts.add(account)
                elif args.accounts and path.endswith('/allow'): rejected_accounts.discard(account)
                if path.endswith('/off'):
                    offline = True
                    for stream in list(streams):
                        try: stream.shutdown(socket.SHUT_RDWR)
                        except OSError: pass
                elif path.endswith('/on'): offline = False
                payload = dict(config=dict(tracks=args.tracks, albums=args.albums, trackSeconds=args.track_seconds, burstSeconds=args.burst_seconds), maxActiveStreams=max_active_streams, offline=offline, activeStreams=len(streams), requests=dict(counts), reports=list(reports), bytesSent=bytes_sent)
            return self.json(payload)
        action = path.rsplit('/',1)[-1].removesuffix('.view')
        with lock:
            counts[action] = counts.get(action, 0)+1
            unavailable = offline
        if unavailable: self.send_error(503); return
        query = parse_qs(parsed.query)
        account = query.get('u', ['fixture'])[0]
        if args.accounts and account in rejected_accounts:
            return self.json({'subsonic-response': dict(status='failed', version='1.16.1', error=dict(code=40, message='Fixture session expired'))})
        if args.accounts and 'id' in query:
            query['id'] = [value.removeprefix(account + ':') for value in query['id']]
        if action == 'stream': return self.audio()
        if action == 'live': return self.audio(live=True)
        if action == 'scrobble':
            with lock:
                for track_id in query.get('id', []):
                    if track_id in [track['id'] for track in TRACKS]:
                        reports.append(dict(id=track_id, submission=query.get('submission', ['true'])[0]))
        response = dict(status='ok', version='1.16.1', type='fixture', serverVersion='1.0', openSubsonic=False)
        responses = {
            'getMusicFolders': {'musicFolders': {'musicFolder': [dict(id=1,name='Fixture')]}},
            'getArtists': {'artists': {'index': [dict(name='N',artist=[dict(id='fixture-artist',name='Naviamp Test',albumCount=args.albums)])]}},
            'getArtist': {'artist': dict(id='fixture-artist',name='Naviamp Test',album=ALBUMS)},
            'getAlbum': {'album': dict(next((album for album in ALBUMS if album['id'] == query.get('id', [''])[0]), ALBUMS[0]), song=[track for track in TRACKS if track['albumId'] == query.get('id', [ALBUMS[0]['id']])[0]])},
            'getAlbumList2': {'albumList2': {'album': ALBUMS}},
            'getSong': {'song': next((track for track in TRACKS if track['id'] == query.get('id', [''])[0]), TRACKS[0])},
            'search3': {'searchResult3': {'song':TRACKS, 'album':ALBUMS, 'artist':[]}},
            'getRandomSongs': {'randomSongs': {'song':TRACKS}},
            'getStarred2': {'starred2': {'song':[], 'album':[], 'artist':[]}},
            'getPlaylists': {'playlists': {'playlist':[]}},
            'getGenres': {'genres': {'genre':[]}},
            'getInternetRadioStations': {'internetRadioStations': {'internetRadioStation':[dict(id='fixture-radio', name='TV live fixture', streamUrl='http://127.0.0.1:18080/live')]}},
            'getOpenSubsonicExtensions': {'openSubsonicExtensions':[]},
        }
        if action == 'getAlbumList2' and int(query.get('offset', ['0'])[0]) > 0:
            responses[action]['albumList2']['album'] = []
        if action == 'search3':
            for kind in ('song', 'album', 'artist'):
                if int(query.get(kind + 'Offset', ['0'])[0]) > 0 or query.get(kind + 'Count') == ['0']:
                    responses[action]['searchResult3'][kind] = []
        response.update(responses.get(action, {}))
        if args.accounts:
            playlist = dict(id='account-playlist', name=account + ' playlist', songCount=1, owner=account, public=False)
            if action == 'getPlaylists': response['playlists'] = dict(playlist=[playlist])
            if action == 'getPlaylist': response['playlist'] = dict(playlist, entry=TRACKS)
            def scoped(value):
                if isinstance(value, list): return [scoped(item) for item in value]
                if not isinstance(value, dict): return value
                result = {}
                for key, item in value.items():
                    if isinstance(item, str) and key in ('id', 'albumId', 'artistId'):
                        item = account + ':' + item
                    elif isinstance(item, str) and key in ('title', 'artist', 'album', 'name') and key != 'owner':
                        item = account + ': ' + item
                    result[key] = scoped(item)
                return result
            response = scoped(response)
        self.json({'subsonic-response': response})
    def json(self, value):
        data = json.dumps(value).encode()
        self.send_response(200); self.send_header('Content-Type','application/json')
        self.send_header('Content-Length',str(len(data))); self.end_headers(); self.wfile.write(data)
    def audio(self, live=False):
        global bytes_sent, max_active_streams
        start = 0
        unknown = live or args.unknown_length
        if not unknown and self.headers.get('Range','').startswith('bytes='):
            start = int(self.headers['Range'][6:].split('-')[0] or 0)
        if start >= len(DATA): self.send_error(416); return
        self.send_response(206 if start else 200)
        self.send_header('Content-Type','audio/wav')
        if not unknown: self.send_header('Accept-Ranges','bytes')
        if start: self.send_header('Content-Range',f'bytes {start}-{len(DATA)-1}/{len(DATA)}')
        if not unknown: self.send_header('Content-Length',str(len(DATA)-start))
        self.end_headers()
        with lock:
            streams.add(self.connection)
            max_active_streams = max(max_active_streams, len(streams))
        try:
            if live:
                # WAV's unknown data-size sentinel; stream PCM until the test closes the socket.
                header = bytearray(DATA[:44])
                struct.pack_into('<I', header, 4, 0xffffffff)
                struct.pack_into('<I', header, 40, 0xffffffff)
                self.wfile.write(header)
                while True:
                    self.wfile.write(PCM); self.wfile.flush()
                    with lock: bytes_sent += len(PCM)
                    time.sleep(1)
            for offset in range(start,len(DATA),8192):
                self.wfile.write(DATA[offset:offset+8192]); self.wfile.flush()
                with lock: bytes_sent += len(DATA[offset:offset+8192])
                if offset - start >= args.burst_seconds * RATE * 2: time.sleep(0.25)
        except (OSError, ConnectionError): pass
        finally:
            with lock: streams.discard(self.connection)

if __name__ == '__main__':
    ThreadingHTTPServer(('127.0.0.1',18080),Handler).serve_forever()
