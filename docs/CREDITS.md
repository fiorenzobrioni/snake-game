# Credits & asset licenses

All third-party assets bundled in the app must be **free** and carry a
**compatible license** (MIT / CC0 / CC-BY). Record every asset here with its
source, author and license **in the same change that adds it**. CC-BY assets
must keep the required attribution.

> The launcher icon and all in-game art are original vectors drawn in-repo. The
> **sound effects** are original works synthesized in-repo by
> [`tools/audio/generate_audio.py`](../tools/audio/generate_audio.py) and dedicated
> to the public domain (CC0). The **background music** is generated with Google
> Gemini (see Audio below). Bundled third-party assets are the Orbitron display
> font (Phase 3, see Fonts below). The AGSL shaders (Phase 5) are also original,
> hand-written in-repo (see Shaders below).

## Sprites

| Asset | Source | Author | License |
|-------|--------|--------|---------|
| Launcher icon - adaptive (`mipmap-anydpi-v26`) + vector fallback (`mipmap-anydpi`), hand-written vectors | original | Snake Game project | CC0 1.0 |

## Audio

### Music

The looping background tracks (`music_menu`, `music_game`) were **generated with
Google Gemini** (Lyria music model) and are used in accordance with
[Google's generative-AI terms of service](https://policies.google.com/terms/generative-ai).
They are bundled as OGG/Vorbis. The raw outputs were post-processed in-repo with
`ffmpeg` (silence trimmed, an equal-power self-crossfade baked in for seamless
looping under `MediaPlayer`, peaks limited to ~−1 dBFS). These tracks are
**aggregated assets** distributed alongside - but not part of - the GPL-3.0 source
code, and do not affect the project's license.

| Asset | Source | Author | License / terms |
|-------|--------|--------|-----------------|
| `music_menu` - menu background loop (OGG) | Google Gemini (Lyria) | Generated via Google Gemini | Google generative-AI terms |
| `music_game` - gameplay background loop (OGG) | Google Gemini (Lyria) | Generated via Google Gemini | Google generative-AI terms |

### Sound effects

All SFX are original works generated procedurally (no samples, no third-party
audio) by [`tools/audio/generate_audio.py`](../tools/audio/generate_audio.py) and
released under **CC0 1.0** (public domain). Re-run the script to reproduce them.

| Asset | Source | Author | License |
|-------|--------|--------|---------|
| `sfx_eat` / `sfx_shrink` / `sfx_mystery` / `sfx_game_over` / `sfx_pause` - sound effects | original (`tools/audio/generate_audio.py`) | Snake Game project | CC0 1.0 |
| `sfx_lightning` / `sfx_snail` / `sfx_star` / `sfx_freeze` / `sfx_jackpot` / `sfx_quake` / `sfx_explosion` - power-up / hazard SFX (Phase 6.2) | original (`tools/audio/generate_audio.py`) | Snake Game project | CC0 1.0 |

## Fonts

| Asset | Source | Author | License |
|-------|--------|--------|---------|
| Orbitron (Regular, Bold) - titles & HUD | [Google Fonts](https://fonts.google.com/specimen/Orbitron) · [The League of Moveable Type](https://github.com/theleagueof/orbitron) | The Orbitron Project Authors (designed by Matt McInerney) | [SIL OFL 1.1](licenses/Orbitron-OFL.txt) |

## Shaders

All AGSL `RuntimeShader`s are original code written in-repo
([`ui/game/Shaders.kt`](../app/src/main/kotlin/com/brioni/snake/ui/game/Shaders.kt)), covered by the
repository's GPL-3.0 license.

| Asset | Source | Author | License |
|-------|--------|--------|---------|
| Head glow / food halo / animated background / CRT filter / board terrains (AGSL) | original (`ui/game/Shaders.kt`) | Snake Game project | GPL-3.0 |

The terrain shaders' value-noise uses the sinless integer hash from
["Hash without Sine"](https://www.shadertoy.com/view/4djSRW) by Dave Hoskins
(MIT) - a tiny, standard snippet used to avoid the precision tearing that
`sin()`-based hashes exhibit on mobile GPUs.
