#!/usr/bin/env bash
set -euxo pipefail

mkdir -p assets/audio/tyrannosaurus_rex assets/audio/shared

# Real archosaur source layers for the T. rex reconstruction. Exact T. rex voice
# is unknown; this is intentionally labeled a reconstruction in the app/credits.
curl -L --fail --retry 3 -o /tmp/alligator1.ogg 'https://upload.wikimedia.org/wikipedia/commons/1/1a/Alligatorbellow1.ogg'
curl -L --fail --retry 3 -o /tmp/alligator2.ogg 'https://commons.wikimedia.org/wiki/Special:Redirect/file/27alligator2bellow.ogg'
curl -L --fail --retry 3 -o /tmp/forest.ogg 'https://commons.wikimedia.org/wiki/Special:Redirect/file/20090610_0_ambience.ogg'
curl -L --fail --retry 3 -o /tmp/stream.ogg 'https://commons.wikimedia.org/wiki/Special:Redirect/file/Swale.ogg'

# High-impact reconstruction: denser low-mid energy for phone speakers, immediate
# attack, and a hard peak ceiling. Runtime adds another +9.5 dB over the previous
# build and uses Godot's Master hard limiter.
ffmpeg -hide_banner -loglevel error -y \
  -i /tmp/alligator1.ogg -i /tmp/alligator2.ogg \
  -filter_complex "[0:a]aresample=48000,atrim=start=0.82:end=7.0,asetpts=PTS-STARTPTS,highpass=f=38,lowpass=f=2100,equalizer=f=180:t=q:w=0.9:g=4,acompressor=threshold=0.060:ratio=4.2:attack=2:release=145,volume=1.75[main];[0:a]aresample=48000,atrim=start=0.82:end=6.2,asetpts=PTS-STARTPTS,asetrate=39840,aresample=48000,lowpass=f=560,equalizer=f=82:t=q:w=0.8:g=8,volume=1.55[sub];[1:a]aresample=48000,atrim=start=4.0:end=10.5,asetpts=PTS-STARTPTS,asetrate=43200,aresample=48000,highpass=f=55,lowpass=f=1650,equalizer=f=330:t=q:w=1.0:g=3,volume=1.25[body];[main][sub][body]amix=inputs=3:normalize=0,acompressor=threshold=0.085:ratio=5.0:attack=1:release=150,equalizer=f=240:t=q:w=0.9:g=2.5,equalizer=f=720:t=q:w=1.1:g=1.8,volume=1.80,silenceremove=start_periods=1:start_duration=0.005:start_threshold=-44dB,alimiter=limit=0.965:attack=1:release=90,atrim=start=0:end=6.8,afade=t=out:st=6.15:d=0.65[out]" \
  -map '[out]' -ar 48000 -ac 1 -c:a pcm_s16le assets/audio/tyrannosaurus_rex/roar_realistic.wav

# Hell Creek ambience used by T. rex and Triceratops.
ffmpeg -hide_banner -loglevel error -y -i /tmp/forest.ogg -i /tmp/stream.ogg \
  -filter_complex "[0:a]atrim=start=4:end=49,asetpts=PTS-STARTPTS,volume=0.70[f];[1:a]aloop=loop=2:size=1000000000,atrim=start=0:end=45,asetpts=PTS-STARTPTS,volume=0.24[s];[f][s]amix=inputs=2:normalize=0,highpass=f=35,lowpass=f=12500[out]" \
  -map '[out]' -t 45 -c:a libvorbis -q:a 4 assets/audio/tyrannosaurus_rex/hell_creek_ambience.ogg

# Neutral synthetic beds avoid injecting identifiable modern animal calls into
# Morrison/Gobi/Early-Jurassic scenes.
ffmpeg -hide_banner -loglevel error -y \
  -f lavfi -i 'anoisesrc=color=pink:amplitude=0.055:sample_rate=48000:duration=45' \
  -f lavfi -i 'anoisesrc=color=brown:amplitude=0.022:sample_rate=48000:duration=45' \
  -filter_complex "[0:a]highpass=f=95,lowpass=f=7200,volume=0.70[w];[1:a]highpass=f=28,lowpass=f=420,volume=0.35[r];[w][r]amix=inputs=2:normalize=0,afade=t=in:st=0:d=1.5,afade=t=out:st=43:d=2[out]" \
  -map '[out]' -c:a libvorbis -q:a 3 assets/audio/shared/morrison_plain.ogg

ffmpeg -hide_banner -loglevel error -y \
  -f lavfi -i 'anoisesrc=color=pink:amplitude=0.050:sample_rate=48000:duration=45' \
  -filter_complex "highpass=f=160,lowpass=f=5200,equalizer=f=650:t=q:w=0.7:g=-4,volume=0.72,afade=t=in:st=0:d=1.2,afade=t=out:st=43:d=2[out]" \
  -map '[out]' -c:a libvorbis -q:a 3 assets/audio/shared/dry_wind.ogg

python3 - <<'PY'
import array, math, wave
path='assets/audio/tyrannosaurus_rex/roar_realistic.wav'
with wave.open(path,'rb') as w:
    assert w.getnchannels() == 1
    assert w.getframerate() == 48000
    first = w.readframes(int(w.getframerate() * 0.10))
    samples = array.array('h', first)
    rms = math.sqrt(sum(int(s)*int(s) for s in samples) / max(1, len(samples)))
    peak = max(abs(s) for s in samples)
    print('roar first-100ms rms=', round(rms,1), 'peak=', peak)
    assert rms > 2200, 'Roar attack is not dense enough for the 3x build'
    assert peak > 7000, 'Roar attack peak is too weak for the 3x build'
PY

# Generate one natural Russian offline narration per encyclopedia page.
python3 -m venv /tmp/silero-venv
/tmp/silero-venv/bin/pip install --quiet --upgrade pip
/tmp/silero-venv/bin/pip install --quiet torch --index-url https://download.pytorch.org/whl/cpu
curl -L --fail --retry 5 --retry-all-errors -o /tmp/v5_5_ru.pt 'https://models.silero.ai/models/tts/ru/v5_5_ru.pt'

for species_id in tyrannosaurus_rex triceratops velociraptor stegosaurus apatosaurus dilophosaurus; do
  mkdir -p "assets/audio/${species_id}"
  /tmp/silero-venv/bin/python tools/generate_silero_narration.py /tmp/v5_5_ru.pt "assets/audio/${species_id}/narration_ru_raw.wav" "${species_id}"
  ffmpeg -hide_banner -loglevel error -y -i "assets/audio/${species_id}/narration_ru_raw.wav" \
    -af "highpass=f=68,equalizer=f=125:t=q:w=0.8:g=1.4,equalizer=f=3000:t=q:w=1.0:g=1.0,acompressor=threshold=0.16:ratio=2.0:attack=12:release=160,loudnorm=I=-16:TP=-1.0:LRA=7" \
    -ar 48000 -ac 1 -c:a pcm_s16le "assets/audio/${species_id}/narration_ru.wav"
  rm -f "assets/audio/${species_id}/narration_ru_raw.wav"
  test -s "assets/audio/${species_id}/narration_ru.wav"
done

for required in \
  assets/audio/tyrannosaurus_rex/roar_realistic.wav \
  assets/audio/tyrannosaurus_rex/hell_creek_ambience.ogg \
  assets/audio/shared/morrison_plain.ogg \
  assets/audio/shared/dry_wind.ogg; do
  test -s "$required"
done

echo AUDIO_ASSETS_OK
