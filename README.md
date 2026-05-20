# 🐍 Snake Game (.NET 10)

[![C#](https://img.shields.io/badge/language-C%23-239120?logo=c-sharp&logoColor=white)](https://dotnet.microsoft.com/)
[![.NET](https://img.shields.io/badge/.NET-10.0-512BD4?logo=dotnet&logoColor=white)](https://dotnet.microsoft.com/)
[![Platform](https://img.shields.io/badge/platform-Windows-0078D4)](https://www.microsoft.com/)
[![License: MIT](https://img.shields.io/badge/license-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

A classic, timeless **Snake Game** built with **C#** and **Windows Forms**.
This repository started as a **learning project** to explore C# features and put **.NET 10** through its paces. It is a great playground for picking up event-driven programming, GDI+ rendering and managing a game loop inside a desktop application.

> 🚧 **What's next:** the project is migrating to **Godot 4 (.NET / C#)** to evolve into a cross-platform game with polished graphics, shaders, audio and menus. See [`ROADMAP.md`](ROADMAP.md) for the full plan.

---

## 🎯 Project overview

The project implements the classic Snake mechanics, extended with several modern and configurable features to make each run feel different:

- 🍎 **Multiple food types** — different kinds of food grant different bonuses (length and score). Rarer foods give more points and grow the snake more.
- 🚧 **Randomly generated obstacles** — gray blocks scattered around the board raise the difficulty.
- 🎚️ **Difficulty levels** — 5 levels (from *Beginner* to *Legend*) tune the snake's speed and the number of obstacles on the field.
- 📐 **Board sizes** — 5 presets, from *Pocket* (30×20) to *Infinite* (120×80).
- ⏸️ **Pause & dynamic UI** — pause any time; the layout adapts when the window is resized.

### 🍽️ Food types at a glance

| Food          | Spawn chance | Growth | Notes                          |
|---------------|--------------|--------|--------------------------------|
| 🟢 Green      | ~25 %        | +2     | Common baseline                |
| 🔴 Red        | ~25 %        | +4     | Better bite                    |
| 🟡 Gold       | ~15 %        | +6     | Rare and tasty                 |
| 🔷 Blue ⭐    | ~10 %        | +2…+24 | Star-shaped, random jackpot    |
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

## 🛠️ Requirements

- **Operating system**: Windows (required to run Windows Forms applications)
- **SDK**: .NET 10.0 SDK or newer installed on your system

---

## 🚀 Build & run

1. **Clone the repository**:
   ```powershell
   git clone https://github.com/fiorenzobrioni/snake-game.git
   ```

2. **Enter the project folder**:
   ```powershell
   cd snake-game/src/SnakeGame
   ```

3. **Build the project**:
   ```powershell
   dotnet build
   ```

4. **Launch the game**:
   ```powershell
   dotnet run
   ```

---

## 🎮 How to play

### Goal
Guide the snake around the playfield. Pick up the food that appears at random to grow the snake's body and increase your score. Avoid crashing into the outer walls, the obstacles scattered on the field, and the snake's own body!

### Controls

| Key                       | Action                          |
|---------------------------|---------------------------------|
| ⬆️ ⬇️ ⬅️ ➡️ Arrow keys  | Move the snake                  |
| `Space` or `P`            | Pause / resume                  |
| `R`                       | Restart the current run         |

### Tips
- 180° reversals are blocked: you cannot suddenly flip into your own body.
- Pause is also a quick way to switch level or board size — the change takes effect on the next run.

---

## 👨‍💻 For the curious — code layout

The bulk of the game (logic, UI and rendering) lives in a single file:
[`src/SnakeGame/SnakeForm.cs`](src/SnakeGame/SnakeForm.cs)

The project showcases a few foundational Windows Forms concepts:

- **Game loop** — driven by a `System.Windows.Forms.Timer` with a dynamic interval per level.
- **2D rendering** — entirely based on `Graphics` / `GDI+`, triggered by the `Paint` event of a `PictureBox`.
- **Input handling** — `ProcessCmdKey` is overridden to capture keyboard input (arrows, hotkeys) at a low level, before standard UI controls consume it.

For a deeper dive (architectural notes, file map, conventions), see [`CLAUDE.md`](CLAUDE.md).

---

## 🗺️ Roadmap

The next chapter of this project is a migration to **Godot 4** for a cross-platform, polished, web-publishable version. The full step-by-step plan — from initial Godot setup to shaders, audio, skins, power-ups and CI builds — is in [`ROADMAP.md`](ROADMAP.md).

---

## 📄 License

This project is distributed under the **MIT** license. Feel free to clone it, study it, modify it and use it for your own experiments! See the [LICENSE](LICENSE) file for details.
