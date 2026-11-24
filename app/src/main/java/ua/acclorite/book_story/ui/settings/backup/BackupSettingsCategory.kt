/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

@file:Suppress("FunctionName")

package ua.acclorite.book_story.ui.settings.backup

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ua.acclorite.book_story.ui.settings.backup.components.ExportBackupOption
import ua.acclorite.book_story.ui.settings.backup.components.RestoreBackupOption

fun LazyListScope.BackupSettingsCategory() {
    item {
        Spacer(modifier = Modifier.height(8.dp))
    }

    item {
        ExportBackupOption()
    }

    item {
        RestoreBackupOption()
    }

    item {
        Spacer(modifier = Modifier.height(16.dp))
    }
}
