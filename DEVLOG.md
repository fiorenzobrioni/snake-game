# DEVLOG — Snake Game (Android)

Development log, working notes, open TODOs and known bugs.
For the forward-looking plan and phase checklists see [`ROADMAP.md`](ROADMAP.md).

---

## TODOs

> Short-term tasks not yet tracked as a formal roadmap step.

- [ ] Finalise `applicationId` from `com.brioni.snake` placeholder before first Play upload (Step 7.1)
- [ ] Write unit tests for `GameEngine` edge-cases: wall collision on all four sides, body self-collision, 180° reversal block
- [ ] Verify smooth-motion interpolation on low-end devices
- [ ] Verify the mystery "?" glyph renders crisply on small cells (dense boards)
- [ ] Re-tune the food spawn weights / time gates after playtesting on a device
- [ ] Replace synthesized audio with richer CC0/commissioned tracks before Play release (optional polish)

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
- AGSL `RuntimeShader` requires API 33+. Since **`minSdk` is 33**, shaders are **always available** — do
  not reintroduce Canvas/older-API fallbacks for them (the old fallbacks were removed when the floor was raised).
- `game/` package must remain free of Android/Compose imports — this is what makes it testable with plain JUnit.
- **Mystery foods are resolved at spawn, not at eat**: `FoodTable.roll` rolls the concealed amount and
  stores the final `FoodEffect`, so `GameEngine.tick` consumes no randomness and stays deterministic.
- **Special power-ups / hazards** shipped in **Phase 6.2** (earthquake, explosion + lethal debris,
  Lightning/Snail, Star/ghost, Freeze, Jackpot) via `FoodCategory.Special`, the extra `FoodEffect`
  cases and `GameState.debris`/`effectTimers`. Effect durations are stored in **ms** and aged by the
  effective interval each tick; the loop reads `GameState.tickIntervalMillis` (never `level.tickMillis`)
  so speed effects actually change the pace. Keep that invariant.
- **Control scheme**: the default is **Swipe** (set in `GameViewModel.DEFAULT_CONTROL` and the
  persisted fallback in `SettingsRepository`); the two-button relative scheme and the D-pad remain
  selectable in Settings (choice persisted via DataStore). Phase 3 had originally shipped two-button
  as the default; it was flipped to swipe per the original request.
- The food spawn table is **time- and level-aware** (`FoodTable.roll(random, elapsedTicks, level)`);
  early game is intentionally simple (grow only) and ramps up — keep this progression intact.
- **Special spawn frequency** is a setting (`SpecialFrequency`) that scales both the special
  branch weight and its unlock gate in `FoodTable.roll`; it is independent of level and board size.
- **Regular food auto-vanishes** after `GameEngine.VANISH_FOOD_MS` (~7 s at the level's base pace),
  stamped via `Food.spawnTick` and removed one-per-tick in `tick`; **specials never vanish**. `tick`
  now refills on any drop below `FOOD_COUNT` (eat *or* vanish) and still stays seed-deterministic.
- **Audio assets are generated, not committed by hand**: `tools/audio/generate_audio.py`
  (stdlib only, no deps) synthesizes every clip in `app/src/main/res/raw/` as original CC0 16-bit
  mono WAV. Music loops are rendered to an exact bar count with zero-amplitude note ends so they
  loop seamlessly under `MediaPlayer`. Re-run the script to regenerate; don't edit the WAVs directly.
- **No encoder in CI**: there's no `ffmpeg`/`oggenc` available, so music ships as WAV (~0.6–0.8 MB
  each). If an OGG/Opus encoder becomes available, encode the music to shrink the APK before release.
- **Audio is decoupled from the model**: the pure `game/` package emits no audio. `GameViewModel`
  depends only on the `audio/GameSfx` interface (default `GameSfx.None`), so it stays unit-testable.
  The Android audio engines (`SoundManager`/`MusicManager`) live behind the `audio/GameAudio` facade,
  which is created once in `ui/App.kt` and released on the host's `onDispose`.
- **Music backend is framework `MediaPlayer`** (two instances for the crossfade), chosen over
  ExoPlayer to keep the binary lean. `MusicManager` requests audio focus and ducks/pauses on loss.
- **AGSL shaders require API 33+ and must stay optional**: `ui/game/Shaders.kt` holds the sources and
  the `BoardShaders` holder (annotated `@RequiresApi(33)`). Construction and every `setFloatUniform`/
  `setColorUniform` call must sit behind an explicit `Build.VERSION.SDK_INT >= TIRAMISU` check — lint
  does **not** treat a `shaders != null` null-check as an API guard. Below 33 the holder is `null`
  and the renderer uses the Canvas fallbacks. Keep both paths in sync when changing visuals.
- **Shaders return premultiplied alpha** (Skia convention): the glow/halo shaders output `rgb * a`.
- **The CRT filter is a `RenderEffect`** applied to the board's `graphicsLayer` (API 33+), gated by a
  persisted `crtEnabled` setting that is only surfaced in Settings when `Shaders.supported`.

---

## Log

> Newest entries at the top. One entry per completed phase/step or significant change.

---

### 2026-06-08 — Gemini background music + in-app Credits screen (Step 7.7)

- **Replaced the synthesized background music with Google Gemini (Lyria) tracks.** The two source
  MP3s (192 kbps, ~140 s / ~153 s) were post-processed with `ffmpeg` into bundled OGG/Vorbis:
  leading/trailing silence (MP3 encoder padding) trimmed, then an **equal-power self-crossfade**
  (`acrossfade … c1=qsin:c2=qsin`, 4 s) baked in so the file loops seamlessly under
  `MediaPlayer.isLooping` (which does a hard loop with no boundary crossfade). Peaks limited to
  ~−1 dBFS to leave headroom for lossy decode overshoot. Final assets: `music_menu.ogg` (~1.7 MB),
  `music_game.ogg` (~1.95 MB) — both smaller than the source MP3s.
- **No audio-code changes:** the new files keep the resource base names `music_menu` / `music_game`,
  so `MusicManager`'s `R.raw.*` references resolve unchanged. The old `.wav` loops were removed and
  the `new-music/` drop folder deleted.
- **Retired music synthesis:** `tools/audio/generate_audio.py` no longer generates the music loops
  (removed `build_music`/`menu_music`/`game_music`); it now only emits SFX. The `NOTES` table is kept
  (used by the melodic SFX).
- **Music audible by default:** `DEFAULT_MUSIC_VOLUME` changed `0f` → `0.5f` so fresh installs hear
  the new tracks (existing users keep their saved value).
- **New in-app Credits screen** (`ui/credits/CreditsScreen.kt`), reachable from the main menu via a
  new button: app identity (name + version from `PackageManager`), author **Fiorenzo Brioni**,
  GPL-3.0, and per-asset attribution (music by Google Gemini, CC0 SFX, Orbitron font, original
  art/shaders, built with Claude Code). Wired through `App.kt` (`Screen.Credits`) following the
  existing Records/Achievements navigation pattern; the system-back + `onBack` return to the menu.
- **Licensing:** the Gemini tracks are aggregated assets (their own terms) alongside the GPL-3.0
  code — no conflict. Documented in `docs/CREDITS.md` and the README. Also corrected a stale
  reference there that called the repo license "MIT" for the shaders (it is GPL-3.0).
- **Note:** the loop seam was verified analytically (no silence gap, continuous waveform, no
  clipping) — there is no audio playback in the build environment, so an on-device ear check is the
  final confirmation.

### 2026-06-07 — Decorate the main menu with a gliding snake

- The main menu's empty space above the title now hosts a **discreet, looping decoration**
  that reuses the gameplay vocabulary: a stylised snake gliding across the top (rounded-rect
  segments + glowing eyed head). (An earlier version also drew two particle bursts low on the
  screen; they were dropped as too distracting — the snake alone reads best.)
- New `ui/menu/MenuDecorations.kt` draws everything with plain Canvas primitives (a
  radial-gradient glow rather than the AGSL shader) so it stays self-contained and cheap, and
  mirrors the snake style of `GameBoard.drawSnakeSegment`/`drawEyes`.
- Colours follow the player's selected **skin** (`paletteFor`), so `MainMenuScreen` now takes
  the shared `SettingsRepository` and collects `skin` from it; `App` passes `repo` through.
- A single `rememberInfiniteTransition` phase drives the snake slither and the head-glow pulse.
  Everything is kept low-opacity so the title and buttons stay perfectly legible, in both dark
  and light themes.

### 2026-06-07 — Fix status-bar icons on cold start with Light theme (v0.7.6)

- v0.7.5 worked when toggling the theme at runtime but **not on a cold start** with the Light theme
  already selected (status-bar icons stayed light/invisible until a manual theme toggle). Cause: the
  splash screen (`core-splashscreen`) owns the system-bar appearance during startup, so the
  `enableEdgeToEdge` calls run while the splash is up and get reset to the theme default
  (`Theme.SnakeGame.Main` → light icons) when the splash is removed; no later theme change occurs, so
  nothing re-applied the app-themed appearance.
- `MainActivity` now re-applies the bar appearance **after the splash is removed** (in the splash
  exit `withEndAction`) and imperatively on every theme change, via a small `applyBarAppearance()`
  helper + a cached `appDarkTheme` flag (`WindowCompat.getInsetsController`).
- `versionCode 18` / `versionName 0.7.6`.

### 2026-06-07 — System-bar icons follow the app theme, take 2 (v0.7.5)

- The v0.7.4 `SideEffect` that set `isAppearanceLightStatusBars` directly did not stick (status-bar
  icons stayed light/invisible under a Light app theme on a dark-mode device — observed on a
  Samsung Galaxy S24 Ultra / One UI).
- Samsung One UI honours the standard appearance flags, so the failure was the direct flag not
  surviving edge-to-edge's own setup. Replaced it with the supported hook: re-apply
  `enableEdgeToEdge` from a `DisposableEffect(darkTheme)`
  using `SystemBarStyle.auto(..., detectDarkMode = { darkTheme })` for both bars, so edge-to-edge's own
  appearance logic uses the app theme instead of the system config. Re-runs on every theme change.
- `versionCode 17` / `versionName 0.7.5`.

### 2026-06-07 — System-bar icons follow the app theme (v0.7.4)

- Follow-up to v0.7.3: with a **Light** app theme on a **dark-mode device**, the status-bar /
  navigation-bar icons stayed light (edge-to-edge defaults their colour to the *system* dark mode),
  so they were invisible on our light background.
- `MainActivity` now drives `isAppearanceLightStatusBars` / `isAppearanceLightNavigationBars` from the
  app's own `darkTheme` value via a `SideEffect` (`WindowCompat.getInsetsController`), re-applied on
  every theme change so toggling the theme in Settings updates the bars immediately.
- `versionCode 16` / `versionName 0.7.4`.

### 2026-06-07 — Gameplay screen follows the selected theme (v0.7.3)

- Reverses the v0.7.1/v0.7.2 approach (which *forced* the dark scheme on the gameplay screen).
  Root cause of the light-mode complaint: with the dark scheme forced, the HUD used the dark accent
  (`SnakeGreenBright`) on what the user expected to be a light surface, and the pre-Play / pause /
  game-over overlays sat on a hardcoded black scrim. The board *interior* is dark by design and
  stays that way; only the surrounding chrome needed to follow the theme.
- `ui/App.kt`: the `Screen.Game` branch no longer wraps in `SnakeGameTheme(darkTheme = true)`; it
  inherits the ambient theme, so the HUD (Score/Pause = `primary`, labels = `onBackground`) picks up
  the light scheme automatically. The standard light-theme green (`SnakeGreen`) is sufficient — no
  new color was introduced.
- `ui/game/GameBoard.kt` + `GameScreen.kt`: the board frame is now theme-aware via a new
  `borderColor` param — a branded green border (`primary`) on the light surround, the skin's subtle
  `palette.boardBorder` in dark mode (detected with `background.luminance() > 0.5f`).
- `ui/game/GameOverlays.kt`: `OverlayScrim` uses a near-opaque light panel under the light theme
  instead of the black scrim, so the pre-Play setup / Pause / Game Over screens read correctly.
- `versionCode 15` / `versionName 0.7.3`.

### 2026-06-07 — Light-theme HUD fix: dark gameplay background (v0.7.2)

- Follow-up to v0.7.1: forcing the dark scheme made the HUD text light, but the HUD margins still
  showed the outer (white) Surface under the light theme, so the top HUD text vanished and the
  phosphor-green Score/Pause read poorly. `GameScreen` is now also given a **dark background** under
  the forced dark scheme, so the whole gameplay screen is consistently dark and its light-on-dark
  text + green accents stay readable in any app theme.
- `versionCode 14` / `versionName 0.7.2`.

### 2026-06-07 — Remove predictive back, fix light-theme gameplay UI (v0.7.1)

- **Removed the in-app predictive-back animation**: it looked janky (screens briefly overlapping), so
  `App` reverts to a plain `BackHandler` → navigate to Menu. The blur-dissolve screen transitions and
  the animated menu backdrop stay. (`enableOnBackInvokedCallback` is kept in the manifest — it only
  drives the smooth *system* exit-to-home from the Menu, which is not janky.)
- **Light-theme gameplay text fixed**: the pre-game (Ready) overlay — and the other in-game overlays /
  HUD — sit on an always-black scrim but used `onBackground`, which is dark in the light theme, so the
  section labels ("Mode / Level / Board scale") were invisible. The board is an always-dark arcade
  surface, so the whole `GameScreen` is now wrapped in a forced **dark** scheme
  (`SnakeGameTheme(darkTheme = true)`), keeping its light-on-dark text visible under any app theme.
- `versionCode 13` / `versionName 0.7.1`.

### 2026-06-07 — Predictive-back overhaul, light-theme fix, landscape board, scale rename (v0.7.0)

- **Predictive back, properly**: during the gesture the current screen now lifts into a rounded,
  shadowed card that shrinks (×0.65) and slides off toward the swipe edge, while the **destination
  (Menu) is previewed behind it**, softly blurred and easing into focus. Release commits with an
  **instant swap** (no Settings flash); cancel eases the card back. (`backInProgress`/`instantSwap`
  state, `BlurEffect` preview, `EnterTransition.None` on commit.)
- **Light theme fixed**: the always-dark AGSL menu backdrop was painting over the light surface, so
  light-theme menus/settings looked dark with invisible captions. The backdrop is now drawn **only in
  the dark theme** (`colorScheme.background.luminance() < 0.5`); light theme keeps its plain surface.
- **Landscape board sizing**: `boardFor` now fixes the preset count on the **short side** and solves
  the long side from the aspect ratio, so a tablet in landscape gets the same density as a phone in
  portrait instead of collapsing (e.g. 18×10 → ~29×18). Phone portrait is unchanged. Renamed
  `BoardScale.cellsOnShortSide`.
- **Naming clash**: the board-scale **"Classic" is renamed "Standard"** (Cozy / Standard / Epic) so it
  no longer collides with **Classic mode** — important since the HUD shows the board-scale label. Enum
  constant kept (`BoardScale.Classic`) so saved settings / highscores are untouched.
- `versionCode 12` / `versionName 0.7.0`.

### 2026-06-07 — Predictive-back "card", 5 more achievements (v0.6.1)

- **Predictive back now reads clearly**: the menu screens are transparent over the shared backdrop, so a
  bare scale was hard to see. During the gesture the foreground now lifts into a **rounded, shadowed,
  opaque card** (`drawBehind` fills it with the surface colour at draw-time; `shadowElevation` on the
  `graphicsLayer`) that shrinks ×0.80 and slides toward the swipe edge, clearly peeling off the backdrop.
- **Five more achievements** (now 15): Stylist (1500 with a x5 combo), Marathoner (5 min), Big Eater
  (100 foods), Trifecta (Explosion + Star + Jackpot in one run), Grandmaster (5000).
- `versionCode 11` / `versionName 0.6.1`.

### 2026-06-07 — Menu backdrop everywhere, stronger predictive back, harder achievements (v0.6.0)

- **Animated backdrop across all menu screens**: moved `AnimatedShaderBackground` from `MainMenuScreen`
  into the App shell, drawn behind the `AnimatedContent` for every non-Game/Intro screen, so Settings /
  Records / Achievements share the living backdrop too.
- **More visible predictive back**: the gesture now scales the foreground to ×0.82, slides it toward the
  swipe edge (via `BackEventCompat.swipeEdge`), rounds its corners and dims it — revealing the backdrop.
  (Needs gesture nav; on Android 13 also the "Predictive back animations" developer option.)
- **Mystery food verified**: added `mysteryFoodAppliesItsResolvedAmount` to `GameEngineTest`. Confirmed
  a "?" food applies its rolled amount exactly — Grow(N) adds N segments; Shrink(N) removes N tail cells
  (the head still advances that tick, so net length is N−1, but N cells are genuinely trimmed). Behaviour
  was already correct; no engine change.
- **Achievements harder + a 10th**: raised HighRoller (1000 → 2500), Survivor (2 → 3 min), SpeedRunner
  (300 → 600); added **Gourmand** (eat 50 foods in one run). Now 10 total.
- `versionCode 10` / `versionName 0.6.0`.

### 2026-06-07 — Premium UX: predictive back, animated menu shader, blur transitions (v0.5.0)

- **Predictive back gesture**: `android:enableOnBackInvokedCallback="true"` in the manifest, and the
  app-level `BackHandler` replaced with a **`PredictiveBackHandler`** in `ui/App.kt` — the secondary
  screens scale (×0.88) and fade back following the gesture, committing to the menu on release and
  resetting on cancel. The Menu (root) lets the system run its exit animation; the Game screen keeps
  its own back (pause/exit).
- **Animated AGSL menu background**: new `ui/AnimatedShaderBackground.kt` reuses the in-game
  `Shaders.BACKGROUND` (drifting glows + vignette), advancing `time` via `withFrameNanos`; placed
  behind `MainMenuScreen`. No fallback (minSdk 33).
- **Blur-dissolve transitions**: navigation moved from `Crossfade` to `AnimatedContent` with a
  fade + per-screen `Modifier.blur` (16dp → 0) so screens sharpen into focus / blur out.
- **Cleanup**: removed `vectorDrawables { useSupportLibrary = true }` (only needed for old APIs).
- `versionCode 9` / `versionName 0.5.0`. Build + lint + unit tests green.

### 2026-06-07 — minSdk → 33, fallback cleanup + two bonus blasts on the splash (v0.4.0)

- **Raised `minSdk` 24 → 33** (Android 13) — premium baseline; AGSL is always available.
- **Removed now-dead fallbacks**: `Shaders.supported` / `@RequiresApi`; the `BoardShaders?` nullability
  and all `if (shaders != null && SDK >= 33) … else <Canvas>` branches in `GameBoard` (background,
  food halo, head glow) — `shaders` is now non-null; the CRT SDK guard in `GameScreen`; the
  `Shaders.supported` gate on the CRT toggle in `SettingsScreen`; the `Build.VERSION_CODES.O`
  audio-focus branches in `MusicManager`; and the bloom SDK guard in the intro. Deleted the API 24–25
  legacy launcher vectors in `mipmap-anydpi/` (adaptive `mipmap-anydpi-v26/` is always used now).
- **Splash bonus blasts**: two AGSL **explosions** (`EXPLOSION_AGSL`, a shared `RuntimeShader` drawn
  additively) detonate in the snake's wake — one gold above the word, one cyan below — each fired once
  as the head sweeps past, driven by the head column. The whole-canvas bloom amplifies them.
- `versionCode 8` / `versionName 0.4.0`. Build + lint + unit tests green.

### 2026-06-07 — Intro: bloom shader (API 33+) + higher-contrast grid (v0.3.2)

- Added an optional **AGSL bloom** post-filter to the splash: a `RuntimeShader` (`BLOOM_AGSL`) applied
  as a `RenderEffect` on the Canvas `graphicsLayer` (`CompositingStrategy.Offscreen`), sampling bright
  neighbours above a luminance threshold and screen-adding them — a soft halo around the snake and the
  glowing letters; the dark board stays untouched. **API 33+ only**, built with `runCatching` so a
  compile failure degrades to null (no crash); below 33 the per-cell radial glows remain the fallback.
- Bumped the splash grid contrast (`SplashGridLine = 0x33FFFFFF`, stroke 1.5) so the board's squares
  read clearly — **splash-only**, the in-game Classic palette is untouched (applied via `.copy()`).
- `versionCode 7` / `versionName 0.3.2`.

### 2026-06-07 — Brand intro redesign: "snake writes its name" (v0.3.1)

- Replaced the split 80s/modern wordmark splash entirely with a gameplay-tied concept: the whole
  splash **is the game board** (drawn like in-game — Classic palette gradient, 1px grid, bordered
  frame). A snake crawls in from the left along the mid row; a **reveal curtain** tied to the float
  head column lights up the **SNAKE** pixel-art (a 4×5 cell font) column-by-column in its wake —
  fresh cells glow `headGlow` lime then **cool to** `snakeBody` green (`lerp(snakeHead, snakeBody, t)`).
  The snake exits right, the word holds, then the whole canvas fades to the menu.
- Kept the existing scaffolding (`entrance`/`exitAlpha`/timer, tap-to-skip, fire-once `finish`);
  added a `travel` Animatable (`LinearEasing`) for the crawl. Board/segment/eye/glow draw calls are
  copied from `GameBoard.kt`; palette via `paletteFor(Skin.Classic)`. No shaders → all-API safe.
- Removed the now-unused `intro_tagline` string and the retro/modern artwork. Bumped to
  `versionCode 6` / `versionName 0.3.1`.

### 2026-06-06 — Step 7.1: branded launch — icon, splash & brand intro (v0.3.0)

- **Two-stage branded launch.** The system `SplashScreen` API can only show a centred icon on a
  background, so the rich "half 80s / half modern" concept lives in a Compose intro after it:
  1. **System splash** — new `splash_icon_animated.xml` (an `animated-vector` over `splash_snake.xml`,
     a copy of the snake wrapped in a named `icon` group) plays a short overshoot **pop-in**;
     `themes.xml` points `windowSplashScreenAnimatedIcon` at it with a matching 500ms duration.
     `MainActivity` fades the splash out (`setOnExitAnimationListener`) for a smooth handoff.
  2. **`ui/intro/BrandIntroScreen`** — a Canvas-drawn, split **"SNAKE"** wordmark: left half 80s neon
     (magenta→cyan gradient + glow, CRT scanlines, a synthwave grid) and right half clean modern flat
     lime. A light sweep crosses on entrance; a small tagline sits underneath. Auto-advances after
     2.6s, tap-to-skip, calls `onFinished` once. All procedural (no shaders) so it renders on every API.
- **Navigation.** Added `Screen.Intro` as the first/default destination in `ui/App.kt`; it cross-fades
  to the menu. `rememberSaveable` keeps it to fresh cold launches (no replay on return-from-background);
  back on the intro skips to the menu. Menu music begins under the intro via the existing crossfade.
- **Final icon polish.** Added a glossy head specular + food highlight to `ic_launcher_foreground` and
  mirrored them into the API 24–25 bundled vectors. **Fixed the themed-icon layer**: the `<monochrome>`
  slot now points at a dedicated single-fill silhouette (`ic_launcher_monochrome.xml`) instead of the
  coloured foreground.
- **Version** bumped to `versionCode 5` / `versionName "0.3.0"`.
- Follow-up polish: intro hold lengthened to 3.2s; the highlight now **sweeps back and forth**
  (infinite `RepeatMode.Reverse`) for the whole duration; and the splash **fades out** (whole-canvas
  `graphicsLayer` alpha over its last 500ms) before the menu, instead of cutting hard.
- Filled out the intro scene so both halves carry artwork: the right half now has a **sleek glowing
  modern snake**; the left half's retro art is bolder — a **sliced synthwave sun** + a stronger grid
  with a bright horizon line. Launcher icon inset to ~0.86 so it no longer touches the safe-zone edge.

### 2026-06-06 — Two specials at once, higher frequency, grow/shrink amount popups (v0.2.1)

- **Up to two specials on the board.** New `GameEngine.MAX_SPECIALS_ON_BOARD = 2`;
  the refill gate (in `tick` and `refill`) now counts specials instead of allowing
  only one. Raised `FOOD_COUNT 2 → 3` so a regular (growth) food is always present
  even when both special slots are filled.
- **Slightly higher spawn frequency** for every tier (`SpecialFrequency`): weights
  `Standard 8→10`, `Frequent 18→22`, `Frenzy 32→40` (gate timing unchanged).
- **Grow/shrink amount callout.** Eating a length-changing food now floats the
  number of segments (`+N` / `-N`) at the food, reusing the `FloatingText` system
  built for the time blocks (shown only when the amount is non-zero, so a shrink at
  the length floor stays silent). Coloured with the food's own palette colour.
- **Version bump** `versionCode 3 → 4`, `versionName "0.2.0" → "0.2.1"`. Tests:
  `onlyOneSpecialIsKeptOnTheBoard` → `atMostTwoSpecialsOnTheBoard`, plus a new
  `twoSpecialsCanCoexist`.

### 2026-06-06 — Time Attack clock blocks, earthquake rework, special timeout (v0.2.0)

- **New Time Attack power-up / hazard.** Two new `FoodEffect`s — `TimeBonus`
  (+5 s, beneficial) and `TimePenalty` (−3 s, hazard) — that roll **only** in
  `GameMode.TimeAttack`. Implemented as a signed `GameState.timeAdjustMs` budget
  shift (keeps `playedMs` truthful); `timeRemainingMs` and the time-up check read
  it, so a penalty can end a run and a bonus extends it. Rendered as a clock disc
  with a +/− badge (`drawClock`) in green/red, with a rising "+5s"/"-3s" floating
  callout and a burst (and a sting shake on the penalty).
- **Floating-text effect system** (`GameEffects.FloatingText` + `emitFloatingText`
  / `updateFloatingTexts`), surfaced via `GameViewModel.floatingText`/`…Id` and
  drawn in `GameBoard` with the existing per-frame loop + `textMeasurer`.
- **Earthquake reworked.** Previously it only bit the tail + shook (felt inert).
  It now also **scatters the bitten segments as lethal debris** on random free
  cells (new `GameEngine.scatterCells`, avoiding snake/obstacles/food/debris),
  lasting `QUAKE_DEBRIS_MS = 5 s`. `GameEvent.Quaked` now carries the debris cells.
- **Specials no longer linger forever.** They now time out after
  `VANISH_SPECIAL_MS = 14 s` (vs 7 s for regular food) with the existing vanish
  burst. Vanish logic is now per-category. Replaced the old `specialsNeverVanish`
  test accordingly.
- **Audio:** `TimeBonus → Jackpot` chime, `TimePenalty → Shrink` tone.
- **Version bump** `versionCode 2 → 3`, `versionName "0.1.1" → "0.2.0"` (prep for
  a future GitHub release). New unit tests in `SpecialFoodTest`/`GameEngineTest`.

### 2026-06-05 — Audio refresh + removed the UI click

- Regenerated all SFX/music with lower, punchier tones: new `noise` oscillator,
  a curved (`^2.5`) note release, a lower noise-layered `sfx_eat`, and
  noise-based `sfx_quake`/`sfx_explosion`. Seeded the generator's RNG
  (`random.seed`) so the noise clips stay reproducible across runs.
- **Removed the UI click sound** at every call site (menu navigation, back
  handlers, Ready/Paused/GameOver overlay buttons). Dropped the now-dead
  `GameAudio.playUiClick()`, the `Sfx.Click` enum case, the `sfx_click()`
  generator function and the `sfx_click.wav` asset (also out of `CREDITS.md`).
  Pause/resume still play `sfx_pause`; gameplay SFX unchanged.

### 2026-06-05 — Stable effect-timer slot + longer effect durations

- **Bug fix — board no longer resizes when effects appear/expire.** The
  `EffectTimersRow` in `GameScreen` used to return early when empty (zero height)
  and grow when a power-up/hazard chip appeared. Because the board fills the
  remaining `weight(1f)` space, that growth shrank the board (and the reverse on
  expiry), making the snake appear to jump. The row now reserves a constant
  `EffectTimersRowHeight` (34.dp) slot whether or not any effect is running, so
  the board keeps a fixed size; chips are centred within the slot.
- **Effect durations bumped** (`Food.kt`) so the player can actually enjoy each
  power-up: Haste `6 s → 9 s`, Slow `6 s → 8 s`, Freeze `5 s → 8 s`, Star/Ghost
  `8 s → 9 s`. Debris lifetime (`BURST_DEBRIS_MS`) unchanged.

---

### 2026-06-05 — Star (Ghost) tuning: longer duration + expiry warning blink

- `GHOST_MS` raised **5 s → 8 s** (`Food.kt`) so the invincibility power is long
  enough to actually escape a tight spot. Fixed ms, independent of level/board.
- Added an **accelerating warning blink**: over the final `GHOST_WARN_MS` (2 s) the
  snake's shimmer ramps up in frequency and swing depth (driven by the live
  `effectTimers` Ghost `remainingMs`), so the player sees the effect ending and can
  steer to safety. Purely a renderer change in `GameBoard` (the model already aged
  the timer); the HUD countdown bar is unchanged.

---

### 2026-06-05 — Special-block frequency setting + auto-vanishing food

Two gameplay-dynamism enhancements (post-Phase 6 polish; not formal roadmap steps).

- **Special frequency setting** (`SpecialFrequency`: Standard/Frequent/Frenzy). New
  enum in `game/`, threaded through `FoodTable.roll` → `GameEngine.tick/refill/spawnFood`,
  persisted in `SettingsRepository` (`special_frequency` key) and exposed as a
  `ChoiceSection` right after the Hazards toggle in `SettingsScreen`. Each tier raises
  the special spawn weight (8 → 18 → 32) **and** pulls the unlock gate earlier
  (`gateFactor` 1.0 → 0.5 → 0.25), so higher tiers feel different from early game. Wired
  live into `GameViewModel` (applies without a board reset, like `hazardsEnabled`).
- **Auto-vanishing food** (always on, ~7 s): an ignored **regular** food times out and is
  replaced elsewhere, to punish aimless looping. Implemented with a per-food `spawnTick`
  stamp (set in `spawnFood`) and `GameEngine.VANISH_FOOD_MS`; `tick` vanishes the single
  oldest stale regular food per tick (staggered bursts), emits `GameEvent.FoodVanished`,
  then tops the board back up. Specials are excluded. New `emitVanishBurst` (a soft upward
  fade) in `GameEffects`; `EatEvent.implode: Boolean` was generalised to a `BurstStyle`
  enum (Eat/Implode/Vanish) dispatched in `GameBoard`. No new audio asset (silent).
- The refill restructure means `tick` now tops up on a vanish (not only on an eat); the
  old eat-only refill is subsumed. `tick` still consumes refill randomness only — fully
  seed-deterministic. Threshold uses `level.tickMillis` (≈7 s at the level's base pace).
- Tests added in `SpecialFoodTest` (frequency raises special rate; Frenzy unlocks earlier)
  and `GameEngineTest` (regular food vanishes & is replaced; fresh food doesn't; specials
  never vanish). Full `./gradlew test` + `lint assembleDebug` green.

---

### 2026-06-05 — Feedback round 5: first-run board overflow — ACTUAL root cause

- It was **not** an inset-timing issue (round 4 was a red herring, though the inset cleanup is kept).
  The board-shake offset was non-zero **at rest**: `shakeY` used `cos(shakeT·…)·amp·damp`, and at idle
  `shakeT=0` → `cos(0)=1`, `damp=1`, so the board sat ~`amplitude (10dp) + quakeAmplitude (7dp)` ≈ 17dp
  **down** before any shake. Its bottom (and the snake) ran off-screen on the first game; the first
  death animation drove `shakeT→1` (`damp=0`), removing the 10dp term so later games looked fine
  (~7dp, absorbed by the board margin). Only the bottom was affected because X used `sin` (0 at rest)
  while Y used `cos`; Cozy overflowed less because its shorter board leaves more bottom margin.
- Fix: use `sin` on both axes so the offset is exactly 0 at rest (and still a proper damped wobble
  during a shake). The stray 7dp came from the quake term added in Phase 6.

### 2026-06-05 — Feedback round 4: first-run board overflow (inset cleanup)

- The board ran under the navigation bar on the **first** game only (snake's bottom row half off-screen;
  worse with denser scales, barely with Cozy) and corrected from the second game. Root cause: the
  safe-area inset padding (`safeDrawingPadding`) was applied **inside** the `Crossfade`, so the first
  `GameScreen` created a fresh padding node that briefly saw 0 insets — the play area was measured at
  full window height (under the nav bar) and the board dimensions were locked to it.
- Fix: apply `safeDrawingPadding` once in `MainActivity`, **outside** the Crossfade (a stable,
  persistent node), so the play area is always inset-correct when GameScreen first appears. Belt-and-
  suspenders: `GameViewModel.start()` re-fits the board to the latest measured area before locking it.

### 2026-06-05 — Feedback round 3

- **Board border clipping (first game)**: `GameBoard` now reserves a margin that's *border-aware*
  (two-pass: probe the cell size, then grow the margin to cover the framing stroke's outer half), so the
  bottom border can no longer be clipped even when the first run locks board dimensions from an
  early/transient play-area measurement.
- **Skin caption invisible in light theme**: the skin preview cards always use a dark gradient
  background, but the unselected caption used the theme `onSurface` colour (black in light mode →
  invisible). Captions now use a fixed light colour in both themes.
- **Settings chip spacing**: `ChoiceSection` rows now use a centred `FlowRow` instead of a plain `Row`,
  so a section with many chips (the 5 levels) wraps neatly instead of overflowing / inflating its
  height — the gap between Level and Board Scale now matches the other sections.

### 2026-06-05 — Feedback round 2

- **Board fit**: the board filled the play area edge-to-edge, so the framing border (a stroke centred
  on the board edge) was half-clipped and could slip off-screen at the bottom. `GameBoard` now reserves
  a small `6.dp` margin when sizing the board, so the whole board + border always fits. Still fully
  dynamic (cell size derived from the measured play area).
- **Launcher icon**: reverted to the original **vector-only** setup (adaptive `mipmap-anydpi-v26` for
  API 26+, vector `mipmap-anydpi` fallback for older) per preference — removed the PNG density buckets
  and `tools/icon/generate_icon.py`. The icon stays crisp/scalable on every device; the earlier
  "missing shortcut" report appears device/launcher-specific (config is identical to the working
  pre-Phase-6 builds).

### 2026-06-05 — Post-Phase-6 fixes (feedback)

- **Launcher icon**: added PNG density buckets (mdpi–xxxhdpi, square + round) via
  `tools/icon/generate_icon.py` and removed the bare `mipmap-anydpi` vector fallback. The icon now
  resolves to the adaptive icon on API 26+ and a real bitmap on API 24–25, so it shows reliably across
  launchers (the previous vector-only setup could fail to appear).
- **Back gesture during play**: the edge-swipe / system Back now **pauses** a running game (instead of
  navigating to the menu while the loop kept ticking). `GameScreen` owns a `BackHandler`; the app-level
  one no longer fires on the Game screen, so leaving the game always stops the loop cleanly.
- **Skin differentiation**: `SkinPalette.rounded` → `cornerFactor` (Neon bubbly 0.5, Classic 0.30, Retro
  0.16, Pixel 0.0) shaping obstacles + snake; Pixel also draws blocky square food. Skins now differ in
  form, not just hue.
- **Settings skin picker**: cards laid out 2×2 (centred) so the fourth no longer wraps on narrow screens.
- **Theme setting**: new `game/ThemeMode` (Light / Dark / System), persisted; `MainActivity` drives
  `SnakeGameTheme(darkTheme=…)` from it (repo lifted to the activity and passed into `App`). Default
  remains System.

### 2026-06-05 — Step 6.5: Endless & Time Attack modes

- `GameState` gains `mode` + `playedMs`; `tickIntervalMillis` now ramps for **Endless** (gentle →
  fast over ticks, clamped to a floor) and the engine accumulates `playedMs`, ending **Time Attack**
  when the 120s budget runs out (HUD shows a mm:ss countdown). `GameEngine.setup`/`newGame` take a mode.
- Mode persisted in `SettingsRepository`; `GameViewModel.selectMode` + settings collection reset the
  board on change. Mode selector added to the Ready overlay; the HUD label shows the mode for
  Endless/Time Attack. Records + achievements were already mode-aware (6.3/6.4), so Speed Runner is now
  reachable.
- Tests: `GameModeTest` (endless ramp + floor, time-attack expiry, played-time accrual, mode in setup).
- **Phase 6 complete (M4 — Deep):** skins, power-ups/hazards, records, achievements and extra modes.

### 2026-06-05 — Step 6.4: Local achievements

- Added a pure `game/Achievement` enum (9 achievements) judged by a pure `test` over a `RunStats`
  snapshot, plus `Achievement.earnedBy(stats, already)`. Stable enum names are the persisted ids.
- `SettingsRepository` stores the unlocked set (`unlockedAchievements()` / `addUnlockedAchievements()`).
- `GameViewModel` accumulates per-run stats (foods, max combo, duration, used explosion/star/jackpot),
  evaluates achievements at game over, persists new unlocks and exposes `newlyUnlocked` — surfaced as a
  banner on the game-over overlay.
- New `ui/achievements/AchievementsScreen` (locked/unlocked list + progress count), wired into `ui/App`
  nav with a main-menu button.
- Tests: `AchievementTest`.

### 2026-06-05 — Step 6.3: Records screen

- Added a pure, unit-tested `game/ScoreKey` (mode × level × scale) codec and `game/GameMode`
  (Classic / Endless / Time Attack — gameplay rules land in 6.5). Highscore keys are now
  `highscore_<mode>_<level>_<scale>`; `SettingsRepository.submitScore`/`highScore` take a mode and a
  new `allHighScores()` bulk flow decodes every stored record.
- New `ui/records/RecordsScreen`: a (level × scale) best-score table per mode, with the overall best
  highlighted and dashes for unplayed slots. Wired a Records destination into `ui/App` and a button on
  the main menu. `GameViewModel` carries a `mode` (Classic for now) used when reading/writing scores.
- Tests: `ScoreKeyTest` (round-trip, format, stale-key rejection).

### 2026-06-05 — Step 6.2: Special foods (power-ups & hazards)

- **Model (6.2a)**: extended the reserved hooks — `FoodEffect` gains Quake / Burst / Haste / Slow /
  Ghost / Freeze / Jackpot (+ `isHazard`); new `Debris` and `ActiveEffect`/`EffectKind` types;
  `GameState` carries `debris` + `effectTimers` and exposes `tickIntervalMillis` (speed effects scale
  the pace, timers age by the same wall-clock interval) and `hasEffect()`. `GameEngine.tick` ages
  timers/debris, wraps the head under Ghost, applies each special, splits the snake on Burst into lethal
  auto-clearing debris, and treats debris as fatal (except under Ghost). `FoodTable` rolls time-gated
  specials (`GATE_SPECIAL_MS`, 60s) honouring the hazards toggle + one-special/freeze gating. New
  `GameEvent`s: Quaked / Exploded / EffectStarted / EffectExpired / JackpotHit. Tests: `SpecialFoodTest`
  (17 cases).
- **UX (6.2b)**: the loop now delays by `tickIntervalMillis`; `GameViewModel` passes `hazardsEnabled`
  to `tick`, fires the new events to SFX + particles + a mid-game board shake (`shakeEventId`).
  `GameBoard` draws each special as a maxi disc with a vivid, skin-independent accent and a vector
  symbol (bolt / spiral / star / snowflake / `$` / spiky burst / crack), lethal fading debris, a
  translucent shimmering snake under Ghost and a frost vignette under Freeze. HUD gains per-effect
  countdown chips. Seven CC0 SFX added via `generate_audio.py`. A **Hazards** Settings toggle (default
  on) disables the harmful specials.

### 2026-06-05 — Step 6.1: Skin system

- Added `game/Skin.kt` (Classic / Neon / Retro / Pixel) — a pure-model identifier; the
  rules never depend on it.
- Refactored the renderer's fixed `GameColors` object into a `ui/game/SkinPalette` data
  class (every colour + the `rounded` / `useGlow` style flags) plus a `paletteFor(skin)`
  lookup. `GameColors.kt` removed. **Classic reproduces the previous look exactly** (no
  visual regression).
- Threaded the active palette from `GameViewModel` (`skin` collected from settings →
  `palette`) into `GameBoard`; Pixel draws square, glow-free cells; Neon boosts glow;
  Retro is a flat phosphor palette that pairs with the CRT filter.
- Persisted `skin` in `SettingsRepository`; added a visual skin picker (preview swatch
  cards) to the Settings screen.
- Tests: `SkinTest` (entry count, unique labels). `assembleDebug` + unit tests green.

### 2026-06-04 — Toolchain: bump to API 36 (Android 16)

- Raised `compileSdk`/`targetSdk` from **35 → 36** (Android 16) in `app/build.gradle.kts`;
  `minSdk` stays at 24.
- Upgraded the build toolchain so API 36 is officially supported (AGP 8.7.3 only certifies
  up to API 35): **AGP 8.7.3 → 8.9.1** in `gradle/libs.versions.toml` and the **Gradle
  wrapper 8.9 → 8.11.1** (AGP 8.9 requires Gradle ≥ 8.11.1). Kotlin 2.0.21 unchanged.
- Verified the new environment end-to-end: `assembleDebug` (APK reports
  `compileSdkVersion=36`, `targetSdkVersion=36`, `platformBuildVersionName=16`),
  `testDebugUnitTest` and `lintDebug` all green on JDK 21 + SDK Platform 36 / Build-Tools 36.0.0.
- Updated API-level references in `README.md` and `ROADMAP.md` (badge + tooling/requirements).

---

### 2026-06-04 — Phase 5 complete: Shaders & FX (AGSL)

Added GPU shader effects via AGSL `RuntimeShader`, completing milestone **M3 ("Alive")**.

**What was done:**
- **5.1** Pulsing, gently rotating glow on the snake's head.
- **5.2** Pulsing outline + halo on rare foods — mapped to the current model (**maxi / mystery /
  huge**), since v1.0.0's Gold/Mega types no longer exist.
- **5.3** Animated board background: the Phase 2 gradient with two drifting glows and a vignette.
- **5.4** Optional retro **CRT filter** (scanlines + vignette) as a `RenderEffect` over the board
  layer, toggled by a new persisted `crtEnabled` setting (shown only where AGSL is supported).

**Architecture:** `ui/game/Shaders.kt` holds the four AGSL sources and the `BoardShaders` holder
(`@RequiresApi(33)`) with live `RuntimeShader`s + `ShaderBrush`es; `GameBoard` mutates uniforms per
frame and draws with them. Everything is **API 33+ only** and falls back cleanly to the existing
Canvas rendering below it (`BoardShaders` is `null`, guarded by explicit `SDK_INT` checks).

**Default tweak (same change):** music volume now defaults to **0%** and SFX to **80%** per request.

**Verification:** `:app:assembleDebug` and `:app:lintDebug` both green (no new lint errors after
adding the `SDK_INT` guards lint requires). AGSL programs compile at runtime, so an on-device check on
an API 33+ device is still pending (no emulator in this environment); the pre-33 Canvas path is
unaffected.

---

### 2026-06-04 — Phase 4 complete: Audio

Added music and sound effects, reaching milestone **M3 ("Alive")** alongside the upcoming shaders.

**What was done:**
- **4.1** Looping background music via the framework `MediaPlayer` (two instances). Two original
  tracks: a calm menu loop and a driving gameplay loop.
- **4.2** SFX via `SoundPool`: eat, shrink, mystery, game over, UI click, pause. Eat pitch
  (playback rate) rises with food tier and combo for reward feel — no extra clips needed.
- **4.3** Master / Music / SFX volume sliders in Settings (persisted via DataStore, live preview
  while dragging). Lifecycle-aware: music pauses on `ON_STOP`, resumes on `ON_START`, and yields to
  other apps via audio focus (ducks on transient loss).
- **4.4** Menu ↔ gameplay music crossfades (~600 ms volume ramp) driven by the active screen.

**Architecture:** new `audio/` package — `GameAudio` facade (owns `SoundManager` + `MusicManager`),
`Sfx`/`MusicTrack` enums, and the `GameSfx` interface the ViewModel depends on (`GameSfx.None`
default keeps `game/` pure and the VM testable). Created once in `ui/App.kt`, released on dispose.

**Assets:** all clips are **original, procedurally synthesized** by `tools/audio/generate_audio.py`
(Python stdlib only) and dedicated to the public domain (CC0) — recorded in `docs/CREDITS.md`.
Music loops are sample-joined (0→0 boundaries) for click-free looping. Shipped as WAV (no encoder
available in this environment).

**Verification:** `:app:testDebugUnitTest` green (22 tests; `game/` untouched, audio additive);
`:app:assembleDebug` builds the debug APK with all 8 raw clips packaged. On-device audio smoke test
still pending (no emulator in this environment). Build used the system Gradle 8.14.3 because the
pinned wrapper distribution (8.9) could not be downloaded here; the wrapper remains pinned to 8.9.

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
