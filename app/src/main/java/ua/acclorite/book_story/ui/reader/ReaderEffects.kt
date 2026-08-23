/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.ui.reader

import android.app.SearchManager
import android.content.Intent
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.flow.SharedFlow
import ua.acclorite.book_story.R
import ua.acclorite.book_story.domain.model.library.Book
import ua.acclorite.book_story.presentation.book_info.BookInfoScreen
import ua.acclorite.book_story.presentation.reader.ReaderEffect
import ua.acclorite.book_story.presentation.reader.ReaderEvent
import ua.acclorite.book_story.ui.common.helpers.LocalActivity
import ua.acclorite.book_story.ui.common.helpers.launchActivity
import ua.acclorite.book_story.ui.common.helpers.setBrightness
import ua.acclorite.book_story.ui.common.helpers.showToast
import ua.acclorite.book_story.ui.navigator.LocalNavigator

private const val EXTERNAL_STORAGE_AUTHORITY = "com.android.externalstorage.documents"
private const val PRIMARY_STORAGE = "/storage/emulated/0"

@Composable
fun ReaderEffects(
    effects: SharedFlow<ReaderEffect>,
    book: Book,
    fullscreen: Boolean,
    grantFolder: (ReaderEvent.OnGrantFolder) -> Unit
) {
    val navigator = LocalNavigator.current
    val activity = LocalActivity.current

    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        grantFolder(ReaderEvent.OnGrantFolder(uri = uri.toString()))
    }

    LaunchedEffect(effects, book, fullscreen) {
        effects.collect { effect ->
            when (effect) {
                is ReaderEffect.OnSystemBarsVisibility -> {
                    WindowCompat.getInsetsController(
                        activity.window,
                        activity.window.decorView
                    ).apply {
                        systemBarsBehavior = WindowInsetsControllerCompat
                            .BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                        if (effect.show ?: !fullscreen) show(WindowInsetsCompat.Type.systemBars())
                        else hide(WindowInsetsCompat.Type.systemBars())
                    }
                }

                is ReaderEffect.OnResetBrightness -> {
                    activity.setBrightness(brightness = null)
                }

                is ReaderEffect.OnOpenTranslator -> {
                    val translatorIntent = Intent()
                    val browserIntent = Intent()

                    translatorIntent.type = "text/plain"
                    translatorIntent.action = Intent.ACTION_PROCESS_TEXT
                    browserIntent.action = Intent.ACTION_WEB_SEARCH

                    translatorIntent.putExtra(
                        Intent.EXTRA_PROCESS_TEXT,
                        effect.textToTranslate
                    )
                    translatorIntent.putExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, true)
                    browserIntent.putExtra(
                        SearchManager.QUERY,
                        "translate: ${effect.textToTranslate.trim()}"
                    )

                    translatorIntent.launchActivity(
                        activity = activity,
                        createChooser = !effect.translateWholeParagraph,
                        success = {
                            return@collect
                        }
                    )
                    browserIntent.launchActivity(
                        activity = activity,
                        success = {
                            return@collect
                        }
                    )

                    activity.getString(R.string.error_no_translator)
                        .showToast(context = activity, longToast = false)
                }

                is ReaderEffect.OnOpenShareApp -> {
                    val shareIntent = Intent()

                    shareIntent.action = Intent.ACTION_SEND
                    shareIntent.type = "text/plain"
                    shareIntent.putExtra(
                        Intent.EXTRA_SUBJECT,
                        activity.getString(R.string.app_name)
                    )
                    shareIntent.putExtra(
                        Intent.EXTRA_TEXT,
                        effect.textToShare.trim()
                    )

                    shareIntent.launchActivity(
                        activity = activity,
                        createChooser = true,
                        success = {
                            return@collect
                        }
                    )

                    activity.getString(R.string.error_no_share_app)
                        .showToast(context = activity, longToast = false)
                }

                is ReaderEffect.OnOpenWebBrowser -> {
                    val browserIntent = Intent()

                    browserIntent.action = Intent.ACTION_WEB_SEARCH
                    browserIntent.putExtra(
                        SearchManager.QUERY,
                        effect.textToSearch
                    )

                    browserIntent.launchActivity(
                        activity = activity,
                        success = {
                            return@collect
                        }
                    )

                    activity.getString(R.string.error_no_browser)
                        .showToast(context = activity, longToast = false)
                }

                is ReaderEffect.OnNavigateBack -> {
                    navigator.pop()
                }

                is ReaderEffect.OnNavigateToBookInfo -> {
                    if (effect.changePath) BookInfoScreen.changePathChannel.trySend(true)
                    navigator.push(
                        BookInfoScreen(
                            bookId = book.id
                        ),
                        popping = true,
                        saveInBackStack = false
                    )
                }

                is ReaderEffect.OnRequestFolderGrant -> {
                    // The book's own folder as the picker's starting point, so
                    // the user confirms rather than navigates. Only external
                    // storage composes a document id this way; any other
                    // provider ignores the hint and opens where it likes, which
                    // is why nothing depends on it.
                    val initial = effect.initialFolder
                        ?.substringAfter(PRIMARY_STORAGE, missingDelimiterValue = "")
                        ?.trim('/')
                        ?.takeIf { it.isNotBlank() }
                        ?.let { relative ->
                            DocumentsContract.buildDocumentUri(
                                EXTERNAL_STORAGE_AUTHORITY,
                                "primary:$relative"
                            )
                        }

                    activity.getString(R.string.add_to_library_grant_folder)
                        .showToast(context = activity, longToast = true)
                    folderPicker.launch(initial)
                }

                is ReaderEffect.OnBookGone -> {
                    activity.getString(R.string.book_gone)
                        .showToast(context = activity, longToast = true)
                }

                is ReaderEffect.OnAddedToLibrary -> {
                    activity.getString(R.string.add_to_library_added)
                        .showToast(context = activity, longToast = false)
                }

                is ReaderEffect.OnCannotAddToLibrary -> {
                    activity.getString(R.string.add_to_library_no_path)
                        .showToast(context = activity, longToast = true)
                }
            }
        }
    }
}