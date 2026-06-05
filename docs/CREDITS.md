# Credits & asset licenses

All third-party assets bundled in the app must be **free** and carry a
**compatible license** (MIT / CC0 / CC-BY). Record every asset here with its
source, author and license **in the same change that adds it**. CC-BY assets
must keep the required attribution.

> The launcher icon and all in-game art are original vectors drawn in-repo. The
> music and sound effects (Phase 4) are likewise **original works synthesized
> in-repo** by [`tools/audio/generate_audio.py`](../tools/audio/generate_audio.py)
> and dedicated to the public domain (CC0). The only bundled third-party asset is
> the Orbitron display font (Phase 3, see Fonts below). The AGSL shaders (Phase 5)
> are also original, hand-written in-repo (see Shaders below).

## Sprites

| Asset | Source | Author | License |
|-------|--------|--------|---------|
| _none yet_ | | | |

## Audio (music & SFX)

All clips are original works generated procedurally (no samples, no third-party
audio) by [`tools/audio/generate_audio.py`](../tools/audio/generate_audio.py) and
released under **CC0 1.0** (public domain). Re-run the script to reproduce them.

| Asset | Source | Author | License |
|-------|--------|--------|---------|
| `music_menu` / `music_game` — looping background music | original (`tools/audio/generate_audio.py`) | Snake Game project | CC0 1.0 |
| `sfx_eat` / `sfx_shrink` / `sfx_mystery` / `sfx_game_over` / `sfx_click` / `sfx_pause` — sound effects | original (`tools/audio/generate_audio.py`) | Snake Game project | CC0 1.0 |
| `sfx_lightning` / `sfx_snail` / `sfx_star` / `sfx_freeze` / `sfx_jackpot` / `sfx_quake` / `sfx_explosion` — power-up / hazard SFX (Phase 6.2) | original (`tools/audio/generate_audio.py`) | Snake Game project | CC0 1.0 |

## Fonts

| Asset | Source | Author | License |
|-------|--------|--------|---------|
| Orbitron (Regular, Bold) — titles & HUD | [Google Fonts](https://fonts.google.com/specimen/Orbitron) · [The League of Moveable Type](https://github.com/theleagueof/orbitron) | The Orbitron Project Authors (designed by Matt McInerney) | [SIL OFL 1.1](licenses/Orbitron-OFL.txt) |

## Shaders

All AGSL `RuntimeShader`s are original code written in-repo
([`ui/game/Shaders.kt`](../app/src/main/kotlin/com/brioni/snake/ui/game/Shaders.kt)), covered by the
repository's MIT license.

| Asset | Source | Author | License |
|-------|--------|--------|---------|
| Head glow / food halo / animated background / CRT filter (AGSL) | original (`ui/game/Shaders.kt`) | Snake Game project | MIT |
