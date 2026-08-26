#!/usr/bin/env python3
import math
import os
import random
import struct
import wave

RATE = 32000
DURATION = 18.0
SAMPLES = int(RATE * DURATION)
OUT = "assets/audio/tyrannosaurus_rex/hell_creek_ambience.wav"
random.seed(14766026)

os.makedirs(os.path.dirname(OUT), exist_ok=True)

# Pre-build sparse distant calls so every CI build is deterministic.
calls = []
for _ in range(9):
    start = random.uniform(1.0, DURATION - 1.4)
    length = random.uniform(0.35, 0.85)
    freq = random.uniform(145.0, 310.0)
    depth = random.uniform(10.0, 34.0)
    calls.append((start, length, freq, depth, random.uniform(0, math.tau)))

# Small high-frequency chirps. These are deliberately abstract ambience, not
# claims about exact Cretaceous animal vocalizations.
chirps = []
for _ in range(34):
    start = random.uniform(0.2, DURATION - 0.25)
    length = random.uniform(0.035, 0.11)
    freq = random.uniform(1700.0, 3600.0)
    chirps.append((start, length, freq, random.uniform(-700.0, 900.0)))

last_noise = 0.0
slow_noise = 0.0
samples = []
for i in range(SAMPLES):
    t = i / RATE

    white = random.uniform(-1.0, 1.0)
    last_noise = last_noise * 0.965 + white * 0.035
    slow_noise = slow_noise * 0.997 + white * 0.003

    # Water + damp floodplain wind. Multiple slow modulations keep the bed alive.
    river_amp = 0.17 + 0.035 * math.sin(math.tau * 0.071 * t) + 0.022 * math.sin(math.tau * 0.113 * t + 1.7)
    river = last_noise * river_amp
    wind = slow_noise * (0.18 + 0.065 * math.sin(math.tau * 0.043 * t + 0.8))

    # Low, broad environmental resonance rather than a musical drone.
    low = 0.020 * math.sin(math.tau * 47.0 * t + 0.4 * math.sin(math.tau * 0.09 * t))
    low += 0.013 * math.sin(math.tau * 71.0 * t + 1.1)

    call_mix = 0.0
    for start, length, freq, depth, phase in calls:
        u = (t - start) / length
        if 0.0 <= u <= 1.0:
            env = math.sin(math.pi * u) ** 2
            sweep = freq - depth * u + 4.0 * math.sin(math.tau * 2.1 * u)
            call_mix += 0.045 * env * math.sin(math.tau * sweep * (t - start) + phase)
            call_mix += 0.018 * env * math.sin(math.tau * sweep * 0.51 * (t - start) + phase * 0.7)

    chirp_mix = 0.0
    for start, length, freq, sweep in chirps:
        u = (t - start) / length
        if 0.0 <= u <= 1.0:
            env = math.sin(math.pi * u) ** 3
            phase = math.tau * (freq * (t - start) + 0.5 * sweep * (t - start) ** 2 / max(length, 1e-6))
            chirp_mix += 0.018 * env * math.sin(phase)

    # Crossfade-compatible ends: fade first/last 0.45 s to a stable bed.
    edge = min(1.0, t / 0.45, (DURATION - t) / 0.45)
    signal = (river + wind + low + call_mix + chirp_mix) * max(0.25, edge)
    signal = math.tanh(signal * 1.35) * 0.72
    samples.append(int(max(-1.0, min(1.0, signal)) * 32767))

with wave.open(OUT, "wb") as wf:
    wf.setnchannels(1)
    wf.setsampwidth(2)
    wf.setframerate(RATE)
    wf.writeframes(b"".join(struct.pack("<h", s) for s in samples))

print(f"generated {OUT} ({SAMPLES} samples @ {RATE} Hz)")
