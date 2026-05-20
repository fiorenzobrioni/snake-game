# CLAUDE.md

Guide for Claude Code (and other AI agents) when working in this repository.

## 🌐 Repository language — English only

**This project is English.** Every artifact committed to the repository — source code, identifiers, inline comments, XML doc comments, log messages, user-facing strings, asset filenames, commit messages, branch names, PR titles and descriptions, issue templates, Markdown documentation — **must be written in English**.

This rule is independent of the language used in chat:

- The user may chat in any language (often Italian).
- The assistant replies to the user in the user's chat language.
- **Nothing of the chat language ever leaks into the repository.** If the user writes a task description in Italian, translate the intent into English before writing anything to disk, naming a branch, or composing a commit message.

If you spot any non-English content already in the repo, treat it as a bug to fix in the same change you are making.

## Project overview

A classic Snake Game, born as a learning project in **C# / .NET 10 / Windows Forms** to practice event-driven programming, game loops and GDI+ rendering.

The repository is migrating to **Godot 4 (.NET / C#)** to take the project from amateur level to "pro" level with eye-catching graphics, effects, audio and multi-platform builds. The migration follows the plan described in `ROADMAP.md`.

## Current state

- **Active version (legacy)**: WinForms in `src/SnakeGame/`
  - Windows only, target `net10.0-windows`
  - All logic + UI + rendering centralized in `SnakeForm.cs` (~740 lines)
  - 2D rendering with `Graphics`/GDI+ on a `PictureBox` (filled rectangles, ellipses, star polygons for Blue food)
  - Game loop via `System.Windows.Forms.Timer`
  - Input via `ProcessCmdKey` override (intercepts arrows/space/P/R before the controls)
- **Target version (upcoming)**: Godot 4 (.NET) in `godot/` — see ROADMAP

## Commands

### Build & run (WinForms version, Windows only)
```powershell
cd src/SnakeGame
dotnet build
dotnet run
```

There are no automated tests, linters or CI at this time.

### Build & run (Godot version — available from Phase 1 of the roadmap)
```bash
# Open the project with the Godot 4.x editor (.NET edition)
godot --path godot --editor

# Headless run from CLI
godot --path godot
```

## Architecture (legacy WinForms)

Main file: `src/SnakeGame/SnakeForm.cs`.

Everything lives in a single `SnakeForm` class. The logical areas, though not split into files, are:

- **Level and board-size configuration**: static arrays `LevelNames`, `BoardSizes`, and the `GetLevelObstacleCount`/`GetLevelSpeed` methods.
- **Game state**: `_snake` (list of `Point`), `_obstacles`, `_foods` (list of `FoodItem`), `_currentDirection`, `_isGameOver`/`_isPaused` flags, `_score`, `_pendingGrowth`.
- **WinForms UI** (`InitializeGameUI`): side panel with level / size radio buttons, score label, Start/Pause buttons, `PictureBox` canvas.
- **Game loop**: `_gameTimer.Tick` → `MoveSnake()` → `CheckCollision()` → `_gameCanvas.Invalidate()`.
- **Food types** (`FoodType`): Green/Red/Gold/Blue + Mega variants. Probabilities in `SpawnFood` via a percentage roll. Blue food grants a random increase of 2-24.
- **Rendering** (`GameCanvas_Paint`): draws food (ellipses or stars for Blue), obstacles (gray rects), snake (green rects, Chartreuse head), pause overlay.
- **Input** (`ProcessCmdKey`): arrows → direction change (with 180° block), Space/P → pause, R → restart.

## Conventions and guidelines for working in this repo

- **Language**: see the "Repository language — English only" section at the top of this file. Everything committed is English; the chat language is irrelevant.
- **Naming**: private fields with underscore prefix (`_snake`, `_score`); PascalCase for methods and types; constants SCREAMING or PascalCase depending on the existing context.
- **Branch**: development of the Godot migration happens on `claude/snake-godot-migration-vL9R6`. Do not push to `main` without explicit permission.
- **Roadmap-driven**: before adding a new feature, check `ROADMAP.md` to see whether it fits into an existing step, and complete steps in order. Every roadmap step must remain **buildable and testable** on its own.
- **No "surprise" refactors** on the legacy WinForms: the migration runs alongside the Godot project without rewriting the old one unless strictly necessary. When the Godot version reaches feature parity (end of Phase 1), the WinForms is archived in `legacy/`.
- **Assets**: new graphic/audio assets must be free and have a compatible license (MIT/CC0/CC-BY). Document the source and license in `godot/assets/CREDITS.md`.

## Key files

| Path | Role |
|---|---|
| `src/SnakeGame/SnakeForm.cs` | All logic + rendering of the WinForms version |
| `src/SnakeGame/SnakeGame.csproj` | .NET 10 Windows project |
| `README.md` | User documentation in English |
| `ROADMAP.md` | Step-by-step migration plan to Godot |
| `.github/copilot-instructions.md` | Copilot instructions (aligned with this file) |
