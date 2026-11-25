#!/usr/bin/env python3
"""Generates a synthetic music library with real encoder and muxer headers (#662 spike).

Each format is encoded once with ffmpeg, then remuxed with `-c copy` and per-file tags, so every
file carries headers written by a real muxer. A manifest records the tags that were written.
"""
import argparse
import concurrent.futures
import os
import random
import subprocess
import sys

FORMATS = [
    # name, extension, encoder arguments, supports attached artwork, supports ReplayGain keys
    ("mp3v24", "mp3", ["-c:a", "libmp3lame", "-b:a", "128k"], True, True),
    ("mp3v23", "mp3", ["-c:a", "libmp3lame", "-q:a", "4"], True, True),
    ("flac", "flac", ["-c:a", "flac"], True, True),
    ("vorbis", "ogg", ["-c:a", "libvorbis", "-q:a", "2"], False, True),
    ("opus", "opus", ["-c:a", "libopus", "-b:a", "64k"], False, True),
    ("aac", "m4a", ["-c:a", "aac", "-b:a", "128k"], True, False),
    ("alac", "m4a", ["-c:a", "alac"], True, False),
    ("wavpack", "wv", ["-c:a", "wavpack"], False, True),
]
GENRES = ["Rock", "Jazz", "Electronic", "Classical", "Hip-Hop", "Folk", "Ambient", "Metal"]
WORDS = ["Night", "River", "Signal", "Glass", "Echo", "Harbor", "Velvet", "Static", "Ember", "North",
         "Lumen", "Cedar", "Orbit", "Prism", "Hollow", "Drift", "Copper", "Meadow", "Pulse", "Tide"]


def run(arguments):
    subprocess.run(arguments, check=True, stdout=subprocess.DEVNULL, stderr=subprocess.PIPE)


def templates(work, seconds):
    paths = {}
    for name, extension, codec, _, _ in FORMATS:
        path = os.path.join(work, f"template-{name}-{seconds}.{extension}")
        if not os.path.exists(path):
            run(["ffmpeg", "-y", "-hide_banner", "-f", "lavfi", "-i", f"sine=frequency=440:duration={seconds}",
                 "-ac", "2", "-ar", "48000" if name == "opus" else "44100", "-map_metadata", "-1", *codec, path])
        paths[name] = path
    cover = os.path.join(work, "cover.jpg")
    if not os.path.exists(cover):
        run(["ffmpeg", "-y", "-hide_banner", "-f", "lavfi", "-i", "testsrc=size=600x600:rate=1",
             "-frames:v", "1", cover])
    return paths, cover


def remux(job):
    source, cover, target, tags, artwork, id3v23 = job
    arguments = ["ffmpeg", "-y", "-hide_banner", "-i", source]
    if artwork:
        arguments += ["-i", cover, "-map", "0:a", "-map", "1:v", "-c", "copy", "-disposition:v", "attached_pic"]
    else:
        arguments += ["-map", "0:a", "-c", "copy"]
    for key, value in tags.items():
        arguments += ["-metadata", f"{key}={value}"]
    if target.endswith(".mp3"):
        arguments += ["-id3v2_version", "3" if id3v23 else "4"]
    run(arguments + [target])


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("output")
    parser.add_argument("--count", type=int, default=20000)
    parser.add_argument("--long", type=int, default=0, help="extra 180 second files per format")
    parser.add_argument("--workers", type=int, default=os.cpu_count() or 4)
    parser.add_argument("--seed", type=int, default=662)
    options = parser.parse_args()
    random.seed(options.seed)
    library = os.path.join(options.output, "library")
    work = os.path.join(options.output, "work")
    os.makedirs(library, exist_ok=True)
    os.makedirs(work, exist_ok=True)
    short, cover = templates(work, 2)
    long = templates(work, 180)[0] if options.long else {}

    jobs, manifest = [], []
    def add(index, fmt, source_paths):
        name, extension, _, art_capable, rg_capable = fmt
        # Ten tracks per album and five albums per artist, stable for a given index.
        artist = f"Artist {index // 50:04d} {random.Random(index // 50).choice(WORDS)}"
        album_words = random.Random(10_000_000 + index // 10)
        album = f"{album_words.choice(WORDS)} {album_words.choice(WORDS)} {index // 10:05d}"
        track = index % 10 + 1
        title = f"{random.choice(WORDS)} {random.choice(WORDS)} {index:05d}"
        if index % 97 == 0:
            title += " été 音楽"
        year = 1960 + index % 60
        tags = {"title": title, "artist": artist, "album_artist": artist, "album": album,
                "track": f"{track}/10", "disc": "1/1", "date": str(year), "genre": random.choice(GENRES)}
        gain = ""
        if rg_capable and index % 3 == 0:
            gain = f"{-random.uniform(1, 12):.2f}"
            tags["REPLAYGAIN_TRACK_GAIN"] = f"{gain} dB"
            tags["REPLAYGAIN_TRACK_PEAK"] = "0.988525"
        artwork = art_capable and index % 10 == 0
        directory = os.path.join(library, artist, album)
        os.makedirs(directory, exist_ok=True)
        relative = os.path.relpath(os.path.join(directory, f"{track:02d} {title}.{extension}"), library)
        jobs.append((source_paths[name], cover, os.path.join(library, relative), tags, artwork, name == "mp3v23"))
        manifest.append("\t".join([relative, name, title, artist, artist, album, str(track), "1", str(year),
                                   tags["genre"], "1" if artwork else "0", gain]))

    for index in range(options.count):
        add(index, FORMATS[index % len(FORMATS)], short)
    for index in range(options.long * len(FORMATS)):
        add(options.count + index, FORMATS[index % len(FORMATS)], long)

    with concurrent.futures.ThreadPoolExecutor(options.workers) as pool:
        for done, _ in enumerate(pool.map(remux, jobs), 1):
            if done % 2000 == 0:
                print(f"{done}/{len(jobs)}", file=sys.stderr)
    with open(os.path.join(options.output, "manifest.tsv"), "w", encoding="utf-8") as output:
        output.write("\n".join(manifest) + "\n")


if __name__ == "__main__":
    main()
