# 🐍 Snake - Android (Kotlin + Jetpack Compose)

[![Kotlin](https://img.shields.io/badge/language-Kotlin-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org/)
[![Jetpack Compose](https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4?logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![Android](https://img.shields.io/badge/platform-Android-3DDC84?logo=android&logoColor=white)](https://www.android.com/)
[![minSdk](https://img.shields.io/badge/minSdk-33-blue)](https://developer.android.com/)
[![targetSdk](https://img.shields.io/badge/targetSdk-36-blue)](https://developer.android.com/)
[![License: GPL v3](https://img.shields.io/badge/license-GPL--3.0-blue.svg)](https://www.gnu.org/licenses/gpl-3.0)

**Inspired by the classic Snake** and reimagined as a **native Android game** in **Kotlin + Jetpack Compose**,
on the way to a polished, **Google-Play-publishable** title with animation, particles, shaders, audio and menus.

---

## 🎯 Features

The classic Snake mechanics, extended with configurable features so every run feels different:

- 🚀 **Branded launch** - an animated splash flows into a short, skippable brand intro played out on
  the game board itself, laid on the animated **Meadow** lawn: a Retro-skin snake crawls across and
  the word **SNAKE** forms from Retro snake-body pieces in its wake, then it slips off-screen and
  the menu fades in.
- 🐍 **Auto-growth** - the snake lengthens **by itself** as a run goes on, so circling an empty board
  can never stall a game: every run has an arc and an end. The rhythm is a five-step **Growth** dial on
  the Custom setup screen (*Off* for the classic food-only rules, up to *Relentless*), each step
  declaring its own score multiplier, and it flips the food's role - grow pieces pay the score,
  **shrink pieces buy the time**.
- 🍽️ **Two food categories** - **grow** food makes the snake longer; **shrink** food trims it back.
- 🔠 **Magnitude tiers + maxi sizes** - each category comes in several strengths, and a 2×2 **maxi**
  variant that amplifies the effect.
- ❓ **Mystery pieces** - a "?" food per category with a random amount.
- ⏳ **Time-gated progression** - maxi and mystery pieces unlock as the session goes on (sooner on
  harder levels), so a run ramps up in difficulty. Shrink food is on the board from the first tick
  whenever auto-growth is on - the brake is always within reach.
- 💨 **Fresh board** - a regular food you ignore for too long fades away (with a little vanish burst)
  and reappears elsewhere, so looping around without eating won't stall the run. Special pieces stick
  around much longer (they're rare events worth reaching) but eventually time out too.
- ✖️ **Combo multiplier** - eating in quick succession multiplies your score (up to ×5).
- 📣 **In-run announcements** - Fever Time, each Endless speed step, a wave starting, a Shed charging and
  beating your stored best all punch in as a short centred banner - drawn **over the HUD**, never over
  the board, so the playfield stays visible in full.
- 🌊 **Endless waves** - every 45 seconds of an Endless run the board is swept by an event, in a fixed
  learnable rotation: a **Feast** (the board floods with food), a **Drought** (it all but dries up, with
  the growth clock still ticking) and a **Hailstorm** (2×2 ice stones rain down, well clear of your head,
  then melt). Each is announced and counts down in the HUD, so a long run has movements instead of one
  long crescendo.
- 🎲 **Risk bonus** - every point is multiplied by how much of the board your body is covering (up to
  ×5). Staying long stops being pure downside and becomes a **bet**: the fuller the arena, the richer
  every bite, right up until you run out of room. The HUD calls the live multiplier out and the board
  frame smoulders crimson once you are deep in it.
- ✂️ **Shed ability** - the one thing you can *do* besides steer. Eating charges a ring in the board's
  corner; when it fills, tap it to cut a third of your tail loose and **cash in** the risk you were
  carrying (the payout scales with the multiplier at the moment of the cut). It is the answer to being
  boxed in with no shrinking food in sight - and spending it means giving up the fat multiplier that
  made you rich. The button is deliberately see-through and **fades out well before the snake reaches
  its corner** - the clearance is measured from the button's real size, so it is the same distance on
  every board scale - and it stays tappable while faded.
- 🚧 **Obstacles** - symmetric blocks that tend to clump into larger shapes and raise the difficulty.
- 🎚️ **Levels & snake speed** - 5 obstacle layouts (*Beginner* → *Legend*) and 5 **independent**
  speeds (*Relaxed* → *Turbo*), mixable freely: play the dense Legend field at a gentle pace, or an
  open board flat out.
- 📐 **Responsive board** - pick a granularity (*Cozy* / *Explorer* / *Epic* / *Colossal*); the grid
  is computed from your device's screen so it fills the display with square cells in portrait. Bigger
  boards also give food, power-ups and hazards proportionally more time before they vanish, so the
  snake can reach them across the longer distances.
- 🎮 **Control schemes** - **swipe** by default (with adjustable sensitivity), a compact **wedge dial** (a single key split into four directional wedges), or one-handed **tap-to-turn**.
  A forgiving **coyote frame** makes a beat-late turn still count: the first lethal step makes the head
  hesitate one tick (instead of dying), giving you a moment to turn away - re-armed by your next safe move.
- 🎨 **Skins** - six selectable looks, each its own palette and render style, **all free from the
  start**, picked in Settings from **live preview cards**: each card shows the skin's snake slithering in
  place, drawn through the real gameplay renderer so its animated body material previews exactly as it
  plays. **Retro** is the default. The glow skins (Classic / Neon / Aurora / Ember) draw food as
  haloed round pieces; the flat skins (Retro / Pixel) render food as squares (crisp on Pixel, lightly
  rounded on Retro). Each skin also has its **own snake body**: Classic a glossy tapered tube, Retro
  chiselled blocks, **Pixel** a chain of 5x5-pixel sprite tiles in classic 80s arcade colours (blue
  bevelled pieces with a brass button, led by a capped 8-bit hero head that turns with the snake),
  **Neon** a hollow glowing neon tube with a pulsing filament, **Aurora** a ribbon
  whose teal-to-violet hues flow along its length, and **Ember** dark rock veined with pulsing molten lava
  that runs hottest at the head. Power-up / hazard pieces are drawn as **premium bevelled tokens** whose
  material changes per skin (glossy enamel, hollow neon tube, warm phosphor, hard pixel tile, frosted
  glass, molten iron), while each effect keeps a constant identity colour and symbol so its meaning never
  shifts between skins.
- 🏞️ **Board terrains** - six selectable animated floors for the board, independent of the skin
  (picked in Settings right under the skins, with live animated preview cards). **Meadow** (a mowed
  two-tone lawn under drifting cloud shadows) is the out-of-the-box default; **Arcade** plays on the
  skin's own dark gradient (with its drifting glows), or swap the stage entirely: **Abyss** (a
  deep-ocean floor lit by animated caustics and faint light shafts), **Nebula** (a twinkling star
  field over slowly drifting nebula wisps), **Dunes** (a night desert with moonlit dune crests and
  sparkling sand) and **Glacier** (a frozen lake veined with bright cracks, an internal drifting
  sheen and cool glints). Every terrain is an AGSL shader, kept calm and slowly animated so the
  snake, food and obstacles stay perfectly readable; the board's frame - and the Campaign gates'
  energy colour - take the selected terrain's accent (the skin's own border on Arcade). The terrain
  also **themes the whole interface**: the menus play its animated floor as their backdrop (under a
  soft dark scrim) and seed the app's accent colour from it - Meadow keeps the classic green, Abyss
  turns the chrome cyan, Nebula lavender, Dunes amber, Glacier ice - cross-fading smoothly when you
  switch.
- 🌗 **Theme** - choose **Light**, **Dark** or **System** (follows the device) in Settings.
- 🔊 **Music & sound effects** - looping background music that crossfades between the menu and
  gameplay, plus SFX for eating, shrinking, mystery pieces, game over and UI. Independent
  **master / music / SFX** volume sliders in Settings; audio pauses when the app is backgrounded.
- 📳 **Haptics & near-miss feedback** - vibration cues scaled by event (a light tap on eating, a
  firmer click on power-ups, the strongest buzz on death) plus a faint **near-miss** tick and a brief
  **danger flash** traced along the board's actual frame - sharp corners flush with the border, the
  terrain's accent colour, and on shaped Campaign boards it follows the real outline - whenever the
  head grazes a wall, obstacle or debris without crashing.
  Toggle it off with **Vibration feedback** in Settings.
- 🔥 **Combo "juice"** - chain bites for a multiplier and the HUD counter punches in and warms through a
  colour ramp while the snake's head **catches fire** (its glow heats from your skin's colour toward a
  fiery orange-red) as the streak climbs.
- 👻 **Ghost replay** - race a translucent "ghost" of your own best run, retracing its path tick-for-tick
  alongside the live snake (in **Endless**, **Time Attack** and **Zen**). It fades in at the start and
  out the moment you outlast it. **Off by default** - switch **Ghost of your best run** on in Settings
  when you want something to chase.
- 👁️ **Reduce motion & flashing** - an accessibility toggle in Settings that damps the screen shake, the
  particle bursts and the near-miss flash for a calmer, flash-free board.
- ✨ **Rich 2D visuals** - the snake renders in each skin's own body style (see **Skins** - glossy tube,
  chiselled blocks, hollow neon, flowing aurora ribbon or molten lava), food is drawn with top-lit
  gradients and soft shadows, and eating
  pops a shockwave ring with **combo-reactive** sparks (the burst grows hotter and bigger as your streak
  climbs); the Explosion hazard sets off a fiery two-tone detonation.
- ✨ **GPU shader effects** - an animated background, a glowing snake head and pulsing
  halos on rare foods, all via **AGSL** `RuntimeShader`s, plus an optional **retro CRT filter**
  toggle in Settings.
- ⚡ **Power-ups & hazards** - rare maxi pieces that appear later in a run: **Lightning** (speed up),
  **Snail** (slow down), **Star** (invincible pass-through; the snake blinks faster as it runs out),
  **Freeze**, **Jackpot** (big bonus),
  plus the hazards **Earthquake** (a sustained screen shake for a few seconds that makes the board hard
  to read - no debris, your length is untouched) and **Explosion** (severs the last third of the snake,
  leaving the detached tail as lethal debris that lingers for several seconds). Hazards wear a notched
  **danger bezel** and, the moment before you would eat one, flash a **danger telegraph** (with a
  pre-haptic) so a strike never feels arbitrary - the flash respects the **Reduce motion** toggle. Active
  effects show countdown chips; up to **two specials** can share the board at once. Toggle **Hazards**
  off in Settings for a calmer run, or dial how often specials appear with the **Special blocks**
  setting (*Standard / Frequent / Frenzy*) - the higher tiers also bring specials online earlier in a
  run. **Time Attack** adds two exclusive clock pieces: a green **+5s** bonus and a red **−3s**
  penalty, each with a floating callout.
- 🏆 **Records screen** - a best-score table per difficulty × board scale (and per mode), reachable
  from the main menu.
- 🎖️ **Achievements on a career ladder** - thirty-eight local milestones grouped into **five ranks**
  (*Hatchling → Forager → Stalker → Constrictor → Mythic*). A rank reveals once you have earned enough
  badges in total, so the list opens up as you play instead of dumping every late-game goal on you at
  launch - and no single stubborn badge can ever wall you out, because the gate is a count, not a
  clean sweep. The Achievements screen leads with your rank and a bar toward the next one; sealed ranks
  say what they cost and how many feats they hide; a promotion is celebrated on the game-over screen.
  The length badges count the segments you **earned by eating**, never the ones auto-growth handed you,
  so they mean the same thing at every growth setting. A handful are deliberately hard: score big while
  staying under twenty segments (*Featherweight*), grow sixty segments without ever trimming
  (*Purist*), or last three minutes - and score 5000 - with the growth dial at *Relentless*
  (*Unbowed*, *Apex Predator*).
- 📊 **Run recap** - the game-over screen shows a short summary of the run: foods eaten, best combo, time
  survived, the snake's longest length, the segments grown from food and trimmed away, and, in Campaign,
  the deepest level reached.
- 🎯 **Daily missions** - three rotating per-run goals (eat so many foods, reach a combo, survive a time,
  score, grow segments out of food, trim segments away, grab a power-up) that refresh each day. The main menu's **Today's Missions** strip
  tracks which you've cleared today (tap it for the full list), and the **game-over screen** shows the
  day's missions with a tick on the ones done, highlighting any you just cleared.
- ▶️ **Quick Play** - the main menu's **Play** button drops you straight into a run with your last-used
  settings; a separate **Custom** entry opens the full pre-game setup (mode, level, snake speed,
  growth, board scale) when you want to tweak everything. Every setting is a labelled gauge with its
  value and a one-line explanation of what it changes, and the whole screen fits without scrolling.
- 📅 **Daily Challenge** - a date-seeded run with the same mode, level, board and **daily twist** for
  everyone that day (the twist rotates through **nine** flavours - Bonus Rush, Frenzy, Compact Arena,
  Grand Arena, Maxi Feast, Combo Rush, Overdrive, Old School or plain Standard - each described on the
  Daily card; the obstacle layout and food sequence come from the day's seed). Beat your **best today** and build a **day streak**.
  A **This Week** screen shows your last 7 days of
  Daily results and a weekly best / total; tap any day there to **replay** that day's exact challenge -
  just for fun, your recorded results are never overwritten. Reached from the main menu.
- 🎲 **Random Challenge** - a one-off surprise run for variety: **Shuffle** for a fresh mode / level /
  board / twist mix, then play. Nothing is recorded - it's pure fun.
- 🕹️ **Game modes** - **Endless** (the default: the pace climbs through announced **speed tiers**
  the longer you survive - each step flashes a *"Speed N!"* banner, a golden board flare and a zap,
  with the live tier in the HUD; harder difficulty levels start the ramp hotter), **Time Attack**
  (score as much as you can in 120s - your chosen pace sets a declared **score multiplier**, up to
  ×1.5 at Turbo, and the final 20 seconds are **Fever Time**: double points under an amber board
  glow while the music speeds up), **Campaign** and **Zen** (see below), selectable on the
  **Custom** setup screen. Beating your stored best mid-run pops a live **"New record!"**
  celebration in any mode.
- 🧩 **Campaign mode** - fifteen **designed board shapes** (cut corners, pillars, chambers, a vault,
  and a tougher late-game gauntlet…) that repeat forever, one **speed step faster** each lap. Eat **12 foods** to clear a level; you
  start with **3 lives** (a crash respawns you in the same level, keeping score and progress) and a
  rare 2×2 **extra-life** piece with a snake-head icon can bank more (up to 5). Every transition
  plays an animated banner with a 3-second countdown showing your progress through the lap
  (*"Level 3/15"*) plus the speed lap. The Level and Snake speed
  selectors are disabled here - the mode has its own layouts and pace - and the Records screen tracks both your best
  score and the deepest level you reached per board scale.
  - 🚩 **Checkpoints** - every level you reach stays unlocked as a starting point: a **Start level**
    selector appears on the setup screen once you get past level 1, so you can jump straight to the
    later designed boards. Starting past level 1 is a **practice run** - great for learning a level,
    but records and Campaign achievements only count from a Level 1 start (the game says so up front).
  - 🚧 **Environmental hazards** - some levels add **moving-wall gates**: glowing energy barriers,
    tinted to match the selected board terrain, that
    open and close on a rhythm (they strobe a warning before slamming shut, so time your dash through),
    and **teleport portals** - step onto one swirling pad to instantly emerge at its partner across the
    board. Gates are lethal only while closed and never seal you in; portals open up bold shortcuts.
- 🧘 **Zen mode** - the calm way to play: a **borderless arena** where the board is a torus - the
  snake slides off one edge and glides back in from the opposite one (a smooth, continuous
  crossing, never a teleport-style jump). **No obstacles, no hazards, no power-ups**: just the food
  progression, a **fixed pace you choose** (it never ramps) and the one rule that matters - don't
  bite your own body. The combo window is stretched so an unhurried eating rhythm keeps the streak
  alive (flow over frenzy). Instead of a solid frame, the board edges are a **breathing veil** - a
  soft teal mist fading inward with a slowly drifting dashed stitch - so you can *see* the boundary
  is permeable, and the run plays the calmer menu soundtrack. Perfect for a five-minute wind-down; records are kept
  per board scale, and three dedicated achievements (*Inner Peace*, *Ouroboros*, *Eternal Flow*)
  reward long flows.
- ⏸️ **Pause & menus** - pause overlay with a blur effect; restart or return to the main menu at any time. Resuming never catches you off guard: a **3-2-1 countdown** plays over the fully visible board while a **locator beacon** pulses around the snake's head with a chevron pointing where it will move, so you re-find the snake and plan the first turn before motion restarts. Highscores are kept per (mode, level, board scale). A **Back during play** setting chooses what the system Back gesture does mid-game: **Keep playing** (default - Back is ignored, and a swipe-back is fed to the snake as a turn when using swipe controls) or **Pause**. And a run can never be lost by accident: pressing Back while paused asks **"Quit this run?"** before abandoning it.
- 💎 **Polished navigation** - an **animated GPU background** behind the menus, a **branded main menu** laid out as a "game launcher" (a glowing wordmark with a small in-game-style snake emblem that follows your selected skin as the hero, over a bottom-anchored cluster of actions grouped by type so everything fits one screen), **premium action buttons** (gradient-lit, with a tactile press), and **blur-dissolve** screen transitions.
- ⏸️ **Auto-pause** - backgrounding the app mid-run pauses the game automatically, so the snake never keeps moving while you're away.
- 📜 **Credits screen** - an in-app **Credits / About** page (author, license and asset attribution), reachable from the main menu.
- 📖 **In-app Guide** - a rules reference on the main menu (between the gear and the info dot), in
  collapsed chapters you open when you want the detail: the basics, length / risk / Shed, food,
  power-ups, the four modes, the Endless waves, scoring, controls and progress. **Every number in it is
  read from the game's own constants**, so the guide cannot drift out of date when the balance is tuned.
- 🧭 **First-run tour** - a premium, skippable six-card tour on first launch, re-openable any time via **How to play** in Settings. Glass cards over the animated brand backdrop cover the goal (with the real in-game snake slithering in your skin), the food language, **length as a resource** (the growth clock, the risk bonus and the Shed button), the power-ups / hazards, the four game modes and the daily loop (Daily Challenge, missions, achievements, skins) - legends show the actual in-game pieces, icons and colours, steering is a glanceable three-chip row, and Back pages backwards instead of bailing out.

### 🍽️ Food system at a glance

| Category | Tiers (standard growth/shrink) | Maxi (2×2) | Mystery "?" | Score |
|----------|-------------------------------|------------|-------------|-------|
| 🟢 **Grow**   | +1 / +2 / +3 / +4 | doubles the amount | random +1…+12 | `+20 × growth × combo × risk bonus` |
| 🟠 **Shrink** | −2 / −3 / −5      | doubles the amount | random −2…−14 | `(5 / 10 maxi) × risk bonus` |

The snake never shrinks below **3 segments**. Grow food drives the score, scaled by the combo
multiplier **and by the risk bonus** - the more of the board your body covers, the more each bite is
worth (up to ×5), so a loaded board pays off more and more right up to the moment it kills you.

Since the body now grows on its own (see *Auto-growth*), a grow piece adds **half** the length it
used to while paying exactly the same score, and a shrink piece out-trims what a comparable grow
piece adds: trimming is a real play, not a last resort, and its token points scale with the length
you cut. Eating either floats the amount of segments gained or lost (**+N** / **−N**) at the food.

A shrink piece can never cut more than **30% of your current length**. The cap is shaped so it only
bites when you are already short: at 60 segments it allows 18 - more than the biggest piece in the
table, so a lucky find is worth every bit of what it looks like when you are in real trouble - while
a couple of big pieces can no longer dump a long snake straight back to the minimum. Length is a
resource you manage, not a switch you flip.

### ⚔️ Levels (obstacles)

The **Level** sets how many obstacles are placed and, in **Endless**, how hot the speed ramp starts
(a per-level tier head start, so Legend is faster from the first tick as well as denser); the
selectable pace (see *Snake speed* below) stays independent.

| Level | Name        | Obstacles (Cozy) | Endless ramp starts at |
|-------|-------------|------------------|------------------------|
| 1     | Beginner    | 0                | Speed 1                |
| 2     | Adventurer  | 8                | Speed 2                |
| 3     | Warrior     | 15               | Speed 3                |
| 4     | Champion    | 25               | Speed 4                |
| 5     | Legend      | 40               | Speed 5                |

The counts above are tuned for the smallest (Cozy) board; on larger board scales they are **scaled
up with the board's area** so the obstacle density stays constant instead of thinning out (e.g. Epic,
at ~2× the short side, gets ~4× the obstacles).

Obstacles are laid out with **4-fold symmetry** (mirrored left/right and top/bottom), with a clear
margin next to every wall and a clear zone around the snake's spawn. New blocks are biased towards
growing next to ones already placed, so they tend to form larger clumped shapes instead of
scattering as isolated cells.

### 🏃 Snake speed

A separate setting (shown under *Level* on the **Custom** setup screen) controls the
pace, independent of the obstacle layout. It applies to **Time Attack**, where it also sets a
declared **score multiplier** - a faster snake covers more board in the fixed 120 seconds, so the
pace choice is an open risk/reward dial and every record slot stays fair - and to **Zen**, where it
simply fixes the run's rhythm (no multiplier, no ramp). Endless ramps its own pace and Campaign
uses its per-lap speed cycle.

| Speed | Name    | Tick (ms) | Time Attack score |
|-------|---------|-----------|-------------------|
| 1     | Relaxed | 175       | ×1                |
| 2     | Steady  | 150       | ×1.1              |
| 3     | Brisk   | 125       | ×1.2              |
| 4     | Rapid   | 100       | ×1.35             |
| 5     | Turbo   | 75        | ×1.5              |

### 🐍 Auto-growth

Your snake gets longer **whether or not you eat**. Every so many steps it keeps its tail instead of
dropping it, so the body is a clock made physical: a run always builds toward a finish, and the
question stops being "can I be careful forever?" and becomes "can I keep up?". The HUD carries a
small ring beside the snake's live length that fills toward the next free segment.

The **Growth** dial sits on the **Custom** setup screen and applies to every mode. Because a faster
growth is strictly harder, each step declares a **score multiplier** (the same open risk/reward
contract the Time Attack pace uses), so records stay on their existing (mode × level × scale) slots
and nothing you already earned is orphaned.

| Growth | Name       | Steps per free segment | Score |
|--------|------------|------------------------|-------|
| 1      | Off        | never                  | ×1    |
| 2      | Gentle     | 24                     | ×1.1  |
| 3      | Steady     | 16 *(default)*         | ×1.25 |
| 4      | Brisk      | 10                     | ×1.5  |
| 5      | Relentless | 6                      | ×1.8  |

At *Relentless* that is about a segment a second at the relaxed pace. The step counts above are for
the **Explorer** board; they scale with the board's size, so a Colossal
arena grows the snake more often and a Cozy one less (filling a big board takes far more length than
choking a small one). **Zen** stretches the interval further - the calm mode still has to end, but it
must never feel like a race - and a **Campaign** respawn or level change restarts the clock with the
snake. The seeded **Daily / Random** challenges pin *Steady* for everyone, like they already pin the
pace and the hazard toggles.

### 🎲 Risk and the Shed ability

The score does not care how long your snake is - it cares **how much of the arena it covers**. The
multiplier climbs from ×1 on an empty board to ×5 once the body fills a fifth of the playable cells
(obstacles and Campaign walls count against that area, so a cluttered board heats up sooner). The
same forty segments are a fortune on a Cozy board and pocket change on a Colossal one, which is
exactly right.

That turns auto-growth from a punishment into a wager, and the **Shed** ability is how you settle it.
Ten bites charge it - five if you keep a combo alive - and spending it cuts 35% of the tail loose for
a payout scaled by the risk you were carrying. Hold on longer and the cut pays more; hold on too long
and you never get to make it.

| | |
|---|---|
| Risk multiplier | ×1 → ×5, tracking body ÷ playable cells (capped at 20% fill) |
| Shed charge | 10 bites, or 5 on a live combo |
| Shed cut | 35% of the body, tail first |
| Shed payout | 8 points per segment × the risk multiplier |

### 🌊 Endless waves

An Endless run plays undisturbed for 45 seconds, then a **wave** sweeps the board for 12 seconds, and
another every 45 seconds after that. They rotate in a fixed order on purpose: a rhythm you can learn
is a rhythm you can plan around.

| Wave | What happens |
|---|---|
| 🍽️ **Feast** | The board floods with food (nine pieces at once). Gorge - or hold the length and let the risk bonus run. |
| 🏜️ **Drought** | Food dries up to a single piece. The growth clock keeps ticking, so it is pure survival. |
| 🧊 **Hailstorm** | Chunky 2×2 **ice stones** rain down and melt away. None lands within four cells of your head, so it is a route to solve, not an ambush. |

Every wave is announced by name and counts down in the HUD's timer row, in its own colour, alongside
any power-up timers.

### 📐 Board scale

The board is **responsive**: pick a granularity and the grid is computed from your device's play-area
aspect ratio so the board fills the screen with square cells. The preset count is applied
to the **short side**, so the cell size - and the feel - stays consistent across different screen
sizes (a tablet gets the same density as a phone, not a squashed few-row board).

| Scale    | Cell size  | Cells on short side |
|----------|------------|---------------------|
| Cozy     | larger     | 13                  |
| Explorer | medium     | 19                  |
| Epic     | smaller    | 27                  |
| Colossal | smallest   | 35                  |

The counts are odd on purpose: the board gets a true middle column, so the snake's centred spawn
lines up exactly with centred overlays (like the Campaign-mode countdown).

---

## 🛠️ Requirements & tools

Install on your development machine:

- **Android Studio** (latest stable) - bundles the JDK (JBR), the SDK Manager and the AVD emulator.
- **Android SDK** via the SDK Manager: **Platform API 36**, **Build-Tools 36.x**, **Platform-Tools** (`adb`),
  **Emulator** + a system image (e.g. API 36).
- A **test target**: an AVD emulator or a physical device with **USB debugging** enabled.
- **Gradle**: not needed globally - the project ships the **Gradle wrapper** (`./gradlew`).

The project targets `minSdk 33` (Android 13) and `compileSdk`/`targetSdk 36` (Android 16) - a modern
baseline so AGSL GPU effects and other recent APIs are available without fallback code.

---

## 🚀 Build & run

1. **Clone**:
   ```bash
   git clone https://github.com/fiorenzobrioni/snake-game.git
   ```
2. **Open the repository root** in **Android Studio** and let Gradle sync.
3. **Run** on an emulator or a connected device (▶ Run, or):
   ```bash
   ./gradlew installDebug      # build + install the debug APK
   ./gradlew assembleDebug     # build the debug APK only
   ```

> The Android SDK location is read from `local.properties` (created by Android Studio) or the
> `ANDROID_HOME` environment variable.

> **Debug signing.** Debug builds are signed with the shared keystore committed at
> `keystore/debug.keystore` (alias `androiddebugkey`, password `android`), not with each machine's
> personal `~/.android/debug.keystore`. Every build - local, CI or the APK attached to a release -
> therefore carries the same signature, so a newer APK installs **over** the previous one and keeps
> your scores and settings. A debug certificate has no publishing value, which is why committing it
> is safe; release signing is a separate, never-committed keystore (Phase 7).

---

## 🎮 How to play

Guide the snake around the board, eat food to score, and avoid the walls, the obstacles and your
own body. Your snake also lengthens on its own as the run goes on, so playing safe is not a strategy -
you have to keep eating, and eat the *right* pieces.

**Food:** green = grow, warm/orange = shrink, "?" = a mystery amount. Bigger (2×2 maxi) and mystery
pieces only start appearing as the session runs on - so each run gets more eventful. Chain bites
together to build a **combo** and multiply your score. Remember the snake also grows **on its own**
(see *Auto-growth*), so shrink food is how you keep room to manoeuvre - you never drop below 3
segments.

**The Shed button:** the ring in the board's bottom corner fills as you eat and lights up when it is
charged. Tap it to cut your tail loose for a payout - it is deliberately unclickable while charging,
so a stray tap during play costs nothing and still reaches the board if you steer by tapping, and it
fades out of the way in good time before your snake reaches that corner (still tappable while faded).

**Need the details?** The **Guide** button on the main menu (between the gear and the info dot) opens
the full rules reference: the growth dial's numbers, the risk cap, what a Shed cuts and pays, every
power-up, the wave schedule, the scoring formula. Its figures are read straight from the game's own
constants, so they always match what you are playing.

**Controls (touch):** by default you **swipe** anywhere on the board to change direction, with an
adjustable **swipe sensitivity** in Settings (the default keeps the tuned feel). Prefer buttons? Switch
in **Settings** to a compact, premium **wedge dial**: a single skin-tinted key split by its diagonals
into four directional wedges (up / right / down / left) around a dead-zone hub, so your thumb barely
moves between turns and the board keeps more height. For one-handed play there is also a **tap-to-turn** scheme: tap the left half of the board to
turn left, the right half to turn right. Your choice is saved. 180° reversals are blocked, so you can't
instantly fold back into your own body. Tap **Play** on the main menu to start instantly with your
last-used settings, or **Custom** to pick the mode, level, snake speed, growth and board scale first; pause and
restart from the in-game controls. Your best score is kept per (mode, level, scale).

**Audio:** the game plays looping background music (it crossfades between the menu and gameplay) and
sound effects for eating, shrinking, mystery pieces, game over and button taps. Tune the **master**,
**music** and **SFX** volumes independently in **Settings** (set any to zero to mute); the music
automatically pauses when you leave the app and yields to other apps' audio.

**Game modes:** choose your mode on the **Custom** setup screen -
**Endless** (the snake keeps accelerating through announced speed tiers the longer you survive, and
every 45 seconds a wave sweeps the board - Feast, Drought or Hailstorm; the default), **Time Attack** (score as much as possible in 120 seconds - your pace sets a declared
score multiplier, the exclusive **+5s** / **−3s** clock pieces stretch or shave your remaining time,
and the last 20 seconds are **Fever Time** with double points), **Campaign** (clear fifteen shaped
boards by eating 12 foods each, with 3 lives, an exclusive 2×2 extra-life piece, a speed-up every
completed lap and **checkpoint starts** from any level you have reached - the HUD shows
*Level x - Speed x*, your hearts and the foods still to go), **Zen** (a calm borderless torus: the
snake wraps through the edges, nothing spawns but food, the pace you pick never ramps, and only
your own body can end the run). Your best score is tracked per mode, level and board scale; check
the **Records** screen from the main menu.

**Power-ups & hazards:** as a run progresses, rare special pieces start appearing on the board.
Power-ups help: **Lightning** speeds the snake up, **Snail** slows it down, **Star** grants brief
invincibility (you can pass through walls, obstacles and your own body - the snake blinks as the
effect fades), **Freeze** pauses further specials for a strategic breather, and **Jackpot** grants a
large score bonus. Hazards hinder: **Earthquake** sets off a sustained screen shake for a few seconds
that makes the board hard to read (it leaves no debris and your length is untouched); **Explosion**
severs the last third of the snake - the detached tail turns into lethal debris that lingers for several
seconds before it auto-clears. Every hazard wears a dashed **caution ring**, and the tick before you
would eat one the board flashes a **danger telegraph** over it (with a short pre-haptic), so a strike is
always announced; the flash honours the **Reduce motion** toggle. Active effects show
a countdown chip in the HUD. **Time Attack** also has two clock-only pieces - a **+5s** bonus and a
**−3s** penalty. Toggle **Hazards** off in **Settings** for a calmer run (this also hides the time
penalty), or raise **Special blocks** to *Frenzy* for constant chaos.

**Achievements:** milestones unlock automatically as you play - high combos, long runs, using
power-ups, building length out of food, trimming a snake back down, and more. They sit on a **five-rank
ladder**: each rank reveals once you have earned enough badges overall, so the goals arrive a handful
at a time instead of all at once, and reaching a new rank is celebrated on the game-over screen. A
banner also appears whenever a single badge unlocks; browse your rank and the whole ladder from the
main menu.

**Daily missions:** alongside the static achievements, three goals rotate each day (eat a number of
foods, reach a combo, survive a time, hit a score, grow segments out of food, trim segments away, or
grab a power-up). They give a single
run a sense of purpose: the main menu's **Today's Missions** card shows which you've cleared today, and
completing one pops a banner on the game-over screen. The set refreshes the next day.

---

## 👨‍💻 Code layout

```
app/src/main/kotlin/com/callbackdev/snake/
├── MainActivity.kt     # Compose entry point
├── game/               # pure-Kotlin game model (no Android imports → unit-testable)
├── ui/                 # Compose UI + Material 3 theme
├── audio/              # SoundPool SFX + MediaPlayer music, behind the GameAudio facade
└── data/               # DataStore persistence (settings, highscores)
```

The sound effects in `app/src/main/res/raw/` are original CC0 clips generated by
[`tools/audio/generate_audio.py`](tools/audio/generate_audio.py) - re-run it to reproduce them. The
background music tracks (`music_menu.ogg`, `music_game.ogg`) are generated with Google Gemini (see
[Media assets & credits](#-media-assets--credits)). The AGSL shaders live in
[`ui/game/Shaders.kt`](app/src/main/kotlin/com/callbackdev/snake/ui/game/Shaders.kt).

For architecture notes, conventions and the file map, see [`CLAUDE.md`](CLAUDE.md).

---

## 🎵 Media assets & credits

The app includes a **Credits** screen, reachable from the main menu, summarizing authorship and asset
attribution. In short:

- **Author** - published under the **Callback Dev** name; copyright Fiorenzo Brioni. Released as free
  software under the **GNU GPL v3.0**.
- **Music** - the looping menu and gameplay tracks are **generated with Google Gemini** (Lyria), used
  in accordance with [Google's generative-AI terms of service](https://policies.google.com/terms/generative-ai).
  They are bundled as OGG/Vorbis and post-processed in-repo (silence trimmed and an equal-power
  self-crossfade baked in) so they loop seamlessly. As aggregated assets they sit alongside - and do
  not affect the license of - the GPL-3.0 source code.
- **Sound effects** - original, synthesized in-repo (CC0) by
  [`tools/audio/generate_audio.py`](tools/audio/generate_audio.py).
- **Fonts** - Orbitron (SIL Open Font License 1.1).
- **Graphics & shaders** - original, hand-written in-repo.
- **Built with** - developed with Google Antigravity and Claude Code.

Full per-asset details and licenses are tracked in [`docs/CREDITS.md`](docs/CREDITS.md).

---

## 🧭 Planning & Roadmap

The full development plan - from foundations through gameplay, visual polish, audio, shaders, content, and
**Google Play distribution** - as well as active TODOs, bugs, and architecture notes, is in [`PLANNING.md`](PLANNING.md).

---

## 🏛️ Legacy - the desktop prototype

This project began as a **learning exercise**: a Snake built in **C# / .NET 10 / Windows Forms** with **GDI+**
rendering, released as its own desktop **v1.0.0**. That desktop version is **frozen** and preserved under
[`legacy/SnakeGame/`](legacy/SnakeGame/) as a reference for the game model. See
[`legacy/README.md`](legacy/README.md) for its build notes.

The native Android app described above is the project's active direction, and its **first release is
Snake `1.0.0` for Android** (a fresh, independent rewrite - not a continuation of the desktop prototype's
version line).

---

## 📄 License

Copyright (C) 2026 Fiorenzo Brioni

This project is free software: you can redistribute it and/or modify it under the terms of the
**GNU General Public License v3.0** as published by the Free Software Foundation.

Distributed in the hope that it will be useful, but **without any warranty**; without even the
implied warranty of merchantability or fitness for a particular purpose.
See the [LICENSE](LICENSE) file for the full terms.
