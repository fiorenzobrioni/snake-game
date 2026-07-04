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

## 2026-07-04 - Release 2.0.0 (version bump + GitHub release)

**Done:**
- **Bumped the app to `versionName 2.0.0` (`versionCode 29`)** in `app/build.gradle.kts`, cutting the
  second public GitHub release. 2.0.0 consolidates every phase shipped since `v1.0.0` (2026-06-27):
  the fourth **Zen** mode (Phase 6.11), the mode-depth & pacing pass (Phase 6.10: Campaign
  checkpoints, Time Attack pace multiplier + Fever Time, stepped Endless ramp, 9-modifier
  Daily/Random pool), the setup-clarity captions (Phase 6.12), the onboarding-tour redesign +
  quit-run guard + pinned game-over footer (Phase 6.13), and the whole premium-visual batch (Steps
  7.8-7.15: replay-a-Daily, death/level-up transitions, selectable **board terrains** with
  terrain-seeded UI accents + terrain menus, live skin/terrain previews, the 3-2-1 resume countdown
  with head beacon, and the redrawn "Serpentine" launcher icon).
- **Documentation squared up:** `PLANNING.md` gains a `2.0.0` entry in the Releases section and a
  "Release 2.0.0" step under Phase 7; the major-version rationale (scale of the overhaul over 1.0.0)
  is recorded there.

**Decisions:** went to a **major** bump (2.0.0) rather than 1.3.0 because the cumulative post-1.0.0
work is a visual and gameplay overhaul (a new mode, terrains, a reworked pacing model, a redesigned
first-run tour, a new icon), not an incremental point release. The tag follows the repo convention
(`v` prefix, e.g. `v2.0.0`).

**Verified:** version strings updated; the changelog for the release was assembled from `git log
v1.0.0..HEAD` (27 commits) cross-checked against the devlog entries below.

**Next:** Play Store phase (Steps 7.2-7.6): R8/shrink verification, upload-keystore signing, signed
AAB + tag-driven CI.

---

## 2026-07-04 - Onboarding tour redesign, quit-run guard, game-over footer (1.2.0)

**Done (Phase 6.13):**
- **First-run tour redesigned as a five-card premium pager** (`OnboardingScreen`). The weak
  dedicated "how to steer" page is gone - steering became a glanceable three-chip row (Swipe /
  Tap to turn / D-pad glyphs) on the welcome card, with Settings named for the full choice. The
  five cards now cover what a new player actually needs sold and taught: **Welcome** (the real
  in-game `SnakeEmblem` slithering in the player's skin on a glass panel; goal + combo pitch),
  **Food** (a gently pulsing grow/shrink/mystery row plus the legend), **Power-ups & hazards**
  (unchanged legend over the real `drawSpecialToken` renderers), **Modes** (four accent-rimmed
  cards with hand-drawn glyphs - infinity, stopwatch, checkpoint flag, enso - one line of honest
  pitch each), and the **daily loop** (Daily Challenge, missions, achievements & records, skins &
  arenas, plus a "reopen from Settings" hint). Hero panels are glass cards with the menus'
  gradient rim; a shared `withFrameNanos` clock drives all live artwork and simply never advances
  under reduce-motion. System-back now pages backwards (finishing only from the first card)
  instead of skipping the whole tour.
- **Quit-run confirmation.** Back while paused used to end a live run silently. It now opens
  `QuitRunDialog` - an error-rimmed glass dialog ("Quit this run?") with the safe **Keep playing**
  as the prominent filled button and **Quit run** as the quiet outlined one; outside-tap and Back
  dismiss safely. A pending resume countdown is cancelled back to the pause overlay first, and the
  dialog auto-drops if the game state moves on. Back during *running* play keeps the existing
  `BackBehavior` setting, and the pause overlay's own Menu / Game setup buttons stay one-tap
  (deliberate intent needs no interrogation).
- **Game-over overlay: pinned action footer.** With a long recap + missions + achievements + skin
  unlocks, the buttons could end up below the fold. The results column now scrolls in a
  `weight(1f)` area (still centring when short) above a pinned footer: full-width **Play again**
  over a **Game setup** / **Menu** row - so the primary action is always on screen.
- **Version bumped to 1.2.0** (`versionCode 28`).

**Decisions:** the tour follows the standard mobile-onboarding playbook - one idea per card,
skippable at every step, progress indicator, benefit-led copy - and teaches with the *real*
renderers (emblem, food, tokens) so every colour/symbol means the same thing in play. The quit
guard applies only to the ambiguous/accidental path (system Back); explicit buttons keep their
single-tap behaviour, matching platform guidance on confirming destructive actions.

**Verified:** `assembleDebug` builds and the full unit suite is green (the wrapper distribution
download is blocked in this sandbox, so builds ran on the preinstalled Gradle 8.14.3; no wrapper
or catalog change was made).

**Next:** Play Store phase (Steps 7.2-7.6).

---

## 2026-07-04 - Zen veil border, captions for every Custom selector, "Explorer" scale

**Done:**
- **Zen's border is now a porous veil, not a wall.** The solid frame is skipped entirely in Zen
  and replaced by: (1) a soft teal **mist** bleeding inward from each of the four edges (linear
  gradients, one cell deep) and (2) a **slowly drifting dashed stitch** along the frame
  (`PathEffect.dashPathEffect` with a time-driven phase), both breathing with the existing
  `zenGlow` envelope. Reduce-motion keeps the veil but freezes the drift and the breath. The
  boundary now *looks* permeable at a glance and Zen is visually unmistakable from the walled
  modes' solid frames.
- **Every Custom selector now explains itself.** The `ChipSection` captions were completed across
  the board, adapting to the selected mode: **Mode** carries a one-line pitch (e.g. Zen: "No
  walls, no hazards - pure flow at your own pace"); **Level** states the obstacle count in Time
  Attack, the obstacle count + ramp start in Endless, or why the selector sleeps in Campaign/Zen;
  **Snake speed** explains the Time Attack multiplier, the Zen fixed rhythm, and why
  Endless/Campaign pace themselves; **Board scale** shows the selected preset's cells on the
  short side; **Start level** (Campaign) invites jumping to reached levels when no checkpoint is
  selected.
- **Board scale "Standard" renamed to "Explorer"** (Cozy - Explorer - Epic - Colossal). Display
  name only: the enum constant remains `Classic` because it is a persisted DataStore / `ScoreKey`
  token, so existing records and preferences are untouched.

**Verified:** full unit suite green and `assembleDebug` builds.

**Next:** Play Store phase (Steps 7.2-7.6).

---

## 2026-07-03 - Zen mode: the calm fourth mode on a torus

**Done (Phase 6.11):**
- **`GameMode.Zen` + `game/ZenMode.kt`.** A borderless arena: all four edges **wrap** (the Ghost
  power-up's wrap made permanent via a shared `wrapAround` flag in `GameEngine.tick`), so the only
  death is the snake's own body. No obstacles regardless of difficulty (`setup` forces an empty
  set; the selector is disabled and scores are pinned to `ZenMode.SCORE_LEVEL`), and **no specials
  ever** (suppressed in `refill`, like the Old School twist) - only the regular grow/shrink/mystery
  progression. The pace is the player's `SnakeSpeed`, fixed for the whole run; the grow-combo
  window is doubled (`ZenMode.COMBO_WINDOW_FACTOR = 2`) so streaks reward unhurried flow.
  Edge-hugging no longer fires the near-miss cue there (`isNearMiss` gained an `edgeLethal` flag) -
  on a torus the edge is a doorway. Zen is excluded from the challenge rotation.
- **Toroidal rendering.** `interpolatedSnakeCenters` now takes the board and glides a wrapping
  segment along the **toroidal shortest path**: first half of the tick it slides out through its
  edge, second half it slides in from the opposite one - continuous speed, clipped by the board's
  existing `clipRect`, tube broken across the gap by `isBrokenSpan`. This replaces the old
  "snap at the exit cell" fallback, so Ghost wraps got smoother too.
- **Zen presentation.** The board frame **breathes** a soft teal (`zenGlow` in `GameBoard`, a slow
  ~5 s pulse; steady glow under reduce-motion) - the visual signature that the edges are open. Zen
  runs play the calmer **menu music track** (crossfaded by `App`; no new asset). Setup captions
  explain the sleeping difficulty selector and the rhythm choice; the HUD shows
  "Zen - pace - board". Records show a single pinned-level row per scale (like Campaign). Three new
  achievements: **Inner Peace** (5 min flow), **Ouroboros** (60 segments in Zen), **Eternal Flow**
  (score 3000 in Zen).

**Verified:** 193 unit tests green (8 new in `ZenModeTest`: all four edges wrap, body-only death,
open board at any difficulty, zero specials under Frenzy, fixed pace with no tier events, stretched
combo window, silent near-miss at the edge, no timeout), plus a full `assembleDebug`.

**Decisions:**
- Zen completes the lineup on the *calm* axis instead of adding a fourth adrenaline mode; it is
  deliberately built from existing systems (wrap, food table, menu track) - zero new assets.
- The wrap is shared with Ghost through one flag rather than special-cased twice, so both paths
  stay in lockstep (movement, telegraph look-ahead, near-miss suppression).

**Next:** Play Store phase (Steps 7.2-7.6); deferred ghost replay (6.9.12) and share card (6.9.11).

---

## 2026-07-03 - Mode depth pass: Campaign checkpoints, Fever Time, live Endless ramp, wider Daily pool

**Done:**
- **Campaign checkpoints.** The furthest level ever reached is persisted (`campaign_checkpoint`)
  and the pre-game setup grows a "Start level" chip row (levels 1..checkpoint) once the player has
  advanced past level 1. `GameEngine.setup`/`newGame` take a `startLevelIndex` and stage that
  level's walls/hazards directly. A start past level 1 is a **practice run**: no highscore, no
  Levels-progress record, no Campaign achievements (depth stats are zeroed in `RunStats`), and the
  game-over overlay shows "Practice run - not recorded" instead of the best row. The chosen start
  is persisted and clamped to the checkpoint; records always count from a Level 1 start.
- **Time Attack pace multiplier.** `SnakeSpeed.timeAttackScoreFactor` (x1.0/x1.1/x1.2/x1.35/x1.5)
  scales every point earned in the mode, fixing the record-fairness hole where a Turbo run
  structurally out-scored a Relaxed one on the same `ScoreKey` slot. The multiplier is declared on
  the setup speed chips ("5. Turbo - x1.5") and appended to the HUD status line.
- **Time Attack Fever Time.** The last 20 s (`GameState.FEVER_MS`) double every point
  (`FEVER_SCORE_FACTOR`, applied on top of the pace multiplier). Entering emits
  `GameEvent.FeverStarted`; the presentation is deliberately loud: pulsing amber board-frame glow
  (`feverGlow` in `GameBoard`), the HUD clock turns hot and pops, a "Fever x2!" banner, a low
  jackpot sting, and the music tempo steps up 12% (`MusicManager.setTempo`, pitch preserved via
  `PlaybackParams`, reset on dispose so no other screen inherits it). A time bonus can lift the
  clock back out of the window; the event re-fires when it drains back in.
- **Endless ramp reworked (it flatlined).** The old linear ramp (190ms - 0.22ms/tick) hit its 70ms
  floor after ~90 seconds - matching the reported "stops getting faster". Replaced with **stepped
  speed tiers**: 20 s of play per tier, each multiplying the pace by 0.94 down to a 60ms floor, so
  the ramp stays alive for ~6-7 minutes and endgames run slightly hotter. Difficulty now gives the
  ramp a head start (`Level.endlessTierHeadStart`, Beginner +0 .. Legend +4) so "harder" finally
  means faster from the first tick, not just denser; the setup screen states it ("40 obstacles -
  pace starts at Speed 5"). Every step emits `GameEvent.SpeedTierUp` -> "Speed N!" banner, golden
  frame flare, rising zap SFX, haptic; the HUD's auxiliary slot shows the live tier. Announcements
  stop once the pace has floored (nothing to feel).
- **Mid-run record celebration.** Passing the stored best now fires once per run, live ("New
  record!" banner + chime) instead of only being revealed at game over.
- **Daily/Random challenge pool widened** from 4 to 9 modifiers: kept None / Bonus Rush / Frenzy /
  Compact Arena, added **Grand Arena** (Colossal board), **Maxi Feast** (all food 2x2, engine
  `forceMaxi` through `FoodTable.roll`), **Combo Rush** (halved combo window), **Overdrive**
  (Endless tier boost +4, Turbo pace in Time Attack) and **Old School** (no specials at all). The
  twist now travels inside `GameState.modifier` so the pure engine honours it per tick; each
  modifier carries a one-line description shown on the Daily/Random cards. A plain "Standard" day
  drops from 1-in-4 to 1-in-9.
- **Quieter default audio.** Music 0.5 -> 0.3, SFX 0.8 -> 0.6 (master unchanged): the music now
  sits as a light backdrop on a fresh install; existing installs keep their saved sliders.

**Verified:** 185 unit tests green (21 new across `EndlessRampTest`, `FeverTimeTest`,
`CampaignCheckpointTest`, `ChallengeModifierTest`; `GameModeTest`'s ramp test ported to the tier
curve), plus a full `assembleDebug`.

**Decisions:**
- Checkpoint runs are "practice" rather than a parallel record family - one honest leaderboard,
  zero extra record UI.
- The fever window is defined purely as remaining-time <= 20 s, so clock blocks interact with it
  deterministically (no hidden latches in the state).
- Endless tiers are computed from `playedMs` (wall-clock play), not ticks, so the tier cadence is
  speed-independent; `SpeedTierUp` is derived by comparing the tier across one tick.

**Next:** Play Store phase (Steps 7.2-7.6); consider the deferred ghost replay (6.9.12) and share
card (6.9.11) next.

---

## 2026-07-03 - Premium launcher icon: the "Serpentine" on the Meadow board

**Done (Step 7.15):**
- **Redesigned the adaptive launcher icon**, untouched since the Phase 0 placeholder. The
  **background layer** (`ic_launcher_background.xml`) is now the Meadow board: the terrain shader's
  two-tone mowed-lawn checker drawn on the 18dp play-grid rhythm, with the shader's grass greens
  lifted in brightness for launcher legibility (#173412/#1E4118 -> #224E1B/#2D6224), under a
  top-light sheen and a soft radial vignette (both `<gradient>` fills via `aapt:attr`).
- **Foreground layer** (`ic_launcher_foreground.xml`): the Classic-skin snake as one smooth
  **S-shaped tube** - a single round-capped 13dp stroke with a lighter core sheen (mirroring the
  in-game tube body), a lime #7CFC00 head with eye + catchlight, the gold food just ahead of the
  head, and a soft offset drop-shadow copy underneath. No lettering. After a first pass sat the
  head too close to the mask edge (user feedback), the S was compressed vertically (0.88 about the
  centre) and the group scaled 0.90 with a small downward nudge, balancing the head/tail clearance
  from round masks at roughly 5-7dp each.
- **Monochrome themed-icon layer** and the **splash-screen vector** (`splash_snake.xml`) mirror the
  new geometry (the splash drops the shadow - invisible on its flat brand background); the splash
  pop-in AVD needed no change since the animated group name stayed `icon`.
- Removed the orphaned `ic_launcher_background` colour resource from `colors.xml` (the adaptive
  icon references the drawable, not the colour).

**Decisions:**
- *Design chosen interactively*: five concepts were mocked as masked HTML previews (serpentine
  tube, coil-around-food, grid runner, pixel-block S, retro-phosphor S) and reviewed in chat; the
  user picked the smooth **Serpentine tube** for its clean S silhouette.
- *Meadow as the stage*: Meadow is the default terrain and the brand's world; its checker also
  survives adaptive-icon cropping at any mask, unlike a composition that depends on edges.
- *Brightened, not recoloured*: the icon keeps the shader's hue relationships and the exact
  in-game snake palette (#3FA34D body, #7CFC00 head, #FFC107 food) so the launcher tile, splash
  and gameplay read as one product.

**Verification:** system `gradle assembleDebug` green (the sandbox's egress policy still blocks the
wrapper's distribution download; on a dev machine `./gradlew` remains the way). Geometry validated
against masked mockups (circle + squircle, 44-140 px) rendered with headless Chromium before
porting the exact coordinates to the vector drawables. On-device check still owed: themed
(monochrome) icon tinting and the splash pop-in with the new shapes.

**Next:** release hardening (Step 7.2 - R8 + resource shrinking).

---

## 2026-07-02 - Premium UI pass: terrain-seeded accents, terrain menus, back headers, Settings cards, bespoke icons

**Done (Step 7.14):**
- **The UI accent colour now follows the selected Board terrain.** New `TerrainAccents` +
  `darkTerrainAccents` / `lightTerrainAccents` in `ui/theme/Color.kt`: each terrain seeds a tuned
  primary/secondary pair into the Material scheme (Meadow chartreuse/green - identical to the old
  brand, Abyss cyan, Nebula lavender, Dunes amber, Glacier ice; Arcade stays on brand green).
  `SnakeGameTheme` takes the terrain from the persisted settings (via `MainActivity`) and animates
  accent changes with a 600 ms colour cross-fade, so picking a terrain in Settings recolours the
  whole app smoothly. The `SnakeButton` ink was neutralised (`0xFF0A0E10`) to stay crisp under
  every accent family.
- **The menu backdrop renders the selected terrain.** `AnimatedShaderBackground` gained a
  `terrain` param and now compiles the terrain's own AGSL floor (shared source mapping moved to
  `Shaders.menuBackdropSource`, also reused by the Settings preview cards); Arcade keeps the
  classic drifting-glows gradient. Terrain floors are tuned for gameplay, not reading, so a
  vertical scrim (heavy at the edges, lighter behind the brand hero) keeps menu text at full
  contrast while the world stays visible as ambience.
- **Back navigation moved to the top, per the Android guidelines.** New shared `ScreenHeader`
  (glassy `MenuIconButton` back arrow + centred title) pinned above the scrolling content on
  Settings, Records, Achievements, Daily, Daily history, Random and Credits; every bottom
  "Menu" `SnakeButton` was removed (the system back gesture already routed correctly).
- **Settings grouped into titled glass cards** - Controls / Appearance / Gameplay /
  Audio & feedback / Accessibility & help - wearing the same faint gradient fill + primary-tinted
  rim as the `SnakeButtons` family; the "Control scheme" label was renamed from "Controls" to not
  clash with its section title, and "How to play" moved into the help card as an outlined button.
- **Bespoke menu icons.** New `MenuIcons` (hand-authored 24x24 `ImageVector`s: tune sliders for
  Custom Game, pinned calendar for Daily, five-face die for Random, trophy for Records, ribboned
  medal for Achievements) replace the misfitting `material-icons-core` picks. The missions strip's
  "✓"/"○" text pips became drawn glyphs (`MissionPip`) immune to system-font drift, and the
  missions dialog got the glassy rim.

**Decisions:**
- *Fixed green vs dynamic accent*: went dynamic-by-terrain (user-confirmed). Terrain is the only
  always-unlocked "world" choice, the per-terrain accent precedent already existed in-game
  (`terrainBoardBorder`, Step 7.12), and the default (Meadow) preserves the green brand exactly.
  The accents are hand-tuned per theme rather than derived from the frame colours, which are too
  muted to carry a filled button.
- *No `material-icons-extended`*: it would bloat the APK while R8 (Step 7.2) is still pending;
  five hand-drawn vectors match the game's drawn-in-code art direction anyway.

**Verification:** `gradle assembleDebug` + `gradle testDebugUnitTest` green (system Gradle 8.14.3;
the sandbox's egress policy blocked the wrapper's distribution download - on a dev machine
`./gradlew` remains the way). On-device smoke test still owed on: accent cross-fade when switching
terrain, menu scrim legibility over the brighter floors (Meadow/Glacier), light-theme accents,
and the icon shapes at tile size.

**Next:** gather feedback on the terrain-accented menus; release hardening (Step 7.2).

---

## 2026-07-02 - Terrain integration batch: intro frame, shaped danger flash, themed gates, Meadow default

**Done (all from on-device user feedback):**
- **Intro frame fully visible**: the splash board filled the whole height (`rows = ceil(...)`), so
  the top/bottom border was drawn off-canvas (only the side borders showed). The board is now inset
  by a 10dp margin on all sides (`rows` sized with floor, board centred), so the framed border and
  its halo read on the full perimeter. Note: the app already applies `safeDrawingPadding()`, so it
  was never *under* the status bar/gesture area - just off the canvas.
- **Near-miss danger flash rebuilt inside the renderer**. The old implementation was an overlay
  `Box` with a 14dp rounded-corner border that overlapped the board's sharp corners. `GameBoard`
  now takes a `dangerFlash` envelope (0..1) and re-traces the board's *exact* frame geometry:
  sharp corners flush with the border on rectangular boards, and on shaped Campaign boards
  (hourglass etc.) the flash follows the real playable outline - the user asked whether it should,
  and it should: the flash warns about the lethal boundary, so on shaped boards a rectangle lies.
  It flares in a hot version of the selected terrain's accent (soft wide halo + crisp stroke) and
  inherits the board shake for free. The overlay Box and `NearMissFlashColor` were removed.
- **Campaign gates now themed to the terrain**: new `gateEnergyFor(terrain)` in `GameHazards.kt`
  maps each floor to a plasma family (base + hot core): Arcade keeps the warm orange, Meadow gets
  golden sunlight, Abyss bioluminescent aqua, Nebula violet plasma, Dunes ember heat, Glacier a
  cold electric blue. The amber closing-strobe stays universal (it is a warning cue, part of the
  game grammar), as do the metal projector nodes.
- **Meadow is the new default terrain** (fresh installs land on the lawn, matching the brand
  intro): it moved to first place in the picker and is the fallback in `SettingsRepository`
  (decode + data-class default) and the `GameViewModel` seed. The skin-following floor was renamed
  **Default → Arcade** (constant and display name; the behaviour - the skin's dark gradient with
  drifting glows - is unchanged). A stale persisted `"Default"` value decodes to Meadow via the
  existing `runCatching` fallback. `BoardTerrainTest` updated (Meadow first, Arcade second).

**Decisions:**
- The gate warn strobe deliberately stays amber everywhere: danger cues keep one meaning across
  cosmetics (same rule as the effect tokens' identity colours).
- The flash colour is `lighten(terrainBoardBorder, 0.35)`: the frame itself appears to flare,
  which ties the cue to the boundary it warns about.

**Issues:** none - build and 164 tests green (system Gradle 8.14.3).

**Next:** Device pass: gate legibility per terrain (especially golden gates on Meadow), and the
danger flash visibility on Glacier's pale ice.

---

## 2026-07-02 - Terrain-accented frame, "Snake skin" label, Meadow brand intro

**Done:**
- Confirmed the Meadow "sharp vertical/horizontal shadow seams" the user screenshotted were the
  same lattice-tearing bug fixed by the sinless hash (the cloud-shadow value noise shared the
  broken `sin` hash) - no further change needed, all four noise-based terrains got the fix.
- **The board frame now follows the selected terrain** in dark mode (`terrainBoardBorder` in
  `GameBoard.kt`): Meadow wears a hedge green, Abyss a caustic teal, Nebula a violet, Dunes a warm
  sand, Glacier an icy blue; the Default floor keeps the skin's own border and the light theme its
  branded primary frame. Rationale: the frame edges the stage, not the snake - Ember's orange
  around a green lawn read as a mismatch.
- Settings: the **"Skin" header renamed "Snake skin"**, so the two cosmetic pickers read as a
  matched pair with "Board terrain" (string change only, key untouched).
- **Brand intro rebased onto the Meadow terrain**: `BrandIntroScreen.drawBoard` now paints the
  real in-game Meadow shader (lawn checker grid-aligned via `cellPx`, drifting cloud shadows,
  built-in vignette) and frames it with Meadow's hedge green, replacing the bespoke Retro gradient
  + two warm glows + extra vignette (and their now-unused imports). The crawling snake and the
  SNAKE wordmark stay **Retro** snake-body pieces as requested; the splash grid line became a
  subtle dark tint (0x22000000) to sit on grass, and the gold/orange detonations stay - fireworks
  over the lawn.

**Decisions:**
- Kept the warm blast accents instead of recoloring them green: complementary warm-on-green pops
  and stays in the Retro family of the snake itself.
- The intro reuses the gameplay `TerrainLayer(Shaders.MEADOW)` rather than a copy, so any future
  Meadow tuning updates the splash automatically.

**Issues:** none - build and 164 tests green (system Gradle 8.14.3).

**Next:** Device pass on the intro (Retro lime pieces on the lawn - if the wordmark doesn't pop
enough, brighten the letter fill or deepen the lawn under the word band).

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
