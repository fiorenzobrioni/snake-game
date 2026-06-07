# CLAUDE.md

Guide for Claude Code (and other AI agents) when working in this repository.

## 🌐 Repository language — English only

**This project is English.** Every artifact committed to the repository — source code, identifiers, inline comments, KDoc, log messages, user-facing strings, asset filenames, commit messages, branch names, PR titles and descriptions, issue templates, Markdown documentation — **must be written in English**.

This rule is independent of the language used in chat:

- The user may chat in any language (often Italian).
- The assistant replies to the user in the user's chat language.
- **Nothing of the chat language ever leaks into the repository.** If the user writes a task description in Italian, translate the intent into English before writing anything to disk, naming a branch, or composing a commit message.

If you spot any non-English content already in the repo, treat it as a bug to fix in the same change you are making.

## Project overview

A classic Snake Game being built **from scratch as a native Android app** in **Kotlin + Jetpack Compose** (Material 3), aiming for a polished, **Google-Play-publishable** title with smooth animation, particles, AGSL shaders, audio and menus. Gameplay is rendered on a Compose `Canvas`. The migration plan is in `ROADMAP.md`.

The project started as a learning exercise in **C# / .NET 10 / Windows Forms (GDI+)**, shipped as **v1.0.0**. That desktop version is **frozen** under `legacy/` and kept only as a reference for the game model — no further feature work happens there.

## Current state

- **Active project**: native Android app at the **repository root**
  - Kotlin + Jetpack Compose (Material 3), built with Gradle (Kotlin DSL) + version catalog + wrapper
  - `minSdk 33` (Android 13), `compileSdk`/`targetSdk 36`; `applicationId` `com.brioni.snake` (placeholder, finalize before first Play upload). The `minSdk 33` floor means **AGSL shaders are always available** — no Canvas/older-API fallbacks are needed
  - Phase 0 done: `MainActivity` launches a themed, edge-to-edge, **portrait** full-screen Compose surface, splash via `core-splashscreen`, adaptive-icon placeholder
  - Phase 1 done: full gameplay at parity with v1.0.0 — pure-Kotlin `game/` model + `GameEngine`, Compose `Canvas` renderer, coroutine loop in `GameViewModel`, swipe + D-pad input, 5 levels, 5 board sizes, 7 food types, score HUD, pause/restart; unit-tested. See `ROADMAP.md`
  - Phase 2 done: visual polish — **portrait** board, smooth interpolated motion, gradient background, animated/haloed food, bevelled obstacles, glowing eyed snake head, eat particles and a game-over screen shake (`GameBoard`/`GameEffects`). See `ROADMAP.md`
  - Phase 3 done: pro UI/UX — Orbitron type scale, state-based menu/settings navigation (`ui/App.kt`) with `Crossfade` fades, **DataStore** settings + per-(level, scale) highscores (`data/SettingsRepository`), pause blur, rolling HUD score. Plus gameplay changes: **two-button relative** controls (default) with swipe/D-pad still selectable; a **responsive** board (`BoardScale` granularity → `BoardDimensions` computed from the device aspect via `boardFor`, replacing the old fixed `BoardSize` presets); **4-fold symmetric** obstacles; ~25% slower per-level speed; a redrawn snake launcher icon. See `ROADMAP.md`
  - Phase 4 done: audio — looping music (`MediaPlayer` crossfade), SFX (`SoundPool`), volume controls; all CC0, synthesized in-repo (`audio/`). See `ROADMAP.md`
  - Phase 5 done: AGSL shaders (API 33+, Canvas fallback) — head glow, food halos, animated background, optional CRT filter (`ui/game/Shaders.kt`). See `ROADMAP.md`
  - Phase 6 done: content & replayability — 4 **skins** (`SkinPalette`), **special power-ups / hazards** (`FoodCategory.Special`, `Debris`, `effectTimers`, variable `tickIntervalMillis`), a **Records** screen, local **achievements** (`game/Achievement`), and extra **modes** (Endless, Time Attack — `game/GameMode`). See `ROADMAP.md`
  - Phase 7 (Play Store distribution & cleanup) still ahead — see `ROADMAP.md`
- **Frozen (legacy)**: `legacy/SnakeGame/` — C#/.NET 10 WinForms v1.0.0
  - Windows only, target `net10.0-windows`; all logic + UI + rendering in `SnakeForm.cs`
  - Solution file `legacy/snake-game.slnx`; build notes in `legacy/README.md`

## Commands

### Build & run (Android — active project)
```bash
# From the repository root (Android SDK required; open in Android Studio for sync)
./gradlew assembleDebug     # build the debug APK
./gradlew installDebug      # build + install on a connected device/emulator
./gradlew lint              # Android lint
```
The Android SDK location comes from `local.properties` (`sdk.dir=...`) or the `ANDROID_HOME` env var. There are no automated tests yet; unit tests for the `game/` model are planned in Phase 1.

### Build & run (legacy WinForms — Windows only, frozen)
```powershell
cd legacy/SnakeGame
dotnet build
dotnet run
```

## Architecture (Android — active)

Package root: `com.brioni.snake` under `app/src/main/kotlin/`.

- **`MainActivity.kt`** — single `ComponentActivity`; calls `installSplashScreen()` + `enableEdgeToEdge()`, then `setContent { SnakeGameTheme { ... } }`. Content uses `safeDrawingPadding()` so the background draws edge-to-edge while UI stays clear of system bars/cutouts. Portrait is locked in the manifest.
- **`game/`** — pure-Kotlin game model: `Direction`, `Position`, `BoardScale`/`BoardDimensions` (+ the `boardFor` responsive-sizing function in `BoardLayout`), `ControlScheme`, `Level`, `Food`/`FoodTable`, `GameState`/`GameStatus` and the rules engine `GameEngine` (deterministic, `Random`-injectable). **Keep this package free of Android/Compose imports** so it stays unit-testable (`app/src/test/`).
- **`ui/`** — Compose UI and `ui/theme/` (`Color.kt`, `Theme.kt`, `Type.kt` — Orbitron type scale); dark-leaning Material 3 scheme, dynamic color intentionally off for a consistent brand look. `ui/App.kt` is the root, hosting state-based navigation (`Crossfade`) between `ui/menu/MainMenuScreen`, `ui/game/GameScreen` and `ui/settings/SettingsScreen`. `ui/game/` holds the gameplay surface: `GameScreen` (layout + overlays + board measurement), `GameBoard` (Canvas renderer), `GameViewModel` (state + coroutine loop, built via a `SettingsRepository` factory), `GameControls` (relative two-button / swipe / D-pad), `SkinPalette` (per-`Skin` colours + style flags via `paletteFor`).
- **`data/`** — `SettingsRepository` over Preferences DataStore (control scheme, level, board scale, skin, hazards toggle, volumes, CRT, and per-(mode, level, scale) highscores). `ui/records/RecordsScreen` shows the highscore tables.
- **Resources** (`app/src/main/res/`): `values/` (strings, colors, themes), adaptive icon in `mipmap-anydpi-v26/` (foreground + background + monochrome themed layer).

### Gradle
- `settings.gradle.kts` (repos + `include(":app")`), root `build.gradle.kts` (plugins declared `apply false`), `app/build.gradle.kts` (application module).
- Versions are centralized in `gradle/libs.versions.toml` (version catalog) — add/upgrade dependencies there, reference them as `libs.*`.
- The Gradle wrapper is pinned (`gradle/wrapper/`); always invoke `./gradlew`, never a global Gradle.

## Architecture (legacy WinForms — reference only)

Everything lives in `legacy/SnakeGame/SnakeForm.cs` (single `SnakeForm` class): level/size config, game state, WinForms UI, `System.Windows.Forms.Timer` loop, 7 food types with percentage-roll spawn, GDI+ rendering, and `ProcessCmdKey` input. Use it as the source of truth for porting gameplay numbers (levels, sizes, food probabilities/bonuses) into the Kotlin `game/` model. Do not add features here.

## Conventions and guidelines for working in this repo

- **Language**: see "Repository language — English only" above. Everything committed is English; the chat language is irrelevant.
- **Kotlin style**: official Kotlin conventions (`kotlin.code.style=official`). PascalCase for types and Composables, camelCase for functions/properties, `UPPER_SNAKE_CASE` for constants. Prefer immutability (`val`), data classes for model state, and idiomatic Kotlin over the legacy C# `_field` style.
- **Compose**: keep game logic out of composables; composables render state and emit events. Heavy per-frame work belongs in the `game/` model / the coroutine loop, not in recomposition.
- **Branch**: develop on the feature branch assigned for the task. Do not push to `main` without explicit permission.
- **Roadmap-driven**: before adding a feature, check `ROADMAP.md` for the matching step and complete steps in order. Every step must remain **buildable and testable** on its own.
- **No work on legacy**: `legacy/` is frozen. Touch it only for a strictly necessary fix, never for new features.
- **Assets**: new graphic/audio/font/shader assets must be free with a compatible license (MIT/CC0/CC-BY). Record source and license in `docs/CREDITS.md` in the same change.
- **Secrets**: never commit keystores or signing material (`*.jks`, `*.keystore`, `keystore.properties` are git-ignored). `local.properties` is machine-specific and git-ignored.

## Deliverables to the user

- **Debug APK at the end of every phase**: when a roadmap **phase** is completed, build the debug APK (`./gradlew assembleDebug`) and deliver it to the user **in chat** (the file at `app/build/outputs/apk/debug/app-debug.apk`) so they can install and try it. If the build environment has no Android SDK and one cannot be provisioned, say so explicitly instead of silently skipping.
- **Keep `README.md` current**: at the end of **every implementation**, review `README.md` and update it if the change affects anything user-facing (features, gameplay rules, controls, screenshots, build/run instructions). If no update is needed, no action is required.

## Documentation files — what goes where

Two files track the project's progress and health. **Write to both when completing a step.**

### `ROADMAP.md` — forward-looking plan (stable, curated)

`ROADMAP.md` is the authoritative source of truth for *where the project is going*.
Keep it clean and factual; do not clutter it with implementation notes or ephemeral tasks.

Update `ROADMAP.md` when:
- Ticking a step as done (`[ ]` → `[x]`) after it is fully implemented and verified.
- A phase definition changes (scope, ordering, or new steps inserted).
- A milestone target or "Definition of Done" criterion changes.

Do **not** add to `ROADMAP.md`:
- Session notes, observations, or "what I tried" prose.
- Short-term TODOs that are not formal roadmap steps.
- Bug reports or temporary workarounds.

### `DEVLOG.md` — running log (append-only, informal)

`DEVLOG.md` is the working notebook: what was done, what was noticed, what still needs doing.
New entries go **at the top of the Log section** (newest first).

Update `DEVLOG.md` when:
- **Completing a step or phase**: add a dated log entry summarising what was implemented and any notable decisions made.
- **Discovering a bug**: add an entry under **Known Bugs** with reproduction steps; remove it once fixed (reference the fix commit).
- **Recording a short-term TODO**: add a checkbox under **TODOs** for tasks that are not yet a formal roadmap step (e.g. "write unit test for edge-case X", "verify on API 33").
- **Noting an architectural constraint**: add a bullet under **Notes** for decisions that future contributors (or future Claude sessions) must not accidentally undo.

---

## Key files

| Path | Role |
|---|---|
| `app/src/main/kotlin/com/brioni/snake/MainActivity.kt` | Compose entry point |
| `app/src/main/kotlin/com/brioni/snake/ui/theme/` | Material 3 theme (Color/Theme/Type) |
| `app/build.gradle.kts` | Android application module config |
| `gradle/libs.versions.toml` | Dependency/version catalog |
| `app/src/main/AndroidManifest.xml` | Single portrait `MainActivity`, launcher |
| `docs/CREDITS.md` | Asset sources & licenses |
| `README.md` | User documentation (English) |
| `ROADMAP.md` | Step-by-step plan to a Play-Store release |
| `DEVLOG.md` | Development log, TODOs, known bugs, notes |
| `legacy/SnakeGame/SnakeForm.cs` | Frozen v1.0.0 reference (game model) |
| `.github/copilot-instructions.md` | Copilot instructions (aligned with this file) |
