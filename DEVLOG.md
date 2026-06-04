# DEVLOG — Snake Game (Android)

Development log, working notes, open TODOs and known bugs.
For the forward-looking plan and phase checklists see [`ROADMAP.md`](ROADMAP.md).

---

## TODOs

> Short-term tasks not yet tracked as a formal roadmap step.

- [ ] Finalise `applicationId` from `com.brioni.snake` placeholder before first Play upload (Step 7.1)
- [ ] Write unit tests for `GameEngine` edge-cases: wall collision on all four sides, body self-collision, 180° reversal block
- [ ] Verify smooth-motion interpolation on low-end devices (API 24/25)
- [ ] Verify the mystery "?" glyph renders crisply on small cells (dense boards) and on API 24
- [ ] Re-tune the food spawn weights / time gates after playtesting on a device
- [ ] Consider per-tier / per-category eat SFX cues when audio lands (Phase 4)

---

## Known Bugs

> Reproducible issues found during testing. Remove entry once fixed and reference the fix commit.

*None open.*

---

## Notes

> Architecture decisions, constraints, and observations worth remembering.

- Board presets are intentionally portrait (~7:10) — landscape grid dimensions from the WinForms prototype were discarded in Phase 2.
- `GameEngine` is `Random`-injectable specifically to make unit tests deterministic; keep it that way.
- Dynamic color (`dynamicColor`) is **off** by design in `Theme.kt` — the game has a fixed dark-arcade brand palette.
- AGSL `RuntimeShader` (Phase 5) requires API 33+; every usage needs a graceful Canvas fallback for API 24–32.
- `game/` package must remain free of Android/Compose imports — this is what makes it testable with plain JUnit.
- **Mystery foods are resolved at spawn, not at eat**: `FoodTable.roll` rolls the concealed amount and
  stores the final `FoodEffect`, so `GameEngine.tick` consumes no randomness and stays deterministic.
- **Special power-ups / hazards** (earthquake, explosion + lethal debris, lampo/lumaca, stella,
  congelamento, jackpot) are deferred to **Phase 6.2** by decision — `FoodCategory.Special`,
  extra `FoodEffect` cases and `GameState.debris`/`effectTimers` are the reserved hooks. Don't add them
  before Phase 6.
- **Control-scheme default = Swipe** is deferred to the Phase 3.3 Settings screen (DataStore-backed);
  do not build a throwaway toggle before then.
- The food spawn table is **time- and level-aware** (`FoodTable.roll(random, elapsedTicks, level)`);
  early game is intentionally simple (grow only) and ramps up — keep this progression intact.

---

## Log

> Newest entries at the top. One entry per completed phase/step or significant change.

---

### 2026-06-04 — Phase 2.5: Gameplay enrichment (food system overhaul)

Reworked the food system to make a session less static, before starting Phase 3. The v1.0.0 model only
had foods that grow the snake; this introduces purpose and progression while keeping the model pure and
deterministic.

**What was done:**
- Redesigned the food model (`game/Food.kt`): orthogonal `FoodCategory` (Grow/Shrink/Special-reserved),
  `FoodSize` (Standard/Maxi), `FoodTier` (Small→Huge + Mystery) and a sealed `FoodEffect`. Removed the
  old flat `FoodType` enum and the `growth` field.
- **Grow** tiers 2/4/6/8 and **Shrink** tiers 2/3/5 (×2 for Maxi); a **mystery** piece per category with
  a random amount resolved at spawn and drawn behind a "?".
- **Time-gated progression** via `GameState.elapsedTicks`: only growing food at first; shrink unlocks
  (~15s), then maxi (~30s), then mystery (~45s); harder levels reach the gates sooner.
- Engine rules: shrink trims the tail with a **minimum-length floor** (`MIN_SNAKE_LENGTH = 3`); a
  **combo multiplier** (cap ×5, 45-tick window) rewards rapid consecutive eats; shrink awards only
  symbolic points (5 / 10 maxi). The engine now emits per-tick `GameEvent`s (`Ate`/`Shrunk`/`Died`),
  which the ViewModel consumes instead of re-deriving the eaten food from positions.
- Rendering: grow (green) vs shrink (warm) colour families shaded by tier, maxi halo, a "?" glyph for
  mystery via Canvas `TextMeasurer`, a shrink "implosion" particle burst, and a combo readout in the HUD.

**Decisions:** specials deferred to Phase 6.2; control-scheme default-Swipe deferred to Phase 3.3;
mystery resolved at spawn for determinism; explosion debris will be lethal + auto-clearing (Phase 6).

**Verification:** the pure `game/` model was compiled and executed standalone (no Android SDK in this
environment) and the JUnit suite passes (22 tests, incl. shrink floor, time gates, mystery range/
determinism, combo). The UI changes (Compose) compile against the Android toolchain — pending an
on-device smoke test.

---

### 2026-06-04 — Phase 2 complete: Visual polish

Completed all Phase 2 steps. The board is now portrait-optimised and visually polished.

**What was done:**
- **2.1** Snake drawn from vector shapes: rounded body segments, glowing head with direction-oriented eyes.
- **2.2** Themed board background: vertical gradient + subtle grid lines + framed border.
- **2.3** Animated food: pulsing scale on all types; slow spin on the blue star; halo on Gold/Mega.
- **2.4** Bevelled obstacle blocks with shadow + highlight instead of flat rectangles.
- **2.5** Smooth inter-tick motion: each segment interpolates between cells over one tick via `withFrameNanos`; game logic remains grid-based.
- **2.6** Particle burst on eat, colour-coded per food type, simulated in cell space on `Canvas`.
- **2.7** Screen shake on game-over (damped wobble).
- **2.8** Radial-gradient glow halo on the snake head.

Board presets re-tuned from 3:2 landscape to portrait (~7:10) so the board fills a phone screen.

---

### 2026-06-04 — Phase 1 complete: Core gameplay (parity with v1.0.0)

Implemented full gameplay in Kotlin + Compose, reaching feature parity with the frozen WinForms prototype.

**What was done:**
- **1.1** Pure-Kotlin model in `game/`: `Direction`, `Position`, `BoardSize`, `Level`, `Food`/`FoodTable`, `GameState`, `GameEngine`. Unit-tested (`./gradlew :app:testDebugUnitTest`).
- **1.2** Compose `Canvas` grid renderer (`GameBoard`): board background, grid, obstacles, foods, snake (bright head).
- **1.3** Coroutine game loop in `GameViewModel` at the per-level tick interval.
- **1.4** Touch input: swipe gestures (`detectDragGestures`) + on-screen D-pad; 180° reversal blocked in the engine.
- **1.5** Food spawn + growth on eat + respawn (two foods kept on board, matching v1.0.0 behaviour).
- **1.6** Collision detection — walls, self, obstacles — triggering `GameOver`.
- **1.7** Score HUD as a Compose overlay.
- **1.8** Pause overlay + Restart + back-to-menu controls.
- **1.9** 5 difficulty levels (speed + obstacle count) ported from `SnakeForm.cs`.
- **1.10** 5 board sizes (Pocket → Infinite) ported from `SnakeForm.cs`.
- **1.11** 7 food types (Green/Red/Gold/Blue/Mega*) with original probabilities and bonuses (Blue: +2…+24).

---

### 2026-06-04 — Phase 0 complete: Android foundations

Bootstrapped the Android project from scratch at the repository root; the WinForms prototype moved to `legacy/`.

**What was done:**
- **0.1** Repo restructure: `legacy/` for the .NET v1.0.0 codebase; Gradle root (Kotlin DSL) + version catalog + wrapper + Android `.gitignore`.
- **0.2** `MainActivity` (`ComponentActivity`) + Compose Material 3 theme + full-screen `Surface`. Splash via `core-splashscreen`.
- **0.3** Portrait lock, edge-to-edge display, `safeDrawingPadding()` for insets.
- **0.4** Adaptive icon placeholder (+ legacy fallback for API 24–25) + `docs/CREDITS.md` created.

**Stack chosen:** Kotlin + Jetpack Compose (Material 3), `minSdk 24`, `compileSdk`/`targetSdk 35`, Gradle (Kotlin DSL) + version catalog.
