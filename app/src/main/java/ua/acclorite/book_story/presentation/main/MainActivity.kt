/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

@file:Suppress("UnusedVariable", "unused")

package ua.acclorite.book_story.presentation.main

import android.annotation.SuppressLint
import android.content.Intent
import android.content.res.Configuration
import android.database.CursorWindow
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.togetherWith
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.collections.immutable.persistentListOf
import ua.acclorite.book_story.R
import ua.acclorite.book_story.core.log.ThemeTraceComposed
import ua.acclorite.book_story.core.log.themeTrace
import ua.acclorite.book_story.data.model.file.CachedFile
import ua.acclorite.book_story.data.settings.SettingsManager
import ua.acclorite.book_story.presentation.browse.BrowseModel
import ua.acclorite.book_story.presentation.browse.BrowseScreen
import ua.acclorite.book_story.presentation.history.HistoryModel
import ua.acclorite.book_story.presentation.history.HistoryScreen
import ua.acclorite.book_story.presentation.library.LibraryModel
import ua.acclorite.book_story.presentation.library.LibraryScreen
import ua.acclorite.book_story.presentation.navigator.NavigatorItem
import ua.acclorite.book_story.presentation.navigator.StackEvent
import ua.acclorite.book_story.presentation.settings.SettingsModel
import ua.acclorite.book_story.presentation.start.StartScreen
import ua.acclorite.book_story.ui.common.components.navigation_bar.NavigationBar
import ua.acclorite.book_story.ui.common.components.navigation_rail.NavigationRail
import ua.acclorite.book_story.ui.common.helpers.ProvideSettings
import ua.acclorite.book_story.ui.main.MainActivityKeyboardManager
import ua.acclorite.book_story.ui.main.MainFileOpenEffects
import ua.acclorite.book_story.ui.navigator.Navigator
import ua.acclorite.book_story.ui.navigator.NavigatorTabs
import ua.acclorite.book_story.ui.settings.SettingsEffects
import ua.acclorite.book_story.ui.theme.BookStoryTheme
import ua.acclorite.book_story.ui.theme.Transitions
import java.lang.reflect.Field
import javax.inject.Inject


@SuppressLint("DiscouragedPrivateApi")
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject
    lateinit var settings: SettingsManager
    private val settingsModel: SettingsModel by viewModels()
    private val mainModel: MainModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen().setKeepOnScreenCondition {
            !settings.initialized.value || !settingsModel.initialized.value
        }

        super.onCreate(savedInstanceState)

        // Bigger Cursor size for Room
        try {
            val field: Field = CursorWindow::class.java.getDeclaredField("sCursorWindowSize")
            field.isAccessible = true
            field.set(null, 100 * 1024 * 1024)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Edge to edge
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)

        themeTrace("onCreate", this)
        receiveFile(intent)

        setContent {
            // Initializing Screen Models
            val libraryModel = hiltViewModel<LibraryModel>()
            val historyModel = hiltViewModel<HistoryModel>()
            val browseModel = hiltViewModel<BrowseModel>()

            SettingsEffects(
                effects = settingsModel.effects
            )

            ProvideSettings(settings) {
                val tabs = persistentListOf(
                    NavigatorItem(
                        screen = LibraryScreen,
                        title = R.string.library_screen,
                        tooltip = R.string.library_content_desc,
                        selectedIcon = R.drawable.library_screen_filled,
                        unselectedIcon = R.drawable.library_screen_outlined
                    ),
                    NavigatorItem(
                        screen = HistoryScreen,
                        title = R.string.history_screen,
                        tooltip = R.string.history_content_desc,
                        selectedIcon = R.drawable.history_screen_filled,
                        unselectedIcon = R.drawable.history_screen_outlined
                    ),
                    NavigatorItem(
                        screen = BrowseScreen,
                        title = R.string.browse_screen,
                        tooltip = R.string.browse_content_desc,
                        selectedIcon = R.drawable.browse_screen_filled,
                        unselectedIcon = R.drawable.browse_screen_outlined
                    )
                )

                MainActivityKeyboardManager()

                if (settings.initialized.collectAsStateWithLifecycle().value) {
                    val isDark = settings.darkTheme.value.isDark()
                    ThemeTraceComposed(isDark)

                    BookStoryTheme(
                        theme = settings.theme.value,
                        isDark = isDark,
                        isPureDark = settings.pureDark.value.isPureDark(this),
                        themeContrast = settings.themeContrast.value
                    ) {
                        Navigator(
                            initialScreen = if (settings.showStartScreen.value) StartScreen
                            else LibraryScreen,
                            transitionSpec = { lastEvent ->
                                when (lastEvent) {
                                    StackEvent.DEFAULT -> {
                                        Transitions.SlidingTransitionIn
                                            .togetherWith(Transitions.SlidingTransitionOut)
                                    }

                                    StackEvent.POP -> {
                                        Transitions.BackSlidingTransitionIn
                                            .togetherWith(Transitions.BackSlidingTransitionOut)
                                    }
                                }
                            },
                            contentKey = {
                                when (it) {
                                    LibraryScreen, HistoryScreen, BrowseScreen -> "tabs"
                                    else -> it
                                }
                            },
                            backHandlerEnabled = { it != StartScreen }
                        ) { screen ->
                            // Inside the navigator's content because that is
                            // where LocalNavigator exists. During a screen
                            // transition two of these collect at once, which is
                            // safe: the model hands the outcome over a channel,
                            // so exactly one of them receives it.
                            MainFileOpenEffects(mainModel = mainModel)

                            when (screen) {
                                LibraryScreen, HistoryScreen, BrowseScreen -> {
                                    NavigatorTabs(
                                        currentTab = screen,
                                        transitionSpec = {
                                            Transitions.FadeTransitionIn
                                                .togetherWith(Transitions.FadeTransitionOut)
                                        },
                                        navigationBar = {
                                            NavigationBar(
                                                tabs = tabs,
                                                showLabels = settings.showNavigationLabels.value
                                            )
                                        },
                                        navigationRail = {
                                            NavigationRail(
                                                tabs = tabs,
                                                showLabels = settings.showNavigationLabels.value
                                            )
                                        }
                                    ) { tab ->
                                        tab.Content()
                                    }
                                }

                                else -> {
                                    screen.Content()
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * A book tapped in a file manager. The activity is `singleTask`-less and
     * ordinary, so the same file can arrive either as the intent that started
     * the activity or, when it is already running, through [onNewIntent].
     *
     * The read permission carried by the intent is transient — it lasts as long
     * as the task holding it and cannot be persisted, because a file manager
     * does not offer it as persistable. That is what makes this a preview rather
     * than an import.
     */
    private fun receiveFile(intent: Intent?) {
        if (intent?.action != Intent.ACTION_VIEW) return
        val uri = intent.data ?: return
        mainModel.onFileReceived(uri.toString())
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        receiveFile(intent)
    }

    // The three moments a lost uiMode change could have been noticed, and was
    // not — see issue #50. Compile to nothing when THEME_TRACE is off.
    override fun onStart() {
        super.onStart()
        themeTrace("onStart", this)
    }

    override fun onResume() {
        super.onResume()
        themeTrace("onResume", this)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        themeTrace("onConfigurationChanged", this)
    }

    override fun onDestroy() {
        // Only the copies made to hand books to parsers that need a real path, not
        // the whole cache: the parse cache and the reader's image files live there
        // too, and they are meant to survive a book being closed.
        CachedFile.clearCopies(this)
        super.onDestroy()
    }
}