# Copilot Instructions

## 🌐 Repository language — English only

**This project is English.** All committed artifacts — source code, identifiers, comments, log messages, user-facing strings, asset filenames, commit messages, branch names, PR titles/descriptions and Markdown docs — **must be in English**, regardless of the language the user uses in chat. Reply to the user in the user's language, but never let any other language end up on disk or in git history.

## Project overview

A native **Android** Snake game built **from scratch** in **Kotlin + Jetpack Compose** (Material 3), targeting a polished **Google-Play-publishable** release. Gameplay is rendered on a Compose `Canvas`. See `ROADMAP.md` for the plan and `CLAUDE.md` for the full guide.

The original **C# / .NET 10 / Windows Forms (GDI+)** version shipped as **v1.0.0** and is **frozen** under `legacy/` — reference only, no new features there.

## Build & run (Android — active project)

```bash
# From the repository root (Android SDK required; open in Android Studio to sync)
./gradlew assembleDebug     # build the debug APK
./gradlew installDebug      # build + install on a device/emulator
./gradlew lint              # Android lint
```

- `minSdk 24`, `compileSdk`/`targetSdk 35`. Kotlin + Jetpack Compose.
- The SDK location comes from `local.properties` (`sdk.dir`) or `ANDROID_HOME` (both machine-specific / git-ignored).
- Dependencies and versions live in the Gradle version catalog `gradle/libs.versions.toml`; reference them as `libs.*`. Always use the pinned `./gradlew` wrapper.

## Architecture

Package root `com.brioni.snake` under `app/src/main/kotlin/`:

- `MainActivity.kt` — single `ComponentActivity`; `installSplashScreen()` + `enableEdgeToEdge()`, then `setContent { SnakeGameTheme { ... } }`. Portrait is locked in the manifest; content uses `safeDrawingPadding()`.
- `game/` — **pure-Kotlin** game model (Phase 1+). Keep it free of Android/Compose imports so it stays unit-testable. Coordinates are grid-based (column/row); pixel mapping happens only in the renderer.
- `ui/` — Compose UI + `ui/theme/` (dark-leaning Material 3; dynamic color off on purpose).
- `data/` — Preferences DataStore persistence (settings, highscores), Phase 3+.

**Game loop (planned):** a coroutine ticker (`withFrameNanos` / per-level interval) advances the model, then Compose renders the new state on `Canvas`. Keep logic out of composables — they render state and emit events.

## Key conventions

- Official Kotlin style (`kotlin.code.style=official`): PascalCase types/Composables, camelCase functions/properties, `UPPER_SNAKE_CASE` constants; prefer `val` and data classes. Don't carry over the legacy C# `_field` naming.
- 180° direction reversals must stay blocked (can't fold instantly into the body).
- Free assets only (MIT/CC0/CC-BY), recorded in `docs/CREDITS.md` in the same change.
- Never commit signing material (`*.jks`, `*.keystore`, `keystore.properties`) — they are git-ignored.
- Roadmap-driven: match work to a `ROADMAP.md` step; keep every step buildable and testable.

## Gameplay reference (port from legacy v1.0.0)

The frozen `legacy/SnakeGame/SnakeForm.cs` is the source of truth for the gameplay numbers to port into the Kotlin `game/` model.

**Difficulty levels**

| Level | Name | Obstacles | Tick interval |
|-------|------|-----------|---------------|
| 1 | Beginner    | 0  | 140 ms |
| 2 | Adventurer  | 8  | 120 ms |
| 3 | Warrior     | 15 | 100 ms |
| 4 | Champion    | 25 | 80 ms  |
| 5 | Legend      | 40 | 60 ms  |

**Board sizes**

| Preset | Dimension (cells) |
|--------|-------------------|
| Pocket | 30 × 20 |
| Classic | 45 × 30 |
| Grand | 60 × 40 |
| Colossal | 75 × 50 |
| Infinite | 120 × 80 |

**Food types:** Green (+2), Red (+4), Gold (+6), Blue (random +2…+24), Mega Green (+8), Mega Red (+16), Mega Gold (+24); spawn chances ~25/25/15/10/10/7/3 %. Score scales at +10 points per unit of growth.
