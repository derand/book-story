/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.domain.use_case.book

import ua.acclorite.book_story.core.log.logI
import ua.acclorite.book_story.core.log.logW
import ua.acclorite.book_story.domain.model.library.Book
import ua.acclorite.book_story.domain.repository.BookRepository
import ua.acclorite.book_story.domain.service.FileProvider
import javax.inject.Inject

private const val TAG = "AddPreviewToLibrary"

/**
 * Promotes a previewed book into the library.
 *
 * This is the one place in the preview feature where storage permissions come
 * into it at all. A library book is a path resolved through a persisted grant on
 * the folder above it, so a book can only be kept if such a grant exists — and
 * the grant the file manager handed over is not one, it dies with the task.
 */
class AddPreviewToLibraryUseCase @Inject constructor(
    private val bookRepository: BookRepository,
    private val fileProvider: FileProvider
) {

    sealed interface Result {
        /**
         * The book is in the library and will open like any other. Carries the
         * row as written, because promoting changes more than a flag — the
         * caller's copy is stale the moment this returns, and the reader writes
         * its whole book row back on every settled scroll.
         */
        data class Added(val book: Book) : Result

        /**
         * Nothing reaches the file yet. [folder] is where the picker should
         * start, or null when even that is unknown.
         */
        data class NeedsGrant(val folder: String?) : Result

        /** The file could not be read, so there was nothing to keep. */
        data object Failed : Result
    }

    suspend operator fun invoke(book: Book): Result {
        // No location to remember, so there is no folder to ask for and nothing
        // to find the book by later: the Downloads provider builds its document
        // ids out of MediaStore row numbers, and downloading a book is the
        // commonest way to get one. Keeping such a book means keeping the file.
        if (book.filePath.isBlank()) {
            logI(TAG, "[${book.title}] has no location; storing a copy of it.")
            return store(book)
        }

        // Exactly the question the reader will ask on every future open: does any
        // persisted grant reach this path? Asking it here means the book is never
        // added in a state where it could not be opened again.
        //
        // Asked without the preview's own URI, which would answer yes and mean
        // nothing — that URI is good only while the task that received it lives,
        // and this is the decision to keep the book past it.
        val reachable = fileProvider.getFileFromBook(book.copy(previewUri = null)).getOrNull()
        if (reachable == null) {
            val folder = book.filePath.substringBeforeLast('/', missingDelimiterValue = "")
            logI(TAG, "[${book.title}] is not reachable yet; asking for [$folder].")
            return Result.NeedsGrant(folder.ifBlank { null })
        }

        // The URI goes with the promotion: keeping it would leave the book
        // reachable by a route that stops working the moment the app is killed,
        // hiding a broken path until the next restart.
        //
        // The identity comes from the file as it was just reached, not from the
        // preview: a preview arrives on whatever URI the handing-over app chose,
        // and the grant that will serve every future open may belong to another
        // provider entirely.
        val promoted = book.copy(
            inLibrary = true,
            previewUri = null,
            documentAuthority = reachable.documentAuthority,
            documentId = reachable.documentId
        )
        bookRepository.updateBook(promoted).onFailure {
            logW(TAG, "Could not add [${book.title}]: ${it.message}")
            return Result.Failed
        }

        logI(TAG, "Added [${book.title}] to the library.")
        return Result.Added(promoted)
    }

    /**
     * Keeps the book by keeping its file: the copy becomes the book's location,
     * and from then on it is an ordinary library book that happens to live in
     * the app's own directory.
     *
     * The original file rather than the parsed one. A parsed book cannot be
     * parsed again — the cache format carries a version, and raising it would
     * leave a book with no source unreadable for good — and the parse cache
     * keeps repeat opens fast on the copy exactly as it does on any other book.
     */
    private suspend fun store(book: Book): Result {
        val path = bookRepository.storeBookFile(book).getOrElse {
            logW(TAG, "Could not store [${book.title}]: ${it.message}")
            return Result.Failed
        }

        // No document identity: the book is a file in the app's own directory now,
        // and the id it arrived with belongs to a provider that no longer has
        // anything to do with it.
        val promoted = book.copy(
            inLibrary = true,
            previewUri = null,
            filePath = path,
            documentAuthority = null,
            documentId = null
        )
        bookRepository.updateBook(promoted).onFailure {
            logW(TAG, "Could not add [${book.title}]: ${it.message}")
            bookRepository.deleteBookFile(book.id)
            return Result.Failed
        }

        logI(TAG, "Added [${book.title}] to the library, holding its file.")
        return Result.Added(promoted)
    }
}
