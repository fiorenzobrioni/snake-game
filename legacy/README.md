# Legacy — Snake Game v1.0.0 (C# / .NET 10 / WinForms)

This folder holds the **original learning prototype** of the project: a classic Snake
implemented in **C# / .NET 10 / Windows Forms** with **GDI+** rendering. It was shipped as
**v1.0.0** and is now **frozen** — kept only as a reference for the game model that the new
native Android app (Kotlin + Jetpack Compose, at the repository root) is built upon.

No further feature work happens here. Bug fixes are made only if strictly necessary.

## Build & run (Windows only)

```powershell
cd SnakeGame
dotnet build
dotnet run
```

- Target framework: `net10.0-windows` (Windows Forms requires Windows).
- All logic + UI + rendering live in `SnakeGame/SnakeForm.cs`.
- Solution file: `snake-game.slnx`.

See the repository root [`README.md`](../README.md) and [`PLANNING.md`](../PLANNING.md) for the
current Android project.
