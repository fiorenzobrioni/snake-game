# DEVLOG Snake Game

Development diary of the project. The newest entries go at the top.
Each entry notes what was done, decisions made, problems encountered, and what comes next.

Suggested format for each entry:

## YYYY-MM-DD - Short title
**Done:** what was completed  
**Decisions:** technical/design choices and why  
**Issues:** what got stuck and how (or if) it was resolved  
**Next:** the next step  

---

## 2026-07-02 - Fix terrain noise tearing (sinless hash)

**Done:**
- Fixed the "misaligned rectangular patches" the user reported on Glacier (visible as a floor made
  of shifted tiles). The value-noise `hash` used `fract(sin(dot(p, k)) * 43758.5)`; on mobile GPUs
  `sin()` is evaluated at reduced precision, so for large arguments (fragment coords are hundreds
  to thousands of pixels; star/sparkle/crack lattices push `dot` into the tens of thousands) the
  result degrades and the noise tears along the lattice into visibly offset blocks.
- Replaced it with the sinless integer hash ("Hash without Sine", Dave Hoskins, MIT), which is
  precision-stable everywhere. Applied to every terrain's `hash` (Meadow / Nebula / Dunes /
  Glacier) since they all shared the same sin-based helper - Glacier just showed it worst because
  of its high-contrast crack veins. Credited in `docs/CREDITS.md`.

**Decisions:** Kept the smooth full-screen `sin` bands (Abyss caustics, Glacier sheen) as-is: those
take small arguments and only shift slightly under precision loss, they don't tear.

**Issues:** none - build and 164 tests green (system Gradle 8.14.3).

**Next:** Confirm on-device that Glacier is now seamless across the whole board.

---

## 2026-07-02 - Terrain tuning pass: sharp resume, brighter floors, Circuit → Glacier

**Done (all from on-device user feedback):**
- **Resume countdown is now sharp.** The step-3.4 pause blur stayed at 14dp through the 3-2-1
  because the status is still `Paused` while counting - defeating the countdown's whole purpose.
  The blur target now also checks `resumeCountdown == 0`, so tapping Resume animates 14dp→0 and
  the board is as crisp as during play while the digits tick (the animated un-blur doubles as a
  nice "snapping back into focus" transition).
- **Meadow / Abyss / Dunes brightened** - they read too dark in play:
  - Meadow: grass checker lifted ~50% (0.090/0.205 base), stronger blade contrast, cloud range
    widened to 0.80-1.10, vignette floor 0.80 → 0.84.
  - Abyss: water column roughly doubled in key (top 0.034/0.100/0.160), caustics widened
    (exponent 4.0 → 3.5) and brightened (0.10/0.32/0.38), stronger light shafts, vignette 0.82.
  - Dunes: sand gradient lifted ~60% (top 0.170/0.112/0.064), deeper below-crest shading (0.84)
    with warmer, stronger crest glints, sparkle gain 0.45 → 0.55, vignette 0.84.
- **Circuit replaced by Glacier** (the PCB floor didn't land): a **frozen lake**, deliberately the
  brightest floor of the set as requested - a pale icy blue surface (top 0.150/0.230/0.330)
  mottled by soft noise, veined with **bright static cracks** (two ridged-noise layers, coarse +
  fine, sharpened via smoothstep+pow), an internal sheen band drifting diagonally through the ice
  and cool twinkling glints. Grid line: a subtle dark blue (0x1A0A2038). All `Circuit` references
  renamed across the enum, shader map, grid tints, the Settings preview map and docs; a stale
  persisted `"Circuit"` pref decodes to `Default` through the existing `runCatching` fallback.

**Decisions:**
- Brightness went up by raising the base gradients and effect gains rather than flattening the
  vignette entirely - the floors keep depth, they just sit a register higher. Ready to tune
  further per-floor if anything still reads muddy on device.
- Glacier's cracks are static (real ice doesn't crawl); only the sheen and glints animate, in
  keeping with the "stage, not protagonist" rule even on the brightest floor.

**Issues:** none - build and 164 tests green (system Gradle 8.14.3).

**Next:** Device pass over the three brightened floors and Glacier's key (snake/food contrast on
the pale ice is the thing to eyeball; the obstacle grays should still separate).

---

## 2026-07-02 - Live skin previews, pause-resume countdown, Campaign level progress

**Done:**
- **Settings skin cards went live**: the four static swatches became a **slithering mini snake**
  drawn through the real gameplay renderer. `SnakeEmblem` gained optional `time`, `waveAmplitude`,
  `cellFraction` and `contentAlpha` parameters (all defaulted so the menu wordmark emblem stays
  static): a sine wave travels tailward along the segment centres so the head reads as leading,
  and the advancing clock animates each skin's body material exactly as in play (Neon's filament,
  Aurora's flowing hues, Ember's breathing lava). Grow/shrink food swatches stay on the card; skin
  and terrain cards now share one 120 s linear preview clock (`rememberPreviewClock`).
- **Pause no longer resumes instantly.** Resume now runs a **3-2-1 countdown**
  (`GameViewModel.resumeFromPause`, `RESUME_COUNTDOWN_SECONDS = 3`): the paused scrim clears and a
  scrim-free `ResumeCountdownOverlay` shows the ticking digit in a pulsing ring over a small
  grounding disc - the board stays fully visible the whole time. Meanwhile the renderer pulses a
  **locator beacon** on the snake's head (`GameBoard.drawResumeBeacon`, new `resumeHighlight`
  param): a steady skin-accent ring + soft glow, two expanding sonar rings and a pulsing chevron
  one cell ahead pointing along the current direction, so the player re-finds the snake and plans
  the first move. `resumeHighlight` also extends `effectsActive`, keeping the per-frame clock
  alive while the game itself stays paused. Reduce-motion keeps the steady ring/chevron but drops
  the expanding pulses.
  - Safety: the countdown is cancelled on Back/menu (`toSetup`) and on app backgrounding
    (`cancelResume` wired into `App`'s ON_STOP next to the auto-pause), so a run can never
    restart unseen; `togglePause` also clears it defensively.
- **Campaign intro shows lap progress**: the banner now reads **"Level 3/15"** - the
  `level_intro_level` string gained a total placeholder and `LevelIntroOverlay` a `levelCount`
  param fed from `LevelsMode.LEVEL_COUNT`, so adding levels later updates the intro automatically.

**Decisions:**
- The resume countdown reuses the Levels-intro visual language (digit + expanding ring, same
  600 ms pop) for consistency, but deliberately drops the scrim: seeing the snake IS the feature.
- Fixed light-on-dark colours for the countdown digits: the board interior is always the dark
  arcade surface in both themes, so theme-driven `onBackground` would break in light mode.
- The beacon is drawn inside the board's `clipRect` after the snake pass, in the skin's head-glow
  accent + white, so it reads on all six terrains under all six skins.

**Issues:** none - build and tests green on the first run (system Gradle 8.14.3, 164 tests).

**Next:** On-device pass over the resume flow (does 3 s feel right with the beacon? drop to 2 s if
it drags); consider a soft tick SFX per countdown second.

---

## 2026-07-02 - Board terrains (6 selectable AGSL floors) + Settings cleanup

**Done:**
- Added **board terrains**: the board's animated floor is now selectable in Settings independently
  of the skin. New pure-model enum `game/BoardTerrain` (Default / Meadow / Abyss / Nebula / Dunes /
  Circuit), persisted in DataStore as `board_terrain` and covered by `BoardTerrainTest`; snake,
  foods, obstacles and tokens keep the active skin's look, only the floor swaps.
- Five new AGSL terrain shaders in `Shaders.kt`, sharing one uniform interface (`origin`,
  `resolution`, `time`, `cellPx`) and compiled lazily through `BoardShaders.terrainLayer`:
  - **Meadow**: mowed-lawn checker aligned to the play grid via `cellPx`, blade-noise texture,
    cloud shadows drifting over the field.
  - **Abyss**: deep-ocean blues with an animated caustic web and faint light shafts.
  - **Nebula**: violet/teal nebula wisps under a two-layer star field with per-star twinkle rates.
  - **Dunes**: night desert with three stacked moonlit dune ridges and rare twinkling sand glints.
  - **Circuit**: dark PCB with a soft pad per cell and hash-picked grid traces carrying pulses.
- Fixed a latent bug the feature surfaced: the in-game `BACKGROUND` shader had Classic's board
  colours **hardcoded**, so all six skins shared the same floor in play (the palette's
  `boardTop`/`boardBottom` were only honoured in previews). The shader now takes both as
  `layout(color)` uniforms; the Default terrain feeds them from the active palette and the menu
  backdrop (`AnimatedShaderBackground`) pins the brand colours explicitly.
- Grid lines get a per-terrain tint (`terrainGridLine` in `GameBoard.kt`) so they whisper over each
  floor instead of fighting it.
- Settings: a **Board terrain** section right under the skins, rendered as live animated shader
  preview cards (a shared 120 s linear clock drives every card; the Default card previews the
  active skin's gradient). Also **removed** the Level / Snake speed / Board scale selectors from
  Settings - they duplicated the Custom Game setup screen, which edits the same persisted
  preferences - so Settings now holds only app-wide options (their strings were dropped too).

**Decisions:**
- Terrains are designed as *stages, not protagonists*: dark, desaturated, slowly animated, so
  gameplay readability holds under any of the 6 skins x 6 terrains = 36 combinations.
- All terrains are free (no unlock gating) for now; gates can be added later the way skins do it.
- `cellPx` grid-aligns terrain features (Meadow checker, Circuit traces) with the actual board
  cells, which makes the floors feel native to the game rather than wallpaper behind it.

**Issues:** The Kotlin compile daemon died once on first launch (transient, known flaky in this
container); the retry built cleanly. Built and tested with the system Gradle at
`/opt/gradle-8.14.3` (wrapper download still blocked by egress policy): `assembleDebug` and
`testDebugUnitTest` (164 tests) both pass.

**Next:** Eyeball the six terrains on a device (especially Meadow's brightness under the Classic
lime snake and Circuit's trace density on Colossal boards); consider unlock gating for terrains
once the skin gates are re-enabled for release.

---

## 2026-07-01 - Teleport: route the snake through the pads instead of streaking

**Done:**
- Fixed the ugly portal animation. Before, a teleport only snapped the head-crossing tick
  (`resetTo`), so the head jumped from the cell *before* the entry pad straight to the exit,
  never touching the entry pad; and every following tick a body segment streaked across the
  board while a continuous-body skin (tube / neon / aurora / ember) drew a tube spanning the
  two portals.
- The renderer now routes the body **through** the pads. New `interpolatedSnakeCenters`
  (in `GameBoard.kt`) replaces the inline per-segment lerp: a segment whose old and new cell
  are not neighbours is recognised as a portal jump and, for the first half of the tick, slides
  from its old cell *into the entry pad*; for the second half it sits on the exit pad. So the
  head visibly dives into the entry portal and re-emerges at its partner, and each body segment
  threads through the same way.
- The body tube is cut at the portal seam. New `isBrokenSpan` (distance > 1.6 cells) makes the
  connected renderers skip the capsule that would bridge the two pads, while keeping the joint
  disc so a lone in-portal segment still reads. Applied to `strokeChain` (tube / neon / ember)
  and the aurora / molten vein loops. Blocky skins were already seam-free (per-cell squares).
- `GameViewModel.advance` now only snaps interpolation on a teleport when **reduce-motion** is on
  (keeps the instantaneous blink); with motion on it commits normally so the routing above runs.

**Decisions:**
- Kept the change renderer-side: no game-logic / engine change, so `LevelHazardsTest` and the
  deterministic teleport model are untouched. The half-tick entry→exit handoff produces a brief
  "pop" between the two pads, but both wear the swirling portal disc so it reads as the jump.
- The seam cut is distance-based (reusing the single global `centers` list) rather than splitting
  into sub-chains, so body width tapering stays continuous along the whole snake.
- Ghost board-wrap (padless discontinuity) also benefits: the tube no longer streaks across the
  board, it just breaks at the wrap.

**Issues:** No local wrapper Gradle (distribution download blocked by egress policy); built and
tested with the system Gradle at `/opt/gradle-8.14.3`. `compileDebugKotlin` and
`testDebugUnitTest` both pass.

**Next:** Watch on-device that the entry→exit handoff feels smooth for long snakes; consider a
short alpha fade at the pads if the pop is noticeable.

---

## 2026-07-01 - Per-skin snake bodies (Neon / Aurora / Ember) + skin unlock bypass

**Done:**
- Replaced the boolean `SkinPalette.segmentedBody` with a `SnakeStyle` enum, dispatched in
  `GameBoard.drawSnake`, so each skin can render the snake body its own way. Three skins
  get a bespoke, premium, subtly animated body:
  - **Neon** - a hollow neon tube: dark core, glowing wall, a bright pulsing filament and a
    ring head (additive `BlendMode.Plus` glow).
  - **Aurora** - a ribbon whose teal-cyan-blue-violet-green hues flow along the body and drift
    over time (a per-segment `auroraColor(u, time)`), with an additive glow and upper sheen.
  - **Ember** - a dark rock crust with a pulsing molten-lava vein that runs hottest at the head.
- Kept Classic = **tube** and Retro / Pixel = **blocks** but made both more premium while
  keeping their identity: a crisp top specular on the tube; a volumetric diagonal gradient +
  specular corner on the blocks (`drawChiselledBlock`).
- Extracted a reusable `strokeChain` (round-capped capsule chain + joint discs, blend-mode
  aware) that backs the tube / neon / molten bodies; debris (severed tails) now render through
  the same per-skin body dispatch (`drawSnakeBody`), passed the frame `time`.
- Added `Skin.ALL_UNLOCKED_PREVIEW = true`: while set, every skin is selectable in Settings and
  the game-over "skin unlocked" toasts are suppressed - a temporary pre-release convenience so
  all skins can be trialled. The `SkinUnlock` rules and `Skin.newlyUnlocked` logic are untouched.

**Decisions:** Animation reuses the `time` value already threaded into `drawSnake`, so nothing
in the coroutine game loop changed. The unlock bypass is a single model-level flag read by the
Settings picker and the ViewModel, rather than deleting the gating - so it is a one-line revert
and leaves `SkinTest` green.

**Issues:** None. `SkinPalette` swapped a field (`segmentedBody` → `snakeStyle`), but it is
constructed only in `paletteFor`, so all six palettes updated in one place.

**Verified:** `./gradlew compileDebugKotlin testDebugUnitTest` (build successful, all unit tests
pass). Reviewed an animated HTML/Canvas mockup of the three new bodies for sign-off first.

**Next:** Refresh screenshots to show the new snake bodies; restore the unlock gating (flip
`ALL_UNLOCKED_PREVIEW`) before an official release.

---

## 2026-07-01 - Premium, per-skin power-up / hazard tokens

**Done:**
- Redesigned the power-up / hazard pieces from flat coloured discs/squares into
  **premium bevelled tokens** with depth: a material body gradient, a rim/bevel and
  a soft glow (glow skins) or drop shadow (flat skins), plus an **embossed glyph**
  (dark drop under a light/ink face).
- **Diversified per skin** via a new `SpecialStyle` enum on `SkinPalette`
  (`ui/game/SkinPalette.kt`): Classic = glossy Enamel, Neon = hollow neon-tube,
  Retro = warm Phosphor, Pixel = hard Pixel tile, Aurora = frosted Glass, Ember =
  molten iron. The effect's identity accent colour + symbol stay constant across
  skins, so meaning never shifts - only the frame's material changes.
- **Hazards** now wear a discreet **notched red danger bezel** (a faint aura on glow
  skins) instead of the thin dashed caution ring.
- **Freeze** symbol changed from a snowflake to a **faceted ice crystal / gem**
  (`drawCrystal`); **Extra life** changed from a snake head to a **heart**
  (`drawHeart`). Removed the now-unused `drawSnowflake` / `drawSnakeHeadIcon`.
- Extracted a single shared renderer `drawSpecialToken` (in `SpecialIcons.kt`) and
  routed both the in-game board (`GameBoard.drawSpecialFood`) and the first-run
  tutorial through it. The onboarding previously **duplicated** the disc rendering
  (`drawSpecialDisc` / `drawSpecialDiscAt`); those were deleted so the tutorial and
  gameplay can never drift.

**Decisions:** Kept the look **sober** (soft gradient + clean border + minimal glyph
shadow, no heavy gloss/emboss) per the design review, while still giving each of the
six skins a distinct material. Reused the existing `useGlow` flag for the depth cue
and added only a `tokenCorner` per style for the token shape (disc vs rounded/hard
square), so the change is data-driven rather than branchy.

**Issues:** None. `SkinPalette` gained a required field, but it is constructed only in
`paletteFor`, so all six palettes were updated in one place.

**Verified:** `./gradlew compileDebugKotlin testDebugUnitTest` (build successful, all
unit tests pass; the two remaining warnings are pre-existing in `LevelHazardsTest` /
`LevelShapesTest`). Rendered HTML/Canvas mockups of the before/after and per-skin
tokens for design sign-off before implementing.

**Next:** Refresh the onboarding / gameplay screenshots in `docs/screenshots/` to show
the new tokens (optional, cosmetic).

---

## 2026-06-27 - Snake 1.0.0 release prep (version bump, screenshots, docs)

**Done:**
- Bumped the app version to the first Android release: `versionCode` 26 -> 27,
  `versionName` "0.9.6" -> "1.0.0" in `app/build.gradle.kts`. The in-app Credits screen reads
  `versionName` from the package, so it now shows "Version 1.0.0".
- Replaced the six stale screenshots in `docs/screenshots/` with eight current ones
  (menu, Endless gameplay, Time Attack, Campaign hazards, Campaign intro banner, Custom setup,
  Settings, onboarding). Each was cropped to drop the device status bar and the bottom gesture
  pill and normalised to an identical `1080x2158`; the README table was rewritten with the new
  set, a visitor-friendly order and per-shot captions.
- README: reframed the Legacy section so the desktop `v1.0.0` reads as the original prototype
  and the Android `1.0.0` as the project's first (independent) release, avoiding the version
  collision.
- PLANNING: added a "Releases" note for Android 1.0.0 and a "Post-1.0.0 / future versions"
  backlog (player-activated power-up 6.9.6, share-score 6.9.11, ghost replay 6.9.12, and the
  remaining Play-Store steps 7.2-7.6); marked Step 7.6 in progress.

**Decisions:** Reviewed the Credits screen and `docs/CREDITS.md` - both already complete and
aligned (identity/version, GPL-3.0, source link, Gemini music, CC0 SFX, Orbitron OFL,
graphics/shaders, "built with"), so no content change was needed. The release ships as a
**debug-signed APK** on GitHub (not Play): Play signing/AAB stay deferred to Phase 7. The
personal-note tagline was kept as-is.

**Issues:** No image tooling pre-installed; used `pip install Pillow` + a one-off scratchpad
script to crop/normalise the shots (verified one output visually before batch-processing).

**Verified:** `./gradlew assembleDebug testDebugUnitTest lintDebug`; APK packaged with
`README.md` + `LICENSE` into `snake-1.0.0.zip` for the GitHub release.

**Next:** User creates the GitHub release (tag `v1.0.0`) and uploads the APK + zip; later,
Google Play distribution (Phase 7).

---

## 2026-06-27 - Time Attack icon cleanup, special-block frequency bump, hide debug unlock

**Done:**
- Removed the `+` / `-` badge from the Time Attack clock icons in `SpecialIcons.kt`
  (`drawClock` lost its `plus` parameter). Gain-vs-lose meaning is now carried purely by
  the block's green/red accent colour, which was already the dominant cue.
- Raised the special-block spawn weights in `SpecialFrequency.kt`: Standard 10 -> 12
  (+20%), Frequent 22 -> 32 (+45%), Frenzy 40 -> 62 (+55%). Unlock gates (`gateFactor`)
  left untouched. Resulting spawn odds (all food branches unlocked): Standard ~11.2% ->
  ~12.8%, Frequent ~21.8% -> ~28.8%, Frenzy ~33.6% -> ~44.6%.
- Hid the debug-only "unlock all themes" menu button even in debug APKs via a new
  `SHOW_DEBUG_UNLOCK_SKINS` flag (default `false`) in `MainMenuScreen.kt`. The button and
  its string stay in the codebase; the gate is now `BuildConfig.DEBUG && SHOW_DEBUG_UNLOCK_SKINS`.

**Decisions:** Kept the frequency change to spawn weight only (not gate timing) to keep
the tuning predictable; magnitudes confirmed with the user (the "Recommended" tier).

**Issues:** None. `SpecialFoodTest` frequency-ordering invariants still hold with the new
weights.

**Verified:** `./gradlew testDebugUnitTest` (pass) and `./gradlew assembleDebug` (pass).

**Next:** Optional on-device check of the three changes.
