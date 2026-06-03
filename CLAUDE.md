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
  - `minSdk 24`, `compileSdk`/`targetSdk 35`; `applicationId` `com.brioni.snake` (placeholder, finalize before first Play upload)
  - Phase 0 done: `MainActivity` launches a themed, edge-to-edge, **portrait** full-screen Compose surface, splash via `core-splashscreen`, adaptive-icon placeholder
  - Phase 1 done: full gameplay at parity with v1.0.0 — pure-Kotlin `game/` model + `GameEngine`, Compose `Canvas` renderer, coroutine loop in `GameViewModel`, swipe + D-pad input, 5 levels, 5 board sizes, 7 food types, score HUD, pause/restart; unit-tested. See `ROADMAP.md`
  - Phase 2+ (visual polish, audio, shaders, distribution) still ahead — see `ROADMAP.md`
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
- **`game/`** — pure-Kotlin game model: `Direction`, `Position`, `BoardSize`, `Level`, `Food`/`FoodTable`, `GameState`/`GameStatus` and the rules engine `GameEngine` (deterministic, `Random`-injectable). **Keep this package free of Android/Compose imports** so it stays unit-testable (`app/src/test/`).
- **`ui/`** — Compose UI and `ui/theme/` (`Color.kt`, `Theme.kt`, `Type.kt`); dark-leaning Material 3 scheme, dynamic color intentionally off for a consistent brand look. `ui/game/` holds the gameplay surface: `GameScreen` (layout + overlays), `GameBoard` (Canvas renderer), `GameViewModel` (state + coroutine loop), `GameControls` (swipe + D-pad), `GameColors`.
- **`data/`** — Preferences DataStore persistence (settings, highscores), from Phase 3.
- **Resources** (`app/src/main/res/`): `values/` (strings, colors, themes), adaptive icon in `mipmap-anydpi-v26/` with a self-contained vector fallback in `mipmap-anydpi/` for API 24–25.

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
| `legacy/SnakeGame/SnakeForm.cs` | Frozen v1.0.0 reference (game model) |
| `.github/copilot-instructions.md` | Copilot instructions (aligned with this file) |
