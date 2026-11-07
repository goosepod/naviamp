#!/usr/bin/env python3
"""Generate the disposable three-track FLAC album used by the #520 native probe.

Requires numpy, soundfile and mutagen. Never point this at a personal music folder.
"""
import argparse
from pathlib import Path

import numpy as np
import soundfile as sf
from mutagen.flac import FLAC


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('output', type=Path, help='New or empty disposable album directory')
    args = parser.parse_args()
    args.output.mkdir(parents=True, exist_ok=True)
    if any(args.output.iterdir()):
        parser.error('output must be empty; existing files are never overwritten')
    rate = 44100
    timeline = np.arange(rate * 30, dtype=np.float64) / rate
    for index, frequency in enumerate((440, 554.365, 659.255), 1):
        mono = 0.04 * np.sin(2 * np.pi * frequency * timeline)
        path = args.output / f'{index:02d}-profile-probe.flac'
        sf.write(path, np.column_stack((mono, mono)), rate, format='FLAC', subtype='PCM_16')
        tags = FLAC(path)
        tags['title'] = f'FLAC profile probe {index}'
        tags['artist'] = ['Naviamp Test']
        tags['albumartist'] = ['Naviamp Test']
        tags['album'] = ['Gapless FLAC probe']
        tags['tracknumber'] = [str(index)]
        tags['replaygain_track_gain'] = ['-6.0 dB']
        tags['replaygain_album_gain'] = ['-3.0 dB']
        tags.save()
        print(sf.info(path))


if __name__ == '__main__':
    main()
