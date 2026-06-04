#!/usr/bin/env python3
"""Synthesize the game's original audio assets (music loops + SFX).

All sounds are generated procedurally from first principles with the Python
standard library only (``wave``/``math``/``struct`` — no third-party deps), so
they are 100% original works, license-clean (CC0) and reproducible. Output is
16-bit mono PCM WAV written into ``app/src/main/res/raw/``.

Run from anywhere:

    python3 tools/audio/generate_audio.py

Music loops are rendered to an exact integer number of beats and every note is
enveloped to start and end at zero amplitude, so the last sample joins the first
without a click — i.e. they loop seamlessly under ``MediaPlayer``.
"""

from __future__ import annotations

import math
import os
import struct
import wave

SR = 22050  # sample rate (Hz); modest rate keeps the WAVs small.
RES_RAW = os.path.normpath(
    os.path.join(os.path.dirname(__file__), "..", "..", "app", "src", "main", "res", "raw")
)

# --- Core synthesis -------------------------------------------------------

A4 = 440.0


def note_freq(semitones_from_a4: int) -> float:
    """Equal-tempered frequency for a semitone offset from A4."""
    return A4 * (2.0 ** (semitones_from_a4 / 12.0))


def _osc(phase: float, wave_kind: str) -> float:
    """One sample of a unit-amplitude oscillator at the given phase (turns)."""
    t = phase - math.floor(phase)  # wrap to [0, 1)
    if wave_kind == "sine":
        return math.sin(2.0 * math.pi * t)
    if wave_kind == "triangle":
        return 4.0 * abs(t - 0.5) - 1.0
    if wave_kind == "square":
        return 1.0 if t < 0.5 else -1.0
    if wave_kind == "saw":
        return 2.0 * t - 1.0
    raise ValueError(f"unknown wave: {wave_kind}")


def _env(i: int, n: int, attack: float, release: float) -> float:
    """Linear attack/release envelope (fractions of the note) → [0, 1].

    Both ends reach exactly zero so concatenated notes never click.
    """
    a = max(1, int(n * attack))
    r = max(1, int(n * release))
    if i < a:
        return i / a
    if i > n - r:
        return max(0.0, (n - i) / r)
    return 1.0


def tone(buf: list[float], start: int, freq: float, dur: float, *,
         wave_kind: str = "square", vol: float = 0.5,
         attack: float = 0.02, release: float = 0.2,
         glide_to: float | None = None) -> None:
    """Add an enveloped (optionally pitch-gliding) tone into ``buf`` in place."""
    n = int(dur * SR)
    phase = 0.0
    for i in range(n):
        idx = start + i
        if idx >= len(buf):
            break
        frac = i / n
        f = freq if glide_to is None else freq + (glide_to - freq) * frac
        phase += f / SR
        buf[idx] += _osc(phase, wave_kind) * vol * _env(i, n, attack, release)


def write_wav(name: str, buf: list[float]) -> None:
    """Normalise softly, clamp and write ``buf`` as 16-bit mono WAV."""
    os.makedirs(RES_RAW, exist_ok=True)
    path = os.path.join(RES_RAW, f"{name}.wav")
    frames = bytearray()
    for s in buf:
        v = max(-1.0, min(1.0, s))
        frames += struct.pack("<h", int(v * 32767))
    with wave.open(path, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(bytes(frames))
    kb = len(frames) / 1024
    print(f"  wrote {name}.wav  ({kb:.0f} KB, {len(buf) / SR:.2f}s)")


# --- Music ----------------------------------------------------------------

# Semitone offsets from A4 for the notes used (negative = below A4).
NOTES = {
    "A2": -24, "C3": -21, "D3": -19, "E3": -17, "F3": -16, "G3": -14, "A3": -12,
    "C4": -9, "D4": -7, "E4": -5, "F4": -4, "G4": -2, "A4": 0, "B4": 2,
    "C5": 3, "D5": 5, "E5": 7, "G5": 10, "A5": 12,
}


def build_music(name: str, bpm: float, bars: int, bass: list[str],
                melody: list[tuple[str | None, float]], *,
                melody_wave: str, bass_wave: str = "triangle") -> None:
    """Render a seamless loop: a per-bar bass note under a melody sequence."""
    beat = 60.0 / bpm
    bar = beat * 4.0
    total = int(bar * bars * SR)
    buf = [0.0] * total

    # Bass: one sustained note per bar (slightly detached so bars don't click).
    for b, bn in enumerate(bass):
        start = int(b * bar * SR)
        tone(buf, start, note_freq(NOTES[bn]), bar * 0.96,
             wave_kind=bass_wave, vol=0.34, attack=0.02, release=0.12)

    # Melody: a flat list of (note|rest, beats) advancing across the whole loop.
    pos = 0.0
    for pitch, beats in melody:
        if pitch is not None:
            start = int(pos * beat * SR)
            tone(buf, start, note_freq(NOTES[pitch]), beat * beats * 0.92,
                 wave_kind=melody_wave, vol=0.26, attack=0.02, release=0.25)
        pos += beats

    write_wav(name, buf)


def menu_music() -> None:
    """Calm, mid-tempo loop — Am–F–C–G, gentle arpeggio melody."""
    bass = ["A2", "F3", "C3", "G3", "A2", "F3", "C3", "G3"]
    # 32 beats (8 bars × 4): flowing eighth/quarter arpeggios.
    seq = [
        ("A4", 1), ("C5", 1), ("E5", 1), ("C5", 1),
        ("F4", 1), ("A4", 1), ("C5", 1), ("A4", 1),
        ("E4", 1), ("G4", 1), ("C5", 1), ("G4", 1),
        ("D4", 1), ("G4", 1), ("B4", 1), ("G4", 1),
        ("A4", 1), ("E5", 1), ("C5", 1), ("A4", 1),
        ("F4", 1), ("C5", 1), ("A4", 1), ("F4", 1),
        ("E4", 1), ("C5", 1), ("G4", 1), ("E4", 1),
        ("G4", 1), ("B4", 1), ("D5", 1), ("G4", 1),
    ]
    build_music("music_menu", bpm=104, bars=8, bass=bass, melody=seq, melody_wave="triangle")


def game_music() -> None:
    """Faster, driving loop — Am–G–F–E energetic line."""
    bass = ["A2", "G3", "F3", "E3", "A2", "G3", "F3", "E3"]
    seq = [
        ("A4", 0.5), ("A4", 0.5), ("E5", 0.5), ("A4", 0.5), ("C5", 0.5), ("A4", 0.5), ("E5", 1),
        ("G4", 0.5), ("G4", 0.5), ("D5", 0.5), ("G4", 0.5), ("B4", 0.5), ("G4", 0.5), ("D5", 1),
        ("F4", 0.5), ("F4", 0.5), ("C5", 0.5), ("F4", 0.5), ("A4", 0.5), ("F4", 0.5), ("C5", 1),
        ("E4", 0.5), ("E4", 0.5), ("B4", 0.5), ("E4", 0.5), ("G4", 0.5), ("E4", 0.5), ("B4", 1),
        ("A4", 0.5), ("C5", 0.5), ("E5", 0.5), ("C5", 0.5), ("A5", 0.5), ("E5", 0.5), ("C5", 1),
        ("G4", 0.5), ("B4", 0.5), ("D5", 0.5), ("B4", 0.5), ("G5", 0.5), ("D5", 0.5), ("B4", 1),
        ("F4", 0.5), ("A4", 0.5), ("C5", 0.5), ("A4", 0.5), ("F4", 0.5), ("C5", 0.5), ("A4", 1),
        ("E4", 0.5), ("G4", 0.5), ("B4", 0.5), ("E5", 0.5), ("D5", 0.5), ("B4", 0.5), ("E5", 1),
    ]
    build_music("music_game", bpm=140, bars=8, bass=bass, melody=seq, melody_wave="square")


# --- SFX ------------------------------------------------------------------

def sfx_eat() -> None:
    buf = [0.0] * int(0.13 * SR)
    tone(buf, 0, 620, 0.12, wave_kind="square", vol=0.55, attack=0.02, release=0.4, glide_to=1180)
    write_wav("sfx_eat", buf)


def sfx_shrink() -> None:
    buf = [0.0] * int(0.16 * SR)
    tone(buf, 0, 900, 0.15, wave_kind="square", vol=0.5, attack=0.02, release=0.5, glide_to=380)
    write_wav("sfx_shrink", buf)


def sfx_mystery() -> None:
    buf = [0.0] * int(0.28 * SR)
    step = int(0.07 * SR)
    for i, f in enumerate((780, 1040, 1560, 2080)):
        tone(buf, i * step, f, 0.09, wave_kind="triangle", vol=0.45, attack=0.04, release=0.5)
    write_wav("sfx_mystery", buf)


def sfx_game_over() -> None:
    buf = [0.0] * int(0.8 * SR)
    step = int(0.17 * SR)
    for i, f in enumerate((note_freq(NOTES["A4"]), note_freq(NOTES["F4"]),
                           note_freq(NOTES["D4"]), note_freq(NOTES["A2"] + 12))):
        tone(buf, i * step, f, 0.22, wave_kind="square", vol=0.5, attack=0.02, release=0.45)
    write_wav("sfx_game_over", buf)


def sfx_click() -> None:
    buf = [0.0] * int(0.04 * SR)
    tone(buf, 0, 1300, 0.03, wave_kind="square", vol=0.4, attack=0.05, release=0.6)
    write_wav("sfx_click", buf)


def sfx_pause() -> None:
    buf = [0.0] * int(0.2 * SR)
    tone(buf, 0, 720, 0.09, wave_kind="sine", vol=0.45, attack=0.05, release=0.4)
    tone(buf, int(0.1 * SR), 520, 0.09, wave_kind="sine", vol=0.45, attack=0.05, release=0.4)
    write_wav("sfx_pause", buf)


def main() -> None:
    print(f"Generating audio into {RES_RAW}")
    menu_music()
    game_music()
    sfx_eat()
    sfx_shrink()
    sfx_mystery()
    sfx_game_over()
    sfx_click()
    sfx_pause()
    print("Done.")


if __name__ == "__main__":
    main()
