package com.brioni.snake.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.brioni.snake.audio.GameAudio
import com.brioni.snake.audio.HapticController
import com.brioni.snake.audio.MusicTrack
import com.brioni.snake.data.SettingsRepository
import com.brioni.snake.game.BoardTerrain
import com.brioni.snake.game.GameMode
import com.brioni.snake.game.GameStatus
import com.brioni.snake.ui.game.GameScreen
import com.brioni.snake.ui.game.GameViewModel
import com.brioni.snake.ui.achievements.AchievementsScreen
import com.brioni.snake.ui.credits.CreditsScreen
import com.brioni.snake.ui.daily.DailyChallengeScreen
import com.brioni.snake.ui.daily.DailyHistoryScreen
import com.brioni.snake.ui.daily.RandomChallengeScreen
import com.brioni.snake.ui.intro.BrandIntroScreen
import com.brioni.snake.ui.menu.MainMenuScreen
import com.brioni.snake.ui.onboarding.OnboardingScreen
import com.brioni.snake.ui.records.RecordsScreen
import com.brioni.snake.ui.settings.SettingsScreen
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** The top-level destinations. [Intro] is the cold-launch brand splash. */
private enum class Screen { Intro, Menu, Game, Daily, DailyHistory, Random, Settings, Records, Achievements, Credits, Onboarding }

/**
 * Root of the UI. Hosts a lightweight, state-based navigation between the main
 * menu, gameplay and settings, cross-fading between them (also covering the
 * Phase 3.7 fade transitions). A single [SettingsRepository], [GameAudio] and
 * [GameViewModel] are shared across destinations.
 *
 * Audio is owned here: music crossfades to match the active screen (Phase 4.4),
 * the host lifecycle pauses/resumes it (Phase 4.3), and SFX fire on navigation
 * actions and (via the ViewModel) gameplay events.
 */
@Composable
fun App(repo: SettingsRepository, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val audio = remember(context) { GameAudio(context.applicationContext, repo) }
    val haptics = remember(context) { HapticController(context.applicationContext, repo) }
    val gameViewModel: GameViewModel = viewModel(factory = GameViewModel.factory(repo, audio, haptics))

    val scope = rememberCoroutineScope()
    // First-run flag: drives whether the splash hands off to the tutorial or the menu.
    val onboardingCompleted by repo.onboardingCompleted().collectAsState(initial = false)
    // The selected terrain drives the shared menu backdrop (and, via the theme,
    // the accent colours), so the menus live in the player's chosen world.
    val terrainFlow = remember(repo) { repo.settings.map { it.terrain } }
    val terrain by terrainFlow.collectAsState(initial = BoardTerrain.Meadow)

    var ordinal by rememberSaveable { mutableIntStateOf(Screen.Intro.ordinal) }
    val screen = Screen.entries[ordinal]
    fun navigate(target: Screen) { ordinal = target.ordinal }
    // Where the tutorial returns to: the Menu after the first run, or Settings when
    // re-opened from the "How to play" entry there.
    var onboardingReturnOrdinal by rememberSaveable { mutableIntStateOf(Screen.Menu.ordinal) }

    // Crossfade music to match the screen (menu/settings share the menu track).
    // Zen borrows the calmer menu track during play - the gameplay loop is too
    // driving for the relaxed mode, and the crossfade makes the swap seamless.
    LaunchedEffect(screen, gameViewModel.mode) {
        val track = if (screen == Screen.Game && gameViewModel.mode != GameMode.Zen) {
            MusicTrack.Gameplay
        } else {
            MusicTrack.Menu
        }
        audio.setMusic(track)
    }

    // Lifecycle-aware music + resource cleanup.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    audio.pauseMusic()
                    // Step 6.9.4: auto-pause a live run when the app is backgrounded,
                    // so the loop never ticks unseen. We deliberately do not auto-resume
                    // on ON_START - the player resumes from the pause overlay.
                    if (gameViewModel.state.status == GameStatus.Running) gameViewModel.togglePause()
                    // A pending resume countdown must not tick down (and restart
                    // the run) unseen either - abort it back to the paused overlay.
                    gameViewModel.cancelResume()
                }
                Lifecycle.Event.ON_START -> audio.resumeMusic()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            audio.release()
            haptics.release()
        }
    }

    // Secondary screens go back to the Menu; the Daily history nests under Daily;
    // the Game screen owns its own back (pause vs. exit) and the Menu is the root
    // (the system handles back/exit).
    BackHandler(enabled = screen != Screen.Menu && screen != Screen.Game && screen != Screen.Onboarding) {
        navigate(if (screen == Screen.DailyHistory) Screen.Daily else Screen.Menu)
    }

    val darkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f

    Box(modifier = modifier.fillMaxSize()) {
        // Shared animated AGSL backdrop behind the menu-family screens — only in the
        // dark (brand) theme; the light theme keeps its plain light surface. The
        // Onboarding screen draws its own (always-dark) backdrop, so it is excluded
        // here to avoid stacking two animated shaders.
        if (darkTheme && screen != Screen.Game && screen != Screen.Intro && screen != Screen.Onboarding) {
            AnimatedShaderBackground(modifier = Modifier.fillMaxSize(), terrain = terrain)
        }

        AnimatedContent(
            targetState = screen,
            transitionSpec = { (fadeIn(tween(260)) togetherWith fadeOut(tween(260))) using null },
            label = "screen",
        ) { current ->
            // A "glass" blur dissolve: each entering screen sharpens into focus,
            // the leaving one blurs out (cheap RenderEffect, always on at minSdk 33).
            val blurRadius by transition.animateDp(
                transitionSpec = { tween(260) },
                label = "screenBlur",
            ) { state -> if (state == EnterExitState.Visible) 0.dp else 16.dp }

            Box(Modifier.fillMaxSize().blur(blurRadius)) {
                when (current) {
                    Screen.Intro -> BrandIntroScreen(
                        // First launch hands off to the tutorial; afterwards straight
                        // to the menu. The 4.1 s splash masks the DataStore load, so the
                        // flag is settled by the time this fires.
                        onFinished = {
                            navigate(if (onboardingCompleted) Screen.Menu else Screen.Onboarding)
                        },
                        modifier = Modifier.fillMaxSize(),
                    )

                    Screen.Menu -> MainMenuScreen(
                        repo = repo,
                        // Quick Play: launch straight into a run with the current
                        // settings; the game screen starts it once it has measured
                        // the board (see GameViewModel.requestQuickStart).
                        onPlay = { gameViewModel.requestQuickStart(); navigate(Screen.Game) },
                        // Custom: open the game screen's setup overlay instead.
                        onCustom = { navigate(Screen.Game) },
                        onDaily = { navigate(Screen.Daily) },
                        onRandom = { navigate(Screen.Random) },
                        onRecords = { navigate(Screen.Records) },
                        onAchievements = { navigate(Screen.Achievements) },
                        onSettings = { navigate(Screen.Settings) },
                        onCredits = { navigate(Screen.Credits) },
                        modifier = Modifier.fillMaxSize(),
                    )

                    // The board interior is an always-dark arcade surface, but the
                    // surrounding chrome (HUD, board frame, overlays) follows the
                    // selected theme so the gameplay screen reads correctly under the
                    // light theme like every other screen.
                    Screen.Game -> Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background),
                    ) {
                        GameScreen(
                            viewModel = gameViewModel,
                            audio = audio,
                            onExitToMenu = { navigate(Screen.Menu) },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }

                    Screen.Daily -> DailyChallengeScreen(
                        repo = repo,
                        // Launch the seeded run; the game screen starts it once the
                        // board is measured (see GameViewModel.requestDailyStart).
                        onPlay = { epochDay ->
                            gameViewModel.requestDailyStart(epochDay)
                            navigate(Screen.Game)
                        },
                        onHistory = { navigate(Screen.DailyHistory) },
                        onBack = { navigate(Screen.Menu) },
                        modifier = Modifier.fillMaxSize(),
                    )

                    Screen.DailyHistory -> DailyHistoryScreen(
                        repo = repo,
                        // Replay a past day's challenge without recording (the day's
                        // best and streak stay as they were).
                        onReplay = { epochDay ->
                            gameViewModel.requestReplayStart(epochDay)
                            navigate(Screen.Game)
                        },
                        onBack = { navigate(Screen.Daily) },
                        modifier = Modifier.fillMaxSize(),
                    )

                    Screen.Random -> RandomChallengeScreen(
                        onPlay = { challenge ->
                            gameViewModel.requestRandomStart(challenge)
                            navigate(Screen.Game)
                        },
                        onBack = { navigate(Screen.Menu) },
                        modifier = Modifier.fillMaxSize(),
                    )

                    Screen.Settings -> SettingsScreen(
                        repo = repo,
                        audio = audio,
                        onBack = { navigate(Screen.Menu) },
                        onShowTutorial = {
                            onboardingReturnOrdinal = Screen.Settings.ordinal
                            navigate(Screen.Onboarding)
                        },
                        modifier = Modifier.fillMaxSize(),
                    )

                    Screen.Records -> RecordsScreen(
                        repo = repo,
                        onBack = { navigate(Screen.Menu) },
                        modifier = Modifier.fillMaxSize(),
                    )

                    Screen.Achievements -> AchievementsScreen(
                        repo = repo,
                        onBack = { navigate(Screen.Menu) },
                        modifier = Modifier.fillMaxSize(),
                    )

                    Screen.Credits -> CreditsScreen(
                        onBack = { navigate(Screen.Menu) },
                        modifier = Modifier.fillMaxSize(),
                    )

                    Screen.Onboarding -> OnboardingScreen(
                        repo = repo,
                        onFinished = {
                            scope.launch { repo.setOnboardingCompleted(true) }
                            navigate(Screen.entries[onboardingReturnOrdinal])
                            onboardingReturnOrdinal = Screen.Menu.ordinal
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}
