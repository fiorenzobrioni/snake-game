# 🐍 Snake — Android (Kotlin + Jetpack Compose)

[![Kotlin](https://img.shields.io/badge/language-Kotlin-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org/)
[![Jetpack Compose](https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4?logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![Android](https://img.shields.io/badge/platform-Android-3DDC84?logo=android&logoColor=white)](https://www.android.com/)
[![minSdk](https://img.shields.io/badge/minSdk-24-blue)](https://developer.android.com/)
[![targetSdk](https://img.shields.io/badge/targetSdk-35-blue)](https://developer.android.com/)
[![License: MIT](https://img.shields.io/badge/license-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

A classic **Snake**, rebuilt as a **native Android game** in **Kotlin + Jetpack Compose**, on the way to a
polished, **Google-Play-publishable** title with animation, particles, shaders, audio and menus.

Gameplay is drawn on a Compose `Canvas` — the natural evolution of the immediate-mode rendering this project
started with. The step-by-step plan lives in [`ROADMAP.md`](ROADMAP.md).

> 🧱 **Current status:** Phase 1 (core gameplay) — a fully playable Snake at feature parity with the frozen
> v1.0.0: 5 difficulty levels, 5 board sizes, 7 food types, swipe + D-pad controls, score HUD and
> pause/restart. Visual polish, audio and shaders arrive in later phases.

---

## 🎯 What it will be

The classic Snake mechanics, extended with configurable features so every run feels different:

- 🍎 **Multiple food types** — different foods grant different growth and score; rarer foods are worth more.
- 🚧 **Obstacles** — scattered blocks that raise the difficulty.
- 🎚️ **Difficulty levels** — 5 levels (*Beginner* → *Legend*) tuning speed and obstacle count.
- 📐 **Board sizes** — 5 presets, from *Pocket* (30×20) to *Infinite* (120×80).
- ⏸️ **Pause, menus, highscores, audio, effects** — added progressively (see the roadmap).

### 🍽️ Food types at a glance

| Food          | Spawn chance | Growth | Notes                          |
|---------------|--------------|--------|--------------------------------|
| 🟢 Green      | ~25 %        | +2     | Common baseline                |
| 🔴 Red        | ~25 %        | +4     | Better bite                    |
| 🟡 Gold       | ~15 %        | +6     | Rare and tasty                 |
| 🔷 Blue ⭐    | ~10 %        | +2…+24 | Random jackpot                 |
| 🟢🟢 Mega Green | ~10 %      | +8     | 2×2 cells                      |
| 🔴🔴 Mega Red   | ~7 %       | +16    | 2×2 cells                      |
| 🟡🟡 Mega Gold  | ~3 %       | +24    | 2×2 cells, the big one         |

The score reward scales with the growth amount (`+10 points` per unit of growth).

### ⚔️ Difficulty levels

| Level | Name        | Obstacles | Tick (ms) |
|-------|-------------|-----------|-----------|
| 1     | Beginner    | 0         | 140       |
| 2     | Adventurer  | 8         | 120       |
| 3     | Warrior     | 15        | 100       |
| 4     | Champion    | 25        | 80        |
| 5     | Legend      | 40        | 60        |

### 📐 Board sizes

| Preset    | Cells     |
|-----------|-----------|
| Pocket    | 30 × 20   |
| Classic   | 45 × 30   |
| Grand     | 60 × 40   |
| Colossal  | 75 × 50   |
| Infinite  | 120 × 80  |

---

## 🛠️ Requirements & tools

Install on your development machine:

- **Android Studio** (latest stable) — bundles the JDK (JBR), the SDK Manager and the AVD emulator.
- **Android SDK** via the SDK Manager: **Platform API 35**, **Build-Tools 35.x**, **Platform-Tools** (`adb`),
  **Emulator** + a system image (e.g. API 35).
- A **test target**: an AVD emulator or a physical device with **USB debugging** enabled.
- **Gradle**: not needed globally — the project ships the **Gradle wrapper** (`./gradlew`).

The project targets `minSdk 24` (Android 7.0) and `compileSdk`/`targetSdk 35` (Android 15).

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

**Controls (touch):** swipe to change direction, or use the on-screen D-pad. 180° reversals are blocked, so
you can't instantly fold back into your own body. Pick a level and board size on the start screen; pause and
restart from the in-game controls.

> Richer menus, persistent high scores and the polished HUD come online during Phases 2–3 of the
> [roadmap](ROADMAP.md).

---

## 👨‍💻 Code layout

```
app/src/main/kotlin/com/brioni/snake/
├── MainActivity.kt     # Compose entry point
├── game/               # pure-Kotlin game model (no Android imports → unit-testable)
├── ui/                 # Compose UI + Material 3 theme
└── data/               # DataStore persistence (settings, highscores)
```

For architecture notes, conventions and the file map, see [`CLAUDE.md`](CLAUDE.md).

---

## 🗺️ Roadmap

The full plan — from these foundations through gameplay, visual polish, audio, AGSL shaders, content and
**Google Play distribution** — is in [`ROADMAP.md`](ROADMAP.md).

---

## 🏛️ Legacy — the v1.0.0 prototype

This project began as a **learning exercise**: a Snake built in **C# / .NET 10 / Windows Forms** with **GDI+**
rendering, shipped as **v1.0.0**. That desktop version is **frozen** and preserved under
[`legacy/SnakeGame/`](legacy/SnakeGame/) as a reference for the game model. See
[`legacy/README.md`](legacy/README.md) for its build notes. The native Android app described above is the
project's active direction.

---

## 📄 License

Distributed under the **MIT** license — clone it, study it, modify it. See [LICENSE](LICENSE) for details.
