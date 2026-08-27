#!/usr/bin/env python3
from __future__ import annotations

import array
import math
import random
import wave
from pathlib import Path

SRC = Path("assets/audio/tyrannosaurus_rex/roar_realistic.wav")
RATE = 48000

PROFILES = {
    "triceratops": dict(speed=0.82, seconds=4.2, lowpass=1550.0, highpass=34.0, sub_hz=58.0, sub=0.18, noise=0.006, drive=1.25, tremolo=3.1),
    "velociraptor": dict(speed=1.58, seconds=2.35, lowpass=4200.0, highpass=150.0, sub_hz=0.0, sub=0.0, noise=0.035, drive=1.55, tremolo=9.0),
    "stegosaurus": dict(speed=0.70, seconds=4.6, lowpass=980.0, highpass=30.0, sub_hz=48.0, sub=0.20, noise=0.004, drive=1.35, tremolo=2.4),
    "apatosaurus": dict(speed=0.50, seconds=6.5, lowpass=620.0, highpass=24.0, sub_hz=34.0, sub=0.28, noise=0.003, drive=1.45, tremolo=1.6),
    "parasaurolophus": dict(speed=0.92, seconds=4.4, lowpass=1850.0, highpass=48.0, sub_hz=64.0, sub=0.12, noise=0.008, drive=1.28, tremolo=3.8),
}


def read_source() -> list[float]:
    with wave.open(str(SRC), "rb") as w:
        assert w.getnchannels() == 1
        assert w.getframerate() == RATE
        raw = array.array("h", w.readframes(w.getnframes()))
    return [x / 32768.0 for x in raw]


def lowpass(samples: list[float], cutoff: float) -> list[float]:
    if cutoff <= 0.0:
        return samples
    rc = 1.0 / (2.0 * math.pi * cutoff)
    dt = 1.0 / RATE
    alpha = dt / (rc + dt)
    y = 0.0
    out: list[float] = []
    for x in samples:
        y += alpha * (x - y)
        out.append(y)
    return out


def highpass(samples: list[float], cutoff: float) -> list[float]:
    if cutoff <= 0.0:
        return samples
    rc = 1.0 / (2.0 * math.pi * cutoff)
    dt = 1.0 / RATE
    alpha = rc / (rc + dt)
    last_x = 0.0
    y = 0.0
    out: list[float] = []
    for x in samples:
        y = alpha * (y + x - last_x)
        last_x = x
        out.append(y)
    return out


def resample_pitch(src: list[float], speed: float, count: int) -> list[float]:
    out: list[float] = []
    n = len(src)
    for i in range(count):
        p = i * speed
        i0 = int(p)
        if i0 >= n - 1:
            p = p % max(1, n - 1)
            i0 = int(p)
        frac = p - i0
        out.append(src[i0] * (1.0 - frac) + src[i0 + 1] * frac)
    return out


def build_species(species: str, profile: dict, src: list[float]) -> None:
    rng = random.Random(147 + len(species))
    count = int(RATE * profile["seconds"])
    data = resample_pitch(src, profile["speed"], count)
    data = highpass(data, profile["highpass"])
    data = lowpass(data, profile["lowpass"])

    out: list[float] = []
    attack = int(RATE * 0.018)
    release = int(RATE * 0.55)
    for i, x in enumerate(data):
        t = i / RATE
        env_in = min(1.0, (i + 1) / max(1, attack))
        env_out = min(1.0, (count - i) / max(1, release))
        env = min(env_in, env_out)
        trem = 1.0 + 0.075 * math.sin(2.0 * math.pi * profile["tremolo"] * t)
        sub = profile["sub"] * math.sin(2.0 * math.pi * profile["sub_hz"] * t) if profile["sub_hz"] > 0 else 0.0
        noise = rng.uniform(-1.0, 1.0) * profile["noise"]
        y = (x * trem + sub + noise) * env
        y = math.tanh(y * profile["drive"])
        out.append(y)

    peak = max(max(abs(x) for x in out), 1e-6)
    gain = 0.94 / peak
    pcm = array.array("h", [int(max(-1.0, min(1.0, x * gain)) * 32767.0) for x in out])

    dst = Path("assets/audio") / species / "roar_voice.wav"
    dst.parent.mkdir(parents=True, exist_ok=True)
    with wave.open(str(dst), "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(RATE)
        w.writeframes(pcm.tobytes())
    print(f"SPECIES_ROAR_OK id={species} frames={len(pcm)} bytes={dst.stat().st_size}")


def main() -> None:
    src = read_source()
    for species, profile in PROFILES.items():
        build_species(species, profile, src)


if __name__ == "__main__":
    main()
