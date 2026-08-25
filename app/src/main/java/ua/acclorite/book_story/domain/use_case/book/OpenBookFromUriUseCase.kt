/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.domain.use_case.book

import ua.acclorite.book_story.core.log.logE
import ua.acclorite.book_story.core.log.logI
import ua.acclorite.book_story.domain.model.library.findForFile
import ua.acclorite.book_story.domain.repository.BookRepository
import ua.acclorite.book_story.domain.repository.FileSystemRepository
import ua.acclorite.book_story.domain.use_case.file_system.GetBookFromFileUseCase
import javax.inject.Inject

private const val TAG = "OpenBookFromUri"

/**
 * Turns a file handed over by another app into a book the reader can open.
 *
 * The book the user already has takes precedence: opening a file that is
 * already in the library reaches that book, with its reading position and its
 * statistics, rather than a second copy of it.
 *
 * Anything else becomes a **preview** — a real row that is not in the library.
 * It is a row because the reader is keyed on a book id from end to end, and it
 * is out of the library because the user has done nothing but look at the file
 * yet. Adding it is theirs to decide; see [AddPreviewToLibraryUseCase].
 */
class OpenBookFromUriUseCase @Inject constructor(
    private val fileSystemRepository: FileSystemRepository,
    private val bookRepository: BookRepository,
    private val getBookFromFileUseCase: GetBookFromFileUseCase,
    private val addBookUseCase: AddBookUseCase
) {

    sealed interface Result {
        /** Open this book: either the user's own, or a fresh preview. */
        data class Open(val bookId: Int) : Result

        /** The file could be read but is not a book this app can open. */
        data object Unsupported : Result

        /** The file could not be read at all — a revoked grant, or it is gone. */
        data object Unreadable : Result
    }

    suspend operator fun invoke(uri: String): Result {
        logI(TAG, "Opening [$uri].")

        val file = fileSystemRepository.getFileFromUri(uri).getOrElse {
            logE(TAG, "Could not read the file: ${it.message}")
            return Result.Unreadable
        }

        bookRepository.findLibraryBookForFile(
            filePath = file.path,
            fileName = file.name,
            documentAuthority = file.documentAuthority,
            documentId = file.documentId
        ).getOrNull()?.let { existing ->
            logI(TAG, "Already in the library as [${existing.id}].")
            return Result.Open(existing.id)
        }

        // The same file arriving twice must reach the same preview, not parse a
        // second one. It happens for ordinary reasons — the process is restored
        // holding the intent that started it, or the user taps the file again —
        // and a duplicate would cost a full parse and leave a stray row behind.
        bookRepository.findPreviews().getOrNull()
            ?.findForFile(
                filePath = file.path,
                fileName = file.name,
                documentAuthority = file.documentAuthority,
                documentId = file.documentId
            )
            ?.let { existing ->
                logI(TAG, "Already being previewed as [${existing.id}].")
                return Result.Open(existing.id)
            }

        val parsed = getBookFromFileUseCase(file) ?: return Result.Unsupported

        // Every field of the parsed book is kept, including the path, which is
        // what makes adding it later a matter of a grant rather than a re-import.
        val id = addBookUseCase(
            parsed.first.copy(inLibrary = false, previewUri = uri),
            parsed.second
        )
            ?: return Result.Unsupported

        logI(TAG, "Previewing [${parsed.first.title}] as [$id].")
        return Result.Open(id)
    }
}
