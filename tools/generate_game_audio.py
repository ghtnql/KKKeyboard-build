#!/usr/bin/env python3
"""Deterministic original BGM + SFX generator for KKKeyboard typing games.

Creates ORIGINAL compositions (no external melodies/assets) as mono
22050 Hz PCM16 little-endian WAV files.

Music tracks (layered bass/chords/melody, integer bars, 15-30 s each):
  practice_loop.wav - 80 BPM, 8 bars 4/4 = 24.000 s, quiet soft plucked learning melody
  cafe_loop.wav     - 90 BPM, 8 bars 4/4 = 21.333 s, warm playful gentle jazzy maj7 broken chords
  rain_loop.wav     - 110 BPM, 8 bars 4/4 = 17.455 s, bright light chiptune pentatonic motifs

SFX (0.08-0.9 s, distinct soft timbres):
  start.wav, hit.wav, error.wav, combo.wav, miss.wav, finish.wav

Usage:
  python3 tools/generate_game_audio.py [--output-dir shared/audio] [--preview-file PATH]

Total assets stay well under 5 MB. Deterministic: fixed seeds, no I/O besides output.
"""
import argparse
import math
import random
import struct
import wave
from pathlib import Path

SR = 22050
PEAK_LIMIT = 0.85

# ---------------------------------------------------------------- helpers

def midi_to_freq(m):
    return 440.0 * (2.0 ** ((m - 69) / 12.0))


def empty(n):
    return [0.0] * n


def add_pluck(buf, start_s, dur_s, freq, amp, bright=0.35, decay=4.0):
    s0 = int(start_s * SR)
    n = int(dur_s * SR)
    for i in range(n):
        t = i / SR
        env = math.exp(-t * decay)
        # smooth attack to avoid clicks (5 ms raised cosine)
        a = min(1.0, i / (0.005 * SR))
        attack = 0.5 - 0.5 * math.cos(math.pi * a)
        v = (math.sin(2 * math.pi * freq * t)
             + bright * 0.5 * math.sin(2 * math.pi * freq * 2 * t)
             + bright * 0.2 * math.sin(2 * math.pi * freq * 3 * t)) * env * attack
        idx = s0 + i
        if 0 <= idx < len(buf):
            buf[idx] += v * amp


def add_pad(buf, start_s, dur_s, freq, amp):
    s0 = int(start_s * SR)
    n = int(dur_s * SR)
    atk = int(0.08 * SR)
    rel = int(0.15 * SR)
    for i in range(n):
        t = i / SR
        e = 1.0
        if i < atk:
            e = 0.5 - 0.5 * math.cos(math.pi * i / atk)
        elif i >= n - rel:
            j = n - 1 - i
            e = 0.5 - 0.5 * math.cos(math.pi * max(j, 0) / rel)
        v = math.sin(2 * math.pi * freq * t) + 0.25 * math.sin(2 * math.pi * freq * 2 * t)
        idx = s0 + i
        if 0 <= idx < len(buf):
            buf[idx] += v * amp * e


def add_lead(buf, start_s, dur_s, freq, amp, squareish=False):
    s0 = int(start_s * SR)
    n = int(dur_s * SR)
    atk = max(1, int(0.008 * SR))
    rel = max(1, int(0.06 * SR))
    for i in range(n):
        t = i / SR
        if squareish:
            v = (math.sin(2 * math.pi * freq * t)
                 + 0.3 * math.sin(2 * math.pi * 2 * freq * t)
                 + 0.12 * math.sin(2 * math.pi * 3 * freq * t))
        else:
            v = math.sin(2 * math.pi * freq * t) + 0.2 * math.sin(2 * math.pi * 2 * freq * t)
        e = 1.0
        if i < atk:
            e = 0.5 - 0.5 * math.cos(math.pi * i / atk)
        elif i >= n - rel:
            j = n - 1 - i
            e = 0.5 - 0.5 * math.cos(math.pi * max(j, 0) / rel)
        idx = s0 + i
        if 0 <= idx < len(buf):
            buf[idx] += v * amp * e


def add_wrap_lead(buf, loop_s, start_s, dur_s, freq, amp, squareish=False):
    """Add a note, wrapping overflow across the loop boundary to sample 0."""
    n_total = len(buf)
    s0 = int(start_s * SR)
    n = int(dur_s * SR)
    atk = max(1, int(0.008 * SR))
    rel = max(1, int(0.06 * SR))
    for i in range(n):
        t = i / SR
        if squareish:
            v = (math.sin(2 * math.pi * freq * t)
                 + 0.3 * math.sin(2 * math.pi * 2 * freq * t)
                 + 0.12 * math.sin(2 * math.pi * 3 * freq * t))
        else:
            v = math.sin(2 * math.pi * freq * t) + 0.2 * math.sin(2 * math.pi * 2 * freq * t)
        e = 1.0
        if i < atk:
            e = 0.5 - 0.5 * math.cos(math.pi * i / atk)
        elif i >= n - rel:
            j = n - 1 - i
            e = 0.5 - 0.5 * math.cos(math.pi * max(j, 0) / rel)
        idx = (s0 + i) % n_total
        buf[idx] += v * amp * e


def add_brushed(buf, start_s, dur_s=0.12, amp=0.10, seed=1, lowpass=0.25):
    rng = random.Random(seed)
    s0 = int(start_s * SR)
    n = int(dur_s * SR)
    prev = 0.0
    for i in range(n):
        w = rng.uniform(-1.0, 1.0)
        prev = prev * (1.0 - lowpass) + w * lowpass  # soft lowpass = brushed
        t = i / SR
        env = math.exp(-t * 30.0)
        idx = s0 + i
        if 0 <= idx < len(buf):
            buf[idx] += prev * amp * env


def add_soft_kick(buf, start_s, amp=0.18, f0=120.0, f1=55.0, dur_s=0.14):
    s0 = int(start_s * SR)
    n = int(dur_s * SR)
    for i in range(n):
        t = i / SR
        f = f1 + (f0 - f1) * math.exp(-t * 30.0)
        phase = 2 * math.pi * f * t
        env = math.exp(-t * 22.0)
        idx = s0 + i
        if 0 <= idx < len(buf):
            buf[idx] += math.sin(phase) * amp * env


def normalize(buf, peak=PEAK_LIMIT):
    m = max((abs(v) for v in buf), default=0.0)
    if m > peak and m > 0:
        s = peak / m
        for i in range(len(buf)):
            buf[i] *= s
    return m


def edge_fade(buf, ms=15.0):
    n = int(ms / 1000.0 * SR)
    n = min(n, len(buf) // 2)
    for i in range(n):
        g = 0.5 - 0.5 * math.cos(math.pi * i / n)
        buf[i] *= g
        buf[len(buf) - 1 - i] *= g


def write_wav(path, buf):
    peak = PEAK_LIMIT
    m = max((abs(v) for v in buf), default=0.0)
    if m > peak:
        s = peak / m
        buf = [v * s for v in buf]
    data = struct.pack("<" + "h" * len(buf),
                       *(max(-32768, min(32767, int(round(v * 32767)))) for v in buf))
    path.parent.mkdir(parents=True, exist_ok=True)
    with wave.open(str(path), "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(data)


# ---------------------------------------------------------------- compositions (ORIGINAL)
# MIDI note numbers. All melodies/chords below were authored for this file.

# practice: C major gentle learning tune, 8 bars, one chord per bar
PRACTICE_CHORDS = [  # (root midi, chord tones)
    (48, [60, 64, 67]),  # C
    (53, [57, 60, 65]),  # F
    (48, [60, 64, 67]),  # C
    (55, [59, 62, 67]),  # G
    (53, [57, 60, 65]),  # F
    (48, [60, 64, 67]),  # C
    (55, [59, 62, 67]),  # G
    (48, [60, 64, 67]),  # C
]
# (bar_index, beat_offset_in_bar, midi, beats_long)
PRACTICE_MELODY = [
    (0, 0.0, 72, 1.0), (0, 1.0, 74, 1.0), (0, 2.0, 76, 2.0),
    (1, 0.0, 77, 1.5), (1, 1.5, 76, 0.5), (1, 2.0, 74, 2.0),
    (2, 0.0, 72, 1.0), (2, 1.0, 74, 1.0), (2, 2.0, 76, 1.0), (2, 3.0, 79, 1.0),
    (3, 0.0, 77, 2.0), (3, 2.0, 74, 2.0),
    (4, 0.0, 76, 1.0), (4, 1.0, 77, 1.0), (4, 2.0, 81, 2.0),
    (5, 0.0, 79, 1.5), (5, 1.5, 76, 0.5), (5, 2.0, 74, 2.0),
    (6, 0.0, 74, 1.0), (6, 1.0, 77, 1.0), (6, 2.0, 71, 2.0),
    (7, 0.0, 72, 4.0),
]
PRACTICE_BASS = [48, 53, 48, 55, 53, 48, 55, 48]  # roots per bar (C F C G F C G C)

# cafe: warm jazzy maj7, broken chords, 8 bars, swing-ish straight eighths
CAFE_CHORDS = [  # maj7 voicings, midi
    [60, 64, 67, 71],  # Cmaj7
    [57, 60, 64, 69],  # Am7 -> use as Am7
    [53, 57, 60, 65],  # Fmaj7
    [55, 59, 62, 65],  # G7-ish (no alarm, soft)
    [60, 64, 67, 71],  # Cmaj7
    [53, 57, 60, 65],  # Fmaj7
    [55, 59, 62, 67],  # G add
    [60, 64, 67, 71],  # Cmaj7
]
CAFE_MELODY = [  # (bar, eighth_offset 0..7, midi)
    (0, 0, 72), (0, 2, 76), (0, 4, 79), (0, 6, 76),
    (1, 0, 76), (1, 2, 72), (1, 4, 69), (1, 6, 72),
    (2, 0, 77), (2, 2, 76), (2, 4, 74), (2, 6, 72),
    (3, 0, 74), (3, 2, 71), (3, 4, 74), (3, 6, 79),
    (4, 0, 81), (4, 2, 79), (4, 4, 76), (4, 6, 74),
    (5, 0, 74), (5, 2, 77), (5, 4, 76), (5, 6, 74),
    (6, 0, 71), (6, 2, 74), (6, 4, 79), (6, 6, 83),
    (7, 0, 84), (7, 2, 79), (7, 4, 76), (7, 6, 72),
]
CAFE_BASSLINE = [36, 33, 29, 31, 36, 29, 31, 36]  # roots C A F G..., per bar alternate fifths below

# rain: bright chiptune, C pentatonic ascending motifs, 8 bars
RAIN_PENTA = [72, 74, 76, 79, 81, 84]
RAIN_MELODY = [  # (bar, sixteenth_offset 0..15, midi)
    (0, 0, 72), (0, 2, 74), (0, 4, 76), (0, 6, 79), (0, 8, 81), (0, 12, 79),
    (1, 0, 81), (1, 4, 79), (1, 8, 76), (1, 12, 74),
    (2, 0, 72), (2, 2, 76), (2, 4, 79), (2, 8, 81), (2, 12, 84),
    (3, 0, 84), (3, 4, 81), (3, 8, 79), (3, 12, 76),
    (4, 0, 76), (4, 2, 79), (4, 4, 81), (4, 8, 84), (4, 12, 81),
    (5, 0, 79), (5, 4, 76), (5, 8, 74), (5, 12, 72),
    (6, 0, 72), (6, 4, 76), (6, 8, 79), (6, 12, 81),
    (7, 0, 84), (7, 4, 81), (7, 8, 79), (7, 12, 76),
]
RAIN_CHORDS = [  # light triads per bar
    [60, 64, 67], [57, 60, 64], [53, 57, 60], [55, 59, 62],
    [60, 64, 67], [53, 57, 60], [55, 59, 62], [60, 64, 67],
]
RAIN_BASS = [48, 45, 41, 43, 48, 41, 43, 48]


# ---------------------------------------------------------------- builders

def build_practice():
    bpm = 80.0
    beat = 60.0 / bpm
    bar = 4 * beat
    bars = 8
    total = bars * bar  # 24.0 s
    buf = empty(int(total * SR))
    # bass + soft chord pads + plucked melody
    for b, (root, tones) in enumerate(PRACTICE_CHORDS):
        t = b * bar
        add_pluck(buf, t, bar * 0.9, midi_to_freq(root - 12), 0.30, bright=0.15, decay=3.0)
        for tone in tones:
            add_pad(buf, t, bar * 0.95, midi_to_freq(tone), 0.055)
    for (b, off, m, blen) in PRACTICE_MELODY:
        t = b * bar + off * beat
        d = blen * beat * 0.95
        add_wrap_lead(buf, total, t, d, midi_to_freq(m), 0.22)
    # gentle brushed ticks on beats 2 and 4
    tick = 0
    for b in range(bars):
        for beat_i in (1, 3):
            add_brushed(buf, b * bar + beat_i * beat, 0.09, 0.05, seed=1000 + tick)
            tick += 1
    normalize(buf)
    edge_fade(buf)
    return buf


def build_cafe():
    bpm = 90.0
    beat = 60.0 / bpm
    bar = 4 * beat
    bars = 8
    total = bars * bar  # 21.333 s
    buf = empty(int(total * SR))
    eighth = beat / 2.0
    for b, chord in enumerate(CAFE_CHORDS):
        t = b * bar
        # broken-chord plucks cycling through voicing
        for k in range(8):
            tone = chord[k % len(chord)]
            add_pluck(buf, t + k * eighth, eighth * 1.8, midi_to_freq(tone), 0.16,
                      bright=0.25, decay=5.0)
        # warm low root + fifth
        root = CAFE_BASSLINE[b]
        add_pad(buf, t, bar * 0.9, midi_to_freq(root), 0.20)
        add_pad(buf, t, bar * 0.9, midi_to_freq(root + 7), 0.10)
    for (b, eoff, m) in CAFE_MELODY:
        t = b * bar + eoff * eighth
        add_wrap_lead(buf, total, t, eighth * 1.6, midi_to_freq(m), 0.13)
    # brushed/simple percussion: soft kick on 1, brush on 2 & 4
    for b in range(bars):
        t = b * bar
        add_soft_kick(buf, t, amp=0.10)
        add_brushed(buf, t + 1 * beat, 0.10, 0.07, seed=2000 + b * 2)
        add_brushed(buf, t + 3 * beat, 0.10, 0.07, seed=2000 + b * 2 + 1)
    normalize(buf)
    edge_fade(buf)
    return buf


def build_rain():
    bpm = 110.0
    beat = 60.0 / bpm
    bar = 4 * beat
    bars = 8
    total = bars * bar  # ~17.455 s
    buf = empty(int(total * SR))
    sixteenth = beat / 4.0
    for b, chord in enumerate(RAIN_CHORDS):
        t = b * bar
        for tone in chord:
            add_pad(buf, t, bar * 0.9, midi_to_freq(tone), 0.045)
        add_pluck(buf, t, beat * 1.2, midi_to_freq(RAIN_BASS[b] - 12), 0.22,
                  bright=0.2, decay=4.0)
        add_pluck(buf, t + 2 * beat, beat * 1.2, midi_to_freq(RAIN_BASS[b] - 12 + 7), 0.15,
                  bright=0.2, decay=4.0)
    for (b, soff, m) in RAIN_MELODY:
        t = b * bar + soff * sixteenth
        add_wrap_lead(buf, total, t, sixteenth * 2.2, midi_to_freq(m), 0.17, squareish=True)
    # clear light pulse: soft tick every beat
    for b in range(bars):
        for q in range(4):
            add_brushed(buf, b * bar + q * beat, 0.05, 0.045, seed=3000 + b * 4 + q,
                        lowpass=0.45)
    normalize(buf)
    edge_fade(buf)
    return buf


def tone(buf, start_s, dur_s, freqs, amp_each, decay=6.0):
    if isinstance(freqs, (int, float)):
        freqs = [freqs]
    s0 = int(start_s * SR)
    n = int(dur_s * SR)
    atk = max(1, int(0.006 * SR))
    rel = max(1, int(min(0.08, dur_s / 2) * SR))
    for i in range(n):
        t = i / SR
        e = math.exp(-t * decay)
        if i < atk:
            e *= 0.5 - 0.5 * math.cos(math.pi * i / atk)
        elif i >= n - rel:
            j = n - 1 - i
            e *= 0.5 - 0.5 * math.cos(math.pi * max(j, 0) / rel)
        v = sum(math.sin(2 * math.pi * f * t) + 0.2 * math.sin(4 * math.pi * f * t)
                for f in freqs) / max(1, len(freqs))
        idx = s0 + i
        if 0 <= idx < len(buf):
            buf[idx] += v * amp_each * e


def build_sfx(name):
    specs = {
        "start": 0.45,
        "hit": 0.18,
        "error": 0.40,
        "combo": 0.60,
        "miss": 0.30,
        "finish": 0.85,
    }
    dur = specs[name]
    buf = empty(int(dur * SR))
    if name == "start":  # two rising soft notes C5 -> G5
        tone(buf, 0.0, 0.20, midi_to_freq(72), 0.5)
        tone(buf, 0.18, 0.27, midi_to_freq(79), 0.5)
    elif name == "hit":  # short sparkle high ping
        tone(buf, 0.0, 0.18, [midi_to_freq(88), midi_to_freq(93)], 0.45, decay=18.0)
    elif name == "error":  # gentle descending two-tone, not an alarm
        tone(buf, 0.0, 0.20, midi_to_freq(69), 0.45, decay=5.0)
        tone(buf, 0.18, 0.22, midi_to_freq(65), 0.45, decay=5.0)
    elif name == "combo":  # ascending bright 3 notes C6 D6 E6
        tone(buf, 0.0, 0.18, midi_to_freq(84), 0.45)
        tone(buf, 0.17, 0.18, midi_to_freq(86), 0.45)
        tone(buf, 0.34, 0.26, midi_to_freq(88), 0.5)
    elif name == "miss":  # soft droplet: quick downward chirp
        s0 = 0
        n = len(buf)
        f0, f1 = midi_to_freq(81), midi_to_freq(69)
        for i in range(n):
            t = i / SR
            f = f0 + (f1 - f0) * (i / max(1, n - 1))
            env = math.exp(-t * 10.0) * (0.5 - 0.5 * math.cos(math.pi * min(1.0, i / max(1, int(0.006 * SR))))) \
                if i < int(0.006 * SR) else math.exp(-t * 10.0)
            v = math.sin(2 * math.pi * f * t)
            # gentle release
            rel = int(0.08 * SR)
            if i >= n - rel:
                j = n - 1 - i
                env *= 0.5 - 0.5 * math.cos(math.pi * max(j, 0) / rel)
            buf[i] += v * 0.5 * env
    elif name == "finish":  # cheerful short cadence C E G C6
        seq = [72, 76, 79, 84]
        for k, m in enumerate(seq):
            tone(buf, k * 0.16, 0.22 if k < 3 else 0.30, midi_to_freq(m), 0.5)
    normalize(buf)
    edge_fade(buf, ms=8.0)
    return buf


def main():
    ap = argparse.ArgumentParser(description="Generate original KKKeyboard game audio.")
    ap.add_argument("--output-dir", default="shared/audio")
    ap.add_argument("--preview-file", default=None)
    args = ap.parse_args()
    out = Path(args.output_dir)
    out.mkdir(parents=True, exist_ok=True)

    # File-name -> composition mapping intentionally swapped by user request.
    tracks = {
        "practice_loop.wav": build_practice(),
        "cafe_loop.wav": build_rain(),
        "rain_loop.wav": build_cafe(),
    }
    for fn, buf in tracks.items():
        write_wav(out / fn, buf)
    for name in ("start", "hit", "error", "combo", "miss", "finish"):
        write_wav(out / f"{name}.wav", build_sfx(name))

    if args.preview_file:
        # ~9 s montage: 2 s of each track + 0.3 s of each SFX (6 x 0.3 = 1.8 s) + padding
        parts = []
        for buf in tracks.values():
            parts += buf[: 2 * SR]
        for name in ("start", "hit", "error", "combo", "miss", "finish"):
            s = build_sfx(name)
            seg = s[: int(0.3 * SR)]
            seg += [0.0] * max(0, int(0.3 * SR) - len(seg))
            parts += seg
        target = 9 * SR
        if len(parts) < target:
            parts += [0.0] * (target - len(parts))
        else:
            parts = parts[:target]
        normalize(parts)
        write_wav(Path(args.preview_file), parts)


if __name__ == "__main__":
    main()
