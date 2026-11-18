/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.presentation.browse.model

/*
 * A file is matched by its `uri`, which no two files share. Its `path` is
 * only what is shown: one path can be a prefix of another (`War.fb2`,
 * `War.fb2.zip`), and a provider may report the same path for two files.
 */

/** Flips the file with this [uri]; every other file keeps its state. */
fun List<SelectableFile>.toggled(uri: String): List<SelectableFile> =
    map { file -> if (file.uri == uri) file.copy(selected = !file.selected) else file }

/** Selects every file of [files], however many; the rest keep their state. */
fun List<SelectableFile>.withSelected(files: List<SelectableFile>): List<SelectableFile> {
    val uris = files.mapTo(HashSet()) { it.uri }
    return map { file -> if (file.uri in uris) file.copy(selected = true) else file }
}
