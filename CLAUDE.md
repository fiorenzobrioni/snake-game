# CLAUDE.md

Guide for Claude Code (and other AI agents) when working in this repository.

## Repository language - English only

**This project is English.** Every artifact committed to the repository - source code, identifiers, inline comments, KDoc, log messages, user-facing strings, asset filenames, commit messages, branch names, PR titles and descriptions, issue templates, Markdown documentation - **must be written in English**.

This rule is independent of the language used in chat:

- The user may chat in any language (often Italian).
- The assistant replies to the user in the user's chat language.
- **Nothing of the chat language ever leaks into the repository.** If the user writes a task description in Italian, translate the intent into English before writing anything to disk, naming a branch, or composing a commit message.

If you spot any non-English content already in the repo, treat it as a bug to fix in the same change you are making.

## Writing style (prose/documentation files only)

When writing or editing the repo's prose files in this repository (`CLAUDE.md`, `README.md`, files under `docs/` or `devlog/`, `PLANNING.md`, etc.):

- **Never use the em dash `—`**. Use a regular hyphen `-`, a colon `:`, a comma, or parentheses depending on context.
- **Never use the middle dot `·`**. Use a regular hyphen `-` or a comma instead.

This does NOT apply to source code or string resources: in Kotlin, XML (`strings.xml`), or other code files, both are allowed where the design calls for them.

## Project overview

A classic Snake Game being built **from scratch as a native Android app** in **Kotlin + Jetpack Compose** (Material 3), aiming for a polished, **Google-Play-publishable** title with smooth animation, particles, AGSL shaders, audio and menus. Gameplay is rendered on a Compose `Canvas`. The development plan is in `PLANNING.md`.

## Architecture & Tech Stack

- Kotlin + Jetpack Compose (Material 3), built with Gradle (Kotlin DSL) + version catalog + wrapper.
- `minSdk 33` (Android 13), `compileSdk`/`targetSdk 36`; `applicationId` `com.brioni.snake` (placeholder, finalize before first Play upload). The `minSdk 33` floor means **AGSL shaders are always available**.
- *For the current state, completed features, and active tasks, always refer to `PLANNING.md`.*

## Commands

### Build & run (Android - active project)
### Build & run
```bash
./gradlew assembleDebug
```
(Or use Android Studio's Run button).

### Run unit tests
```bash
./gradlew testDebugUnitTest
```

## Architectural Constraints

To keep the codebase maintainable, agents must respect these structural rules:

- **`game/` (The Model)**: Must remain **pure Kotlin**. Never import `android.*` or `androidx.compose.*` here. This ensures the game logic remains 100% unit-testable.
- **`ui/` (The View)**: Built with Jetpack Compose (Material 3). UI navigation uses state-based transitions.
- **`data/` (The Persistence)**: Built over Preferences DataStore.
- **Gradle**: Dependencies and versions are centralized in `gradle/libs.versions.toml`. Always add/upgrade dependencies there, referencing them as `libs.*` in the `build.gradle.kts` files. Always use the `./gradlew` wrapper.

## Conventions and guidelines for working in this repo

- **Language**: see "Repository language - English only" above. Everything committed is English; the chat language is irrelevant.
- **Kotlin style**: official Kotlin conventions (`kotlin.code.style=official`). PascalCase for types and Composables, camelCase for functions/properties, `UPPER_SNAKE_CASE` for constants. Prefer immutability (`val`), data classes for model state, and idiomatic Kotlin.
- **Compose**: keep game logic out of composables; composables render state and emit events. Heavy per-frame work belongs in the `game/` model / the coroutine loop, not in recomposition.
- **Branch**: develop on the feature branch assigned for the task. Do not push to `main` without explicit permission.
- **Plan-driven**: before adding a feature, check `PLANNING.md` for the matching step and complete steps in order. Every step must remain **buildable and testable** on its own.
- **Assets**: new graphic/audio/font/shader assets must be free with a compatible license (MIT/CC0/CC-BY). Record source and license in `docs/CREDITS.md` in the same change.
- **Secrets**: never commit keystores or signing material (`*.jks`, `*.keystore`, `keystore.properties` are git-ignored). `local.properties` is machine-specific and git-ignored.

## Documentation files

Keeping these current is part of the task, not an afterthought.

- **`PLANNING.md`** - the authoritative source for *where the project is going*: version roadmap, active TODOs/ideas, known bugs, architectural design decisions.
  - **Update it when:**
    - Ticking a step as done (`[ ]` → `[x]`) after it is fully implemented and verified.
    - A phase definition changes (scope, ordering, or new steps inserted).
    - Adding or updating active TODOs, feature ideas, or known bugs.
    - Adding architectural notes or constraints that future contributors must follow.
  - **CRITICAL RULE:** Never let it drift from the actual state of the code. If you implement something the plan still lists as open, fix the plan in the same change.
- **`devlog/`** - the historical record of *what happened*: completed work and the decisions behind it. Active file `devlog/devlog-0.md`, newest entry on top; full rules and template in `devlog/README.md` (don't edit that file). Add a dated entry when completing a step or phase: what was implemented, how it was verified, notable decisions or problems.
- **Keep `README.md` current**: at the end of **every implementation**, review `README.md` and update it if the change affects anything user-facing (features, gameplay rules, controls, screenshots, build/run instructions). If no update is needed, no action is required.

---

## Key files

| Path | Role |
|---|---|
| `app/src/main/kotlin/com/brioni/snake/MainActivity.kt` | Compose entry point |
| `app/src/main/kotlin/com/brioni/snake/ui/theme/` | Material 3 theme (Color/Theme/Type) |
| `app/build.gradle.kts` | Android application module config |
| `gradle/libs.versions.toml` | Dependency/version catalog |
| `app/src/main/AndroidManifest.xml` | Single portrait `MainActivity`, launcher |
| `docs/CREDITS.md` | Asset sources & licenses |
| `README.md` | User documentation (English) |
| `PLANNING.md` | Plan, roadmap, active TODOs, bugs, and design notes |
| `devlog/devlog-0.md` | Implementation history (the "what happened"); rules in `devlog/README.md` |
| `legacy/SnakeGame/SnakeForm.cs` | Frozen v1.0.0 reference (game model) |
| `.github/copilot-instructions.md` | Copilot instructions (aligned with this file) |
