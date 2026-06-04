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

> 🧱 **Current status:** Phase 3 (pro UI/UX) complete, on top of the **Phase 2.5 food system** — a
> polished, playable Snake with an animated main menu and settings, persistent highscores, a
> responsive full-screen board, control schemes (two-button relative by default, plus swipe and D-pad),
> smooth interpolated motion, particles, screen shake and a pause blur. The food layer adds two
> **categories** (grow / shrink) with magnitude tiers, **maxi** sizes, a **mystery "?"** piece, a
> **time-gated** progression that ramps mid-session, and a **combo** score multiplier. Audio and AGSL
> shaders arrive in later phases.

---

## 🎯 What it will be

The classic Snake mechanics, extended with configurable features so every run feels different:

- 🍽️ **Two food categories** — **grow** food makes the snake longer; **shrink** food trims it back.
- 🔠 **Magnitude tiers + maxi sizes** — each category comes in several strengths, and a 2×2 **maxi**
  variant that amplifies the effect.
- ❓ **Mystery pieces** — a "?" food per category with a random amount.
- ⏳ **Time-gated progression** — early on you only see growing food; shrink, maxi and mystery pieces
  unlock as the session goes on (sooner on harder levels), so a run ramps up in difficulty.
- ✖️ **Combo multiplier** — eating in quick succession multiplies your score (up to ×5).
- 🚧 **Obstacles** — scattered blocks that raise the difficulty.
- 🎚️ **Difficulty levels** — 5 levels (*Beginner* → *Legend*) tuning speed and obstacle count.
- 📐 **Responsive board** — pick a granularity (*Cozy* / *Classic* / *Epic*); the board's rows and
  columns are computed from your device's screen so it fills the display with square cells.
- 🎮 **Control schemes** — two-button *relative* steering by default, or classic swipe / D-pad.
- ⏸️ **Pause, menus, highscores, audio, special power-ups** — added progressively (see the roadmap).

### 🍽️ Food system at a glance

| Category | Tiers (standard growth/shrink) | Maxi (2×2) | Mystery "?" | Score |
|----------|-------------------------------|------------|-------------|-------|
| 🟢 **Grow**   | +2 / +4 / +6 / +8 | doubles the amount | random +2…+24 | `+10 × growth × combo` |
| 🟠 **Shrink** | −2 / −3 / −5      | doubles the amount | random −2…−14 | small symbolic bonus (5 / 10 maxi) |

The snake never shrinks below **3 segments**. Grow food drives the score (scaled by the combo
multiplier); shrink food is a tactical tool — it gives only token points but lets you cut your length
to manoeuvre. The big, rarer **special power-ups and hazards** (earthquake, explosion, speed, ghost,
freeze, jackpot) are planned for a later phase — see the [roadmap](ROADMAP.md).

### ⚔️ Difficulty levels

| Level | Name        | Obstacles | Tick (ms) |
|-------|-------------|-----------|-----------|
| 1     | Beginner    | 0         | 175       |
| 2     | Adventurer  | 8         | 150       |
| 3     | Warrior     | 15        | 125       |
| 4     | Champion    | 25        | 100       |
| 5     | Legend      | 40        | 75        |

Obstacles are laid out with **4-fold symmetry** (mirrored left/right and top/bottom), with a clear
margin next to every wall and a clear zone around the snake's spawn.

### 📐 Board scale

The board is **responsive**: pick a granularity and the rows×columns are computed from your device's
play-area aspect ratio so the board fills the screen with square cells.

| Scale   | Cell size | Columns (target) |
|---------|-----------|------------------|
| Cozy    | larger    | 12               |
| Classic | medium    | 18               |
| Epic    | smaller   | 26               |

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

**Food:** green = grow, warm/orange = shrink, "?" = a mystery amount. Bigger (2×2 maxi) pieces and the
mystery and shrink foods only start appearing as the session runs on — so each run gets more eventful.
Chain bites together to build a **combo** and multiply your score, and use shrink food to cut your length
when the board gets tight (you never drop below 3 segments).

**Controls (touch):** by default, two large buttons fill the bottom of the screen and turn the snake
**left / right relative to its heading**. Prefer something else? Switch to **swipe** or the classic
**D-pad** in **Settings** — your choice is saved. 180° reversals are blocked, so you can't instantly
fold back into your own body. Pick a level and board scale on the start screen; pause and restart from
the in-game controls. Your best score is kept per (level, scale).

> Audio and the special power-ups (earthquake, explosion, speed, ghost, freeze, jackpot) come online in
> later phases of the [roadmap](ROADMAP.md).

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
