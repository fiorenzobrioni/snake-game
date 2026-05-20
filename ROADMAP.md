# 🗺️ ROADMAP — Migration to Godot 4

Evolution plan for Snake Game from the current WinForms (GDI+) prototype to a "pro" production-quality game built on **Godot 4 (.NET / C#)**.

---

## 🎯 Goal

Transform the current amateur Snake into a visually polished, cross-platform game (Windows / Linux / macOS / Web) with animated graphics, particles, shaders, audio, menus and replayability — while keeping C# as the language.

## 🧰 Chosen stack

- **Engine**: [Godot 4.x — .NET edition](https://godotengine.org/) (MIT, free)
- **Language**: C# (.NET 8+, as required by Godot .NET)
- **Recommended IDE**: VS Code or Rider (with the Godot extension)
- **Assets**: free licenses only (CC0 / CC-BY / MIT). Suggested sources: [Kenney.nl](https://kenney.nl), [OpenGameArt](https://opengameart.org), [itch.io free assets](https://itch.io/game-assets/free)

### Why Godot and not something else
- Native C# support → reuse of existing skills
- Mature 2D editor: TileMap, AnimationPlayer, GPUParticles2D, GLSL-like shaders
- Web (HTML5) export → publishable on itch.io / GitHub Pages
- Open source, no royalties, large community

Alternatives discarded: MonoGame (no editor → more work), Unity (restrictive licensing, overkill), Stride (small community).

---

## 📐 Target repo layout

```
snake-game/
├── src/SnakeGame/         # Legacy WinForms (stays until parity is reached)
├── godot/                 # New Godot project
│   ├── project.godot
│   ├── scenes/
│   ├── scripts/
│   ├── assets/
│   │   ├── sprites/
│   │   ├── audio/
│   │   ├── fonts/
│   │   ├── shaders/
│   │   └── CREDITS.md
│   └── themes/
├── legacy/                # Final destination of the WinForms version (Phase 7)
├── .github/workflows/     # Export CI (Phase 7)
├── CLAUDE.md
├── README.md
└── ROADMAP.md
```

---

## ✅ Execution principles

1. **Every step must be buildable and testable** in isolation. Never leave the branch in a broken state.
2. One step = one commit (or a small group of logically related commits).
3. After each step: build + open the editor + manual smoke test, then tick the todo.
4. No opportunistic refactors outside the current step.
5. Free assets only: every added asset must be recorded in `godot/assets/CREDITS.md` with source and license.

---

## 🛣️ Roadmap

### Phase 0 — Setup & foundations

- [ ] **Step 0.1** — Add `godot/` containing an empty Godot 4.x .NET project (`project.godot`, `.csproj`, `.gitignore` for `.godot/`, `.mono/`, `bin/`, `obj/`). Verify: the editor opens the project without errors.
- [ ] **Step 0.2** — Create scene `Main.tscn` with a `Node2D` root and a full-screen `ColorRect` background. Verify: `godot --path godot` shows a colored window.
- [ ] **Step 0.3** — Configure display: base resolution 1280×720, stretch mode `viewport`, aspect `keep`. Verify: resizing the window scales the contents correctly.
- [ ] **Step 0.4** — Add `godot/assets/CREDITS.md` (empty template).

### Phase 1 — Core gameplay (functional parity with WinForms)

- [ ] **Step 1.1** — `GameBoard.cs` script with `WIDTH`/`HEIGHT`/`CELL_SIZE` constants. Draw the play grid as a bordered `ColorRect` background. Verify: the board is visible and centered.
- [ ] **Step 1.2** — `Snake.cs`: list of `Vector2I`, render the body as `ColorRect` repositioned every tick. No movement yet. Verify: a 3-segment snake is visible in the center.
- [ ] **Step 1.3** — Game loop using a Godot `Timer` at 150ms: move the snake in a single direction. Verify: it moves steadily upward until the edge.
- [ ] **Step 1.4** — Arrow input + 180° reversal block. Verify: controlled with arrows.
- [ ] **Step 1.5** — Spawn basic food (single type, red `ColorRect`). Snake grows when eating. Verify: eating grows the snake and respawns food.
- [ ] **Step 1.6** — Collisions: walls, body, obstacles. `GameOver` state stops the timer. Verify: the snake dies correctly in all three cases.
- [ ] **Step 1.7** — HUD: Score `Label` at the top. Verify: the score updates when eating.
- [ ] **Step 1.8** — Pause (Space/P) and Restart (R). Verify: keys work and a "PAUSED" overlay appears.
- [ ] **Step 1.9** — Port the **5 difficulty levels** (speed + obstacle count). Side UI with `OptionButton` or `Button` group. Verify: changing level changes speed and obstacles.
- [ ] **Step 1.10** — Port the **5 board sizes** (Pocket → Infinite). Verify: changing size resizes the board before the match.
- [ ] **Step 1.11** — Port the **7 food types** (Green/Red/Gold/Blue/Mega*) with the same probabilities and bonuses as the original. Verify: over time all types appear.

> 🎯 **End of Phase 1**: functional parity with WinForms. The game is playable but still visually "blocky".

### Phase 2 — Visual polish

- [ ] **Step 2.1** — Replace the `ColorRect` snake with a `Sprite2D` per segment using a tileset (head/body/curve/tail). Handle orientation and curves based on neighbors. Verify: the snake has proper sprites and curves rendered correctly.
- [ ] **Step 2.2** — Background `TileMap` with textures (grass, dungeon, neon — pick one). Verify: the board has a coherent background, no longer flat black.
- [ ] **Step 2.3** — Animated food sprites (`AnimatedSprite2D`, 4-6 frames of bobbing/sparkle). Verify: foods pulse/sparkle while idle.
- [ ] **Step 2.4** — Obstacles with sprites (rocks / walls) instead of gray rects. Verify: the board feels "set" in a place.
- [ ] **Step 2.5** — **Smooth movement**: interpolate segment positions via `Tween` between ticks (logical motion stays grid-based, but visually fluid). Verify: the snake no longer "jumps" between cells.
- [ ] **Step 2.6** — `GPUParticles2D` "burst" when eating food (color based on type). Verify: each bite produces particles.
- [ ] **Step 2.7** — Screen shake (Camera2D + tween) on game-over collision. Verify: on death the camera shakes briefly.
- [ ] **Step 2.8** — "Trail" / glow effect on the snake's head (light or particle wake). Verify: the head is visually distinct from the body.

### Phase 3 — Pro UI / UX

- [ ] **Step 3.1** — Import a custom font (e.g. *Press Start 2P* or *VT323*, free fonts) and create a reusable Godot `Theme`. Verify: the whole UI uses the new font.
- [ ] **Step 3.2** — `MainMenu.tscn` scene with animated title, Play / Options / Quit buttons, animated background (demo snake moving behind). Verify: launching the game starts from the menu.
- [ ] **Step 3.3** — `Settings.tscn` scene: choose level, board size, music/SFX volume. Persistence to `user://settings.cfg`. Verify: closing/reopening preserves choices.
- [ ] **Step 3.4** — **Pause** overlay with a blur shader on the background. Verify: while paused the background is blurred.
- [ ] **Step 3.5** — `GameOver.tscn` scene with final score, persistent **highscore** per (level, size), Retry / Menu buttons. Verify: highscores survive restarts.
- [ ] **Step 3.6** — Animated HUD score (counter that increments progressively with `Tween`, not in jumps). Verify: the score "rolls" like in arcades.
- [ ] **Step 3.7** — Scene transitions with fade-in/fade-out. Verify: no "hard" scene changes.

### Phase 4 — Audio

- [ ] **Step 4.1** — Add a looping background music track (CC0). `AudioStreamPlayer` with autoplay. Verify: music plays in the background.
- [ ] **Step 4.2** — SFX: eat (variants per food type), game over, UI click, pause. Verify: every event has its own sound.
- [ ] **Step 4.3** — Separate audio buses Master/Music/SFX + sliders in Settings. Verify: volumes are adjusted independently.
- [ ] **Step 4.4** — Music crossfade between menu and gameplay. Verify: the transition is smooth.

### Phase 5 — Shaders & FX

- [ ] **Step 5.1** — **Glow** shader on the snake's head (additive blending or `CanvasItem` shader). Verify: the head emits light.
- [ ] **Step 5.2** — Pulsing shader on rare foods (Gold/Mega) with a luminous outline. Verify: rare foods clearly look more "precious".
- [ ] **Step 5.3** — Background shader (animated gradient, parallax stars, or water caustics). Verify: the background feels alive.
- [ ] **Step 5.4** — (Optional) CRT/scanline filter as a Settings-toggleable option. Verify: can be enabled/disabled in real time.

### Phase 6 — Content and replayability

- [ ] **Step 6.1** — **Skin** system (Classic / Neon / Retro / Pixel). Skin = sprite set + tileset + palette + optional shader. Selection in Settings. Verify: changing skin updates everything.
- [ ] **Step 6.2** — Temporary power-ups: speed boost, ghost mode (pass through walls for 3s), magnet (attracts food). Rare spawn. Verify: power-ups appear and work, with HUD icon and timer.
- [ ] **Step 6.3** — Highscore table per (level × size), shown in a "Records" menu. Verify: top 5 scores are visible and persistent.
- [ ] **Step 6.4** — Local achievements (e.g. "100 foods eaten", "survive 5 min on Legend"). Verify: they unlock and are visible in a panel.
- [ ] **Step 6.5** — Extra modes: "Endless" (no obstacles, growing board), "Time Attack" (60s). Verify: selectable from the menu, each has its own rules.

### Phase 7 — Distribution & cleanup

- [ ] **Step 7.1** — Configure Godot export presets: Windows, Linux, Web (HTML5). Verify: a working build is produced for each target.
- [ ] **Step 7.2** — GitHub Actions workflow that builds exports on every `v*` tag and publishes to Releases. Verify: creating a tag produces a release with the binaries.
- [ ] **Step 7.3** — Web build published on GitHub Pages (or itch.io). Verify: playable in the browser.
- [ ] **Step 7.4** — Move `src/SnakeGame/` to `legacy/SnakeGame/` and update README + CLAUDE.md to point to the Godot version as the "main" one. Verify: docs reflect the new state.
- [ ] **Step 7.5** — Update `README.md` with screenshots/GIFs of the new version, Godot instructions, link to the web build. Verify: a new user understands what to do in 30 seconds.

---

## 🧪 Definition of Done (per step)

- Builds without new warnings
- Opens in the Godot editor without errors
- Manual smoke test of the affected flow OK
- `godot/assets/CREDITS.md` updated if assets were added
- Commit message in English in the format: `feat(godot): step X.Y — <description>` or `fix`/`docs`/`chore`

## 📊 High-level milestones

| Milestone | Steps | Outcome |
|---|---|---|
| **M1 — Parity** | End of Phase 1 | Snake playable in Godot, same gameplay as WinForms |
| **M2 — Pretty** | End of Phases 2-3 | Polished graphics, UI with menus, professional look |
| **M3 — Alive** | End of Phases 4-5 | Audio + shaders, "premium arcade" feel |
| **M4 — Deep** | End of Phase 6 | Skins, power-ups, achievements, replayability |
| **M5 — Public** | End of Phase 7 | Multi-platform + web builds, legacy archived |
