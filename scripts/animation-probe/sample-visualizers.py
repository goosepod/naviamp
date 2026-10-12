#!/usr/bin/env python3
"""Sample an already-running isolated visualizer fixture using its test-only file protocol.

Run GPU traces separately, after this script completes. Captures and CPU/frame measurements are
provided by ApplicationRenderProbe.java; failed physical captures remain failed evidence.
"""
import argparse
from pathlib import Path
import time

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("output", type=Path)
parser.add_argument("--states", nargs="+", default=["static", "sphere", "analog", "sphere-combined", "analog-combined", "paused-sphere", "paused-analog"])
parser.add_argument("--prefix", default="sample")
parser.add_argument("--repeats", type=int, default=3)
parser.add_argument("--warmup", type=float, default=8)
args = parser.parse_args()
if args.repeats < 1 or args.warmup < 0:
    parser.error("repeats must be positive and warmup must be nonnegative")
allowed = set("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-_")
if any(not value or not set(value) <= allowed for value in [args.prefix, *args.states]):
    parser.error("prefix and states must contain only letters, digits, hyphens or underscores")

def wait_for(predicate, label):
    deadline = time.monotonic() + 30
    while not predicate():
        if time.monotonic() >= deadline:
            raise SystemExit(f"Timed out waiting for {label}")
        time.sleep(.1)

output = args.output
wait_for(lambda: (output / "state-ready").exists(), "fixture startup")
for state in args.states:
    (output / "state").write_text(state)
    wait_for(lambda: (output / "state-ready").read_text() == state, state)
    time.sleep(args.warmup)
    for repeat in range(1, args.repeats + 1):
        phase = f"{args.prefix}-{state}-{repeat}"
        result = output / f"{phase}.txt"
        if result.exists():
            raise SystemExit(f"Refusing to overwrite retained measurement: {result}")
        (output / "phase").write_text(phase)
        wait_for(result.exists, phase)
        print(result.read_text(), end="", flush=True)
        (output / f"{phase}-draw.txt").write_text((output / "draw-metrics").read_text())
