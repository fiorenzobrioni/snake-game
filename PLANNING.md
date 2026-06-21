# 🧭 PLANNING - Snake

Plan to take Snake from a learning prototype to a **polished, Play-Store-ready Android game**, built
**from scratch** in **Kotlin + Jetpack Compose**.

Roadmap, work in progress, TODOs, known bugs, and ideas. For the history of development cycles and design notes, see the [`devlog/`](devlog/) folder.

> Status: `[x] Done` · `[-] In progress` · `[ ] To do`

---

## TODOs

> Short-term tasks not yet tracked as a formal roadmap step.

- [ ] Finalise `applicationId` from `com.brioni.snake` placeholder before first Play upload (Step 7.1)
- [ ] Write unit tests for `GameEngine` edge-cases: wall collision on all four sides, body self-collision, 180° reversal block
- [ ] Verify smooth-motion interpolation on low-end devices
- [ ] Verify the mystery "?" glyph renders crisply on small cells (dense boards)
- [ ] Re-tune the food spawn weights / time gates after playtesting on a device
- [ ] Optionally enrich the synthesized SFX before Play release (background music is now Gemini-generated)

---

## Known Bugs

*None open.*

---

> The original **C# / .NET 10 / WinForms (GDI+)** version shipped as **v1.0.0** and is now **frozen** under [`legacy/`](legacy/). It is kept only as a reference for the *game model* - no further feature work happens there. Everything below is the new app at the repository root.

---

## 🎯 Goal

A native Android Snake that looks and feels professional - smooth animation, particles, shaders, audio,
menus and replayability - and is **publishable on the Google Play Store** as a signed App Bundle (AAB).

## 🧰 Chosen stack

- **Language**: Kotlin
- **UI / rendering**: **Jetpack Compose** (Material 3) - gameplay drawn on Compose `Canvas`, the natural
  evolution of the immediate-mode GDI+ rendering learned in v1.0.0.
- **Build**: Gradle (Kotlin DSL) + version catalog, Gradle wrapper pinned.
- **Min/target SDK**: `minSdk 33` (Android 13), `compileSdk`/`targetSdk 36` (Android 16). The 33 floor
  is intentional - it keeps the app on a modern, premium-device baseline and lets AGSL shaders and
  other recent APIs be used without fallbacks.
- **Persistence**: Preferences **DataStore** (settings, highscores).
- **Effects**: hand-drawn particles on `Canvas`; **AGSL `RuntimeShader`** for glow/background on **API 33+**.
- **Audio**: `SoundPool` (SFX) + `MediaPlayer`/`ExoPlayer` (music).
- **Assets**: free licenses only (CC0 / CC-BY / MIT), recorded in [`docs/CREDITS.md`](docs/CREDITS.md).

### Why native Kotlin + Compose
- Smallest, fastest Android binary; no game-engine runtime to ship.
- Compose `Canvas` maps cleanly onto the grid-based rendering already prototyped in WinForms.
- First-class access to Android platform APIs (haptics, AGSL shaders, splash screen, Play distribution).

---

## 🛠️ Local tools / SDKs

Install on your development machine:

- **Android Studio** (latest stable) - bundles the JDK (JBR), the SDK Manager and the AVD emulator.
- **Android SDK** via SDK Manager: **Platform API 36**, **Build-Tools 36.x**, **Platform-Tools** (`adb`),
  **Emulator** + a system image (e.g. API 36).
- A **test target**: an AVD emulator or a physical device with **USB debugging** enabled.
- **Gradle**: not needed globally - use the project's `./gradlew` wrapper.
- **For Play distribution (Phase 7)**: a **Google Play Console** account, an **upload keystore**
  (`keytool` / Android Studio), and **Play App Signing** enabled.

---

## 📐 Target repo layout

```
snake-game/
├── settings.gradle.kts          # Gradle root (Kotlin DSL)
├── build.gradle.kts
├── gradle.properties
├── gradle/
│   ├── libs.versions.toml        # version catalog
│   └── wrapper/                  # pinned Gradle wrapper
├── gradlew / gradlew.bat
├── app/
│   ├── build.gradle.kts
│   ├── proguard-rules.pro
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── kotlin/com/brioni/snake/
│       │   ├── MainActivity.kt
│       │   ├── game/             # pure-Kotlin game model (no Android imports)
│       │   ├── ui/               # Compose UI + theme
│       │   └── data/             # DataStore persistence
│       └── res/                  # themes, strings, colors, adaptive icon
├── devlog/                       # development log
├── docs/CREDITS.md               # asset credits (CC0/CC-BY/MIT)
├── legacy/SnakeGame/             # frozen C#/.NET 10 GDI+ v1.0.0 (learning)
├── .github/workflows/            # release CI (Phase 7)
└── CLAUDE.md  README.md  PLANNING.md  LICENSE
```

---

## ✅ Execution principles

1. **Every step must be buildable and testable** in isolation. Never leave the branch broken.
2. One step = one commit (or a small group of related commits).
3. After each step: Gradle sync + run on emulator/device + manual smoke test, then tick the todo.
4. No opportunistic refactors outside the current step.
5. The `game/` package stays free of Android/Compose imports so the model is **unit-testable**.
6. Free assets only; every added asset is recorded in [`docs/CREDITS.md`](docs/CREDITS.md) with source and license.

---

## 🛣️ Roadmap

### Phase 0 - Foundations ✅ (implemented)

- [x] **Step 0.1** - Restructure repo: move the .NET v1.0.0 prototype to `legacy/`; stand up the Gradle root
      (Kotlin DSL), version catalog, wrapper and Android `.gitignore`. Verify: `settings.gradle.kts` parses;
      Android Studio Gradle sync succeeds.
- [x] **Step 0.2** - App entry: `MainActivity` (`ComponentActivity`) + Compose Material 3 theme rendering a
      full-screen `Surface`. Splash via `core-splashscreen`. Verify: the app launches to a colored screen.
- [x] **Step 0.3** - Display: **portrait lock**, **edge-to-edge**, content respects **safe-area insets**.
      Verify: portrait, background draws under the bars, content stays clear of cutouts.
- [x] **Step 0.4** - Adaptive-icon placeholder and `docs/CREDITS.md`.

> 🎯 **End of Phase 0**: an installable app that launches to a themed, edge-to-edge, portrait screen.

### Phase 1 - Core gameplay (model parity with v1.0.0) ✅ (implemented)

- [x] **Step 1.1** - Pure-Kotlin model in `game/`: `Direction`, `Position`, `BoardSize`, `Level`, `Food`,
      `GameState`, `GameEngine`. No rendering. Verify: model compiles; unit tests on movement/growth/
      collisions/spawn pass (`./gradlew :app:testDebugUnitTest`).
- [x] **Step 1.2** - Compose `Canvas` grid renderer (`GameBoard`): board background + grid, obstacles,
      foods and the snake (bright head). Verify: a centered board with a snake is visible.
- [x] **Step 1.3** - Game loop via coroutine in `GameViewModel` at the per-level interval. Verify: the
      snake advances steadily until it dies.
- [x] **Step 1.4** - Touch input: **swipe gestures** (`detectDragGestures`) + an on-screen D-pad; 180°
      reversal blocked in the engine. Verify: the snake is steered by swipes and the pad.
- [x] **Step 1.5** - Food spawn + growth on eat + respawn (two foods kept on the board, as in v1.0.0).
      Verify: eating grows the snake.
- [x] **Step 1.6** - Collisions (walls, body, obstacles) → `GameOver` stops the loop. Verify: all three
      deaths trigger correctly (covered by unit tests).
- [x] **Step 1.7** - Score HUD (Compose overlay). Verify: score updates on eat.
- [x] **Step 1.8** - Pause / Restart controls (pause overlay, play-again, back-to-menu). Verify: pause
      overlay shows; restart resets state.
- [x] **Step 1.9** - Ported the **5 difficulty levels** (speed + obstacle count). Verify: changing level
      changes speed and obstacle count.
- [x] **Step 1.10** - Ported the **5 board sizes** (Pocket → Infinite). Verify: changing size reshapes the board.
- [x] **Step 1.11** - Ported the **7 food types** (Green/Red/Gold/Blue/Mega*) with the original probabilities
      and bonuses (Blue grants +2…+24). Verify: over time all types appear.

> 🎯 **End of Phase 1 (M1)**: feature parity with v1.0.0, playable on a phone - still visually "blocky".

### Phase 2 - Visual polish ✅ (implemented)

> Board presets were also re-tuned from 3:2 landscape (inherited from the desktop build) to **portrait**
> (~7:10) so the board fills a phone screen; the app stays portrait-locked by design. See `BoardSize`.

- [x] **Step 2.1** - Snake drawn from vector shapes: rounded body segments, a brighter head with eyes
      oriented to travel direction (curve/tail sprite refinement can follow later).
- [x] **Step 2.2** - Themed board background: vertical gradient + subtle grid, framed border.
- [x] **Step 2.3** - Animated food (pulsing scale; slow spin on the blue star; halo on gold/mega).
- [x] **Step 2.4** - Bevelled obstacle blocks (shadow + highlight) instead of flat rectangles.
- [x] **Step 2.5** - **Smooth inter-tick motion**: each segment interpolates from its previous cell to the
      current one over one tick via a `withFrameNanos` clock; logic stays grid-based.
- [x] **Step 2.6** - Particle burst on eat (colour per food type), simulated in cell space on `Canvas`.
- [x] **Step 2.7** - Screen shake on the game-over collision (damped wobble).
- [x] **Step 2.8** - Glow on the snake's head (radial gradient halo).

### Phase 2.5 - Gameplay enrichment (food system) ✅ (implemented)

> A pre-Phase-3 gameplay layer to make a session less "static": the v1.0.0 model only had foods that
> grow the snake. This step adds two food **categories**, magnitude **tiers**, **maxi** sizes, a
> **mystery** piece per category and a **time-gated** progression, plus combo scoring - all in the
> pure-Kotlin model. The flashier **special power-ups / hazards** (earthquake, explosion, …) are
> deliberately deferred to **Phase 6.2**, where they can reuse the HUD timers, audio and shaders.

- [x] **Step 2.5.1** - Food model redesign: orthogonal `FoodCategory` (Grow/Shrink), `FoodSize`
      (Standard 1×1 / Maxi 2×2, maxi amplifies), `FoodTier` (Small→Huge + Mystery) and a sealed
      `FoodEffect`. `FoodType`/`growth` removed. Verify: model unit tests pass.
- [x] **Step 2.5.2** - Magnitude variants: Grow 2/4/6/8 and Shrink 2/3/5 (×2 for Maxi); a **mystery**
      piece per category with a random, spawn-resolved amount drawn behind a "?".
- [x] **Step 2.5.3** - Time-gated progression (`GameState.elapsedTicks`): only growing food at first;
      shrink, then maxi, then mystery unlock as a session runs; harder levels reach the gates sooner.
- [x] **Step 2.5.4** - Engine rules: shrink trims the tail with a **minimum-length floor** (never below
      3); a **combo multiplier** rewards rapid consecutive eats; shrink awards only symbolic points.
      Per-tick `GameEvent`s replace the renderer's fragile state re-derivation.
- [x] **Step 2.5.5** - Rendering: grow (green) vs shrink (warm) colour families shaded by tier, maxi
      halo, a "?" glyph for mystery (Canvas `TextMeasurer`), a shrink "implosion" particle burst and a
      combo readout in the HUD.

### Phase 3 - Pro UI / UX ✅ (implemented)

> Alongside Phase 3 the gameplay was tuned: **swipe** is the default control scheme, with a
> **two-button relative** scheme (turn left/right relative to heading) and the 4-button D-pad
> kept as selectable schemes; the board is now **responsive** - its rows×columns are computed from
> the device's play-area aspect ratio for a chosen granularity (`BoardScale`: Cozy/Classic/Epic) so
> it fills the screen with square cells (the old fixed `BoardSize` presets were dropped); obstacles
> are laid out with **4-fold symmetry** (clear border margin + clear spawn zone); and per-level
> speed was eased ~25% slower for comfortable touch play. The launcher icon was redrawn to resemble
> the in-game snake. Navigation uses a lightweight **state-based screen switch + `Crossfade`** rather
> than `navigation-compose` (single Activity, three destinations) - an intentional, justified
> deviation that keeps the binary lean and still delivers the fade transitions.

- [x] **Step 3.1** - Orbitron (OFL) display font + a reusable Material 3 type scale.
- [x] **Step 3.2** - Main menu (animated title, Play / Settings) with state-based navigation.
- [x] **Step 3.3** - Settings screen (level, board scale, control scheme) persisted via **DataStore**.
- [x] **Step 3.4** - Pause overlay with a **blur** (`Modifier.blur`) over the frozen board.
- [x] **Step 3.5** - Game-over screen + persistent **highscores per (level, scale)**.
- [x] **Step 3.6** - Animated, rolling HUD score counter.
- [x] **Step 3.7** - Fade scene transitions (`Crossfade`).

### Phase 4 - Audio ✅ (implemented)

> Audio assets are **original works synthesized in-repo** (CC0) by
> `tools/audio/generate_audio.py` - no third-party samples. Background music uses
> the framework `MediaPlayer` (two instances for a volume crossfade) rather than
> ExoPlayer, keeping the binary lean per the chosen-stack rationale. SFX use
> `SoundPool`, with playback-rate variation by food tier / combo. All audio is
> owned by a single `audio/GameAudio` facade created in `ui/App.kt`.

- [x] **Step 4.1** - Looping background music (CC0) via `MediaPlayer`.
- [x] **Step 4.2** - SFX via `SoundPool`: eat (per food tier/combo), shrink, mystery, game over, UI click, pause.
- [x] **Step 4.3** - Master / Music / SFX volumes in Settings; **lifecycle-aware** pause/resume + audio focus.
- [x] **Step 4.4** - Menu ↔ gameplay music crossfade.

### Phase 5 - Shaders & FX (AGSL) ✅ (implemented)

> All effects are original AGSL `RuntimeShader`s (`ui/game/Shaders.kt`), live only on **API 33+**
> and **degrade gracefully** to the existing Canvas rendering below it (the `BoardShaders` holder is
> `null` pre-33). "Rare foods" maps onto the current food model - **maxi / mystery / huge** pieces -
> since the v1.0.0 Gold/Mega types were replaced by the Phase 2.5 category/tier system.

- [x] **Step 5.1** - `RuntimeShader` glow on the snake's head (API 33+).
- [x] **Step 5.2** - Pulsing shader outline + halo on rare foods (maxi / mystery / huge).
- [x] **Step 5.3** - Animated background shader (drifting glows + vignette over the gradient).
- [x] **Step 5.4** - CRT / scanline filter, toggleable in Settings (API 33+).

### Phase 6 - Content & replayability

- [x] **Step 6.1** - Skin system (Classic / Neon / Retro / Pixel = palette + render style + optional shader).
- [x] **Step 6.2** - **Special food pieces / power-ups & hazards** (extends the Phase 2.5 food system).
      All are **maxi-sized** with a distinctive shape/symbol, **time-gated** (appear later in a session)
      and surfaced through the existing `GameEvent` channel + HUD timers. They add `FoodCategory.Special`,
      new `FoodEffect` cases, and `GameState` fields `debris: List<Debris>` + `effectTimers`. A Settings
      toggle disables the harmful ones (Earthquake / Explosion / Snail) for a calmer run.
  - [x] **Earthquake** - a pure-disruption **malus**: a **sustained screen shake** (timed
        `EffectKind.Quake`, a few seconds) that makes the board hard to read. It leaves **no debris**
        and does **not** change the snake's length (reworked in Step 6.7; it previously bit the tail
        and scattered debris).
  - [x] **Explosion** - severs the **last third** of the snake (keeps two-thirds); the detached tail
        stays on the board as **lethal debris** (crashing into it kills) that **auto-clears** after a
        long timer (~9 s) so it is a real obstacle, not a quick free shortening (tuned in Step 6.7).
  - [x] **Lightning / Snail** - temporary speed boost / slow-down (scales the tick interval) with a HUD timer.
  - [x] **Star** - brief invincibility / ghost (pass through walls, obstacles and self) with a HUD timer.
  - [x] **Freeze** - temporarily freezes special spawns / slows time - a strategic breather.
  - [x] **Jackpot** - rare piece granting a large score bonus (and a random growth).
- [x] **Step 6.7** - **Refinements & rebalance**:
  - **Earthquake / Explosion rebalance** - see Step 6.2 bullets above (sustained-shake malus; 1/3 split
    with longer-lived debris).
  - **Length-scaled scoring** - grow-food points scale with the current snake length (×1 short → ×5 cap,
    `GameEngine.lengthScoreFactor`), so the same bite is worth far more late in a run.
  - **HUD length** - the current snake length is shown in the fixed-height HUD second row.
  - **Length achievements** - three new max-length achievements (`LongHaul` 25 / `Anaconda` 50 /
    `Titanoboa` 90), tracked via `RunStats.maxSnakeLength`.
- [x] **Step 6.3** - Highscore tables per (level × size), per mode, in a "Records" screen.
- [x] **Step 6.4** - Local achievements.
- [x] **Step 6.5** - Extra modes: Endless, Time Attack.
- [x] **Step 6.6** - **Levels mode** (`GameMode.Levels`, `game/LevelsMode.kt`): ten **designed board
      shapes** (procedural over the responsive grid - no random obstacles; walls reshape the playable
      area and the frame follows the outline) repeating forever, one **speed cycle** faster each lap
      (170 ms → 80 ms floor); advance by **eating 12 foods** per level (HUD counts down); **3 lives**
      with same-level respawns (score and progress kept) and a rare 2×2 **extra-life** special
      (snake-head icon, capped at 5 → points); a `LevelIntro` status drives the animated
      **"Level x · Speed x" 3-2-1 countdown** overlay (game start, every advance, every respawn).
      The difficulty selector is hidden/ignored (scores pinned to one level per scale; a "best level"
      record is also kept); three new achievements (Climber / Tower Topper / Full Circle).

### Phase 6.9 - Feel, accessibility & retention (pre-launch polish)

> A grab-bag of polish, depth and retention ideas to tackle **before** the Play Store phase. Each step is
> self-contained and can be picked up in its own chat. Already shipped in this band: a **coyote/grace
> tick**, **haptics + near-miss feedback**, a **Daily Challenge** (with per-day modifiers), **combo
> "juice"** (head-on-fire + animated multiplier), a **near-miss visual flash**, a **Reduce motion &
> flashing** accessibility toggle, and a **hazard telegraph** (Step 6.9.1). The remaining steps below are
> still open.

**Game feel & telegraphing**

- [x] **Step 6.9.1 - Telegraph hazards before they strike.** Hazards (Earthquake / Explosion / Snail /
      time penalty) now give a one-tick "tell". `GameEngine.tick` emits `GameEvent.HazardImminent` when
      continuing straight would land on a hazard food next tick (predictive, advisory only - never affects
      the rules; covered by `HazardTelegraphTest`). The ViewModel fires a distinct **pre-haptic**
      (`GameHaptics.hazardWarning`, a crisp double-tick) and the renderer flashes a universal-red danger
      telegraph over the piece (steady highlight + strobing border + an outward "radar ping" ring), gated
      under **Reduce motion**. Hazard specials also wear a steady dashed **"caution" ring** so a dangerous
      piece reads at a glance, the calm counterpart to the eat-imminent flash.

- [x] **Step 6.9.2 - Richer game-over summary.** The game-over overlay now shows a run recap card under the
      best-score line: foods eaten, best combo, time survived, max length (and, in Campaign, the deepest
      level reached as "L-S"). The per-run accumulators in `GameViewModel` are surfaced via a small
      `RunSummary` object (`lastSummary`) passed to `GameOverOverlay`, which renders it as a `RunRecap`
      stat card. The Explosion's severed tail also now renders with the exact live-snake graphics in the
      current skin (same shaded tube / blocky segments, colours and taper) instead of plain faded blocks -
      contiguous debris cells are chained and drawn headless via the snake renderer in `GameBoard`.

**Accessibility & controls**

- [ ] **Step 6.9.3 - Swipe sensitivity + tap-to-turn.** Add a swipe-distance threshold setting and an
      optional "tap a screen half to turn" scheme (left half = turn left, right half = turn right) for
      comfortable one-handed play. Impl: new `ControlScheme.TapTurn` (or a sensitivity slider in Settings)
      wired through `GameControls` / `swipeToSteer`; persist via `SettingsRepository`.

- [x] **Step 6.9.4 - Auto-pause on focus loss.** When the app is backgrounded mid-run it now auto-pauses
      instead of letting the loop tick unseen. The `App` lifecycle observer calls
      `GameViewModel.togglePause()` on `ON_STOP` when the status is `Running` (it does not auto-resume on
      `ON_START` - the player resumes from the pause overlay).

**Depth / objectives**

- [ ] **Step 6.9.5 - Rotating objectives / missions.** Beyond the static achievements, show 2-3 rotating
      per-run goals ("eat 3 maxi foods", "reach a x5 combo", "survive 90s") that refresh daily/weekly and
      give a sense of purpose to a single run. Impl: a pure-Kotlin `Mission` model evaluated against the
      existing `RunStats`; persist completion + a small reward (points/cosmetic) in `SettingsRepository`;
      surface progress on the menu or game-over screen.

- [ ] **Step 6.9.6 - Player-activated power-up (one slot).** A single chargeable ability the player
      triggers on demand (e.g. a one-shot **Dash** that skips a few cells, or a **Bomb** that clears nearby
      debris/obstacles), instead of only random pickups - adds a tactical decision. Impl: a `GameState`
      "charge" field filled by play (e.g. by combos), a HUD button, and an engine action; respect
      determinism so it stays test-friendly.

- [x] **Step 6.9.7 - Environmental hazards in Campaign.** The level shapes are already procedural, so they
      lend themselves to **moving walls** (gates that open/close on a cycle) and **teleport pads** (enter
      one, exit its pair). Impl: extend `LevelsMode`/the wall set with time-phased cells and a teleport map;
      apply in `GameEngine.tick`; cover with `LevelShapesTest`-style connectivity/lethality tests. **Done:**
      new `game/LevelHazards.kt` (`Gate` with a deterministic tick-phased open/close cycle + close/open
      warnings, `TeleportPair`, `LevelHazards`); `LevelsMode.hazardsFor` assigns designed gates/portals to
      six levels (Open Field & Hourglass & Vault portals; Twin Pillars, Octagon, Three Chambers & Vault
      gates) and **sanitises** them - off walls / the spawn zone, distinct pads, and gates trimmed until a
      fully-closed set still leaves every cell reachable, so a closing gate can never trap the snake.
      `GameState` carries `gates`/`teleports`; `GameEngine.tick` teleports the head onto its pad's partner,
      treats closed-gate cells as lethal (Ghost passes through), excludes all hazard cells from food spawns,
      and emits `GameEvent.Teleported`. Premium rendering in `ui/game/GameHazards.kt`: energy-barrier gates
      with projector nodes, a sweeping scanline and a strobing close warning (smoothly eased across the
      inter-tick fraction), and swirling counter-rotating portal discs with paired colours + a jump
      burst/whoosh; the head's portal jump snaps interpolation. Covered by `LevelHazardsTest` (phase logic,
      determinism, connectivity-with-all-gates-closed, pad reachability, and engine teleport/gate/spawn
      behaviour).

**Retention & social**

- [ ] **Step 6.9.8 - Daily streak achievements & rewards.** Tie achievements (and maybe an unlockable skin)
      to the Daily Challenge streak ("7-day streak", "30-day streak"). Impl: the streak already lives in
      `SettingsRepository` (`dailyStreak`); add `Achievement` entries that read it, and gate a `Skin` behind a
      milestone.

- [ ] **Step 6.9.9 - Unlockable skins.** The four skins are all available immediately; gate some behind
      achievements / streaks / score milestones to give long-term goals. Impl: an "unlocked skins" set in
      `SettingsRepository`, a lock state in the Settings skin picker, and unlock triggers on game-over.

- [ ] **Step 6.9.10 - Weekly challenge / local Daily history.** A small screen showing the last 7 days'
      Daily results (best per day) and a "this week" aggregate. Impl: the per-day bests are already stored
      (`daily_best_<epochDay>`); add a bulk read over the last N days and a simple table view.

- [ ] **Step 6.9.11 - Share your score.** From the game-over (and Daily) screen, render a small score card
      and open the Android share sheet (`ACTION_SEND` with a generated image). Impl: draw the card to a
      `Bitmap`, save to cache via a `FileProvider`, and launch a share `Intent`.

- [ ] **Step 6.9.12 - Ghost replay of your best run.** Re-play a translucent "ghost" snake of your best run
      alongside the live one. The interpolation + per-tick state machinery already exists; record the best
      run's input/positions and render the ghost from the same `previousSnake`/`tickTimeNanos` path. Impl:
      capture a compact per-tick position log on a best run, persist it, and add a ghost layer to `GameBoard`.

- [x] **Step 6.9.13 - Per-skin item shapes.** Board items now match their skin's visual language: the flat
      skins (Retro / Pixel) render regular food **and** special power-ups / hazards as rounded squares
      (corner from the skin's `cornerFactor` - crisp Pixel, lightly rounded Retro), with a square grounding
      shadow, top sheen and a square dashed "caution" ring for hazards. The glow skins (Classic / Neon) are
      intentionally left as haloed discs (their strength). Implemented in `GameBoard` by branching the food /
      special renderers on `SkinPalette.useGlow`.

- [x] **Step 6.9.14 - Premium action buttons + menu polish.** Replaced the flat, fully-rounded Material
      `Button` / `OutlinedButton` used for the menu-style actions across the app with branded
      `SnakeButton` / `SnakeOutlinedButton` (`ui/components/SnakeButtons.kt`): defined 15dp corners, a
      top-lit gradient fill (or glassy fill + gradient rim), a hairline/coloured lift shadow and a tactile
      press-scale + ripple. The D-pad (`FilledTonalButton`), the HUD pause (`TextButton`) and the selector
      chips are intentionally untouched. The **main menu** also dropped the full-screen gliding-snake
      decoration; the wordmark gained a green gradient + soft glow, and a small in-game-style **`TitleSnake`**
      emblem now sits beneath it (recoloured from the active skin). The menu list became vertically
      scrollable so the taller buttons never clip on short screens. The wordmark's emblem is a static,
      in-game-accurate snake (`GameBoard.SnakeEmblem`, drawn through the gameplay `drawSnake` renderer so it
      matches the active skin exactly), sized to the measured width of the title.

**Onboarding & polish**

- [ ] **Step 6.9.13 - First-run onboarding / tutorial.** A brief, skippable intro on first launch (controls
      + objective), so new players aren't dropped cold. Impl: a one-time flag in `SettingsRepository`; a
      lightweight overlay or a 2-3 card pager shown before the first game.

### Phase 7 - Play Store distribution & cleanup

- [x] **Step 7.1** - Final app icon / adaptive icon + branded **SplashScreen API**; set `versionCode`/`versionName`.
- [ ] **Step 7.2** - Release hardening: **R8** + resource shrinking, verify the minified build runs.
- [ ] **Step 7.3** - **Signing**: upload keystore wired via env/CI secrets (never committed) + **Play App Signing**.
- [ ] **Step 7.4** - Build a signed **AAB**; **GitHub Actions** on `v*` tags → signed AAB artifact (optionally
      upload to a Play track via service account / Gradle Play Publisher).
- [ ] **Step 7.5** - Play readiness: privacy policy, **Data Safety** form, **IARC** rating, store listing
      (icon, feature graphic, phone screenshots), **internal testing** track.
- [ ] **Step 7.6** - Finalize docs: README screenshots/GIFs + Play link; confirm the legacy note.
- [x] **Step 7.7** - In-app **Credits / About** screen (author, GPL-3.0 license, asset attribution),
      reachable from the main menu; bundle the Google Gemini background music in place of the
      synthesized loops.

---

## 🧪 Definition of Done (per step)

- Builds without new warnings (`./gradlew assembleDebug`).
- Gradle sync succeeds in Android Studio.
- Runs on an emulator/device; manual smoke test of the affected flow OK.
- `docs/CREDITS.md` updated if assets were added.
- Commit message in English: `feat(android): step X.Y - <description>` or `fix` / `docs` / `chore`.

## 📊 High-level milestones

| Milestone | Steps | Outcome |
|---|---|---|
| **M1 - Parity** | End of Phase 1 | Snake playable on Android, same gameplay as v1.0.0 |
| **M2 - Pretty** | End of Phases 2–3 | Polished graphics, menus, professional look |
| **M3 - Alive** | End of Phases 4–5 | Audio + AGSL shaders, "premium arcade" feel |
| **M4 - Deep** | End of Phase 6 | Skins, power-ups, achievements, extra modes |
| **M4.5 - Felt** | End of Phase 6.9 | Game feel, accessibility & retention polish |
| **M5 - Published** | End of Phase 7 | Signed AAB on the Google Play Store, legacy archived |

---

## Notes

> Architecture decisions, constraints, and observations worth remembering.

- Board presets are intentionally portrait (~7:10) - landscape grid dimensions from the WinForms prototype were discarded in Phase 2.
- `GameEngine` is `Random`-injectable specifically to make unit tests deterministic; keep it that way.
- Dynamic color (`dynamicColor`) is **off** by design in `Theme.kt` - the game has a fixed dark-arcade brand palette.
- AGSL `RuntimeShader` requires API 33+. Since **`minSdk` is 33**, shaders are **always available** - do
  not reintroduce Canvas/older-API fallbacks for them (the old fallbacks were removed when the floor was raised).
- `game/` package must remain free of Android/Compose imports - this is what makes it testable with plain JUnit.
- **Mystery foods are resolved at spawn, not at eat**: `FoodTable.roll` rolls the concealed amount and
  stores the final `FoodEffect`, so `GameEngine.tick` consumes no randomness and stays deterministic.
- **Special power-ups / hazards** shipped in **Phase 6.2** (earthquake, explosion + lethal debris,
  Lightning/Snail, Star/ghost, Freeze, Jackpot) via
  `FoodCategory.Special`, the extra `FoodEffect` cases and `GameState.debris`/`effectTimers`. Effect
  durations are stored in **ms** and aged by the effective interval each tick; the loop reads
  `GameState.tickIntervalMillis` (never `level.tickMillis`) so speed effects actually change the pace.
  Keep that invariant. The board swipe uses a **single** `pointerInput` routed through
  `GameViewModel.onSwipe`, which steers by the swiped absolute direction.
- **Control scheme**: the default is **Swipe** (set in `GameViewModel.DEFAULT_CONTROL` and the
  persisted fallback in `SettingsRepository`); the two-button relative scheme and the D-pad remain
  selectable in Settings (choice persisted via DataStore). Phase 3 had originally shipped two-button
  as the default; it was flipped to swipe per the original request.
- **Level and Snake speed are independent** (split out of the old combined `Level`): `Level` now
  carries only `obstacleCount`; the pace lives in a separate `SnakeSpeed` enum (5 settings:
  Relaxed→Turbo, the old per-level tick values), persisted via DataStore (`Settings.snakeSpeed`,
  default `SnakeSpeed.DEFAULT = Relaxed`) and stamped onto `GameState.snakeSpeed`. The base tick is
  read from `snakeSpeed.tickMillis` in `GameState.tickIntervalMillis` for Time Attack (Endless and
  Campaign still override it). Both selectors sit on the **Custom** setup screen and in Settings (speed
  under Level), and are disabled in the modes that ignore them. Highscores stay keyed on `(mode, level,
  scale)` only - speed is **not** part of `ScoreKey`, so all speeds share a level/scale's best score.
- **Back-during-play behaviour is a setting** (`BackBehavior`: `Pause` (default) / `KeepPlaying`,
  persisted as `back_behavior`): it only affects a **Running** game (paused / game-over / Ready always
  return to the menu). `GameScreen` uses a `PredictiveBackHandler` (not `BackHandler`) so that in
  `KeepPlaying` + Swipe controls the back gesture's `BackEventCompat.swipeEdge` (EDGE_LEFT→Right,
  EDGE_RIGHT→Left) is fed to `GameViewModel.onSwipe`; a Back *button* press has no edge and is just
  ignored. Do not regress the non-running exit path.
- **Obstacle counts scale with board area**: `Level.obstacleCount` is tuned for the smallest (Cozy)
  board; `obstacleCountFor(level, board)` (in `BoardLayout.kt`) scales it by `(shortSide /
  OBSTACLE_REFERENCE_SHORT_SIDE)²` (reference 13) so density stays constant as the board grows, instead
  of a fixed handful of blocks looking sparse on large grids. `GameEngine.generateObstacles` uses it.
- **Item vanish times scale with board size**: in `GameEngine.tick` the regular/special vanish
  lifetimes are multiplied by `shortSide / VANISH_REFERENCE_SHORT_SIDE` (19) so bigger boards (incl.
  the `Colossal` 35-cell scale) give the snake proportionally more time to reach food/power-ups/hazards
  across the longer distances.
- The food spawn table is **time- and level-aware** (`FoodTable.roll(random, elapsedTicks, level, baseTickMillis)`);
  early game is intentionally simple (grow only) and ramps up - keep this progression intact. The tick
  count is converted to wall-clock ms via `baseTickMillis` (the snake's `SnakeSpeed` pace) so the
  unlock gates track real seconds at any speed.
- **Special spawn frequency** is a setting (`SpecialFrequency`) that scales both the special
  branch weight and its unlock gate in `FoodTable.roll`; it is independent of level and board size.
- **Regular food auto-vanishes** after `GameEngine.VANISH_FOOD_MS` (~7 s at the level's base pace),
  stamped via `Food.spawnTick` and removed one-per-tick in `tick`; **specials never vanish**. `tick`
  now refills on any drop below `FOOD_COUNT` (eat *or* vanish) and still stays seed-deterministic.
- **Audio assets are generated, not committed by hand**: `tools/audio/generate_audio.py`
  (stdlib only, no deps) synthesizes every clip in `app/src/main/res/raw/` as original CC0 16-bit
  mono WAV. Music loops are rendered to an exact bar count with zero-amplitude note ends so they
  loop seamlessly under `MediaPlayer`. Re-run the script to regenerate; don't edit the WAVs directly.
- **No encoder in CI**: there's no `ffmpeg`/`oggenc` available, so music ships as WAV (~0.6–0.8 MB
  each). If an OGG/Opus encoder becomes available, encode the music to shrink the APK before release.
- **Audio is decoupled from the model**: the pure `game/` package emits no audio. `GameViewModel`
  depends only on the `audio/GameSfx` interface (default `GameSfx.None`), so it stays unit-testable.
  The Android audio engines (`SoundManager`/`MusicManager`) live behind the `audio/GameAudio` facade,
  which is created once in `ui/App.kt` and released on the host's `onDispose`.
- **Music backend is framework `MediaPlayer`** (two instances for the crossfade), chosen over
  ExoPlayer to keep the binary lean. `MusicManager` requests audio focus and ducks/pauses on loss.
- **Shaders return premultiplied alpha** (Skia convention): the glow/halo shaders output `rgb * a`.
- **The CRT filter is a `RenderEffect`** applied to the board's `graphicsLayer` (API 33+), gated by a
  persisted `crtEnabled` setting that is only surfaced in Settings when `Shaders.supported`.
- **Levels mode walls are not obstacles**: `GameState.walls` is a separate cell set - lethal like
  out-of-bounds, excluded from every spawn (food, quake debris), passed through under Ghost, and
  rendered as "outside the board" (background fill + a boundary-edge frame) rather than bevelled
  blocks. Shapes come from `LevelsMode.shapeFor(levelIndex, board)` - deterministic, designed,
  guarded by `LevelShapesTest` (spawn-zone clearance, flood-fill connectivity, ≥60% playable).
- **`GameStatus.LevelIntro` separates staging from timing**: the engine atomically stages a level
  (walls swapped, snake at spawn, transients cleared) inside `setup`/`tick` and lands on `LevelIntro`;
  the 3-second countdown itself is wall-clock UI and lives in `GameViewModel` (`introCountdown`),
  which then calls `GameEngine.beginLevel` to seed food and resume. `tick` stays deterministic.
- **Levels mode pins the difficulty**: the Level (and Snake speed) selectors are disabled (greyed
  out, not removed, so the Ready overlay layout never reflows) and the ViewModel
  compares settings against `LevelsMode.SCORE_LEVEL` (not `settings.level`) while the mode is active -
  otherwise the settings collector would endlessly reset the board. Scores stay on the existing
  `ScoreKey(mode, level, scale)` codec; the deepest run is stored separately
  (`levels_progress_<scale>`, count of completed levels).
- **`elapsedTicks` is deliberately not reset across Levels transitions** so the time-gated food
  progression (shrink → maxi → mystery → specials) spans the whole run, not a single level.
- **`GameMode.Levels` is displayed as "Campaign"**: the user-facing `displayName` was renamed to stop
  the confusion with the difficulty "Level" selector, but the enum constant name doubles as the
  DataStore key (saved mode preference + `ScoreKey.storageName()`), so the constant - and internal
  identifiers like `LevelsMode` - must stay `Levels` to keep existing highscores readable.
- **Random obstacles are cluster-biased**: `generateObstacles` grows each new quadrant cell out of an
  already-placed one with probability `OBSTACLE_CLUSTER_BIAS` (0.6), so blocks clump into larger
  shapes instead of scattering as singletons. Per-level counts, 4-fold symmetry, border margins, the
  centre clear zone and seed determinism are unchanged (guarded by `ObstacleSymmetryTest`).
