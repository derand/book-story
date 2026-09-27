/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.ui.library

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import ua.acclorite.book_story.R
import ua.acclorite.book_story.domain.use_case.backup.RestoreNotice
import ua.acclorite.book_story.presentation.library.LibraryEvent
import ua.acclorite.book_story.ui.common.components.dialog.Dialog

/**
 * Shown once, on the first start after a restore.
 *
 * A folder grant never survives a reinstall, so the usual case is a library
 * whose books cannot be opened until their folders are added again — and it
 * has to be *the same* folders, since a Drive book is found only through the
 * folder it was added from. So the folders are named, not just "pick a folder".
 * With none missing it is still shown, briefly, so the restart that just
 * happened does not look like a crash.
 */
@Composable
fun LibraryRestoreDialog(
    notice: RestoreNotice,
    dismiss: (LibraryEvent.OnDismissRestoreNotice) -> Unit,
    addFolders: (LibraryEvent.OnRestoreNoticeAddFolders) -> Unit
) {
    if (notice.failed) {
        Dialog(
            title = stringResource(id = R.string.restore_failed_dialog),
            icon = Icons.Outlined.ErrorOutline,
            description = stringResource(id = R.string.restore_failed_dialog_desc),
            actionEnabled = true,
            showDismiss = false,
            onDismiss = { dismiss(LibraryEvent.OnDismissRestoreNotice) },
            onAction = { dismiss(LibraryEvent.OnDismissRestoreNotice) },
            withContent = false
        )
        return
    }

    val summary = stringResource(
        id = R.string.restore_done_dialog_desc,
        pluralStringResource(R.plurals.backup_books_plural, notice.books, notice.books),
        pluralStringResource(R.plurals.backup_sessions_plural, notice.sessions, notice.sessions)
    )
    if (notice.missingSources.isEmpty()) {
        Dialog(
            title = stringResource(id = R.string.restore_done_dialog),
            icon = Icons.Outlined.Restore,
            description = summary,
            actionEnabled = true,
            showDismiss = false,
            onDismiss = { dismiss(LibraryEvent.OnDismissRestoreNotice) },
            onAction = { dismiss(LibraryEvent.OnDismissRestoreNotice) },
            withContent = false
        )
        return
    }

    val context = LocalContext.current
    val unknown = stringResource(id = R.string.source_unknown_provider)
    val folders = notice.missingSources.joinToString("\n") { source ->
        context.getString(
            R.string.restore_done_dialog_folder,
            source.name ?: "?",
            source.provider ?: source.authority ?: unknown
        )
    }
    Dialog(
        title = stringResource(id = R.string.restore_done_dialog),
        icon = Icons.Outlined.Restore,
        description = summary + "\n\n" +
                stringResource(id = R.string.restore_done_dialog_missing, folders),
        actionEnabled = true,
        onDismiss = { dismiss(LibraryEvent.OnDismissRestoreNotice) },
        onAction = { addFolders(LibraryEvent.OnRestoreNoticeAddFolders) },
        action = stringResource(id = R.string.restore_done_dialog_add_folders),
        dismiss = stringResource(id = R.string.restore_done_dialog_later),
        withContent = false
    )
}
