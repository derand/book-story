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
import ua.acclorite.book_story.presentation.settings.BackupSettingsEvent
import ua.acclorite.book_story.presentation.settings.BackupSettingsModel
import ua.acclorite.book_story.ui.common.components.common.StyledText
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

            current.lastExport != null -> stringResource(
                id = R.string.export_backup_done,
                current.lastExport.manifest.counts.books,
                current.lastExport.manifest.counts.sessions,
                Formatter.formatShortFileSize(context, current.lastExport.bytes)
            )

            else -> stringResource(id = R.string.export_backup_option_desc)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !state.value.exporting) {
                // Dated, so a second backup does not offer to overwrite the
                // first one; not translated, since it is a file name.
                picker.launch("book-story-backup-${LocalDate.now()}.zip")
            }
            .padding(horizontal = 18.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.Center
    ) {
        StyledText(
            text = stringResource(id = R.string.export_backup_option),
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
}
