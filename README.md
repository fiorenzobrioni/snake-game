# 🐍 Snake - Android (Kotlin + Jetpack Compose)

[![Kotlin](https://img.shields.io/badge/language-Kotlin-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org/)
[![Jetpack Compose](https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4?logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![Android](https://img.shields.io/badge/platform-Android-3DDC84?logo=android&logoColor=white)](https://www.android.com/)
[![minSdk](https://img.shields.io/badge/minSdk-33-blue)](https://developer.android.com/)
[![targetSdk](https://img.shields.io/badge/targetSdk-36-blue)](https://developer.android.com/)
[![License: GPL v3](https://img.shields.io/badge/license-GPL--3.0-blue.svg)](https://www.gnu.org/licenses/gpl-3.0)

**Inspired by the classic Snake** and reimagined as a **native Android game** in **Kotlin + Jetpack Compose**,
on the way to a polished, **Google-Play-publishable** title with animation, particles, shaders, audio and menus.

---

## Screenshots

<table>
  <tr>
    <td align="center"><img src="docs/screenshots/screenshot_splash.jpg" width="220"/><br/><sub>Splash intro</sub></td>
    <td align="center"><img src="docs/screenshots/screenshot_menu.jpg" width="220"/><br/><sub>Main menu</sub></td>
    <td align="center"><img src="docs/screenshots/screenshot_settings.jpg" width="220"/><br/><sub>Settings</sub></td>
  </tr>
  <tr>
    <td align="center"><img src="docs/screenshots/screenshot_gameplay.jpg" width="220"/><br/><sub>Gameplay</sub></td>
    <td align="center"><img src="docs/screenshots/screenshot_gameplay_powerups.jpg" width="220"/><br/><sub>Power-ups &amp; hazards</sub></td>
    <td align="center"><img src="docs/screenshots/screenshot_campaign.jpg" width="220"/><br/><sub>Campaign - Level 4</sub></td>
  </tr>
</table>

---

## 🎯 Features

The classic Snake mechanics, extended with configurable features so every run feels different:

- 🚀 **Branded launch** - an animated splash flows into a short, skippable brand intro played out on
  the game board itself: a snake crawls across and the word **SNAKE** forms in glowing cells in its
  wake, then it slips off-screen and the menu fades in.
- 🍽️ **Two food categories** - **grow** food makes the snake longer; **shrink** food trims it back.
- 🔠 **Magnitude tiers + maxi sizes** - each category comes in several strengths, and a 2×2 **maxi**
  variant that amplifies the effect.
- ❓ **Mystery pieces** - a "?" food per category with a random amount.
- ⏳ **Time-gated progression** - early on you only see growing food; shrink, maxi and mystery pieces
  unlock as the session goes on (sooner on harder levels), so a run ramps up in difficulty.
- 💨 **Fresh board** - a regular food you ignore for too long fades away (with a little vanish burst)
  and reappears elsewhere, so looping around without eating won't stall the run. Special pieces stick
  around much longer (they're rare events worth reaching) but eventually time out too.
- ✖️ **Combo multiplier** - eating in quick succession multiplies your score (up to ×5).
- 📏 **Length-scaled scoring** - the longer your snake gets, the more each grow is worth (and you unlock
  dedicated length achievements as you stretch out).
- 🚧 **Obstacles** - symmetric blocks that tend to clump into larger shapes and raise the difficulty.
- 🎚️ **Levels & snake speed** - 5 obstacle layouts (*Beginner* → *Legend*) and 5 **independent**
  speeds (*Relaxed* → *Turbo*), mixable freely: play the dense Legend field at a gentle pace, or an
  open board flat out.
- 📐 **Responsive board** - pick a granularity (*Cozy* / *Standard* / *Epic* / *Colossal*); the grid
  is computed from your device's screen so it fills the display with square cells in portrait. Bigger
  boards also give food, power-ups and hazards proportionally more time before they vanish, so the
  snake can reach them across the longer distances.
- 🎮 **Control schemes** - **swipe** by default (with adjustable sensitivity), a compact **wedge dial** (a single key split into four directional wedges), or one-handed **tap-to-turn**.
  A forgiving **coyote frame** makes a beat-late turn still count: the first lethal step makes the head
  hesitate one tick (instead of dying), giving you a moment to turn away - re-armed by your next safe move.
- 🎨 **Skins** - six selectable looks, each its own palette and render style. **Retro** (the default) and
  **Classic** are unlocked from the start; the rest are earned: **Neon** (score 1500 in a run), **Pixel**
  (score 5000), **Aurora** (a 7-day Daily streak) and **Ember** (a 30-day Daily streak). Locked skins show
  their unlock condition in Settings. The glow skins (Classic / Neon / Aurora / Ember) draw food and
  power-ups as haloed round pieces; the flat skins (Retro / Pixel) render them as squares (crisp on Pixel,
  lightly rounded on Retro). Aurora and Ember pair a glowing look with a **segmented** snake body that reads
  beautifully through teleports and the invincibility shimmer.
- 🌗 **Theme** - choose **Light**, **Dark** or **System** (follows the device) in Settings.
- 🔊 **Music & sound effects** - looping background music that crossfades between the menu and
  gameplay, plus SFX for eating, shrinking, mystery pieces, game over and UI. Independent
  **master / music / SFX** volume sliders in Settings; audio pauses when the app is backgrounded.
- 📳 **Haptics & near-miss feedback** - vibration cues scaled by event (a light tap on eating, a
  firmer click on power-ups, the strongest buzz on death) plus a faint **near-miss** tick and a brief
  on-screen **danger flash** whenever the head grazes a wall, obstacle or debris without crashing.
  Toggle it off with **Vibration feedback** in Settings.
- 🔥 **Combo "juice"** - chain bites for a multiplier and the HUD counter punches in and warms through a
  colour ramp while the snake's head **catches fire** (its glow heats from your skin's colour toward a
  fiery orange-red) as the streak climbs.
- ♿ **Reduce motion & flashing** - an accessibility toggle in Settings that damps the screen shake, the
  particle bursts and the near-miss flash for a calmer, flash-free board.
- ✨ **Rich 2D visuals** - the snake renders as a smooth, shaded, **tapered tube** with a glossy head
  (crisp blocks on the flat skins), food is drawn with top-lit gradients and soft shadows, and eating
  pops a shockwave ring with **combo-reactive** sparks (the burst grows hotter and bigger as your streak
  climbs); the Explosion hazard sets off a fiery two-tone detonation.
- ✨ **GPU shader effects** - an animated background, a glowing snake head and pulsing
  halos on rare foods, all via **AGSL** `RuntimeShader`s, plus an optional **retro CRT filter**
  toggle in Settings.
- ⚡ **Power-ups & hazards** - rare maxi pieces that appear later in a run: **Lightning** (speed up),
  **Snail** (slow down), **Star** (invincible pass-through; the snake blinks faster as it runs out),
  **Freeze**, **Jackpot** (big bonus),
  plus the hazards **Earthquake** (a sustained screen shake for a few seconds that makes the board hard
  to read - no debris, your length is untouched) and **Explosion** (severs the last third of the snake,
  leaving the detached tail as lethal debris that lingers for several seconds). Hazards wear a dashed
  **caution ring** and, the moment before you would eat one, flash a **danger telegraph** (with a
  pre-haptic) so a strike never feels arbitrary - the flash respects the **Reduce motion** toggle. Active
  effects show countdown chips; up to **two specials** can share the board at once. Toggle **Hazards**
  off in Settings for a calmer run, or dial how often specials appear with the **Special blocks**
  setting (*Standard / Frequent / Frenzy*) - the higher tiers also bring specials online earlier in a
  run. **Time Attack** adds two exclusive clock pieces: a green **+5s** bonus and a red **−3s**
  penalty, each with a floating callout.
- 🏆 **Records screen** - a best-score table per difficulty × board scale (and per mode), reachable
  from the main menu.
- 🎖️ **Achievements** - thirty local milestones (combos, scores, endurance, eating sprees, using
  power-ups, growing a very long snake, keeping a Daily streak…) that unlock as you play, with a dedicated
  screen and an unlock banner on the game-over screen.
- 📊 **Run recap** - the game-over screen shows a short summary of the run: foods eaten, best combo, time
  survived, the snake's longest length and, in Campaign, the deepest level reached.
- 🎯 **Daily missions** - three rotating per-run goals (eat so many foods, reach a combo, survive a time,
  score, grow long, grab a power-up) that refresh each day. The main menu's **Today's Missions** strip
  tracks which you've cleared today (tap it for the full list), and the **game-over screen** shows the
  day's missions with a tick on the ones done, highlighting any you just cleared.
- ▶️ **Quick Play** - the main menu's **Play** button drops you straight into a run with your last-used
  settings; a separate **Custom** entry opens the full pre-game setup (mode, level, snake speed, board
  scale) when you want to tweak everything.
- 📅 **Daily Challenge** - a date-seeded run with the same mode, level, board and **daily twist** for
  everyone that day (the twist rotates through Bonus Rush, Frenzy and Compact Arena; the obstacle layout
  and food sequence come from the day's seed). Beat your **best today** and build a **day streak** (which
  unlocks the Aurora and Ember skins at 7 and 30 days). A **This Week** screen shows your last 7 days of
  Daily results and a weekly best / total; tap any day there to **replay** that day's exact challenge -
  just for fun, your recorded results are never overwritten. Reached from the main menu.
- 🎲 **Random Challenge** - a one-off surprise run for variety: **Shuffle** for a fresh mode / level /
  board / twist mix, then play. Nothing is recorded - it's pure fun.
- 🕹️ **Game modes** - **Endless** (speed ramps up the longer you survive; the default),
  **Time Attack** (score as much as you can in 120s) and **Campaign** (see below), selectable on the
  **Custom** setup screen.
- 🧩 **Campaign mode** - fifteen **designed board shapes** (cut corners, pillars, chambers, a vault,
  and a tougher late-game gauntlet…) that repeat forever, one **speed step faster** each lap. Eat **12 foods** to clear a level; you
  start with **3 lives** (a crash respawns you in the same level, keeping score and progress) and a
  rare 2×2 **extra-life** piece with a snake-head icon can bank more (up to 5). Every transition
  plays an animated *"Level x - Speed x"* banner with a 3-second countdown. The Level and Snake speed
  selectors are disabled here - the mode has its own layouts and pace - and the Records screen tracks both your best
  score and the deepest level you reached per board scale.
  - 🚧 **Environmental hazards** - some levels add **moving-wall gates**: glowing energy barriers that
    open and close on a rhythm (they strobe a warning before slamming shut, so time your dash through),
    and **teleport portals** - step onto one swirling pad to instantly emerge at its partner across the
    board. Gates are lethal only while closed and never seal you in; portals open up bold shortcuts.
- ⏸️ **Pause & menus** - pause overlay with a blur effect; restart or return to the main menu at any time. Highscores are kept per (mode, level, board scale). A **Back during play** setting chooses what the system Back gesture does mid-game: **Keep playing** (default - Back is ignored, and a swipe-back is fed to the snake as a turn when using swipe controls) or **Pause**.
- 💎 **Polished navigation** - an **animated GPU background** behind the menus, a **branded main menu** laid out as a "game launcher" (a glowing wordmark with a small in-game-style snake emblem that follows your selected skin as the hero, over a bottom-anchored cluster of actions grouped by type so everything fits one screen), **premium action buttons** (gradient-lit, with a tactile press), and **blur-dissolve** screen transitions.
- ⏸️ **Auto-pause** - backgrounding the app mid-run pauses the game automatically, so the snake never keeps moving while you're away.
- 📜 **Credits screen** - an in-app **Credits / About** page (author, license and asset attribution), reachable from the main menu.
- 🧭 **First-run tutorial** - a premium, skippable walkthrough on first launch, re-openable any time via **How to play** in Settings. Four dark, minimal cards (over the animated brand backdrop) explain the objective, the three control styles, the food types and the power-ups / hazards - each with detailed legend rows that show the real in-game pieces, icons and colours.

### 🍽️ Food system at a glance

| Category | Tiers (standard growth/shrink) | Maxi (2×2) | Mystery "?" | Score |
|----------|-------------------------------|------------|-------------|-------|
| 🟢 **Grow**   | +2 / +4 / +6 / +8 | doubles the amount | random +2…+24 | `+10 × growth × combo × length bonus` |
| 🟠 **Shrink** | −2 / −3 / −5      | doubles the amount | random −2…−14 | small symbolic bonus (5 / 10 maxi) |

The snake never shrinks below **3 segments**. Grow food drives the score, scaled by the combo
multiplier **and by your current length** - the longer the snake, the more each bite is worth (up to
about ×5 for a very long snake), so growing pays off more and more as a run goes on. Shrink food is a
tactical tool - it gives only token points but lets you cut your length to manoeuvre. Eating either
floats the amount of segments gained or lost (**+N** / **−N**) at the food.

### ⚔️ Levels (obstacles)

The **Level** sets only how many obstacles are placed; it no longer affects speed (see *Snake speed*
below), so any level can be paired with any pace.

| Level | Name        | Obstacles (Cozy) |
|-------|-------------|------------------|
| 1     | Beginner    | 0                |
| 2     | Adventurer  | 8                |
| 3     | Warrior     | 15               |
| 4     | Champion    | 25               |
| 5     | Legend      | 40               |

The counts above are tuned for the smallest (Cozy) board; on larger board scales they are **scaled
up with the board's area** so the obstacle density stays constant instead of thinning out (e.g. Epic,
at ~2× the short side, gets ~4× the obstacles).

Obstacles are laid out with **4-fold symmetry** (mirrored left/right and top/bottom), with a clear
margin next to every wall and a clear zone around the snake's spawn. New blocks are biased towards
growing next to ones already placed, so they tend to form larger clumped shapes instead of
scattering as isolated cells.

### 🏃 Snake speed

A separate setting (shown under *Level* on the **Custom** setup screen and in Settings) controls the
pace, independent of the obstacle layout. It applies to **Time Attack**; Endless ramps its own pace
and Campaign uses its per-lap speed cycle.

| Speed | Name    | Tick (ms) |
|-------|---------|-----------|
| 1     | Relaxed | 175       |
| 2     | Steady  | 150       |
| 3     | Brisk   | 125       |
| 4     | Rapid   | 100       |
| 5     | Turbo   | 75        |

### 📐 Board scale

The board is **responsive**: pick a granularity and the grid is computed from your device's play-area
aspect ratio so the board fills the screen with square cells. The preset count is applied
to the **short side**, so the cell size - and the feel - stays consistent across different screen
sizes (a tablet gets the same density as a phone, not a squashed few-row board).

| Scale    | Cell size  | Cells on short side |
|----------|------------|---------------------|
| Cozy     | larger     | 13                  |
| Standard | medium     | 19                  |
| Epic     | smaller    | 27                  |
| Colossal | smallest   | 35                  |

The counts are odd on purpose: the board gets a true middle column, so the snake's centred spawn
lines up exactly with centred overlays (like the Campaign-mode countdown).

---

## 🛠️ Requirements & tools

Install on your development machine:

- **Android Studio** (latest stable) - bundles the JDK (JBR), the SDK Manager and the AVD emulator.
- **Android SDK** via the SDK Manager: **Platform API 36**, **Build-Tools 36.x**, **Platform-Tools** (`adb`),
  **Emulator** + a system image (e.g. API 36).
- A **test target**: an AVD emulator or a physical device with **USB debugging** enabled.
- **Gradle**: not needed globally - the project ships the **Gradle wrapper** (`./gradlew`).

The project targets `minSdk 33` (Android 13) and `compileSdk`/`targetSdk 36` (Android 16) - a modern
baseline so AGSL GPU effects and other recent APIs are available without fallback code.

---

## 🚀 Build & run

1. **Clone**:
   ```bash
   git clone https://github.com/fiorenzobrioni/snake-game.git
   ```
2. **Open the repository root** in **Android Studio** and let Gradle sync.
3. **Run** on an emulator or a connected device (▶ Run, or):
   ```bash
   ./gradlew installDebug      # build + install the debug APK
   ./gradlew assembleDebug     # build the debug APK only
   ```

> The Android SDK location is read from `local.properties` (created by Android Studio) or the
> `ANDROID_HOME` environment variable.

---

## 🎮 How to play

Guide the snake around the board, eat food to grow and score, and avoid the walls, the obstacles and your
own body.

**Food:** green = grow, warm/orange = shrink, "?" = a mystery amount. Bigger (2×2 maxi) pieces and the
mystery and shrink foods only start appearing as the session runs on - so each run gets more eventful.
Chain bites together to build a **combo** and multiply your score, and use shrink food to cut your length
when the board gets tight (you never drop below 3 segments).

**Controls (touch):** by default you **swipe** anywhere on the board to change direction, with an
adjustable **swipe sensitivity** in Settings (the default keeps the tuned feel). Prefer buttons? Switch
in **Settings** to a compact, premium **wedge dial**: a single skin-tinted key split by its diagonals
into four directional wedges (up / right / down / left) around a dead-zone hub, so your thumb barely
moves between turns and the board keeps more height. For one-handed play there is also a **tap-to-turn** scheme: tap the left half of the board to
turn left, the right half to turn right. Your choice is saved. 180° reversals are blocked, so you can't
instantly fold back into your own body. Tap **Play** on the main menu to start instantly with your
last-used settings, or **Custom** to pick the mode, level, snake speed and board scale first; pause and
restart from the in-game controls. Your best score is kept per (mode, level, scale).

**Audio:** the game plays looping background music (it crossfades between the menu and gameplay) and
sound effects for eating, shrinking, mystery pieces, game over and button taps. Tune the **master**,
**music** and **SFX** volumes independently in **Settings** (set any to zero to mute); the music
automatically pauses when you leave the app and yields to other apps' audio.

**Game modes:** choose your mode on the **Custom** setup screen -
**Endless** (the snake keeps accelerating the longer you survive; the default), **Time Attack** (score as much
as possible in 120 seconds - watch for the exclusive **+5s** / **−3s** clock pieces that stretch or
shave your remaining time), **Campaign** (clear fifteen shaped boards by eating 12 foods each, with 3
lives, an exclusive 2×2 extra-life piece, and a speed-up every completed lap - the HUD shows
*Level x - Speed x*, your hearts and the foods still to go). Your best score is tracked per mode,
level and board scale; check the **Records** screen from the main menu.

**Power-ups & hazards:** as a run progresses, rare special pieces start appearing on the board.
Power-ups help: **Lightning** speeds the snake up, **Snail** slows it down, **Star** grants brief
invincibility (you can pass through walls, obstacles and your own body - the snake blinks as the
effect fades), **Freeze** pauses further specials for a strategic breather, and **Jackpot** grants a
large score bonus. Hazards hinder: **Earthquake** sets off a sustained screen shake for a few seconds
that makes the board hard to read (it leaves no debris and your length is untouched); **Explosion**
severs the last third of the snake - the detached tail turns into lethal debris that lingers for several
seconds before it auto-clears. Every hazard wears a dashed **caution ring**, and the tick before you
would eat one the board flashes a **danger telegraph** over it (with a short pre-haptic), so a strike is
always announced; the flash honours the **Reduce motion** toggle. Active effects show
a countdown chip in the HUD. **Time Attack** also has two clock-only pieces - a **+5s** bonus and a
**−3s** penalty. Toggle **Hazards** off in **Settings** for a calmer run (this also hides the time
penalty), or raise **Special blocks** to *Frenzy* for constant chaos.

**Achievements:** milestones unlock automatically as you play - high combos, long runs, using
power-ups, growing a very long snake, and more. A banner appears on the game-over screen when one
unlocks; browse the full list from the main menu.

**Daily missions:** alongside the static achievements, three goals rotate each day (eat a number of
foods, reach a combo, survive a time, hit a score, grow long, or grab a power-up). They give a single
run a sense of purpose: the main menu's **Today's Missions** card shows which you've cleared today, and
completing one pops a banner on the game-over screen. The set refreshes the next day.

---

## 👨‍💻 Code layout

```
app/src/main/kotlin/com/brioni/snake/
├── MainActivity.kt     # Compose entry point
├── game/               # pure-Kotlin game model (no Android imports → unit-testable)
├── ui/                 # Compose UI + Material 3 theme
├── audio/              # SoundPool SFX + MediaPlayer music, behind the GameAudio facade
└── data/               # DataStore persistence (settings, highscores)
```

The sound effects in `app/src/main/res/raw/` are original CC0 clips generated by
[`tools/audio/generate_audio.py`](tools/audio/generate_audio.py) - re-run it to reproduce them. The
background music tracks (`music_menu.ogg`, `music_game.ogg`) are generated with Google Gemini (see
[Media assets & credits](#-media-assets--credits)). The AGSL shaders live in
[`ui/game/Shaders.kt`](app/src/main/kotlin/com/brioni/snake/ui/game/Shaders.kt).

For architecture notes, conventions and the file map, see [`CLAUDE.md`](CLAUDE.md).

---

## 🎵 Media assets & credits

The app includes a **Credits** screen, reachable from the main menu, summarizing authorship and asset
attribution. In short:

- **Author** - Fiorenzo Brioni. Released as free software under the **GNU GPL v3.0**.
- **Music** - the looping menu and gameplay tracks are **generated with Google Gemini** (Lyria), used
  in accordance with [Google's generative-AI terms of service](https://policies.google.com/terms/generative-ai).
  They are bundled as OGG/Vorbis and post-processed in-repo (silence trimmed and an equal-power
  self-crossfade baked in) so they loop seamlessly. As aggregated assets they sit alongside - and do
  not affect the license of - the GPL-3.0 source code.
- **Sound effects** - original, synthesized in-repo (CC0) by
  [`tools/audio/generate_audio.py`](tools/audio/generate_audio.py).
- **Fonts** - Orbitron (SIL Open Font License 1.1).
- **Graphics & shaders** - original, hand-written in-repo.
- **Built with** - developed with Google Antigravity and Claude Code.

Full per-asset details and licenses are tracked in [`docs/CREDITS.md`](docs/CREDITS.md).

---

## 🧭 Planning & Roadmap

The full development plan - from foundations through gameplay, visual polish, audio, shaders, content, and
**Google Play distribution** - as well as active TODOs, bugs, and architecture notes, is in [`PLANNING.md`](PLANNING.md).

---

## 🏛️ Legacy - the v1.0.0 prototype

This project began as a **learning exercise**: a Snake built in **C# / .NET 10 / Windows Forms** with **GDI+**
rendering, shipped as **v1.0.0**. That desktop version is **frozen** and preserved under
[`legacy/SnakeGame/`](legacy/SnakeGame/) as a reference for the game model. See
[`legacy/README.md`](legacy/README.md) for its build notes. The native Android app described above is the
project's active direction.

---

## 📄 License

Copyright (C) 2026 Fiorenzo Brioni

This project is free software: you can redistribute it and/or modify it under the terms of the
**GNU General Public License v3.0** as published by the Free Software Foundation.

Distributed in the hope that it will be useful, but **without any warranty**; without even the
implied warranty of merchantability or fitness for a particular purpose.
See the [LICENSE](LICENSE) file for the full terms.
