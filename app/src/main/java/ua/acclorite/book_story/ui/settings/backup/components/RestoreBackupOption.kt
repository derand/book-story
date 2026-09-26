/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.ui.settings.backup.components

import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ua.acclorite.book_story.R
import ua.acclorite.book_story.data.backup.RestoreException
import ua.acclorite.book_story.presentation.settings.BackupSettingsEvent
import ua.acclorite.book_story.presentation.settings.BackupSettingsModel
import ua.acclorite.book_story.ui.common.components.common.StyledText
import ua.acclorite.book_story.ui.common.components.dialog.Dialog
import java.text.DateFormat
import java.time.LocalDate
import java.util.Date

/**
 * Replaces the library with the one in a backup file the user picks.
 *
 * `OpenDocument`, for the same reason the backup uses `CreateDocument`: a
 * one-shot grant to the one file, no permission asked for, nothing kept.
 *
 * The file is unpacked and checked before the confirmation is shown, so the
 * dialog only ever offers a backup that can be restored, with what is in it.
 * It also offers to save the current library first — a restore replaces, it
 * does not merge, and that is the last moment the current one can be kept.
 */
@Composable
fun RestoreBackupOption() {
    val model = hiltViewModel<BackupSettingsModel>()
    val state = model.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        model.onEvent(BackupSettingsEvent.OnPickRestore(uri))
    }

    val savePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        model.onEvent(BackupSettingsEvent.OnSaveBeforeRestore(uri))
    }

    val description = state.value.let { current ->
        val error = current.restoreError
        when {
            current.staging -> stringResource(id = R.string.restore_backup_in_progress)
            error != null -> when (error.reason) {
                RestoreException.Reason.NOT_A_BACKUP ->
                    stringResource(id = R.string.restore_backup_not_a_backup)

                RestoreException.Reason.TOO_NEW ->
                    stringResource(id = R.string.restore_backup_too_new)

                RestoreException.Reason.DAMAGED ->
                    stringResource(id = R.string.restore_backup_damaged, error.detail)

                null -> stringResource(id = R.string.restore_backup_failed, error.detail)
            }

            else -> stringResource(id = R.string.restore_backup_option_desc)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !state.value.staging && !state.value.exporting) {
                // Some providers report a zip as octet-stream, Drive among them
                // for a file it did not create; a narrower filter would grey out
                // the very backup the user is looking for.
                picker.launch(
                    arrayOf(
                        "application/zip",
                        "application/x-zip-compressed",
                        "application/octet-stream"
                    )
                )
            }
            .padding(horizontal = 18.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.Center
    ) {
        StyledText(
            text = stringResource(id = R.string.restore_backup_option),
            style = MaterialTheme.typography.titleMedium.copy(
                color = MaterialTheme.colorScheme.onSurface
            )
        )
        StyledText(
            text = description,
            style = MaterialTheme.typography.bodySmall.copy(
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        )
    }

    val staged = state.value.stagedRestore ?: return
    val current = state.value

    val summary = stringResource(
        id = R.string.restore_backup_dialog_desc,
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
            .format(Date(staged.createdAt)),
        staged.counts.books,
        staged.counts.sessions,
        staged.counts.covers,
        staged.counts.ownedBooks
    )
    val saved = current.savedBeforeRestore
    val status = when {
        current.restarting -> stringResource(id = R.string.restore_backup_dialog_restarting)
        current.exporting -> stringResource(id = R.string.restore_backup_dialog_saving)
        current.exportError != null -> stringResource(
            id = R.string.export_backup_failed,
            current.exportError
        )

        saved != null -> stringResource(
            id = R.string.restore_backup_dialog_saved,
            saved.manifest.counts.books,
            saved.manifest.counts.sessions,
            Formatter.formatShortFileSize(context, saved.bytes)
        )

        else -> null
    }

    Dialog(
        title = stringResource(id = R.string.restore_backup_dialog),
        description = if (status != null) "$summary\n\n$status" else summary,
        // The buttons follow the state rather than locking on the first tap:
        // saving first has to leave "Restore" usable afterwards.
        disableOnClick = false,
        actionEnabled = !current.exporting && !current.restarting,
        withContent = false,
        onDismiss = {
            if (!current.restarting) model.onEvent(BackupSettingsEvent.OnCancelRestore)
        },
        onAction = { model.onEvent(BackupSettingsEvent.OnConfirmRestore) },
        action = stringResource(id = R.string.restore_backup_dialog_action),
        secondaryAction = stringResource(id = R.string.restore_backup_dialog_save_first)
            .takeIf { saved == null },
        onSecondaryAction = {
            if (!current.exporting && !current.restarting) {
                savePicker.launch("book-story-backup-${LocalDate.now()}.zip")
            }
        }
    )
}
