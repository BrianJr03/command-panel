#!/usr/bin/env python3
"""Flicker check for one bottom-screen handoff (#19), used by verify-plugin-handtest.sh.

    plugin-flicker.py <handoff.mp4> <bare.mp4>

<handoff.mp4> is a screenrecord of the bottom screen across one handoff (panel to plugin, or back). <bare.mp4> is a
short recording of the bottom screen with neither the panel nor the plugin up (what shows through a gap).

Each frame is shrunk to 64x64 gray and matched to the nearest of four references: the steady state before the
handoff (the first frames), the one after (the last frames), the bare screen, and black. A fade between "before" and
"after" stays nearest to one of them; a frame nearest "bare" or "black" means neither UI was drawn. Prints one
key=value per line: frames, black_frames, gap_frames (longest run of bare/black), gap_ms, worst_gap_at_ms.
"""
import subprocess
import sys

import numpy as np

SIZE = 64


def frames(path):
    """(times in ms, frames as float arrays) for every frame of the video, in order."""
    probe = subprocess.run(
        ["ffprobe", "-v", "error", "-select_streams", "v:0", "-show_entries", "frame=pts_time",
         "-of", "csv=p=0", path], capture_output=True, text=True, check=True)
    times = [float(t) * 1000 for t in probe.stdout.split() if t.strip() not in ("", "N/A")]
    raw = subprocess.run(
        ["ffmpeg", "-v", "error", "-i", path, "-fps_mode", "passthrough", "-vf", f"scale={SIZE}:{SIZE},format=gray",
         "-f", "rawvideo", "-"], capture_output=True, check=True).stdout
    n = len(raw) // (SIZE * SIZE)
    data = np.frombuffer(raw[: n * SIZE * SIZE], dtype=np.uint8).reshape(n, SIZE, SIZE).astype(np.float32)
    times = (times + [times[-1] if times else 0.0] * n)[:n]
    return times, data


def main():
    times, video = frames(sys.argv[1])
    _, bare = frames(sys.argv[2])
    if len(video) < 6 or len(bare) == 0:
        print("error=too few frames")
        return 1
    refs = {
        "before": np.median(video[:3], axis=0),
        "after": np.median(video[-3:], axis=0),
        "bare": np.median(bare, axis=0),
        "black": np.zeros((SIZE, SIZE), dtype=np.float32),
    }
    labels = []
    black_frames = 0
    for f in video:
        if f.mean() < 8:
            black_frames += 1
            labels.append("black")
            continue
        labels.append(min(refs, key=lambda k: float(np.abs(f - refs[k]).mean())))
    best = (0, 0.0, 0.0)  # frames, ms, start ms
    run_start = None
    for i, label in enumerate(labels + ["end"]):
        gap = label in ("bare", "black")
        if gap and run_start is None:
            run_start = i
        elif not gap and run_start is not None:
            end_ms = times[i] if i < len(times) else times[-1]
            run = (i - run_start, end_ms - times[run_start], times[run_start] - times[0])
            if run[0] > best[0]:
                best = run
            run_start = None
    print(f"frames={len(video)}")
    print(f"black_frames={black_frames}")
    print(f"gap_frames={best[0]}")
    print(f"gap_ms={best[1]:.0f}")
    print(f"worst_gap_at_ms={best[2]:.0f}")
    print("labels=" + "".join({"before": "B", "after": "A", "bare": "_", "black": "#"}[l] for l in labels))
    return 0


if __name__ == "__main__":
    sys.exit(main())
