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

## 🚀 Releases

- **Android `2.0.0`** (`versionCode 29`, tag `v2.0.0`) - the "premium" release. Consolidates every
  post-1.0.0 phase into one GitHub release: the fourth **Zen** mode (torus, no walls), mode-depth
  pacing (Campaign checkpoints, Time Attack pace multiplier + Fever Time, stepped Endless ramp, a
  9-modifier Daily/Random pool), six selectable **board terrains** with terrain-seeded UI accents,
  live skin/terrain previews, a 3-2-1 resume countdown with head beacon, the redesigned 5-card
  onboarding tour, the quit-run guard, the pinned game-over footer, and the redrawn "Serpentine"
  launcher icon. The major-version bump reflects the scale of the visual and gameplay overhaul over
  1.0.0. Distributed as a **debug-signed APK** on the GitHub Releases page; the Google Play track
  (signed AAB) remains a later phase.
- **Android `1.0.0`** (first Android release, GitHub) - the app exits "beta". Ships the full feature
  set (Endless / Time Attack / Campaign, power-ups & hazards, six skins, achievements, daily missions
  & challenge, onboarding, audio, AGSL shaders, accessibility). Distributed as a **debug-signed APK**
  on the GitHub Releases page (tag `v1.0.0`); the Google Play track (signed AAB) is a later phase.
  Distinct from the frozen desktop prototype's own `v1.0.0`.

## 🔮 Post-1.0.0 / future versions

> Deliberately deferred so 1.0.0 ships lean. Pick these up in their own chats for the next versions.

- **Gameplay depth** - Step 6.9.6 player-activated power-up (one chargeable slot: Dash / Bomb).
- **Sharing & social** - Step 6.9.11 share-your-score card via the Android share sheet.
- **Replayability** - Step 6.9.12 translucent ghost replay of your best run.
- **Google Play distribution** - Steps 7.2-7.6: R8/shrink verification, upload-keystore signing,
  signed AAB + tag-driven CI, Play readiness (privacy policy, Data Safety, IARC, store listing,
  internal testing track), and the README Play link.

---

> The original **C# / .NET 10 / WinForms (GDI+)** version shipped as its own desktop **v1.0.0** and is now **frozen** under [`legacy/`](legacy/). It is kept only as a reference for the *game model* - no further feature work happens there. Everything below is the new app at the repository root.

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

- [x] **Step 6.1** - Skin system (palette + render style + optional shader). Now **six** skins: Retro
      (default) / Classic / Neon / Pixel / Aurora / Ember. Render style is driven by `SkinPalette.useGlow`
      (head glow + food halos) and `SkinPalette.snakeStyle` (the per-skin snake body material). *(Updated
      2026-07-01: the old boolean `segmentedBody` was replaced by the `SnakeStyle` enum in Step 6.9.19,
      giving Neon / Aurora / Ember bespoke bodies.)* Skins carry an unlock rule and only Retro + Classic
      are free (see Step 6.9.9), though a pre-release `Skin.ALL_UNLOCKED_PREVIEW` flag currently makes them
      all selectable (see Step 6.9.19).
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
- [x] **Step 6.6** - **Levels mode** (`GameMode.Levels`, `game/LevelsMode.kt`): fifteen **designed board
      shapes** (procedural over the responsive grid - no random obstacles; walls reshape the playable
      area and the frame follows the outline) repeating forever, one **speed cycle** faster each lap
      (170 ms → 80 ms floor); advance by **eating 12 foods** per level (HUD counts down); **3 lives**
      with same-level respawns (score and progress kept) and a rare 2×2 **extra-life** special
      (heart icon, capped at 5 → points); a `LevelIntro` status drives the animated
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
      under **Reduce motion**. Hazard specials also wear a steady notched **danger bezel** so a dangerous
      piece reads at a glance, the calm counterpart to the eat-imminent flash. *(Updated 2026-07-01: the
      old dashed caution ring became a notched bezel with the premium per-skin tokens - see Step 6.9.18.)*

- [x] **Step 6.9.2 - Richer game-over summary.** The game-over overlay now shows a run recap card under the
      best-score line: foods eaten, best combo, time survived, max length (and, in Campaign, the deepest
      level reached as "L-S"). The per-run accumulators in `GameViewModel` are surfaced via a small
      `RunSummary` object (`lastSummary`) passed to `GameOverOverlay`, which renders it as a `RunRecap`
      stat card. The Explosion's severed tail also now renders with the exact live-snake graphics in the
      current skin (same shaded tube / blocky segments, colours and taper) instead of plain faded blocks -
      contiguous debris cells are chained and drawn headless via the snake renderer in `GameBoard`.

**Accessibility & controls**

- [x] **Step 6.9.3 - Swipe sensitivity + tap-to-turn.** A **swipe sensitivity** slider (0..100%, persisted as
      `swipe_sensitivity`) maps to the steer threshold via `swipeThresholdPx(sensitivity)` in `GameControls`;
      the midpoint 50% reproduces the existing tuned 48px distance exactly, so the default feel is unchanged.
      The slider is surfaced in Settings only when the Swipe scheme is active. A new `ControlScheme.TapTurn`
      lets the player turn **relative to the heading** by tapping the **left/right half of the board** (left =
      counter-clockwise, right = clockwise) via the `Modifier.tapToTurn` gesture - comfortable one-handed play
      with no buttons stealing board space. The old **Two buttons** scheme was **removed** as redundant
      (Tap-to-turn is the same relative-turn mechanic, one-handed and without occupying board space), leaving a
      clean, non-overlapping set: Swipe (absolute) / D-pad (4-way) / Tap-to-turn (relative). The **D-pad** was
      restyled premium and made **compact**: the flat `FilledTonalButton`s became skin-tinted glassy keys
      (top-lit gradient fill, coloured rim, lift shadow, press-scale + ripple) with **perfectly centred,
      Canvas-drawn vector chevrons** replacing the off-centre Unicode glyphs, laid out as a tight regular cross
      (58dp buttons, 6dp gaps) so the thumb travels less between directions when the snake is fast.

- [x] **Step 6.9.4 - Auto-pause on focus loss.** When the app is backgrounded mid-run it now auto-pauses
      instead of letting the loop tick unseen. The `App` lifecycle observer calls
      `GameViewModel.togglePause()` on `ON_STOP` when the status is `Running` (it does not auto-resume on
      `ON_START` - the player resumes from the pause overlay).

**Depth / objectives**

- [x] **Step 6.9.5 - Rotating objectives / missions.** Beyond the static achievements, three rotating
      per-run goals ("eat 30 foods", "reach a x6 combo", "survive 90s") that refresh daily and give a sense
      of purpose to a single run. **Done:** a pure-Kotlin `game/Mission` model (a stable `id`, a
      `description`, a `target` and a `progressOf(RunStats)` extractor, with `progress`/`completedBy`
      helpers) plus a `forDay(epochDay)` deterministic daily picker over a fixed `POOL` (same day → same
      goals, rotating as the day advances; SplitMix64-hashed like `Challenge`). Every mission is expressed
      purely over the existing `RunStats` fields (foods eaten, combo, survived seconds, score, max length,
      any power-up), so the rotation never offers an impossible goal in the current mode. Completion is
      persisted in `SettingsRepository` tagged with its day (`completed_missions` set of `"epochDay/id"`),
      so the daily set resets naturally (yesterday's completions never satisfy today's goals);
      `completedMissionsForDay` / `addCompletedMissions` / `completedMissionsTotal` expose it.
      `GameViewModel.onGameOver` evaluates today's missions against the run and surfaces newly completed
      ones (`newlyCompletedMissions`); the game-over overlay shows them in a "Mission complete!" banner and
      the main menu shows a compact "Today's Missions" card (the three goals with a tick on the ones done
      today + an `n / 3` counter). Covered by `MissionTest`.
      **Re-verified ideas:** the pure-`Mission`-over-`RunStats`, daily-rotation and menu/game-over
      surfacing are all valid and were kept. Two adaptations: (1) the "eat 3 maxi foods" example needs a
      maxi-food counter that `RunStats` does not carry, so the pool uses goals expressible from the
      existing stats instead of adding a new accumulator; (2) the "small reward (points/cosmetic)" was
      dropped - the app has no points/currency economy and all skins are unlocked (gating skins is the
      still-open Step 6.9.9), so inventing a currency here would be premature. Completion (and a lifetime
      `completedMissionsTotal`) is persisted, leaving the door open for a reward once Step 6.9.9 lands.

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
      burst/whoosh. The snake is routed *through* the pads: each segment dives into the entry portal and
      re-emerges at the exit (`interpolatedSnakeCenters`), and the body tube is cut at the portal seam
      (`isBrokenSpan`) instead of streaking across the board; reduce-motion keeps the instantaneous blink.
      Covered by `LevelHazardsTest` (phase logic,
      determinism, connectivity-with-all-gates-closed, pad reachability, and engine teleport/gate/spawn
      behaviour).

**Retention & social**

- [x] **Step 6.9.8 - Daily streak achievements & rewards.** Two `Achievement` entries read the post-run
      Daily streak (`RunStats.dailyStreak`): `WeekWarrior` (7-day) and `MonthMaster` (30-day). The streak
      also rewards skins: Aurora unlocks at 7 days, Ember at 30 (see Step 6.9.9). `GameViewModel.onGameOver`
      submits the daily score first, then reads `dailyStreak()` so the streak is current before evaluation.

- [x] **Step 6.9.9 - Unlockable skins.** Skins now carry a `SkinUnlock` rule (`Always` / `Score` / `Streak`).
      Retro (the new default) and Classic are always unlocked and listed first; Neon (score 500), Pixel
      (score 1500), Aurora (7-day streak) and Ember (30-day streak) are gated. An `unlocked_skins` set in
      `SettingsRepository` persists earned skins; `Skin.newlyUnlocked` evaluates new unlocks on game-over;
      the Settings picker shows locked cards (dimmed, lock badge, requirement hint) that can't be selected,
      and the game-over overlay surfaces any skin just unlocked.

- [x] **Step 6.9.10 - Weekly challenge / local Daily history.** `DailyHistoryScreen` ("This Week", reached
      from the Daily hub) shows the last 7 days of Daily results - best per day plus that day's twist - and a
      week aggregate (best / total / days played). `SettingsRepository.dailyBests(endEpochDay, days)` bulk-reads
      the per-day `daily_best_<epochDay>` keys.

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
      special renderers on `SkinPalette.useGlow`. *(Superseded for the special power-up / hazard pieces by
      Step 6.9.18, which replaced the disc/square split with premium per-skin material tokens; regular food
      still follows this step.)*

- [x] **Step 6.9.14 - Premium action buttons + menu polish.** Replaced the flat, fully-rounded Material
      `Button` / `OutlinedButton` used for the menu-style actions across the app with branded
      `SnakeButton` / `SnakeOutlinedButton` (`ui/components/SnakeButtons.kt`): defined 15dp corners, a
      top-lit gradient fill (or glassy fill + gradient rim), a hairline/coloured lift shadow and a tactile
      press-scale + ripple. The D-pad (`FilledTonalButton`), the HUD pause (`TextButton`) and the selector
      chips are intentionally untouched. The **main menu** also dropped the full-screen gliding-snake
      decoration; the wordmark gained a green gradient + soft glow, and a small in-game-style **`TitleSnake`**
      emblem now sits beneath it (recoloured from the active skin). The wordmark's emblem is a static,
      in-game-accurate snake (`GameBoard.SnakeEmblem`, drawn through the gameplay `drawSnake` renderer so it
      matches the active skin exactly), sized to the measured width of the title. *(Superseded layout note:
      the menu was later reworked from a single scrolling list into the bottom-anchored "game launcher"
      below.)*

- [x] **Step 6.9.15 - Main menu "game launcher" layout.** Reworked `MainMenuScreen` from a single
      vertically scrolling `Column` (which pushed the missions card below the fold) into a `Box` with a
      weighted `Column`: a `weight(1f)` **brand region** (pulsing wordmark + larger skin-coloured
      `SnakeEmblem`) over a bottom-anchored **controls cluster** grouped by type - a slim missions strip, a
      full-width **Play** (with a leading icon), a **Modes** shelf (Custom / Daily / Random) and a
      **Progress** shelf (Records / Achievements) - with `Settings` / `Credits` demoted to small overflow
      icon buttons in the top-right corner. Everything now fits one screen on normal/large phones without
      scrolling. Added `MenuTile` / `MenuIconButton` to `ui/components/SnakeButtons.kt` (reusing the glass-rim
      + press-scale styling) and the lean `material-icons-core` dependency (core `Icons.Filled` glyphs, not
      the heavy `-extended`). The old `DailyMissionsCard` collapsed into a single-line `MissionsStrip`
      (title + `✓`/`○` pips + `n / 3`); full mission descriptions remain on the game-over banner. The
      composable signature/callbacks are unchanged, so `App.kt` was untouched.

**Onboarding & polish**

- [x] **Step 6.9.16 - First-run onboarding / tutorial.** A brief, skippable intro shown once on first launch
      (gated by a one-time `onboardingCompleted` flag in `SettingsRepository`), then re-openable any time from a
      **"How to play"** button in Settings. Implemented as a dedicated, re-callable `ui/onboarding/OnboardingScreen`
      (a 4-card `HorizontalPager`): **Objective**, **Controls**, **Food** (Grow / Shrink / Mystery legend) and
      **Power-ups & hazards** (two labelled legends). Each card pairs a hand-drawn Canvas illustration - a framed
      mini-board styled from the player's active skin via `paletteFor` - with an Orbitron title + body; animation
      is restrained (a subtle artwork parallax driven by `pagerState.currentPageOffsetFraction` and an animated
      page indicator, over the shared AGSL menu backdrop). Wired into `App` as a new `Screen.Onboarding`: the brand
      splash hands off to it on first run (else straight to the menu) and it returns to the Menu (first run) or
      Settings (replay). *(Renumbered from the duplicate "6.9.13" this step was originally filed under.)*

- [x] **Step 6.9.17 - Brand-splash visual overhaul.** Redrew `BrandIntroScreen` in the game's **Retro skin**: the
      crawling snake and the **SNAKE** wordmark are now retro snake-body pieces (top-lit sheen via a vertical
      gradient fill). The two shader "explosions" were replaced with **real particle bursts** reusing the in-game
      `emitExplosionBurst` / `updateParticles` (a per-frame `withFrameNanos` loop spawns each burst once as the head
      sweeps past, advances the particles and drives the redraw). The board is more **premium**: the Retro gradient
      lifted with two slow-drifting warm glows, a radial vignette for depth and a soft-haloed framed border. The
      bloom post-filter stays (now also blooming the sparks).

- [x] **Step 6.9.18 - Premium, per-skin power-up / hazard tokens.** Replaced the flat coloured disc/square
      special pieces with **bevelled "tokens"** that have depth (material body gradient, rim/bevel, glow on
      glow skins or a drop shadow on flat skins) and an **embossed glyph**. Diversified per skin via a new
      `SpecialStyle` on `SkinPalette` (Classic = enamel, Neon = neon tube, Retro = phosphor, Pixel = pixel
      tile, Aurora = frosted glass, Ember = molten iron) - the effect's identity accent colour + symbol stay
      constant across skins, only the frame's material changes. Hazards now wear a **notched danger bezel**
      (a faint aura on glow skins) instead of the dashed ring. **Freeze**'s symbol became a faceted **ice
      crystal** and **Extra life**'s became a **heart** (removed `drawSnowflake` / `drawSnakeHeadIcon`).
      Extracted one shared renderer `drawSpecialToken` (`ui/game/SpecialIcons.kt`) used by both
      `GameBoard.drawSpecialFood` and the onboarding, and deleted the onboarding's duplicated disc drawing
      (`drawSpecialDisc` / `drawSpecialDiscAt`) so the tutorial and gameplay never drift.

- [x] **Step 6.9.19 - Per-skin snake bodies + pre-release skin unlock bypass.** Replaced the boolean
      `SkinPalette.segmentedBody` with a `SnakeStyle` enum dispatched in `GameBoard.drawSnake`, giving three
      skins a bespoke, premium (subtly animated) body: **Neon** = a hollow neon tube (dark core, glowing
      wall, pulsing bright filament, ring head); **Aurora** = a ribbon whose teal-cyan-blue-violet-green
      hues flow along the body and drift over time; **Ember** = a dark rock crust with a pulsing molten-lava
      vein that runs hottest at the head. Classic keeps the smooth **tube** and Retro / Pixel keep **blocks**,
      both made more premium (a crisp top specular on the tube; a volumetric diagonal gradient + specular
      corner on the blocks). Debris (severed tails) render in the same per-skin body. Animation reuses the
      `time` already passed to `drawSnake`, so the game loop is untouched. Also added
      `Skin.ALL_UNLOCKED_PREVIEW` (currently `true`): while set, every skin is selectable and the game-over
      "skin unlocked" toasts are suppressed - a temporary pre-release convenience to trial all skins. The
      underlying `SkinUnlock` rules / `Skin.newlyUnlocked` logic are untouched (still covered by `SkinTest`);
      flip the flag to `false` to restore gating before an official release.

### Phase 6.10 - Mode depth & pacing ✅ (implemented, post-1.0.0)

> A focused pass on the three modes' long-term appeal, from a design review of the mode lineup.
> Guiding rule: expose the value already built before adding content; every rhythm change must be
> seen and heard (premium feel); nothing may weigh the app down (no new assets).

- [x] **Step 6.10.1 - Campaign checkpoints.** The furthest level reached is persisted; once past
      level 1 the pre-game setup offers a "Start level" chip row (1..checkpoint). Starting past
      level 1 is a **practice run**: no highscore, no progress record, no Campaign achievements -
      stated in the setup caption and on the game-over overlay ("Practice run - not recorded").
      Records count from a Level 1 start only, keeping one honest leaderboard.
- [x] **Step 6.10.2 - Time Attack pace multiplier.** `SnakeSpeed.timeAttackScoreFactor`
      (x1.0 - x1.5) scales all Time Attack scoring, turning the pace choice into a declared
      risk/reward dial instead of a record-fairness hole (speed is not part of `ScoreKey`).
      Declared on the setup speed chips and in the HUD status line.
- [x] **Step 6.10.3 - Time Attack Fever Time.** The last 20 s double every point
      (`GameState.FEVER_MS` / `FEVER_SCORE_FACTOR`; `GameEvent.FeverStarted` on entry). Loud on
      purpose: pulsing amber board-frame glow, hot HUD clock, "Fever x2!" banner, sting SFX +
      haptic, and the music tempo steps up 12% (`MusicManager.setTempo`, reset on leaving).
- [x] **Step 6.10.4 - Endless stepped speed tiers.** Replaced the linear ramp (which flatlined at
      ~90 s) with 20 s tiers (x0.94 each, 190 ms base, 60 ms floor - alive for ~6-7 minutes).
      Difficulty adds a ramp head start (`Level.endlessTierHeadStart`, Beginner +0 .. Legend +4) so
      harder is faster from the first tick, spelled out in the setup caption. Every step is
      announced (`GameEvent.SpeedTierUp`: "Speed N!" banner, golden frame flare, zap SFX, haptic)
      and the live tier shows in the HUD. Plus a one-time mid-run **"New record!"** celebration
      when the live score passes the stored best (all modes).
- [x] **Step 6.10.5 - Challenge modifier pool widened (4 -> 9).** Added Grand Arena (Colossal
      board), Maxi Feast (all food 2x2), Combo Rush (halved combo window), Overdrive (hotter pace
      everywhere) and Old School (no specials). Each twist carries a one-line description shown on
      the Daily/Random cards and rides inside `GameState.modifier` so the pure engine applies it.
- [x] **Step 6.10.6 - Quieter default audio.** Defaults tuned to music 0.3 / SFX 0.6 (master 1.0)
      so the music sits as a light backdrop out of the box; saved sliders are untouched.

### Phase 6.11 - Zen mode ✅ (implemented, post-1.0.0)

> The fourth mode completes the lineup on the missing axis: a **calm** run for short, relaxed
> sessions. Built entirely from existing systems (the Ghost wrap, the food table, the menu music
> track) - premium feel, zero new assets.

- [x] **Step 6.11.1 - Rules (`GameMode.Zen`, `game/ZenMode.kt`).** The arena is a **torus**: all
      four edges wrap (the Ghost power-up's wrap, made permanent), so only the snake's own body can
      end the run. No obstacles (whatever difficulty; the selector is disabled, scores pinned to
      `ZenMode.SCORE_LEVEL`), no specials ever - just the grow/shrink/mystery food progression. The
      pace is the selected `SnakeSpeed`, fixed for the whole run ("pick your rhythm"), and the
      grow-combo window is **doubled** (`ZenMode.COMBO_WINDOW_FACTOR`) so streaks reward flow, not
      frenzy. Edge-hugging never fires the near-miss cue (the edge is a doorway, not a hazard).
      Zen is excluded from the Daily/Random challenge rotation.
- [x] **Step 6.11.2 - Toroidal rendering.** `interpolatedSnakeCenters` now glides a wrapping
      segment along the toroidal shortest path - out through one edge for the first half of the
      tick, in from the opposite edge for the second - clipped at the frame and with the body tube
      broken across the gap, so a crossing reads as one smooth pass (this also upgrades the old
      Ghost-wrap snap). The solid frame is replaced by a **porous boundary veil**: a soft teal mist
      bleeding inward from each edge plus a slowly drifting dashed stitch along the frame, both
      breathing with `zenGlow` (~5 s cycle; steady and non-drifting under reduce-motion) - so the
      open, wrapping edges read at a glance and Zen looks unmistakably different from the walled
      modes.
- [x] **Step 6.11.3 - Presentation.** Zen plays the calmer **menu track** during the run (crossfade
      on entry; no new audio asset). Setup captions explain the sleeping difficulty selector ("No
      obstacles here - the edges wrap around") and the speed choice ("Pick your rhythm - the pace
      never ramps"); the HUD status line shows mode - pace - board. Records get a single pinned-level
      row per scale (like Campaign); three new achievements (Inner Peace / Ouroboros / Eternal Flow).

### Phase 6.12 - Setup clarity ✅ (implemented, post-1.0.0)

- [x] **Step 6.12.1 - A caption for every Custom selector.** Each section of the pre-game setup now
      carries a one-line explanation that adapts to the selected mode: **Mode** gets an elevator
      pitch per mode; **Level** states what difficulty changes (obstacles + Endless ramp start) or
      why it sleeps (Campaign designs its own boards, Zen has none); **Snake speed** explains the
      Time Attack multiplier, the Zen rhythm, or why Endless/Campaign pace themselves; **Board
      scale** shows the selected grid's cells-per-short-side; **Start level** (Campaign) invites
      jumping to reached levels when no checkpoint is picked.
- [x] **Step 6.12.2 - Board scale renamed.** The middle preset's user-facing name changed from
      "Standard" to **"Explorer"** (Cozy - Explorer - Epic - Colossal). The enum constant stays
      `Classic` - it is a persisted DataStore / `ScoreKey` token - so no stored record or
      preference is orphaned.

### Phase 6.13 - Onboarding redesign & run-exit safety (1.2.0) ✅ (implemented, post-1.0.0)

- [x] **Step 6.13.1 - First-run tour redesigned (5 cards).** `OnboardingScreen` rebuilt as a premium,
      Play-Store-grade tour. The dedicated "how to steer" page (static arrows artwork) is gone -
      steering is a glanceable three-chip row (Swipe / Tap to turn / D-pad glyphs) on the welcome
      card, since the swipe default just works. The freed room teaches what a player cannot guess:
      card 1 **Welcome** (the live in-game `SnakeEmblem` slithering in the player's skin on a glass
      panel, goal + combo pitch, steering chips), card 2 **Food** (pulsing grow/shrink/mystery row +
      legend), card 3 **Power-ups & hazards** (real `drawSpecialToken` renderers, unchanged legend),
      card 4 **Modes** (four accent-rimmed cards with hand-drawn glyphs: infinity, stopwatch,
      checkpoint flag, enso), card 5 **Daily loop** (Daily Challenge / missions / achievements &
      records / skins & arenas rows + "reopen from Settings" hint). Onboarding best practices kept:
      skippable at every step, animated page indicator, one idea per card, system-back pages
      backwards (finishing only from the first card), every card scrolls so nothing clips, all
      artwork motion freezes under reduce-motion (a shared `withFrameNanos` clock that simply never
      advances).
- [x] **Step 6.13.2 - Quit-run confirmation on Back.** Back from the *paused* state used to end a
      live run silently. It now opens `QuitRunDialog` (error-tinted glass rim, "Quit this run?"):
      the safe **Keep playing** is the prominent filled action, **Quit run** the quiet outlined one,
      and outside-tap / Back dismisses safely - per the platform guidance on confirming destructive,
      easily-accidental actions. A pending resume countdown is cancelled back to the pause overlay
      first, and the dialog auto-drops if the run state moves on. Deliberate exits (the pause
      overlay's own Menu / Game setup buttons) stay one-tap; Back during *running* play keeps its
      `BackBehavior` setting, and Back from setup / game over still leaves directly (no progress at
      stake).
- [x] **Step 6.13.3 - Game-over overlay: pinned action footer.** With a long recap + missions +
      achievements + skin unlocks the buttons could scroll off short screens. The results column now
      scrolls in a `weight(1f)` area (still centring when it fits) above a pinned footer: full-width
      **Play again** plus **Game setup** / **Menu** sharing one row - actions always one tap away,
      content readability unchanged.
- [x] **Step 6.13.4 - Version 1.2.0** (`versionName 1.2.0`, `versionCode 28`).

### Phase 7 - Play Store distribution & cleanup

- [x] **Step 7.0** - Pre-publication polish: default **Back during play** is now **Keep playing** (fresh
      installs only); the snake-length readout is removed from the HUD; the D-pad is a compact single
      **4-wedge dial** (`GameControls.DirectionPad`) that frees board height; campaign grows from 10 to
      **15 levels** (`LevelsMode.LEVEL_COUNT`, 5 new mirror-symmetric shapes/hazards); achievements grow
      from 27 to **30** (3 new top-tier entries, depth-gated thresholds rebalanced for 15 levels).
- [x] **Step 7.1** - Final app icon / adaptive icon + branded **SplashScreen API**; set `versionCode`/`versionName`.
- [x] **Release 2.0.0** - version bumped to `versionName 2.0.0` (`versionCode 29`) and published as the
      `v2.0.0` GitHub release, consolidating all post-1.0.0 work (Phases 6.10-6.13, Steps 7.8-7.15).
- [ ] **Step 7.2** - Release hardening: **R8** + resource shrinking, verify the minified build runs.
- [ ] **Step 7.3** - **Signing**: upload keystore wired via env/CI secrets (never committed) + **Play App Signing**.
- [ ] **Step 7.4** - Build a signed **AAB**; **GitHub Actions** on `v*` tags → signed AAB artifact (optionally
      upload to a Play track via service account / Gradle Play Publisher).
- [ ] **Step 7.5** - Play readiness: privacy policy, **Data Safety** form, **IARC** rating, store listing
      (icon, feature graphic, phone screenshots), **internal testing** track.
- [-] **Step 7.6** - Finalize docs: README screenshots/GIFs + Play link; confirm the legacy note.
      *(README screenshots refreshed to the current build (8 shots, status bar/gesture pill cropped)
      and the legacy note reframed against the Android `1.0.0` release; the **Play link** stays
      pending until the Play listing exists.)*
- [x] **Step 7.7** - In-app **Credits / About** screen (author, GPL-3.0 license, asset attribution),
      reachable from the main menu; bundle the Google Gemini background music in place of the
      synthesized loops.
- [x] **Step 7.8** - Premium polish batch: (1) **Replay a past Daily** from the weekly history -
      `DailyHistoryScreen` rows are tappable (no layout change) and confirm before reliving that day's
      seeded challenge; replays never record, so the day's best and the streak are untouched
      (`GameViewModel.requestReplayStart` / `replayDay`, "Replay" HUD tag). (2) **Transition animations** -
      the snake bursts into particles before the game-over overlay (`deathAnimating`) and dissolves with a
      teleport sparkle before the Campaign level-up countdown (`levelVanishing`), via a whole-snake
      `BodyBurstEvent` + a `dissolve` envelope in `GameBoard`; both honour reduce-motion. (3) **Level 11
      (the Lattice) rebalanced** - pillars now only on alternate rows (~half the old density), still
      isolated/connected. (4) **CRT filter** made visible (fixed ~3px scanline period + aperture grille +
      deeper vignette in `Shaders.kt`). (5) **Debug-only "unlock all themes"** menu button gated on
      `BuildConfig.DEBUG` (stripped from release). Currently also hidden in debug builds behind the
      `SHOW_DEBUG_UNLOCK_SKINS` flag (default `false`); flip it to `true` to bring the shortcut back.
- [x] **Step 7.9 - Board terrains + Settings cleanup.** The board floor is now selectable independently
      of the skin: a new pure-model `BoardTerrain` enum (Default / Meadow / Abyss / Nebula / Dunes /
      Circuit, persisted as `board_terrain`, guarded by `BoardTerrainTest`) picks the animated AGSL
      backdrop while the snake / foods / obstacles / tokens keep the skin's look. `Default` plays on the
      skin's own gradient - the shared `BACKGROUND` shader now takes the palette's `boardTop`/`boardBottom`
      as colour uniforms, fixing a latent bug where the in-game board wore Classic's hardcoded colours
      under every skin (the menu backdrop pins those colours explicitly). The five standalone terrains are
      new AGSL shaders sharing one uniform interface (`origin`/`resolution`/`time`/`cellPx`), compiled
      lazily via `BoardShaders.terrainLayer`: **Meadow** (grid-aligned mowed-lawn checker, blade-noise
      texture, drifting cloud shadows), **Abyss** (deep-ocean caustic web + light shafts), **Nebula**
      (two-layer twinkling star field over drifting nebula wisps), **Dunes** (stacked moonlit dune ridges +
      rare sand sparkles) and a fifth grid-aligned floor. *(That fifth floor shipped as Circuit - dark PCB
      traces with travelling pulses - and was replaced by **Glacier** in Step 7.11, which also brightened
      Meadow / Abyss / Dunes. Step 7.13 then renamed **Default** to **Arcade** and made **Meadow** the
      out-of-the-box default terrain.)* Terrains are deliberately calm and slowly animated (stages, not
      protagonists) and each carries its own subtle grid-line tint (`terrainGridLine`). The Settings picker
      sits right under the skins as
      **live animated shader preview cards**; all terrains are free (no unlock gating - can be revisited
      later). Settings also got **cleaner**: the Level / Snake speed / Board scale selectors were removed,
      since they duplicated the Custom Game setup screen (both edit the same persisted preferences).
- [x] **Step 7.10 - Premium polish batch 2: live skin previews, pause-resume countdown, Campaign
      level progress.** (1) The Settings **skin cards** now show a **live, slithering mini snake**
      instead of static swatches: `SnakeEmblem` gained optional `time` / `waveAmplitude` /
      `cellFraction` / `contentAlpha` params (defaults keep the menu emblem static), so the card
      previews each skin's real animated body material (Neon filament, Aurora flow, Ember lava)
      through the actual gameplay renderer; skin and terrain cards share one preview clock
      (`rememberPreviewClock`). (2) **Resuming from pause runs a 3-2-1 countdown** instead of
      restarting instantly (`GameViewModel.resumeFromPause` / `resumeCountdown`,
      `RESUME_COUNTDOWN_SECONDS = 3`): the paused scrim clears, the board stays fully visible under a
      scrim-free `ResumeCountdownOverlay` (digit in a pulsing ring over a small grounding disc), and
      the renderer pulses a **locator beacon** on the snake's head - steady accent ring + soft glow,
      two expanding sonar rings (suppressed under reduce-motion) and a pulsing chevron pointing along
      the travel direction (`GameBoard.drawResumeBeacon`, driven by the new `resumeHighlight` flag,
      which also keeps `effectsActive` alive so the pulse animates while paused). The countdown is
      cancelled by Back/menu (`toSetup`) and on app backgrounding (`cancelResume` from `App`'s
      ON_STOP), so it can never restart the run unseen. (3) The Campaign intro banner shows lap
      progress - **"Level 3/15"** - via `LevelIntroOverlay`'s new `levelCount` param fed from
      `LevelsMode.LEVEL_COUNT`, so a future level-count change updates it automatically.
- [x] **Step 7.11 - Terrain tuning pass (user feedback).** (1) The **pause blur now lifts during the
      resume countdown**: the 3-2-1 exists to re-find the snake, so the board snaps back into focus
      (animated 14dp→0) the moment Resume is tapped, staying as sharp as during play. (2) **Meadow,
      Abyss and Dunes brightened** - higher-key base gradients, stronger caustics/crest glints/cloud
      contrast and a shallower vignette - after feedback that they read too dark in play. (3) **Circuit
      replaced by Glacier**: a frozen lake, deliberately the brightest floor of the set - pale icy blue
      mottled surface, two ridged-noise layers of bright static crack veins, a diagonal internal sheen
      drifting through the ice and cool twinkling glints. The old persisted `Circuit` value decodes to
      the `Default` terrain via the existing `runCatching` enum fallback, so stale prefs cannot crash.
      *(Follow-up in the same step family: the terrain value-noise `hash` was switched from the classic
      `fract(sin(dot))` to the sinless "Hash without Sine" (Dave Hoskins, MIT, credited in
      `docs/CREDITS.md`) - mobile-GPU `sin()` loses precision at large arguments and tore the noise into
      visibly misaligned rectangular patches, worst on Glacier's crack veins and Meadow's cloud shadows.)*
- [x] **Step 7.12 - Terrain-accented frame, "Snake skin" label, Meadow brand intro.** (1) In dark mode
      the board's framing border now follows the **selected terrain** (`terrainBoardBorder` in
      `GameBoard.kt`: hedge green for Meadow, caustic teal for Abyss, violet for Nebula, sand for Dunes,
      icy blue for Glacier; the Default floor keeps the skin's own border, and the light theme keeps its
      branded primary frame) - the frame belongs to the stage, not to the snake. (2) The Settings header
      **"Skin" was renamed "Snake skin"** so the two cosmetic pickers read as a pair with "Board terrain".
      (3) The **brand intro** now plays on the real **Meadow terrain shader** (grid-aligned lawn checker,
      cloud shadows, its own vignette) framed in Meadow's hedge green, replacing the bespoke Retro
      gradient + warm glows + extra vignette; the crawling snake and the SNAKE wordmark stay **Retro**
      snake-body pieces, and the splash grid line became a subtle dark tint to sit on grass.
- [x] **Step 7.13 - Terrain integration batch (user feedback).** (1) **Intro frame fully visible**: the
      splash board is now inset by a 10dp margin on every side (rows sized with floor instead of ceil),
      so the framed border + halo no longer run off the top/bottom of the canvas. (2) The **near-miss
      danger flash moved into the renderer** (`GameBoard`'s new `dangerFlash` envelope param, the old
      overlay `Box` in `GameScreen` removed): it re-traces the board's exact frame geometry - sharp
      corners flush with the border (no more rounded-rect overlap), on shaped Campaign boards it follows
      the real playable outline, it inherits the board shake for free, and it flares in a hot version of
      the terrain's accent (`lighten(terrainBoardBorder, 0.35)`). (3) The **board frame** (dark theme)
      already followed the terrain (Step 7.12); now the **Campaign gates** do too: `gateEnergyFor` in
      `GameHazards.kt` maps each terrain to a plasma family (Arcade warm orange, Meadow golden, Abyss
      aqua, Nebula violet, Dunes ember, Glacier electric blue); the amber closing-strobe stays universal
      as a warning cue. (4) **Meadow is the default terrain** on fresh installs (Settings decode fallback,
      data-class default, ViewModel seed) and sits first in the picker; the skin-following floor was
      renamed **Default → Arcade** (constant + display name; a stale persisted "Default" value falls back
      to Meadow via the `runCatching` enum decode).
- [x] **Step 7.14 - Premium UI pass: terrain-seeded accents, terrain menus, back headers, Settings
      cards, bespoke icons.** (1) The **UI accent now follows the selected terrain** - the in-app
      equivalent of Material You with the terrain as the seed: `TerrainAccents` in `ui/theme/Color.kt`
      maps each terrain to a tuned primary/secondary pair (dark variants bright enough to fill a button
      behind dark ink, light variants sunk for pale surfaces); `SnakeGameTheme` takes the terrain and
      **cross-fades accent changes** (600 ms), and Meadow/Arcade keep the brand greens exactly, so a
      fresh install looks unchanged. (2) The **menu backdrop renders the selected terrain's AGSL floor**
      (`AnimatedShaderBackground` gained a `terrain` param; Arcade keeps the classic drifting-glows
      gradient) under a vertical scrim - lighter behind the brand hero, heavier at the edges - so the
      menus live in the chosen world without costing text contrast. (3) **Secondary screens follow the
      Android back-affordance guideline**: a shared `ScreenHeader` (glassy back icon + centred title)
      replaced every bottom "Menu" button on Settings / Records / Achievements / Daily / Daily history /
      Random / Credits, with the content scrolling under a pinned header. (4) **Settings grouped into
      titled glass cards** (Controls, Appearance, Gameplay, Audio & feedback, Accessibility & help) in
      the `SnakeButtons` family look. (5) **Bespoke hand-authored menu icons** (`MenuIcons`: tune
      sliders, calendar, die, trophy, medal) replaced the misfitting `material-icons-core` picks
      (Refresh read as "reload", List was anonymous) without pulling in the heavy extended set; the
      missions strip's text pips ("✓"/"○") became drawn glyphs and the missions dialog wears the glassy
      rim.
- [x] **Step 7.15 - Premium launcher icon: the "Serpentine" on the Meadow board.** Complete redesign of
      the adaptive icon, unchanged since the Phase 0 placeholder - drawing only, no lettering. The
      **background layer** is now the Meadow board: the terrain shader's two-tone mowed-lawn checker on
      the 18dp play-grid rhythm (greens lifted in brightness for launcher legibility), under a top-light
      sheen and a soft radial vignette. The **foreground layer** is the Classic-skin snake as a single
      smooth **S-shaped tube** (round-capped stroke with a lighter core sheen, lime head with eye and
      catchlight, gold food ahead of the head, soft offset drop shadow), fitted to the adaptive safe
      zone. The **monochrome themed-icon layer** and the **splash-screen vector** (`splash_snake.xml`,
      shadow-less since the splash background is flat) mirror the new geometry; the orphaned
      `ic_launcher_background` colour resource was removed.

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
  Campaign still override it). Both selectors sit on the **Custom** setup screen only (speed under
  Level; their Settings duplicates were removed in Step 7.9), and are disabled in the modes that
  ignore them. Highscores stay keyed on `(mode, level,
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
