#!/usr/bin/env python3
"""Minimal real-output reproduction for #497; never substitutes a decoded clock.

Each attempt is a separate process. A stalled attempt exits nonzero even if the
optional post-deadline native recovery experiment subsequently succeeds.
"""
import argparse
import ctypes as c
import hashlib
import json
import math
import platform
from pathlib import Path
import subprocess
import sys
import threading
import time
import wave


def attempt(args):
    bass = c.CDLL(str(args.library_dir / 'libbass.dylib'))
    mixer = c.CDLL(str(args.library_dir / 'libbassmix.dylib'))

    def api(library, name, result, parameters):
        function = getattr(library, name)
        function.restype = result
        function.argtypes = parameters
        return function

    uint, integer, quad, pointer = c.c_uint, c.c_int, c.c_ulonglong, c.c_void_p
    error = api(bass, 'BASS_ErrorGetCode', integer, [])
    configure = api(bass, 'BASS_SetConfig', integer, [uint, uint])
    initialize = api(bass, 'BASS_Init', integer, [integer, uint, uint, pointer, pointer])
    free = api(bass, 'BASS_Free', integer, [])
    create = api(bass, 'BASS_StreamCreateFile', uint, [integer, pointer, quad, quad, uint])
    create_mixer = api(mixer, 'BASS_Mixer_StreamCreate', uint, [uint, uint, uint])
    add = api(mixer, 'BASS_Mixer_StreamAddChannel', integer, [uint, uint, uint])
    volume = api(bass, 'BASS_ChannelSetAttribute', integer, [uint, uint, c.c_float])
    play = api(bass, 'BASS_ChannelPlay', integer, [uint, integer])
    position = api(bass, 'BASS_ChannelGetPosition', quad, [uint, uint])
    audible_position = api(mixer, 'BASS_Mixer_ChannelGetPosition', quad, [uint, uint])
    seconds = api(bass, 'BASS_ChannelBytes2Seconds', c.c_double, [uint, quad])
    active = api(bass, 'BASS_ChannelIsActive', uint, [uint])
    get_device = api(bass, 'BASS_ChannelGetDevice', uint, [uint])
    report = {'platform': platform.platform(), 'requestedDevice': args.device,
              'startupDelaySeconds': args.startup_delay, 'deadlineSeconds': 10,
              'bassSha256': hashlib.sha256((args.library_dir / 'libbass.dylib').read_bytes()).hexdigest(),
              'calls': [], 'observations': [], 'advanced': False}

    def checked(name, result):
        code = error()
        report['calls'].append({'name': name, 'result': result, 'error': code})
        if not result:
            raise RuntimeError(f'{name}: BASS error {code}')
        return result

    wav = args.output / 'owned.wav'
    with wave.open(str(wav), 'wb') as output:
        output.setparams((1, 2, 16_000, 0, 'NONE', 'not compressed'))
        output.writeframes(bytes(16_000 * 2 * 12))

    failures = []

    def run():
        initialized = False
        try:
            checked('update period', configure(1, 100))
            checked('playback buffer', configure(0, 1_500))
            checked('device period', configure(53, 30))
            checked('initialize', initialize(args.device, 44_100, 0, None, None))
            initialized = True
            time.sleep(args.startup_delay)
            source = checked('source', create(0, c.cast(c.c_char_p(str(wav.resolve()).encode()), pointer),
                                              0, 0, 0x20000 | 0x100 | 0x200000))
            output = checked('mixer', create_mixer(16_000, 1, 0x100 | 0x2000 | 0x8000))
            checked('attach source', add(output, source, 0x800000))
            checked('mute', volume(output, 2, 0))
            checked('play', play(output, 0))

            def observe():
                return {'decoded': seconds(source, position(source, 0)),
                        'audible': seconds(source, audible_position(source, 0)),
                        'outputPosition': seconds(output, position(output, 0)), 'active': active(output)}

            start = time.monotonic()
            while time.monotonic() - start < 10:
                time.sleep(0.2)
                snapshot = {'elapsed': time.monotonic() - start, **observe()}
                report['observations'].append(snapshot)
                if snapshot['audible'] >= 0.2:
                    report['advanced'] = True
                    break
            report['channelDevice'] = get_device(output)
            if not report['advanced'] and args.recover:
                report['recovery'] = []
                start_output = api(bass, 'BASS_Start', integer, [])
                pause_output = api(bass, 'BASS_Pause', integer, [])
                for name, operation in [
                    ('start', start_output),
                    ('pause-start', lambda: pause_output() and start_output()),
                    ('reinitialize', lambda: initialize(get_device(output), 44_100, 0x80, None, None)),
                ]:
                    result = operation()
                    code = error()
                    begin = time.monotonic()
                    samples = []
                    while time.monotonic() - begin < 2:
                        time.sleep(0.2)
                        samples.append(observe())
                        if samples[-1]['audible'] >= 0.2:
                            break
                    report['recovery'].append({'name': name, 'result': result, 'error': code, 'observations': samples})
                    if samples[-1]['audible'] >= 0.2:
                        break
        except BaseException as failure:
            failures.append(failure)
            report['error'] = str(failure)
        finally:
            if initialized:
                free()

    worker = threading.Thread(target=run, name='native-output-probe')
    worker.start()
    worker.join()
    wav.unlink()
    (args.output / 'result.json').write_text(json.dumps(report, indent=2) + '\n')
    print(json.dumps({'output': str(args.output), 'advanced': report['advanced'], 'error': report.get('error')}))
    return 0 if report['advanced'] and not failures else 1


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--library-dir', type=Path, required=True, help='Directory containing libbass.dylib and libbassmix.dylib')
    parser.add_argument('--output', type=Path, required=True, help='New directory; existing evidence is never overwritten')
    parser.add_argument('--runs', type=int, default=1)
    parser.add_argument('--device', type=int, default=-1, help='Real device only; -1 follows the system default')
    parser.add_argument('--startup-delay', type=float, default=0.3)
    parser.add_argument('--recover', action='store_true', help='Try native recovery only after a failed deadline; result remains failed')
    args = parser.parse_args()
    if (sys.platform != 'darwin' or args.device == 0 or args.device < -1 or args.device > 4096 or
            args.runs < 1 or not math.isfinite(args.startup_delay) or args.startup_delay < 0):
        parser.error('Requires macOS, a real output device, positive runs and a nonnegative startup delay')
    args.library_dir = args.library_dir.resolve()
    args.output.mkdir(parents=True, exist_ok=False)
    if args.runs == 1:
        return attempt(args)
    results = []
    for index in range(1, args.runs + 1):
        command = [sys.executable, __file__, '--library-dir', str(args.library_dir),
                   '--output', str(args.output / f'run-{index}'), '--device', str(args.device),
                   '--startup-delay', str(args.startup_delay)]
        if args.recover:
            command.append('--recover')
        result = subprocess.run(command, timeout=45)
        results.append({'run': index, 'exit': result.returncode})
        (args.output / 'summary.json').write_text(json.dumps(results, indent=2) + '\n')
        if result.returncode:
            return result.returncode
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
