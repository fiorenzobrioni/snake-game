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
