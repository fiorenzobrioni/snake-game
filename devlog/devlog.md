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
