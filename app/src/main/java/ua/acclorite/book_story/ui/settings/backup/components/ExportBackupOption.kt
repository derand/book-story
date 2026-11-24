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
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ua.acclorite.book_story.R
import ua.acclorite.book_story.presentation.settings.BackupSettingsEvent
import ua.acclorite.book_story.presentation.settings.BackupSettingsModel
import ua.acclorite.book_story.ui.settings.components.SettingsActionRow
import java.time.LocalDate

/**
 * Writes a backup to a file the user names in the system picker.
 *
 * `CreateDocument` rather than a folder grant: it hands over a one-shot URI to
 * exactly the file being written, so the app asks for no permission and keeps
 * no access afterwards. The user decides where the file lands — the device, a
 * cloud folder, a USB stick — and the app sends it nowhere itself.
 */
@Composable
fun ExportBackupOption() {
    val model = hiltViewModel<BackupSettingsModel>()
    val state = model.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        model.onEvent(BackupSettingsEvent.OnExport(uri))
    }

    val description = state.value.let { current ->
        when {
            current.exporting -> stringResource(id = R.string.export_backup_in_progress)
            current.exportError != null -> stringResource(
                id = R.string.export_backup_failed,
                current.exportError
            )

            current.lastExport != null -> {
                val counts = current.lastExport.manifest.counts
                stringResource(
                    id = R.string.export_backup_done,
                    pluralStringResource(R.plurals.backup_books_plural, counts.books, counts.books),
                    pluralStringResource(R.plurals.backup_sessions_plural, counts.sessions, counts.sessions),
                    Formatter.formatShortFileSize(context, current.lastExport.bytes)
                )
            }

            else -> stringResource(id = R.string.export_backup_option_desc)
        }
    }

    SettingsActionRow(
        title = stringResource(id = R.string.export_backup_option),
        description = description,
        enabled = !state.value.exporting
    ) {
        // Dated, so a second backup does not offer to overwrite the
        // first one; not translated, since it is a file name.
        picker.launch("book-story-backup-${LocalDate.now()}.zip")
    }
}
