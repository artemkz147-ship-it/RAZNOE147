#!/usr/bin/env python3
"""Generate a deterministic low-frequency T. rex vocal reconstruction.

This is intentionally labelled a reconstruction, not a claim about the real
voice of Tyrannosaurus. It avoids copyrighted movie/stock roar samples.
"""

from __future__ import annotations

import math
import random
import struct
import wave
from pathlib import Path

SAMPLE_RATE = 48_000
DURATION = 4.8
SEED = 147


def clamp(value: float, lo: float, hi: float) -> float:
    return max(lo, min(hi, value))


def main() -> None:
    out = Path("assets/audio/tyrannosaurus_rex/roar_reconstruction.wav")
    out.parent.mkdir(parents=True, exist_ok=True)

    rng = random.Random(SEED)
    frames = int(SAMPLE_RATE * DURATION)
    phase = 0.0
    low_noise = 0.0
    slow_noise = 0.0
    samples: list[int] = []

    # One-pole coefficients for broad turbulent components.
    alpha_low = 1.0 - math.exp(-2.0 * math.pi * 380.0 / SAMPLE_RATE)
    alpha_slow = 1.0 - math.exp(-2.0 * math.pi * 45.0 / SAMPLE_RATE)

    for i in range(frames):
        t = i / SAMPLE_RATE
        white = rng.uniform(-1.0, 1.0)
        low_noise += alpha_low * (white - low_noise)
        slow_noise += alpha_slow * (white - slow_noise)

        attack = clamp(t / 0.45, 0.0, 1.0) ** 1.6
        decay = clamp((DURATION - t) / 0.75, 0.0, 1.0) ** 1.3
        envelope = attack * decay
        envelope *= 0.78 + 0.22 * math.sin(math.pi * t / DURATION) ** 2
        envelope *= 0.86 + 0.14 * math.sin(2.0 * math.pi * (0.72 * t + 0.05 * math.sin(2.0 * math.pi * 0.19 * t)))

        f0 = 47.0 + 5.0 * math.sin(2.0 * math.pi * 0.23 * t) + 2.0 * math.sin(2.0 * math.pi * 0.51 * t)
        phase += 2.0 * math.pi * f0 / SAMPLE_RATE

        harmonic = (
            0.46 * math.sin(phase)
            + 0.30 * math.sin(2.0 * phase + 0.7)
            + 0.18 * math.sin(3.0 * phase + 1.9)
            + 0.10 * math.sin(4.0 * phase + 0.2)
            + 0.055 * math.sin(5.0 * phase + 2.3)
        )

        sub = (
            0.45 * math.sin(2.0 * math.pi * (34.0 * t + 0.15 * math.sin(2.0 * math.pi * 0.17 * t)))
            + 0.28 * math.sin(2.0 * math.pi * (51.0 * t + 0.12 * math.sin(2.0 * math.pi * 0.13 * t + 0.6)))
        )

        # Moving low formants to suggest a very large resonating vocal tract.
        formants = (
            0.34 * math.sin(2.0 * math.pi * (82.0 + 2.5 * math.sin(2.0 * math.pi * 0.11 * t)) * t + 0.4)
            + 0.18 * math.sin(2.0 * math.pi * (156.0 + 4.0 * math.sin(2.0 * math.pi * 0.09 * t)) * t + 1.0)
            + 0.08 * math.sin(2.0 * math.pi * 272.0 * t + 2.0)
        )

        # Two gravelly air pulses, kept subordinate to the low bellow.
        breath_window = math.exp(-((t - 0.75) / 0.52) ** 2) + 0.7 * math.exp(-((t - 3.85) / 0.72) ** 2)
        breath = (low_noise - 0.45 * slow_noise) * breath_window * 0.55

        value = (0.58 * harmonic + 0.54 * sub + 0.34 * formants + 1.8 * low_noise + breath)
        value = math.tanh(1.45 * value) * envelope
        value *= 0.80  # headroom
        pcm = int(clamp(value, -1.0, 1.0) * 32767.0)
        samples.append(pcm)

    with wave.open(str(out), "wb") as wav:
        wav.setnchannels(1)
        wav.setsampwidth(2)
        wav.setframerate(SAMPLE_RATE)
        wav.writeframes(b"".join(struct.pack("<h", s) for s in samples))

    print(f"generated {out} ({frames} samples @ {SAMPLE_RATE} Hz)")


if __name__ == "__main__":
    main()
