#!/usr/bin/env python3
import array
import json
import re
import sys
import wave
from pathlib import Path

import torch

MODEL_PATH = Path(sys.argv[1] if len(sys.argv) > 1 else "/tmp/v5_5_ru.pt")
OUTPUT_PATH = Path(sys.argv[2] if len(sys.argv) > 2 else "assets/audio/tyrannosaurus_rex/narration_ru_raw.wav")
SAMPLE_RATE = 48_000
SPEAKER = "eugene"

with open("data/dinosaurs.json", encoding="utf-8") as f:
    data = json.load(f)
text = data["dinosaurs"][0]["narration_text_ru"].strip()

# Short sentence-sized chunks sound much more deliberate than asking the model
# to render the entire encyclopedia article as a single breath.
sentences = [s.strip() for s in re.split(r"(?<=[.!?])\s+", text) if s.strip()]
if not sentences:
    raise RuntimeError("Narration text is empty")

print(f"Loading Silero model: {MODEL_PATH}")
torch.set_num_threads(4)
model = torch.package.PackageImporter(str(MODEL_PATH)).load_pickle("tts_models", "model")
model.to(torch.device("cpu"))

parts = []
short_pause = torch.zeros(int(SAMPLE_RATE * 0.18), dtype=torch.float32)
long_pause = torch.zeros(int(SAMPLE_RATE * 0.28), dtype=torch.float32)
for index, sentence in enumerate(sentences):
    print(f"[{index + 1}/{len(sentences)}] {sentence}")
    audio = model.apply_tts(text=sentence, speaker=SPEAKER, sample_rate=SAMPLE_RATE)
    if not isinstance(audio, torch.Tensor):
        audio = torch.tensor(audio, dtype=torch.float32)
    audio = audio.detach().cpu().float().flatten()
    parts.append(audio)
    if index != len(sentences) - 1:
        # Slightly longer pause every few sentences keeps a documentary rhythm.
        parts.append(long_pause if (index + 1) % 3 == 0 else short_pause)

full = torch.cat(parts)
peak = float(full.abs().max()) if full.numel() else 0.0
if peak <= 0.0001:
    raise RuntimeError("Silero returned silent narration")
# Preserve dynamics; only prevent clipping before the mastering pass.
if peak > 0.97:
    full = full * (0.97 / peak)

# Avoid an unnecessary NumPy dependency in CI: convert the int16 tensor through
# Python's standard array module before writing the PCM WAV.
samples = (full.clamp(-1.0, 1.0) * 32767.0).to(torch.int16).tolist()
pcm = array.array("h", samples).tobytes()
OUTPUT_PATH.parent.mkdir(parents=True, exist_ok=True)
with wave.open(str(OUTPUT_PATH), "wb") as wav:
    wav.setnchannels(1)
    wav.setsampwidth(2)
    wav.setframerate(SAMPLE_RATE)
    wav.writeframes(pcm)

print(f"SILERO_NARRATION_OK speaker={SPEAKER} sample_rate={SAMPLE_RATE} frames={full.numel()}")
