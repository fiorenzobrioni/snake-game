# 🗺️ ROADMAP — Native Android Snake (Kotlin + Jetpack Compose)

Plan to take Snake from a learning prototype to a **polished, Play-Store-ready Android game**, built
**from scratch** in **Kotlin + Jetpack Compose**.

The original **C# / .NET 10 / WinForms (GDI+)** version shipped as **v1.0.0** and is now **frozen** under
[`legacy/`](legacy/). It is kept only as a reference for the *game model* — no further feature work happens
there. Everything below is the new app at the repository root.

---

## 🎯 Goal

A native Android Snake that looks and feels professional — smooth animation, particles, shaders, audio,
menus and replayability — and is **publishable on the Google Play Store** as a signed App Bundle (AAB).

## 🧰 Chosen stack

- **Language**: Kotlin
- **UI / rendering**: **Jetpack Compose** (Material 3) — gameplay drawn on Compose `Canvas`, the natural
  evolution of the immediate-mode GDI+ rendering learned in v1.0.0.
- **Build**: Gradle (Kotlin DSL) + version catalog, Gradle wrapper pinned.
- **Min/target SDK**: `minSdk 24` (Android 7.0), `compileSdk`/`targetSdk 35` (Android 15).
- **Persistence**: Preferences **DataStore** (settings, highscores).
- **Effects**: hand-drawn particles on `Canvas`; **AGSL `RuntimeShader`** for glow/background on **API 33+**
  with a graceful fallback below.
- **Audio**: `SoundPool` (SFX) + `MediaPlayer`/`ExoPlayer` (music).
- **Assets**: free licenses only (CC0 / CC-BY / MIT), recorded in [`docs/CREDITS.md`](docs/CREDITS.md).

### Why native Kotlin + Compose
- Smallest, fastest Android binary; no game-engine runtime to ship.
- Compose `Canvas` maps cleanly onto the grid-based rendering already prototyped in WinForms.
- First-class access to Android platform APIs (haptics, AGSL shaders, splash screen, Play distribution).

---

## 🛠️ Local tools / SDKs

Install on your development machine:

- **Android Studio** (latest stable) — bundles the JDK (JBR), the SDK Manager and the AVD emulator.
- **Android SDK** via SDK Manager: **Platform API 35**, **Build-Tools 35.x**, **Platform-Tools** (`adb`),
  **Emulator** + a system image (e.g. API 35).
- A **test target**: an AVD emulator or a physical device with **USB debugging** enabled.
- **Gradle**: not needed globally — use the project's `./gradlew` wrapper.
- **For Play distribution (Phase 7)**: a **Google Play Console** account, an **upload keystore**
  (`keytool` / Android Studio), and **Play App Signing** enabled.

> Godot, the .NET SDK and Visual Studio are **no longer required** for the active project.

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
├── docs/CREDITS.md               # asset credits (CC0/CC-BY/MIT)
├── legacy/SnakeGame/             # frozen C#/.NET 10 GDI+ v1.0.0 (learning)
├── .github/workflows/            # release CI (Phase 7)
├── CLAUDE.md  README.md  ROADMAP.md  LICENSE
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

### Phase 0 — Foundations ✅ (implemented)

- [x] **Step 0.1** — Restructure repo: move the .NET v1.0.0 prototype to `legacy/`; stand up the Gradle root
      (Kotlin DSL), version catalog, wrapper and Android `.gitignore`. Verify: `settings.gradle.kts` parses;
      Android Studio Gradle sync succeeds.
- [x] **Step 0.2** — App entry: `MainActivity` (`ComponentActivity`) + Compose Material 3 theme rendering a
      full-screen `Surface`. Splash via `core-splashscreen`. Verify: the app launches to a colored screen.
- [x] **Step 0.3** — Display: **portrait lock**, **edge-to-edge**, content respects **safe-area insets**.
      Verify: portrait, background draws under the bars, content stays clear of cutouts.
- [x] **Step 0.4** — Adaptive-icon placeholder (+ legacy fallback for API 24–25) and `docs/CREDITS.md`.

> 🎯 **End of Phase 0**: an installable app that launches to a themed, edge-to-edge, portrait screen.

### Phase 1 — Core gameplay (model parity with v1.0.0) ✅ (implemented)

- [x] **Step 1.1** — Pure-Kotlin model in `game/`: `Direction`, `Position`, `BoardSize`, `Level`, `Food`,
      `GameState`, `GameEngine`. No rendering. Verify: model compiles; unit tests on movement/growth/
      collisions/spawn pass (`./gradlew :app:testDebugUnitTest`).
- [x] **Step 1.2** — Compose `Canvas` grid renderer (`GameBoard`): board background + grid, obstacles,
      foods and the snake (bright head). Verify: a centered board with a snake is visible.
- [x] **Step 1.3** — Game loop via coroutine in `GameViewModel` at the per-level interval. Verify: the
      snake advances steadily until it dies.
- [x] **Step 1.4** — Touch input: **swipe gestures** (`detectDragGestures`) + an on-screen D-pad; 180°
      reversal blocked in the engine. Verify: the snake is steered by swipes and the pad.
- [x] **Step 1.5** — Food spawn + growth on eat + respawn (two foods kept on the board, as in v1.0.0).
      Verify: eating grows the snake.
- [x] **Step 1.6** — Collisions (walls, body, obstacles) → `GameOver` stops the loop. Verify: all three
      deaths trigger correctly (covered by unit tests).
- [x] **Step 1.7** — Score HUD (Compose overlay). Verify: score updates on eat.
- [x] **Step 1.8** — Pause / Restart controls (pause overlay, play-again, back-to-menu). Verify: pause
      overlay shows; restart resets state.
- [x] **Step 1.9** — Ported the **5 difficulty levels** (speed + obstacle count). Verify: changing level
      changes speed and obstacle count.
- [x] **Step 1.10** — Ported the **5 board sizes** (Pocket → Infinite). Verify: changing size reshapes the board.
- [x] **Step 1.11** — Ported the **7 food types** (Green/Red/Gold/Blue/Mega*) with the original probabilities
      and bonuses (Blue grants +2…+24). Verify: over time all types appear.

> 🎯 **End of Phase 1 (M1)**: feature parity with v1.0.0, playable on a phone — still visually "blocky".

### Phase 2 — Visual polish ✅ (implemented)

> Board presets were also re-tuned from 3:2 landscape (inherited from the desktop build) to **portrait**
> (~7:10) so the board fills a phone screen; the app stays portrait-locked by design. See `BoardSize`.

- [x] **Step 2.1** — Snake drawn from vector shapes: rounded body segments, a brighter head with eyes
      oriented to travel direction (curve/tail sprite refinement can follow later).
- [x] **Step 2.2** — Themed board background: vertical gradient + subtle grid, framed border.
- [x] **Step 2.3** — Animated food (pulsing scale; slow spin on the blue star; halo on gold/mega).
- [x] **Step 2.4** — Bevelled obstacle blocks (shadow + highlight) instead of flat rectangles.
- [x] **Step 2.5** — **Smooth inter-tick motion**: each segment interpolates from its previous cell to the
      current one over one tick via a `withFrameNanos` clock; logic stays grid-based.
- [x] **Step 2.6** — Particle burst on eat (colour per food type), simulated in cell space on `Canvas`.
- [x] **Step 2.7** — Screen shake on the game-over collision (damped wobble).
- [x] **Step 2.8** — Glow on the snake's head (radial gradient halo).

### Phase 3 — Pro UI / UX ✅ (implemented)

> Alongside Phase 3 the gameplay was tuned: a **two-button relative** control scheme (turn
> left/right relative to heading) is now the default, with classic swipe and the 4-button D-pad
> kept as selectable schemes; the board is now **responsive** — its rows×columns are computed from
> the device's play-area aspect ratio for a chosen granularity (`BoardScale`: Cozy/Classic/Epic) so
> it fills the screen with square cells (the old fixed `BoardSize` presets were dropped); obstacles
> are laid out with **4-fold symmetry** (clear border margin + clear spawn zone); and per-level
> speed was eased ~25% slower for comfortable touch play. The launcher icon was redrawn to resemble
> the in-game snake. Navigation uses a lightweight **state-based screen switch + `Crossfade`** rather
> than `navigation-compose` (single Activity, three destinations) — an intentional, justified
> deviation that keeps the binary lean and still delivers the fade transitions.

- [x] **Step 3.1** — Orbitron (OFL) display font + a reusable Material 3 type scale.
- [x] **Step 3.2** — Main menu (animated title, Play / Settings) with state-based navigation.
- [x] **Step 3.3** — Settings screen (level, board scale, control scheme) persisted via **DataStore**.
- [x] **Step 3.4** — Pause overlay with a **blur** (`Modifier.blur`) over the frozen board (API 31+, scrim fallback).
- [x] **Step 3.5** — Game-over screen + persistent **highscores per (level, scale)**.
- [x] **Step 3.6** — Animated, rolling HUD score counter.
- [x] **Step 3.7** — Fade scene transitions (`Crossfade`).

### Phase 4 — Audio

- [ ] **Step 4.1** — Looping background music (CC0) via `MediaPlayer`/`ExoPlayer`.
- [ ] **Step 4.2** — SFX via `SoundPool`: eat (per food type), game over, UI click, pause.
- [ ] **Step 4.3** — Master / Music / SFX volumes in Settings; **lifecycle-aware** pause/mute.
- [ ] **Step 4.4** — Menu ↔ gameplay music crossfade.

### Phase 5 — Shaders & FX (AGSL)

- [ ] **Step 5.1** — `RuntimeShader` glow on the snake's head (API 33+), graceful fallback below.
- [ ] **Step 5.2** — Pulsing shader outline on rare foods (Gold / Mega).
- [ ] **Step 5.3** — Animated background shader (gradient / stars / caustics).
- [ ] **Step 5.4** — (Optional) CRT / scanline filter, toggleable in Settings.

### Phase 6 — Content & replayability

- [ ] **Step 6.1** — Skin system (Classic / Neon / Retro / Pixel = palette + sprite set + optional shader).
- [ ] **Step 6.2** — Temporary power-ups (speed boost, ghost, magnet) with HUD timer.
- [ ] **Step 6.3** — Highscore tables per (level × size) in a "Records" screen.
- [ ] **Step 6.4** — Local achievements.
- [ ] **Step 6.5** — Extra modes: Endless, Time Attack.

### Phase 7 — Play Store distribution & cleanup

- [ ] **Step 7.1** — Final app icon / adaptive icon + branded **SplashScreen API**; set `versionCode`/`versionName`.
- [ ] **Step 7.2** — Release hardening: **R8** + resource shrinking, verify the minified build runs.
- [ ] **Step 7.3** — **Signing**: upload keystore wired via env/CI secrets (never committed) + **Play App Signing**.
- [ ] **Step 7.4** — Build a signed **AAB**; **GitHub Actions** on `v*` tags → signed AAB artifact (optionally
      upload to a Play track via service account / Gradle Play Publisher).
- [ ] **Step 7.5** — Play readiness: privacy policy, **Data Safety** form, **IARC** rating, store listing
      (icon, feature graphic, phone screenshots), **internal testing** track.
- [ ] **Step 7.6** — Finalize docs: README screenshots/GIFs + Play link; confirm the legacy note.

---

## 🧪 Definition of Done (per step)

- Builds without new warnings (`./gradlew assembleDebug`).
- Gradle sync succeeds in Android Studio.
- Runs on an emulator/device; manual smoke test of the affected flow OK.
- `docs/CREDITS.md` updated if assets were added.
- Commit message in English: `feat(android): step X.Y — <description>` or `fix` / `docs` / `chore`.

## 📊 High-level milestones

| Milestone | Steps | Outcome |
|---|---|---|
| **M1 — Parity** | End of Phase 1 | Snake playable on Android, same gameplay as v1.0.0 |
| **M2 — Pretty** | End of Phases 2–3 | Polished graphics, menus, professional look |
| **M3 — Alive** | End of Phases 4–5 | Audio + AGSL shaders, "premium arcade" feel |
| **M4 — Deep** | End of Phase 6 | Skins, power-ups, achievements, extra modes |
| **M5 — Published** | End of Phase 7 | Signed AAB on the Google Play Store, legacy archived |
