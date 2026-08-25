/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.ui.settings.browse.scan.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ua.acclorite.book_story.R
import ua.acclorite.book_story.domain.model.file.BookSource
import ua.acclorite.book_story.presentation.browse.BrowseScreen
import ua.acclorite.book_story.presentation.settings.SettingsEvent
import ua.acclorite.book_story.presentation.settings.SettingsModel
import ua.acclorite.book_story.ui.common.components.common.IconButton
import ua.acclorite.book_story.ui.common.components.common.StyledText
import ua.acclorite.book_story.ui.common.helpers.noRippleClickable
import ua.acclorite.book_story.ui.common.helpers.showToast
import ua.acclorite.book_story.ui.theme.dynamicListItemColor

@Composable
fun BrowseScanOption() {
    val settingsModel = hiltViewModel<SettingsModel>()
    val context = LocalContext.current
    val state = settingsModel.state.collectAsStateWithLifecycle()

    // Asking a source whether it answers is a query against its provider, and
    // for one that is not on this device that is a round trip — so it is asked
    // when the screen opens and when a grant changes, not while drawing.
    LaunchedEffect(Unit) {
        settingsModel.onEvent(SettingsEvent.OnRefreshBookSources)
    }

    val persistedUriIntent = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        settingsModel.onEvent(
            SettingsEvent.OnGrantPersistableUriPermission(
                uri = uri.toString()
            )
        )
        BrowseScreen.refreshListChannel.trySend(Unit)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
    ) {
        state.value.bookSources.forEachIndexed { index, source ->
            BrowseScanFolderItem(
                index = index,
                source = source,
                releasePersistableUriPermission = {
                    settingsModel.onEvent(
                        SettingsEvent.OnReleasePersistableUriPermission(uri = source.uri)
                    )
                    BrowseScreen.refreshListChannel.trySend(Unit)
                }
            )
        }
    }

    BrowseScanAction(
        requestPersistableUriPermission = {
            try {
                persistedUriIntent.launch(null)
            } catch (e: Exception) {
                e.printStackTrace()

                context.getString(R.string.error_no_file_manager_app)
                    .showToast(context, longToast = false)
            }
        }
    )
}

@Composable
private fun BrowseScanFolderItem(
    index: Int,
    source: BookSource,
    releasePersistableUriPermission: () -> Unit
) {
    // Never dropped for being unreadable: a grant the user made is theirs to see
    // and to remove, and a source that says nothing is exactly the one they need
    // to be told about. The row used to vanish instead — `DocumentFileCompat`
    // returning null took the whole item with it.
    val unavailable = !source.isAvailable

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = 18.dp,
                vertical = 8.dp
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Icon(
            imageVector = when {
                unavailable -> Icons.Outlined.FolderOff
                else -> Icons.Outlined.Folder
            },
            contentDescription = null,
            modifier = Modifier
                .clip(CircleShape)
                .background(
                    when {
                        unavailable -> MaterialTheme.colorScheme.surfaceVariant
                        else -> MaterialTheme.colorScheme.dynamicListItemColor(index)
                    }
                )
                .padding(11.dp)
                .size(22.dp),
            tint = when {
                unavailable -> MaterialTheme.colorScheme.onSurfaceVariant
                else -> MaterialTheme.colorScheme.onSurface
            }
        )

        Column(
            modifier = Modifier.weight(1f)
        ) {
            // The folder as its provider names it, over the provider as it names
            // itself. Both are answers; the path this used to show was a guess
            // that only external storage can be guessed for, and it rendered a
            // folder on Drive as an empty line over "/storage/emulated/0".
            //
            // A source that will not answer has no folder name to give — the
            // name has to be asked for, and asking is what failed — so the
            // provider moves up to the line that is always filled, and the
            // second line carries the state and whatever tells this grant apart
            // from the next one. Two folders gone dark on one provider would
            // otherwise be the same row twice, with two buttons to remove them.
            val providerName = source.provider
                ?: source.authority
                ?: stringResource(R.string.source_unknown_provider)

            StyledText(
                text = source.name ?: providerName,
                style = MaterialTheme.typography.bodyLarge.copy(
                    color = when {
                        unavailable -> MaterialTheme.colorScheme.onSurfaceVariant
                        else -> MaterialTheme.colorScheme.onSurface
                    }
                )
            )
            StyledText(
                text = when {
                    !unavailable -> providerName
                    else -> listOfNotNull(
                        stringResource(R.string.source_unavailable),
                        source.treeDocumentId
                    ).joinToString(" · ")
                },
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                maxLines = 1
            )
        }

        IconButton(
            modifier = Modifier.size(24.dp),
            icon = Icons.Outlined.Clear,
            contentDescription = R.string.remove_content_desc,
            disableOnClick = false,
            color = MaterialTheme.colorScheme.onSurface
        ) {
            releasePersistableUriPermission()
        }
    }
}

@Composable
private fun BrowseScanAction(
    requestPersistableUriPermission: () -> Unit
) {
    Row(
        modifier = Modifier
            .padding(horizontal = 18.dp, vertical = 18.dp)
            .noRippleClickable {
                requestPersistableUriPermission()
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            modifier = Modifier.size(24.dp),
            imageVector = Icons.Default.Add,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.secondary
        )
        StyledText(
            text = stringResource(id = R.string.add_folder),
            style = MaterialTheme.typography.labelLarge.copy(
                color = MaterialTheme.colorScheme.secondary
            )
        )
    }
}