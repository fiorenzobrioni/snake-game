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
