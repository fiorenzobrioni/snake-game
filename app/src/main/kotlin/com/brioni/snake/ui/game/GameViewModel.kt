package com.brioni.snake.ui.game

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.brioni.snake.audio.GameHaptics
import com.brioni.snake.audio.GameSfx
import com.brioni.snake.data.DEFAULT_SWIPE_SENSITIVITY
import com.brioni.snake.data.SettingsRepository
import com.brioni.snake.game.Achievement
import com.brioni.snake.game.BackBehavior
import com.brioni.snake.game.BoardDimensions
import com.brioni.snake.game.BoardScale
import com.brioni.snake.game.BoardTerrain
import com.brioni.snake.game.ControlScheme
import com.brioni.snake.game.Challenge
import com.brioni.snake.game.EffectKind
import com.brioni.snake.game.DEFAULT_ASPECT
import com.brioni.snake.game.Direction
import com.brioni.snake.game.FoodEffect
import com.brioni.snake.game.GameEngine
import com.brioni.snake.game.GameEvent
import com.brioni.snake.game.GameMode
import com.brioni.snake.game.GameState
import com.brioni.snake.game.GameStatus
import com.brioni.snake.game.Level
import com.brioni.snake.game.LevelsMode
import com.brioni.snake.game.Mission
import com.brioni.snake.game.Position
import com.brioni.snake.game.RunStats
import com.brioni.snake.game.Skin
import com.brioni.snake.game.SnakeSpeed
import com.brioni.snake.game.ZenMode
import com.brioni.snake.game.SpecialFrequency
import com.brioni.snake.game.boardFor
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.random.Random
import kotlin.math.max
import java.time.LocalDate

/** Which particle burst the renderer should spawn for an [EatEvent]. */
enum class BurstStyle {
    /** Outward spray — growing food, jackpot. */
    Eat,

    /** Inward implosion — shrinking food. */
    Implode,

    /** Gentle upward fade — an ignored food that timed out and vanished. */
    Vanish,

    /** A big, fiery two-tone detonation with embers — the Explosion hazard. */
    Blast,
}

/**
 * A board event, surfaced to the renderer to spawn a particle burst. [style]
 * selects which burst to play at [cell]; [combo] lets the renderer make the
 * burst grow hotter and larger as the eat-streak climbs.
 */
data class EatEvent(
    val cell: Position,
    val span: Int,
    val color: Color,
    val style: BurstStyle,
    val combo: Int = 1,
)

/**
 * A short floating label to spawn on the board (e.g. "+5s" / "-3s" for the Time
 * Attack clock blocks), placed at [cell] (its [span] centres the text).
 */
data class FloatingTextEvent(val cell: Position, val span: Int, val text: String, val color: Color)

/**
 * A one-tick hazard telegraph: the renderer flashes a danger warning over the
 * [span]-cell square at [cell] the tick before the snake would eat the hazard.
 */
data class HazardWarnEvent(val cell: Position, val span: Int)

/**
 * A portal jump (Step 6.9.7): the head left pad [from] and emerged at [to]. The
 * renderer flashes both pads and plays an implode-at-entry / burst-at-exit pair.
 */
data class TeleportEvent(val from: Position, val to: Position)

/** Which run-level announcement the HUD banner should flash. */
enum class BannerKind {
    /** Time Attack: Fever Time just started — double points until the end. */
    Fever,

    /** Endless: the speed ramp stepped up; the banner shows the new tier. */
    SpeedUp,

    /** The live score just passed the stored best for this slot. */
    NewRecord,
}

/**
 * A short, centred announcement flashed over the board ([kind] selects the text
 * and colour; [value] carries the Endless speed tier). Surfaced with a monotonic
 * id so repeats are observable.
 */
data class BannerEvent(val kind: BannerKind, val value: Int = 0)

/**
 * A whole-snake particle effect to play over the body [cells] (head first). Used
 * for the two transition flourishes: the snake bursting apart on death
 * ([BurstStyle.Blast]) and dissolving away on a Campaign level-up
 * ([BurstStyle.Vanish]). The renderer staggers the per-cell bursts head-to-tail
 * and fades the snake out as they fire.
 */
data class BodyBurstEvent(val cells: List<Position>, val style: BurstStyle)

/**
 * Shared timing of the whole-snake transition burst ([BodyBurstEvent]). The
 * renderer staggers one per-cell burst head-to-tail, so the animation's length
 * grows with the snake: the ViewModel holds the game-over overlay / next-level
 * countdown for [totalMs] while the renderer paces its emissions with
 * [stepDelayMs] and fades the body over [dissolveMs]. Both sides derive from
 * the same cell count, so the overlay can never cut the animation short.
 */
object BodyBurstTiming {
    /** Gap between two adjacent per-cell bursts, walking head to tail. */
    fun stepDelayMs(cells: Int): Long =
        (EMISSION_TARGET_MS / cells.coerceAtLeast(1)).coerceIn(MIN_STEP_MS, MAX_STEP_MS)

    /** When the tail cell's burst fires, relative to the start of the effect. */
    fun emissionMs(cells: Int): Long = cells.coerceAtLeast(1) * stepDelayMs(cells)

    /** The body fade-out window: outlives the last emission by a settling beat. */
    fun dissolveMs(cells: Int): Long = emissionMs(cells) + DISSOLVE_TAIL_MS

    /** Full hold before the overlay/countdown: lets the tail burst play out. */
    fun totalMs(cells: Int): Long = emissionMs(cells) + BURST_TAIL_MS

    /** The head-to-tail emission window the stagger aims for on mid-size snakes. */
    private const val EMISSION_TARGET_MS = 540L

    /** Stagger bounds: a long snake still ripples (>= 5 ms), a short one isn't instant. */
    private const val MIN_STEP_MS = 5L
    private const val MAX_STEP_MS = 36L

    /** How long the body keeps fading after the tail cell has burst. */
    private const val DISSOLVE_TAIL_MS = 350L

    /** Covers the visible life of the tail burst's sparks (~0.4-0.8 s). */
    private const val BURST_TAIL_MS = 800L
}

/**
 * A compact recap of a finished run, surfaced to the game-over overlay
 * (Step 6.9.2). [deepestLevel]/[deepestSpeed] are only meaningful for Campaign
 * ([isCampaign]); the overlay hides that row in the other modes.
 */
data class RunSummary(
    val foodsEaten: Int,
    val maxCombo: Int,
    val durationMs: Long,
    val maxLength: Int,
    val isCampaign: Boolean,
    val deepestLevel: Int,
    val deepestSpeed: Int,
)

/** A day's mission with its status after a run, for the game-over panel. */
data class MissionProgress(
    val description: String,
    val done: Boolean,
    val justCompleted: Boolean,
)

/**
 * Holds the [GameState] and drives the tick loop. All game rules live in
 * [GameEngine]; this class owns the timing coroutine, surfaces state to Compose
 * and publishes the data the renderer needs for inter-tick interpolation
 * ([previousSnake] + [tickTimeNanos]) and effects ([eatEvent]/[deathEventId]).
 *
 * Player preferences (level, board scale, control scheme) and highscores are
 * read from and written back to [repo]; the board's concrete dimensions are
 * computed from the selected [scale] and the measured play-area aspect ratio.
 */
class GameViewModel(
    private val repo: SettingsRepository,
    private val sfx: GameSfx = GameSfx.None,
    private val haptics: GameHaptics = GameHaptics.None,
) : ViewModel() {

    private var engine = GameEngine()

    /** The selected granularity preset; concrete dims derive from it + the aspect. */
    var scale by mutableStateOf(DEFAULT_SCALE)
        private set

    /** The selected snake pace (loaded from settings); drives the tick interval. */
    var snakeSpeed by mutableStateOf(SnakeSpeed.DEFAULT)
        private set

    /** Active steering scheme (loaded from settings). */
    var controlScheme by mutableStateOf(DEFAULT_CONTROL)
        private set

    /** Swipe steering sensitivity (0..1; loaded from settings). */
    var swipeSensitivity by mutableFloatStateOf(DEFAULT_SWIPE_SENSITIVITY)
        private set

    /** What the Back gesture does during active play (loaded from settings). */
    var backBehavior by mutableStateOf(BackBehavior.DEFAULT)
        private set

    /** Whether the retro CRT post-filter is enabled (loaded from settings). */
    var crtEnabled by mutableStateOf(false)
        private set

    /** Accessibility: damp screen shake, particle bursts and near-miss flashes (setting). */
    var reduceMotion by mutableStateOf(false)
        private set

    /** Active visual skin (loaded from settings); drives the renderer [palette]. */
    var skin by mutableStateOf(Skin.Retro)
        private set

    /** The board's animated backdrop (loaded from settings), independent of [skin]. */
    var terrain by mutableStateOf(BoardTerrain.Meadow)
        private set

    /** Whether harmful specials (earthquake/explosion/snail) may spawn (setting). */
    var hazardsEnabled by mutableStateOf(true)
        private set

    /** How often specials (power-ups / hazards) spawn (setting). */
    var specialFrequency by mutableStateOf(SpecialFrequency.Standard)
        private set

    /** Active play mode; highscores are tracked per (mode, level, scale). */
    var mode by mutableStateOf(GameMode.Endless)
        private set

    /** The colour palette + style flags for the active [skin]. */
    val palette: SkinPalette get() = paletteFor(skin)

    var state by mutableStateOf(engine.setup(DEFAULT_LEVEL, boardFor(DEFAULT_SCALE, DEFAULT_ASPECT)))
        private set

    /** The snake as it was before the most recent tick, for smooth motion. */
    var previousSnake by mutableStateOf(state.snake)
        private set

    /**
     * `System.nanoTime()` of the most recent tick (or reset). Updated atomically
     * with [state]/[previousSnake] so the renderer can derive the interpolation
     * fraction from a single consistent snapshot.
     */
    var tickTimeNanos by mutableLongStateOf(System.nanoTime())
        private set

    /** Latest eat event and a monotonic id so repeats are observable. */
    var eatEvent: EatEvent? = null
        private set
    var eatEventId by mutableIntStateOf(0)
        private set

    /** Latest floating-text event and a monotonic id so repeats are observable. */
    var floatingText: FloatingTextEvent? = null
        private set
    var floatingTextId by mutableIntStateOf(0)
        private set

    /** Bumped when the snake dies, so the UI can trigger a screen shake. */
    var deathEventId by mutableIntStateOf(0)
        private set

    /**
     * Latest whole-snake burst (death explosion / level-up dissolve) and a
     * monotonic id so repeats are observable by the renderer.
     */
    var bodyBurst: BodyBurstEvent? = null
        private set
    var bodyBurstId by mutableIntStateOf(0)
        private set

    /**
     * True for the brief window after a death while the snake bursts apart, before
     * the game-over overlay is revealed. The board keeps animating during it (so
     * the particles play) and the overlay waits for it to clear. Suppressed under
     * reduce-motion (the overlay then shows instantly, as before).
     */
    var deathAnimating by mutableStateOf(false)
        private set

    /**
     * True for the brief hold on a Campaign level-up while the completing snake
     * dissolves away before the next level's countdown. Keeps the board effects
     * loop alive across the transition.
     */
    var levelVanishing by mutableStateOf(false)
        private set

    /** Bumped on earthquake/explosion so the UI can shake the board mid-game. */
    var shakeEventId by mutableIntStateOf(0)
        private set

    /** Bumped on a near-miss / grace dodge so the UI can flash a brief danger cue. */
    var nearMissEventId by mutableIntStateOf(0)
        private set

    /** Latest hazard telegraph and a monotonic id so repeats are observable. */
    var hazardWarn: HazardWarnEvent? = null
        private set
    var hazardWarnId by mutableIntStateOf(0)
        private set

    /** Latest teleport jump and a monotonic id so repeats are observable. */
    var teleportEvent: TeleportEvent? = null
        private set
    var teleportEventId by mutableIntStateOf(0)
        private set

    /** Latest HUD announcement (fever / speed-up / record) and its monotonic id. */
    var bannerEvent: BannerEvent? = null
        private set
    var bannerEventId by mutableIntStateOf(0)
        private set

    /** Campaign: the furthest level ever reached (start levels 1..this are open). */
    var campaignCheckpoint by mutableIntStateOf(1)
        private set

    /** Campaign: the chosen starting level for the next run (1 = a record run). */
    var campaignStartLevel by mutableIntStateOf(1)
        private set

    /**
     * True when the finished run was a Campaign practice start from a checkpoint
     * (started past level 1): its score is not recorded, so the game-over overlay
     * hides the best-score row instead of comparing apples to oranges.
     */
    var lastRunFromCheckpoint by mutableStateOf(false)
        private set

    /** Best score for the current (level, scale), and whether the last run beat it. */
    var bestScore by mutableIntStateOf(0)
        private set
    var isNewBest by mutableStateOf(false)
        private set

    /** Achievements unlocked by the most recent run (for the game-over banner). */
    var newlyUnlocked by mutableStateOf<List<Achievement>>(emptyList())
        private set

    /** Skins unlocked by the most recent run (for the game-over banner). */
    var newlyUnlockedSkins by mutableStateOf<List<Skin>>(emptyList())
        private set

    /**
     * Today's rotating missions with their status after the most recent run, for
     * the game-over panel: each carries its description, whether it is done (now)
     * and whether this run is the one that just completed it (for emphasis).
     */
    var missionsProgress by mutableStateOf<List<MissionProgress>>(emptyList())
        private set

    /** Recap of the most recent finished run, for the game-over summary. */
    var lastSummary by mutableStateOf<RunSummary?>(null)
        private set

    /** Levels mode: seconds left on the intro countdown (0 when not counting). */
    var introCountdown by mutableIntStateOf(0)
        private set

    /** Seconds left on the pause-resume countdown (0 when not counting). */
    var resumeCountdown by mutableIntStateOf(0)
        private set

    /** Levels mode: true when the current intro follows a life loss. */
    var introIsRespawn by mutableStateOf(false)
        private set

    // Per-run accumulators feeding the achievement check at game over.
    private var runFoodsEaten = 0
    private var runMaxCombo = 0
    private var runStartLevelIndex = 1
    private var recordCelebrated = false
    private var runUsedExplosion = false
    private var runUsedStar = false
    private var runUsedJackpot = false
    private var runStartMs = 0L
    private var runMaxLevel = 1
    private var runMaxCycle = 1
    private var runMaxDepth = 0
    private var runLivesLost = 0
    private var runFlawlessLap = false
    private var runExtraLives = 0
    private var runMaxLength = 0

    private var loop: Job? = null
    private var bestJob: Job? = null
    private var introJob: Job? = null
    private var resumeJob: Job? = null
    private var lastAspect = DEFAULT_ASPECT

    /** Set by Quick Play: start the run as soon as the board has been measured. */
    private var pendingQuickStart = false

    /**
     * Non-null while a seeded **challenge** run (Daily or Random) is configured
     * (vs a normal run): it pins the mode/level/scale and seeds the engine. The
     * settings sync is suspended and the score routing adapts. Cleared by [toSetup].
     */
    var activeChallenge by mutableStateOf<Challenge?>(null)
        private set

    /** For a Daily challenge only: the epoch day, used to route the score + best. */
    private var dailyEpochDay: Long? = null

    /**
     * For a **replay** of a past Daily (from the weekly history): the epoch day
     * being relived. Label-only - a replay runs like a Random challenge (it stores
     * nothing, so that day's recorded best and streak are never overwritten); this
     * just lets the HUD show the relived date.
     */
    var replayDay: Long? by mutableStateOf(null)
        private set

    /** True while the active challenge is a one-off Random run (no stored best). */
    val isRandomChallenge: Boolean get() = activeChallenge != null && dailyEpochDay == null

    /** True while the active challenge is the date-seeded Daily run. */
    val isDailyChallenge: Boolean get() = dailyEpochDay != null

    /** Set by [requestDailyStart] / [requestRandomStart]; consumed once measured. */
    private var pendingChallenge: Challenge? = null

    /** Wall-clock of the last near-miss haptic, for throttling (see [NEAR_MISS_MIN_GAP_NANOS]). */
    private var lastNearMissNanos = 0L

    val level: Level get() = state.level
    val board: BoardDimensions get() = state.board

    /** Current score multiplier (consecutive-eat streak), for the HUD. */
    val combo: Int get() = state.combo

    init {
        // Campaign checkpoints: track the furthest level ever reached and restore
        // the last chosen starting level (clamped to the checkpoint). If the
        // saved start level arrives after the initial setup staged level 1,
        // restage the board so the preview and the actual run agree.
        viewModelScope.launch {
            repo.campaignCheckpoint().collect { campaignCheckpoint = it.coerceIn(1, LevelsMode.LEVEL_COUNT) }
        }
        viewModelScope.launch {
            val saved = repo.campaignStartLevel().first().coerceIn(1, LevelsMode.LEVEL_COUNT)
            campaignStartLevel = saved
            if (saved > 1 && mode == GameMode.Levels && state.status == GameStatus.Ready &&
                activeChallenge == null && state.levelIndex != campaignStartFor(mode)
            ) {
                resetTo(engine.setup(LevelsMode.SCORE_LEVEL, state.board, mode, snakeSpeed, startLevelIndex = campaignStartFor(mode)))
            }
        }
        // Seed level/scale/scheme from persisted settings; only re-apply while
        // Ready so a mid-game preference change can't disturb a live board.
        viewModelScope.launch {
            repo.settings.collect { settings ->
                controlScheme = settings.controlScheme
                swipeSensitivity = settings.swipeSensitivity
                backBehavior = settings.backBehavior
                crtEnabled = settings.crtEnabled
                reduceMotion = settings.reduceMotion
                skin = settings.skin
                terrain = settings.terrain
                // The Daily Challenge pins the spawn-affecting toggles so the run is
                // identical for everyone; only sync them from settings outside it.
                if (activeChallenge == null) {
                    hazardsEnabled = settings.hazardsEnabled
                    specialFrequency = settings.specialFrequency
                }
                // While a challenge is configured its fixed mode/level/scale must
                // stick, so skip the persisted-settings sync below.
                if (state.status == GameStatus.Ready && activeChallenge == null) {
                    // Levels and Zen ignore the difficulty selector: each is
                    // pinned to its score level so this collector can't keep
                    // resetting.
                    val targetLevel = pinnedLevelFor(settings.mode) ?: settings.level
                    val levelChanged = targetLevel != state.level
                    val modeChanged = settings.mode != mode
                    val speedChanged = settings.snakeSpeed != snakeSpeed
                    mode = settings.mode
                    scale = settings.scale
                    snakeSpeed = settings.snakeSpeed
                    if (levelChanged || modeChanged) {
                        resetTo(engine.setup(targetLevel, boardFor(scale, lastAspect), mode, snakeSpeed, startLevelIndex = campaignStartFor(mode)))
                    } else if (speedChanged) {
                        // Pace only: no board/obstacle rebuild needed, just restamp
                        // the speed so the loop reads it from the next tick on.
                        state = state.copy(snakeSpeed = snakeSpeed)
                        reconfigureBoard()
                    } else {
                        reconfigureBoard()
                    }
                    refreshBest()
                }
            }
        }
    }

    fun selectLevel(level: Level) {
        if (state.status != GameStatus.Ready) return
        if (pinnedLevelFor(mode) != null) return // the selector is disabled and ignored
        viewModelScope.launch { repo.setLevel(level) }
        resetTo(engine.setup(level, state.board, mode, snakeSpeed))
        refreshBest()
    }

    /**
     * The score level a mode is pinned to (its difficulty selector is disabled),
     * or null for the modes where difficulty is a real choice.
     */
    private fun pinnedLevelFor(mode: GameMode): Level? = when (mode) {
        GameMode.Levels -> LevelsMode.SCORE_LEVEL
        GameMode.Zen -> ZenMode.SCORE_LEVEL
        else -> null
    }

    fun selectSnakeSpeed(speed: SnakeSpeed) {
        if (state.status != GameStatus.Ready) return
        snakeSpeed = speed
        viewModelScope.launch { repo.setSnakeSpeed(speed) }
        // Pace is read live from the state each tick, so a restamp is enough.
        state = state.copy(snakeSpeed = speed)
    }

    fun selectMode(newMode: GameMode) {
        if (state.status != GameStatus.Ready) return
        mode = newMode
        viewModelScope.launch { repo.setGameMode(newMode) }
        // Levels/Zen pin their score level; leaving them, the settings collector
        // restores the user's persisted difficulty right after this reset.
        val level = pinnedLevelFor(newMode) ?: state.level
        resetTo(engine.setup(level, state.board, newMode, snakeSpeed, startLevelIndex = campaignStartFor(newMode)))
        refreshBest()
    }

    /**
     * Campaign: picks the starting level for the next run among the reached
     * checkpoints (1..[campaignCheckpoint]). Starting past level 1 is a practice
     * run — its score and progress are not recorded (records count from a
     * Level 1 start), which keeps the leaderboard honest.
     */
    fun selectCampaignStartLevel(levelIndex: Int) {
        if (state.status != GameStatus.Ready || mode != GameMode.Levels) return
        val clamped = levelIndex.coerceIn(1, campaignCheckpoint)
        campaignStartLevel = clamped
        viewModelScope.launch { repo.setCampaignStartLevel(clamped) }
        resetTo(engine.setup(LevelsMode.SCORE_LEVEL, state.board, mode, snakeSpeed, startLevelIndex = clamped))
        refreshBest()
    }

    /** The Campaign starting level for [mode]: the clamped pick, or 1 elsewhere. */
    private fun campaignStartFor(mode: GameMode): Int =
        if (mode == GameMode.Levels) campaignStartLevel.coerceIn(1, campaignCheckpoint) else 1

    fun selectScale(scale: BoardScale) {
        if (state.status != GameStatus.Ready) return
        this.scale = scale
        viewModelScope.launch { repo.setScale(scale) }
        reconfigureBoard()
        refreshBest()
    }

    /**
     * Called by the UI when the play area is (re)measured. Resizes the board to
     * fill it, but only while [GameStatus.Ready]; ignored once a game starts so
     * dimensions stay locked during play. Idempotent against measurement jitter.
     * Also the gate for a pending Quick Play start: the run only launches once the
     * board has been fitted to the device.
     */
    fun onPlayAreaMeasured(aspectRatio: Float) {
        if (state.status != GameStatus.Ready) return
        if (aspectRatio > 0f && aspectRatio != lastAspect) {
            lastAspect = aspectRatio
            reconfigureBoard()
        }
        val challenge = pendingChallenge
        if (challenge != null) {
            pendingChallenge = null
            startChallenge(challenge)
            return
        }
        if (pendingQuickStart) {
            pendingQuickStart = false
            start()
        }
    }

    /**
     * Menu "Play" (Quick Play): launch with the current persisted settings,
     * skipping the Custom setup overlay. Deferred until [onPlayAreaMeasured] so the
     * board fits the device before the run locks its dimensions.
     */
    fun requestQuickStart() {
        if (state.status == GameStatus.Ready) pendingQuickStart = true
    }

    /**
     * Launch [epochDay]'s **Daily** Challenge: the score and best are routed to the
     * per-day daily store. Deferred to [onPlayAreaMeasured] so the board is fitted
     * to the device first.
     */
    fun requestDailyStart(epochDay: Long) {
        if (state.status != GameStatus.Ready) return
        dailyEpochDay = epochDay
        activeChallenge = Challenge.forDay(epochDay)
        pendingChallenge = activeChallenge
    }

    /**
     * Launch a one-off **Random** challenge ([challenge] generated by the screen):
     * same pinning/seeding as the daily, but no stored best or streak.
     */
    fun requestRandomStart(challenge: Challenge) {
        if (state.status != GameStatus.Ready) return
        dailyEpochDay = null
        replayDay = null
        activeChallenge = challenge
        pendingChallenge = challenge
    }

    /**
     * **Replay** a past Daily ([epochDay], from the weekly history): reruns that
     * day's exact seeded challenge, but - by leaving [dailyEpochDay] null - it is
     * treated like a Random run and stores nothing, so the day's recorded best and
     * the streak stay untouched. [replayDay] is kept only for the HUD label.
     */
    fun requestReplayStart(epochDay: Long) {
        if (state.status != GameStatus.Ready) return
        dailyEpochDay = null
        replayDay = epochDay
        activeChallenge = Challenge.forDay(epochDay)
        pendingChallenge = activeChallenge
    }

    /** Reseeds the engine and starts the [challenge] run on the measured board. */
    private fun startChallenge(challenge: Challenge) {
        // A fresh seeded engine makes the obstacle layout and the food sequence
        // the challenge's (for a given board size).
        engine = GameEngine(Random(challenge.seed))
        mode = challenge.mode
        scale = challenge.scale
        snakeSpeed = challenge.modifier.speedOverride ?: SnakeSpeed.DEFAULT
        // Pin the spawn-affecting toggles so a seeded run is reproducible,
        // applying the modifier (Bonus Rush, Frenzy, ...) on top; the remaining
        // twists (Maxi Feast, Combo Rush, Old School, Overdrive's Endless boost)
        // ride along inside the state's modifier for the engine to honour.
        hazardsEnabled = challenge.modifier.hazardsOverride ?: true
        specialFrequency = challenge.modifier.specialFrequencyOverride ?: SpecialFrequency.Standard
        resetTo(engine.setup(challenge.level, boardFor(scale, lastAspect), mode, snakeSpeed, challenge.modifier))
        resetTo(engine.start(state))
        isNewBest = false
        refreshBest()
        beginRun()
        afterReset()
    }

    private fun reconfigureBoard() {
        val dims = boardFor(scale, lastAspect)
        if (dims != state.board) {
            resetTo(engine.setup(state.level, dims, mode, snakeSpeed, state.modifier, campaignStartFor(mode)))
        }
    }

    fun start() {
        // Re-fit the board to the latest measured play area before locking it in,
        // so the very first run can't start from a stale/too-tall measurement.
        reconfigureBoard()
        resetTo(engine.start(state))
        isNewBest = false
        beginRun()
        afterReset()
    }

    fun setDirection(direction: Direction) {
        state = engine.changeDirection(state, direction)
    }

    fun turnLeft() {
        state = engine.turnLeft(state)
    }

    fun turnRight() {
        state = engine.turnRight(state)
    }

    fun togglePause() {
        cancelResume()
        state = engine.togglePause(state)
        if (state.status == GameStatus.Running) runLoop() else stopLoop()
    }

    /**
     * Resumes from pause through a short 3-2-1 countdown instead of instantly:
     * the paused overlay clears, the board stays fully visible (with a locator
     * beacon on the head, see `GameBoard`'s `resumeHighlight`) while the digits
     * tick, then the loop restarts - so the player re-finds the snake and plans
     * the first move before motion returns.
     */
    fun resumeFromPause() {
        if (state.status != GameStatus.Paused || resumeJob != null) return
        resumeJob = viewModelScope.launch {
            resumeCountdown = RESUME_COUNTDOWN_SECONDS
            while (resumeCountdown > 0) {
                delay(1_000)
                resumeCountdown--
            }
            resumeJob = null
            state = engine.togglePause(state)
            runLoop()
        }
    }

    /**
     * Aborts a pending resume countdown, falling back to the paused overlay.
     * Also called when the app is backgrounded, so a countdown can never tick
     * down (and restart the run) unseen.
     */
    fun cancelResume() {
        resumeJob?.cancel()
        resumeJob = null
        resumeCountdown = 0
    }

    fun playAgain() {
        // A Daily Challenge replays the exact same seeded run (retry for a better
        // score); a normal run just starts a fresh game with the current config.
        activeChallenge?.let { startChallenge(it); return }
        resetTo(engine.newGame(state.level, state.board, mode, snakeSpeed, startLevelIndex = campaignStartFor(mode)))
        isNewBest = false
        beginRun()
        afterReset()
    }

    /** Routes a freshly reset game: Levels opens with its intro, others just run. */
    private fun afterReset() {
        if (state.status == GameStatus.LevelIntro) {
            introIsRespawn = false
            startIntro()
        } else {
            runLoop()
        }
    }

    /**
     * Levels mode: runs the 3-2-1 countdown over the staged board, then hands
     * back to the engine ([GameEngine.beginLevel]) and restarts the loop.
     */
    private fun startIntro() {
        introJob?.cancel()
        introJob = viewModelScope.launch {
            introCountdown = INTRO_SECONDS
            while (introCountdown > 0) {
                delay(1_000)
                introCountdown--
            }
            resetTo(engine.beginLevel(state))
            runLoop()
        }
    }

    private fun cancelIntro() {
        introJob?.cancel()
        introJob = null
        introCountdown = 0
        introIsRespawn = false
    }

    /** Resets the per-run achievement accumulators at the start of a run. */
    private fun beginRun() {
        // Clear any leftover transition flags so a fresh run never starts mid-fade.
        deathAnimating = false
        levelVanishing = false
        runFoodsEaten = 0
        runMaxCombo = 0
        runStartLevelIndex = state.levelIndex
        recordCelebrated = false
        lastRunFromCheckpoint = false
        runUsedExplosion = false
        runUsedStar = false
        runUsedJackpot = false
        runStartMs = System.currentTimeMillis()
        runMaxLevel = state.levelIndex
        runMaxCycle = 1
        runMaxDepth = levelDepth(state.levelIndex, state.speedCycle)
        runLivesLost = 0
        runFlawlessLap = false
        runExtraLives = 0
        runMaxLength = state.snake.size
        newlyUnlocked = emptyList()
        newlyUnlockedSkins = emptyList()
        missionsProgress = emptyList()
    }

    /**
     * Ends the current run and returns the game screen to the pre-game setup
     * ([GameStatus.Ready]). Used both by the game-over "Game setup" action and
     * when leaving for the main menu (so the next visit starts at setup).
     */
    fun toSetup() {
        stopLoop()
        cancelIntro()
        cancelResume()
        pendingQuickStart = false
        pendingChallenge = null
        if (activeChallenge != null) {
            // Leaving a challenge run: drop the seeded engine and restore the player's
            // persisted Custom setup (the settings collector won't re-emit on its own).
            activeChallenge = null
            dailyEpochDay = null
            replayDay = null
            engine = GameEngine()
            viewModelScope.launch {
                val s = repo.settings.first()
                mode = s.mode
                scale = s.scale
                snakeSpeed = s.snakeSpeed
                // Restore the spawn toggles the daily had pinned (the settings
                // collector only re-emits on an actual change).
                hazardsEnabled = s.hazardsEnabled
                specialFrequency = s.specialFrequency
                val level = pinnedLevelFor(mode) ?: s.level
                resetTo(engine.setup(level, boardFor(scale, lastAspect), mode, snakeSpeed, startLevelIndex = campaignStartFor(mode)))
                refreshBest()
            }
            return
        }
        resetTo(engine.setup(state.level, state.board, mode, snakeSpeed, startLevelIndex = campaignStartFor(mode)))
        refreshBest()
    }

    /** Replaces the state and resets interpolation bookkeeping to it. */
    private fun resetTo(newState: GameState) {
        previousSnake = newState.snake
        state = newState
        tickTimeNanos = System.nanoTime()
    }

    private fun runLoop() {
        stopLoop()
        loop = viewModelScope.launch {
            while (state.status == GameStatus.Running) {
                // Read the *effective* interval each iteration so Lightning/Snail/
                // Freeze actually change the pace mid-run.
                delay(state.tickIntervalMillis)
                if (state.status == GameStatus.Running) advance()
            }
        }
    }

    /** One simulation step, reacting to the events the engine emitted. */
    private fun advance() {
        val before = state
        val after = engine.tick(before, hazardsEnabled, specialFrequency)
        runMaxLength = max(runMaxLength, after.snake.size)

        after.lastEvents.forEach { event ->
            when (event) {
                is GameEvent.Ate -> {
                    eatEvent = EatEvent(event.food.position, event.food.span, palette.foodColor(event.food), BurstStyle.Eat, event.combo)
                    eatEventId++
                    val grown = (event.food.effect as? FoodEffect.Grow)?.segments ?: 0
                    if (grown > 0) {
                        floatingText = FloatingTextEvent(event.food.position, event.food.span, "+$grown", palette.foodColor(event.food))
                        floatingTextId++
                    }
                    sfx.ate(event.food, event.combo)
                    haptics.eat()
                    runFoodsEaten++
                    runMaxCombo = max(runMaxCombo, event.combo)
                }
                is GameEvent.Shrunk -> {
                    eatEvent = EatEvent(event.food.position, event.food.span, palette.foodColor(event.food), BurstStyle.Implode)
                    eatEventId++
                    if (event.removed > 0) {
                        floatingText = FloatingTextEvent(event.food.position, event.food.span, "-${event.removed}", palette.foodColor(event.food))
                        floatingTextId++
                    }
                    sfx.shrunk(event.food)
                    haptics.eat()
                }
                GameEvent.Died -> {
                    deathEventId++
                    sfx.died()
                    haptics.death()
                    onGameOver(after.score)
                    // Burst the whole snake apart, then reveal the game-over overlay.
                    // onGameOver already computed records/summary; only the overlay
                    // reveal is delayed (gated on deathAnimating in GameScreen). The
                    // hold scales with the snake's length, matching the renderer's
                    // head-to-tail stagger, so a long body finishes bursting before
                    // the overlay appears.
                    if (!reduceMotion) {
                        bodyBurst = BodyBurstEvent(after.snake, BurstStyle.Blast)
                        bodyBurstId++
                        deathAnimating = true
                        viewModelScope.launch {
                            delay(BodyBurstTiming.totalMs(after.snake.size))
                            deathAnimating = false
                        }
                    }
                }
                GameEvent.NearMiss -> {
                    // Throttled so hugging an edge gives an occasional tick/flash, not a buzz.
                    val now = System.nanoTime()
                    if (now - lastNearMissNanos >= NEAR_MISS_MIN_GAP_NANOS) {
                        haptics.nearMiss()
                        nearMissEventId++
                        lastNearMissNanos = now
                    }
                }
                GameEvent.GraceDodge -> {
                    // A whisker from death: a firmer cue and a flash sell the save.
                    // Suppress the near-miss tick that would otherwise pile on right after.
                    haptics.special()
                    nearMissEventId++
                    lastNearMissNanos = System.nanoTime()
                }
                is GameEvent.HazardImminent -> {
                    // One tick before the snake would eat a hazard: telegraph it
                    // with a warning flash (the renderer gates it under reduce-motion)
                    // and a pre-haptic, so the strike never lands unannounced.
                    hazardWarn = HazardWarnEvent(event.food.position, event.food.span)
                    hazardWarnId++
                    haptics.hazardWarning()
                }
                is GameEvent.Exploded -> {
                    // Explosion: a fiery detonation at the blast + a board shake.
                    eatEvent = EatEvent(event.food.position, event.food.span, SpecialVisuals.ExplosionColor, BurstStyle.Blast)
                    eatEventId++
                    shakeEventId++
                    sfx.special(event.food)
                    haptics.special()
                    runUsedExplosion = true
                }
                is GameEvent.JackpotHit -> {
                    eatEvent = EatEvent(event.food.position, event.food.span, palette.foodColor(event.food), BurstStyle.Eat)
                    eatEventId++
                    sfx.special(event.food)
                    haptics.special()
                    runUsedJackpot = true
                    runFoodsEaten++
                }
                is GameEvent.TimeGained -> {
                    // Time Attack: a green burst + a rising "+Ns" callout.
                    eatEvent = EatEvent(event.food.position, event.food.span, SpecialVisuals.TimeBonusColor, BurstStyle.Eat)
                    eatEventId++
                    floatingText = FloatingTextEvent(event.food.position, event.food.span, "+${event.seconds}s", SpecialVisuals.TimeBonusColor)
                    floatingTextId++
                    sfx.special(event.food)
                    haptics.special()
                }
                is GameEvent.TimeLost -> {
                    // Time Attack: a red implosion + a "-Ns" callout + a sting shake.
                    eatEvent = EatEvent(event.food.position, event.food.span, SpecialVisuals.TimePenaltyColor, BurstStyle.Implode)
                    eatEventId++
                    floatingText = FloatingTextEvent(event.food.position, event.food.span, "-${event.seconds}s", SpecialVisuals.TimePenaltyColor)
                    floatingTextId++
                    shakeEventId++
                    sfx.special(event.food)
                    haptics.special()
                }
                is GameEvent.FoodVanished -> {
                    // An ignored food timed out: a quiet upward fade, no sound.
                    eatEvent = EatEvent(event.food.position, event.food.span, palette.foodColor(event.food), BurstStyle.Vanish)
                    eatEventId++
                }
                is GameEvent.EffectStarted -> {
                    sfx.special(event.food)
                    haptics.special()
                    if (event.kind == EffectKind.Ghost) runUsedStar = true
                }
                is GameEvent.EffectExpired -> Unit
                GameEvent.FeverStarted -> {
                    // Time Attack finale: double points until the clock runs out.
                    banner(BannerKind.Fever)
                    sfx.feverStarted()
                    haptics.special()
                }
                is GameEvent.SpeedTierUp -> {
                    // Endless ramp step: seen (banner + frame flash) and heard.
                    banner(BannerKind.SpeedUp, event.tier)
                    sfx.speedTierUp()
                    haptics.special()
                }
                is GameEvent.LevelAdvanced -> {
                    sfx.levelUp()
                    haptics.levelUp()
                    runMaxLevel = max(runMaxLevel, event.levelIndex)
                    runMaxCycle = max(runMaxCycle, event.speedCycle)
                    runMaxDepth = max(runMaxDepth, levelDepth(event.levelIndex, event.speedCycle))
                    // First full lap cleared with no life lost so far: a flawless lap.
                    if (event.speedCycle >= 2 && runLivesLost == 0) runFlawlessLap = true
                    // Reaching a level opens it as a Campaign starting checkpoint
                    // (wrapping to level 1 of the next cycle proves the full lap).
                    val reached = if (event.levelIndex == 1) LevelsMode.LEVEL_COUNT else event.levelIndex
                    viewModelScope.launch { repo.submitCampaignCheckpoint(reached) }
                }
                is GameEvent.LifeLost -> {
                    // A non-final crash: the lighter quake shake, not the death one.
                    shakeEventId++
                    sfx.lifeLost()
                    haptics.lifeLost()
                    runLivesLost++
                }
                is GameEvent.Teleported -> {
                    // Portal jump: paired bursts at both pads + a whoosh. The
                    // interpolation snap is handled below (resetTo) so the head
                    // doesn't streak across the board.
                    teleportEvent = TeleportEvent(event.from, event.to)
                    teleportEventId++
                    sfx.teleport()
                    haptics.special()
                }
                is GameEvent.LifeGained -> {
                    eatEvent = EatEvent(event.food.position, event.food.span, SpecialVisuals.ExtraLifeColor, BurstStyle.Eat)
                    eatEventId++
                    val text = if (event.capped) "+${LevelsMode.LIFE_CAP_BONUS}" else "+1♥"
                    floatingText = FloatingTextEvent(event.food.position, event.food.span, text, SpecialVisuals.ExtraLifeColor)
                    floatingTextId++
                    sfx.lifeGained()
                    haptics.special()
                    if (!event.capped) runExtraLives++
                }
            }
        }

        // The live score just passed the stored best: celebrate once per run,
        // right when it happens, instead of only revealing it at game over.
        if (!recordCelebrated && bestScore > 0 && after.score > bestScore &&
            after.status == GameStatus.Running && !lastRunFromCheckpointCandidate()
        ) {
            recordCelebrated = true
            banner(BannerKind.NewRecord)
            sfx.recordBroken()
            haptics.special()
        }

        if (after.status == GameStatus.LevelIntro) {
            val respawn = after.lastEvents.any { it is GameEvent.LifeLost }
            val advanced = after.lastEvents.any { it is GameEvent.LevelAdvanced }
            if ((advanced || respawn) && !reduceMotion) {
                // Hold the old snake on screen and play its transition before staging
                // the next board: a level-up dissolves with a teleport-style vanish, a
                // non-final death bursts apart like the game-over explosion. The loop is
                // stopped so the still-Running `before` state isn't ticked again during
                // the hold; `state` keeps showing the old snake (which GameBoard fades
                // out) until the hold elapses.
                stopLoop()
                val style = if (respawn) BurstStyle.Blast else BurstStyle.Vanish
                val holdMs = BodyBurstTiming.totalMs(before.snake.size)
                bodyBurst = BodyBurstEvent(before.snake, style)
                bodyBurstId++
                levelVanishing = true
                introIsRespawn = respawn
                viewModelScope.launch {
                    delay(holdMs)
                    levelVanishing = false
                    resetTo(after)
                    startIntro()
                }
                return
            }
            // Reduce-motion: stage instantly. Use resetTo — not the interpolation commit
            // — so the renderer doesn't tween the old snake across the board to the spawn.
            resetTo(after)
            introIsRespawn = respawn
            startIntro()
            return
        }

        if (reduceMotion && after.lastEvents.any { it is GameEvent.Teleported }) {
            // Reduce motion: keep the portal jump instantaneous - snap to the new
            // positions so the head blinks across rather than sliding. With motion
            // on we fall through to the normal commit and let the renderer route the
            // body through the pads (it dives into the entry portal and re-emerges
            // at the exit) instead of streaking the head across the board.
            resetTo(after)
            return
        }

        // Commit the interpolation snapshot atomically: previous, current, time.
        previousSnake = before.snake
        state = after
        tickTimeNanos = System.nanoTime()
    }

    /** Routes a board swipe: steers by the swiped absolute [direction]. */
    fun onSwipe(direction: Direction) {
        setDirection(direction)
    }

    /** True while the current run is a Campaign practice start (past level 1). */
    private fun lastRunFromCheckpointCandidate(): Boolean =
        mode == GameMode.Levels && runStartLevelIndex > 1

    /** Flashes a HUD announcement [kind] (with an optional [value]). */
    private fun banner(kind: BannerKind, value: Int = 0) {
        bannerEvent = BannerEvent(kind, value)
        bannerEventId++
    }

    private fun onGameOver(score: Int) {
        // A Campaign practice start (from a checkpoint) is not record-eligible:
        // no highscore, no progress record, no Campaign achievements — records
        // count from a Level 1 start, so the leaderboard stays honest.
        val fromCheckpoint = lastRunFromCheckpointCandidate()
        lastRunFromCheckpoint = fromCheckpoint
        isNewBest = !fromCheckpoint && score > bestScore
        val durationMs = System.currentTimeMillis() - runStartMs
        // Run recap for the game-over overlay (Step 6.9.2).
        lastSummary = RunSummary(
            foodsEaten = runFoodsEaten,
            maxCombo = runMaxCombo,
            durationMs = durationMs,
            maxLength = runMaxLength,
            isCampaign = mode == GameMode.Levels,
            deepestLevel = runMaxLevel,
            deepestSpeed = runMaxCycle,
        )
        val stats = RunStats(
            mode = mode,
            score = score,
            maxCombo = runMaxCombo,
            durationMs = durationMs,
            foodsEaten = runFoodsEaten,
            usedExplosion = runUsedExplosion,
            usedStar = runUsedStar,
            usedJackpot = runUsedJackpot,
            // Campaign depth stats are zeroed for a checkpoint practice start,
            // so the tower-climb achievements can only be earned from Level 1.
            maxLevelReached = if (mode == GameMode.Levels && !fromCheckpoint) runMaxLevel else 0,
            maxSpeedCycle = if (fromCheckpoint) 1 else runMaxCycle,
            maxLevelDepth = if (mode == GameMode.Levels && !fromCheckpoint) runMaxDepth else 0,
            flawlessLap = mode == GameMode.Levels && runFlawlessLap && !fromCheckpoint,
            extraLivesGained = runExtraLives,
            maxSnakeLength = runMaxLength,
        )
        // Persist scores, then evaluate achievements and skin unlocks against the
        // run. The Daily keeps its own per-day best (and streak); a Random challenge
        // is a one-off and stores nothing; a normal run uses the (mode, level, scale)
        // records. For a Daily, submitting first lets the streak achievements / skin
        // rewards see the just-updated streak.
        val epochDay = dailyEpochDay
        viewModelScope.launch {
            val streak = when {
                epochDay != null -> {
                    repo.submitDailyScore(epochDay, score)
                    repo.dailyStreak().first()
                }
                activeChallenge != null -> 0 // Random challenge: not recorded.
                fromCheckpoint -> 0 // Campaign practice start: not recorded.
                else -> {
                    repo.submitScore(mode, state.level, scale, score)
                    if (mode == GameMode.Levels) {
                        // Levels: also track how deep the run got, for the Records screen.
                        val completed = (state.speedCycle - 1) * LevelsMode.LEVEL_COUNT + (state.levelIndex - 1)
                        repo.submitLevelsProgress(scale, completed)
                    }
                    0
                }
            }
            // Bring the daily streak into the run stats so the streak achievements
            // (and streak-gated skins) judge against the post-run value.
            val finalStats = stats.copy(dailyStreak = streak)
            val unlockedAchievements = repo.unlockedAchievements().first()
            val earned = Achievement.earnedBy(finalStats, unlockedAchievements)
            if (earned.isNotEmpty()) {
                repo.addUnlockedAchievements(earned.map { it.name })
                newlyUnlocked = earned
            }
            // Reward progression: unlock gated skins reached by this run's score /
            // the post-run streak, and surface them in the game-over banner.
            val unlockedSkins = repo.unlockedSkins().first()
            // During the pre-release preview all skins are already available, so
            // suppress the "skin unlocked" surfacing (the gates are bypassed).
            val newSkins = if (Skin.ALL_UNLOCKED_PREVIEW) emptyList()
            else Skin.newlyUnlocked(score, streak, unlockedSkins)
            if (newSkins.isNotEmpty()) {
                repo.addUnlockedSkins(newSkins.map { it.name })
                newlyUnlockedSkins = newSkins
            }
        }
        // Evaluate today's rotating missions (Step 6.9.5) and surface the full set
        // with each goal's status, emphasising any just completed by this run.
        // Completion is persisted tagged with the day so it counts only once and
        // resets when the daily set rotates.
        viewModelScope.launch {
            val epochDay = LocalDate.now().toEpochDay()
            val alreadyDone = repo.completedMissionsForDay(epochDay).first()
            val today = Mission.forDay(epochDay)
            val justDone = today.filter { it.id !in alreadyDone && it.completedBy(stats) }
            if (justDone.isNotEmpty()) {
                repo.addCompletedMissions(epochDay, justDone.map { it.id })
            }
            val justDoneIds = justDone.mapTo(mutableSetOf()) { it.id }
            missionsProgress = today.map { mission ->
                MissionProgress(
                    description = mission.description,
                    done = mission.id in alreadyDone || mission.id in justDoneIds,
                    justCompleted = mission.id in justDoneIds,
                )
            }
        }
    }

    /**
     * Tracks the best score for the current run: a Daily reads its per-day best, a
     * Random challenge has none (0), else the (mode, level, scale) record.
     */
    private fun refreshBest() {
        bestJob?.cancel()
        val epochDay = dailyEpochDay
        val randomChallenge = isRandomChallenge
        val level = state.level
        val currentScale = scale
        val currentMode = mode
        bestJob = viewModelScope.launch {
            when {
                epochDay != null -> repo.dailyBest(epochDay).collect { bestScore = it }
                randomChallenge -> bestScore = 0
                else -> repo.highScore(currentMode, level, currentScale).collect { bestScore = it }
            }
        }
    }

    /**
     * Levels mode: the deepest 1-based linear progress of a (level, cycle) pair,
     * `(speedCycle - 1) * LEVEL_COUNT + levelIndex` - so Level 10 of Speed 1 is 10,
     * Level 10 of Speed 2 is 20, and so on. Feeds the tower-climb achievements.
     */
    private fun levelDepth(levelIndex: Int, speedCycle: Int): Int =
        (speedCycle - 1) * LevelsMode.LEVEL_COUNT + levelIndex

    private fun stopLoop() {
        loop?.cancel()
        loop = null
    }

    override fun onCleared() {
        stopLoop()
        cancelIntro()
        cancelResume()
    }

    companion object {
        private val DEFAULT_LEVEL = Level.Beginner
        private val DEFAULT_SCALE = BoardScale.Classic
        private val DEFAULT_CONTROL = ControlScheme.Swipe

        /** Levels mode: seconds counted down by the intro overlay. */
        private const val INTRO_SECONDS = 3

        /**
         * Seconds counted down before a paused run resumes, so the player can
         * re-locate the snake and get ready (kept in sync with the Levels intro
         * cadence; must stay >= 2 s to actually give the eye time).
         */
        private const val RESUME_COUNTDOWN_SECONDS = 3

        fun factory(
            repo: SettingsRepository,
            sfx: GameSfx = GameSfx.None,
            haptics: GameHaptics = GameHaptics.None,
        ) = viewModelFactory {
            initializer { GameViewModel(repo, sfx, haptics) }
        }

        /** Minimum gap between near-miss haptics, so riding an edge does not buzz every tick. */
        private const val NEAR_MISS_MIN_GAP_NANOS = 220_000_000L
    }
}
