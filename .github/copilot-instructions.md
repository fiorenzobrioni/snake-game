# Copilot Instructions

## Build & Run

```powershell
cd src/SnakeGame
dotnet build        # build
dotnet run          # build and launch the game
```

Target framework: `net10.0-windows` (Windows Forms, requires Windows).

## Architecture

This is a single-Form Windows Forms game. All game logic lives in `src/SnakeGame/SnakeForm.cs` (partial class; `SnakeForm.Designer.cs` holds only the WinForms designer bootstrap).

**Coordinate system:** all game positions use a grid-based `Point` (X = column, Y = row). Pixel positions are always `point.X * _gridSize` / `point.Y * _gridSize`. Never mix pixel and grid units.

**Key fields on `SnakeForm`:**
- `_snake` — `List<Point>`, head is index 0, tail is last element.
- `_foods` — `List<FoodItem>`, each with type, size, and increase amount.
- `_obstacles` — `List<Point>`, randomly placed at game start.
- `_gridSize` — pixel size of one cell, computed dynamically on resize.
- `_width` / `_height` — grid dimensions, changeable via UI presets.

**Game loop:** driven by `System.Windows.Forms.Timer` (`_gameTimer`, configurable tick based on level). Each tick calls `MoveSnake()` → `CheckCollision()` → `_gameCanvas.Invalidate()`.

**Rendering:** all drawing happens in `GameCanvas_Paint` using GDI+ on a `PictureBox`. The canvas background is black; foods are colored circles/stars; obstacles are gray filled rectangles; snake body is green, head is Chartreuse.

## Key Conventions

- `TabStop = false` on the Start button, Pause button, and level/size radio buttons — intentional so arrow keys are never intercepted by button focus.
- `KeyPreview = true` on the form ensures `ProcessCmdKey` receives all key events before child controls.
- 180° direction reversal is explicitly blocked in `ProcessCmdKey` (e.g., can't go Down while moving Up).
- `SpawnFood()` and `GenerateObstacles()` both loop until a valid non-occupied cell is found — avoid spawning on border cells.
- Score is tracked purely in `_score` (int) and displayed on `_lblScore`; food points scale with length increase.

## Level & Dimension System

Five difficulty levels and five board size presets selectable via a left-side panel. Controls are disabled during gameplay and re-enabled on game pause/over.

| Level | Name | Obstacles | Timer interval |
|-------|------|-----------|----------------|
| 1 | Principiante | 0 | 140 ms |
| 2 | Avventuriero | 8 | 120 ms |
| 3 | Guerriero | 15 | 100 ms |
| 4 | Campione | 25 | 80 ms |
| 5 | Leggenda | 40 | 60 ms |

| Size Preset | Dimension (cells) |
|-------------|-------------------|
| Tascabile   | 30 × 20           |
| Classico    | 45 × 30           |
| Grandioso   | 60 × 40           |
| Colossale   | 75 × 50           |
| Infinito    | 120 × 80          |

`GetLevelObstacleCount()` and `GetLevelSpeed()` are the single source of truth for level configuration. `BoardSizes` defines the dimensions.
