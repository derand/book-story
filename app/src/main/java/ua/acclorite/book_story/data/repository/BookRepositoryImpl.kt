/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.data.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import ua.acclorite.book_story.core.CoverImage
import ua.acclorite.book_story.core.log.bookTimingNote
import ua.acclorite.book_story.core.log.timed
import ua.acclorite.book_story.data.cache.ImageMemoryBudget
import ua.acclorite.book_story.data.cache.ParseCache
import ua.acclorite.book_story.data.cache.ReaderImageFiles
import ua.acclorite.book_story.data.local.room.BookDatabase
import ua.acclorite.book_story.data.settings.SettingsManager
import ua.acclorite.book_story.data.mapper.book.BookMapper
import ua.acclorite.book_story.data.mapper.file.FileMapper
import ua.acclorite.book_story.data.parser.cover.CoverParser
import ua.acclorite.book_story.data.parser.image.BookImageLoader
import ua.acclorite.book_story.data.parser.text.TextParser
import ua.acclorite.book_story.domain.model.file.File
import ua.acclorite.book_story.domain.model.library.Book
import ua.acclorite.book_story.domain.model.reader.BookImage
import ua.acclorite.book_story.domain.model.reader.ParsedText
import ua.acclorite.book_story.domain.model.reader.ReaderText
import ua.acclorite.book_story.domain.repository.BookRepository
import ua.acclorite.book_story.domain.service.FileProvider
import kotlin.coroutines.coroutineContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BookRepositoryImpl @Inject constructor(
    private val database: BookDatabase,
    private val bookMapper: BookMapper,
    private val fileMapper: FileMapper,
    private val coverParser: CoverParser,
    private val textParser: TextParser,
    private val fileProvider: FileProvider,
    private val parseCache: ParseCache,
    private val readerImageFiles: ReaderImageFiles,
    private val bookImageLoader: BookImageLoader,
    private val settings: SettingsManager
) : BookRepository {

    override suspend fun searchBooks(query: String): Result<List<Book>> = runCatching {
        withContext(Dispatchers.IO) {
            database.bookDao.searchBooks(query).map { bookMapper.toBook(it) }
        }
    }

    override suspend fun getBook(bookId: Int): Result<Book> = runCatching {
        withContext(Dispatchers.IO) {
            database.bookDao.findBookById(bookId).let {
                if (it == null) throw NoSuchElementException("Couldn't get book [$bookId].")
                else bookMapper.toBook(it)
            }
        }
    }

    override suspend fun getText(bookId: Int): Result<ParsedText> {
        return withContext(Dispatchers.IO) {
            timed("  open: book row") { getBook(bookId) }
                .mapCatching { book ->
                    // Walks every persisted SAF tree looking for the book's path,
                    // a ContentResolver query per directory — hence timed on its own.
                    timed("  open: find file") {
                        fileProvider.getFileFromBook(book).getOrThrow()
                    }
                }
                .mapCatching { cachedFile ->
                    // A size-cap of 0 means the parse cache is disabled entirely.
                    val capMb = settings.parseCacheSizeMb.lastValue
                    val cachingEnabled = capMb > 0
                    val maxBytes = capMb.toLong() * 1024 * 1024
                    val cacheImages = settings.cacheImagesInBooks.lastValue

                    // The parse-cache key, resolved before the cache is consulted
                    // so its cost is not counted as cache time. Each of the three
                    // is a lazy property that may be a ContentResolver round-trip
                    // against the document URI; `size` and `lastModified` share
                    // one query, so whichever is asked for first pays for both.
                    val path = timed("  open: key path") { cachedFile.path }
                    val size = timed("  open: key size") { cachedFile.size }
                    val lastModified = timed("  open: key modified") {
                        cachedFile.lastModified
                    }

                    timed("getText", describe = { it.describeForTiming() }) {
                        val cached = if (cachingEnabled) parseCache.read(
                            path, size, lastModified
                        ) else null

                        bookTimingNote {
                            val state = when {
                                !cachingEnabled -> "cache off"
                                cached != null -> "cache HIT"
                                else -> "cache MISS"
                            }
                            "$state — ${cachedFile.name}, ${size / 1024} KB"
                        }

                        if (cached != null) {
                            // Hit: the stored text carries image metadata only. Its
                            // bytes are loaded in the background (see [loadBookImages])
                            // so the reader can show the text right away; the layout
                            // is unaffected, image slots are sized from the cached
                            // width/height.
                            cached
                        } else {
                            // With images off the reader never renders them, so the
                            // parse keeps their size (the cached text is the same
                            // either way) but not their bytes — on an image-heavy
                            // book that is tens of MB held for the whole session.
                            timed("  parse", describe = { it.describeForTiming() }) {
                                textParser.parse(
                                    cachedFile,
                                    keepImageBytes = settings.images.lastValue
                                )
                            }.also { fresh ->
                                // Best-effort caching; skip empty/failed parses.
                                if (cachingEnabled && fresh.text.isNotEmpty()) {
                                    timed("  cache write") {
                                        parseCache.write(
                                            path,
                                            size,
                                            lastModified,
                                            fresh,
                                            maxBytes = maxBytes,
                                            images = if (cacheImages) {
                                                fresh.collectImageBytes()
                                            } else null
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
        }
    }

    /**
     * Resolves [srcs], reporting each through [onImage] as soon as it is
     * available, cheapest source first:
     *
     * 1. cached blobs — already on disk, so nothing is read or written;
     * 2. session files an earlier open of this book left behind — likewise;
     * 3. [parsed] bytes a fresh parse just produced;
     * 4. a scan of the source book file for whatever is left.
     *
     * Bytes from 3 and 4 have to be put somewhere: a parse-cache blob when image
     * caching is on (so the next open lands in 1), memory while the budget lasts,
     * a session file after that (so the next open lands in 2). Only the budgeted
     * few are bytes; the rest of the book is files.
     *
     * Runs off the reader's critical path — the text is already on screen.
     */
    override suspend fun loadBookImages(
        bookId: Int,
        srcs: Set<String>,
        parsed: Map<String, ByteArray>,
        onImage: (src: String, image: BookImage.Ready) -> Unit
    ): Result<Unit> {
        if (srcs.isEmpty()) return Result.success(Unit)
        return withContext(Dispatchers.IO) {
            // The source scan is plain blocking I/O with nowhere to observe
            // cancellation, so it runs on after the pass is cancelled. Publishing
            // has to check for itself, or it would keep writing files into a
            // directory the closing reader has just deleted.
            val pass = coroutineContext.job
            getBook(bookId)
                .mapCatching { fileProvider.getFileFromBook(it).getOrThrow() }
                .mapCatching { cachedFile ->
                    val capMb = settings.parseCacheSizeMb.lastValue
                    val maxBytes = capMb.toLong() * 1024 * 1024
                    val cacheImages = capMb > 0 && settings.cacheImagesInBooks.lastValue

                    val fromBlobs = if (capMb > 0) parseCache.imageBlobFiles(
                        cachedFile.path, cachedFile.size, cachedFile.lastModified, srcs
                    ) else emptyMap()
                    fromBlobs.forEach { (src, file) -> onImage(src, BookImage.Ready.InFile(file)) }

                    // Written while this book was open earlier in the session and
                    // kept on purpose; re-extracting them would only overwrite
                    // identical files.
                    val fromSession = readerImageFiles.existing(bookId, srcs - fromBlobs.keys)
                    fromSession.forEach { (src, file) ->
                        onImage(src, BookImage.Ready.InFile(file))
                    }

                    val budget = ImageMemoryBudget()
                    var wroteBlob = false
                    fun publish(src: String, bytes: ByteArray) {
                        if (!pass.isActive) return
                        val blob = if (cacheImages) parseCache.writeImageBlob(
                            cachedFile.path, cachedFile.size, cachedFile.lastModified,
                            src, bytes
                        ) else null
                        wroteBlob = wroteBlob || blob != null
                        when {
                            // A blob is written for the sake of the *next* session,
                            // so the budget has nothing to save here.
                            blob != null -> onImage(src, BookImage.Ready.InFile(blob))
                            budget.claim(bytes.size) ->
                                onImage(src, BookImage.Ready.InMemory(bytes))
                            else -> readerImageFiles.write(bookId, src, bytes)
                                ?.let { onImage(src, BookImage.Ready.InFile(it)) }
                        }
                    }

                    var stillMissing = srcs - fromBlobs.keys - fromSession.keys
                    // A fresh parse already read these; handing them over now is
                    // what lets the reader drop them from the text.
                    stillMissing.forEach { src -> parsed[src]?.let { publish(src, it) } }

                    stillMissing = stillMissing - parsed.keys
                    if (stillMissing.isNotEmpty()) {
                        bookImageLoader.loadImages(cachedFile, stillMissing, ::publish)
                    }

                    // One cap check for the whole pass, rather than one per image.
                    if (wroteBlob && pass.isActive) parseCache.trimToSizeKeeping(
                        cachedFile.path, cachedFile.size, cachedFile.lastModified, maxBytes
                    )
                }
        }
    }

    override suspend fun keepOnlyBookImages(bookId: Int): Result<Unit> = runCatching {
        withContext(Dispatchers.IO) {
            readerImageFiles.keepOnly(bookId)
        }
    }

    /** What the timing log says about a parsed book: how much text, how many images. */
    private fun ParsedText.describeForTiming(): String {
        val images = text.filterIsInstance<ReaderText.Image>()
        val chars = text.filterIsInstance<ReaderText.Text>().sumOf { it.line.length }
        return "chars=$chars images=${images.size} " +
                "imageBytes=${images.sumOf { it.image.bytes.size } / 1024} KB"
    }

    /** Encoded bytes of every image that has them, keyed by src. */
    private fun ParsedText.collectImageBytes(): Map<String, ByteArray> =
        text.asSequence()
            .filterIsInstance<ReaderText.Image>()
            .map { it.image }
            .filter { it.bytes.isNotEmpty() }
            .associate { it.src to it.bytes }

    override suspend fun getFileFromBook(bookId: Int): Result<File> {
        return withContext(Dispatchers.IO) {
            getBook(bookId)
                .mapCatching { fileProvider.getFileFromBook(it).getOrThrow() }
                .mapCatching { fileMapper.toFile(it) }
        }
    }

    override suspend fun addBook(book: Book): Result<Unit> = runCatching {
        withContext(Dispatchers.IO) {
            database.bookDao.insertBook(bookMapper.toBookEntity(book))
        }
    }

    override suspend fun updateBook(book: Book): Result<Unit> = runCatching {
        withContext(Dispatchers.IO) {
            database.bookDao.updateBook(bookMapper.toBookEntity(book)).also {
                if (it == 0) throw Exception("Could not update book in database.")
            }
        }
    }

    override suspend fun deleteBook(book: Book): Result<Unit> = runCatching {
        withContext(Dispatchers.IO) {
            database.bookDao.deleteBook(bookMapper.toBookEntity(book)).also {
                if (it == 0) throw Exception("Could not delete book in database.")
            }
        }
    }

    override suspend fun getDefaultCover(book: Book): Result<CoverImage?> = runCatching {
        return withContext(Dispatchers.IO) {
            fileProvider.getFileFromBook(book).mapCatching {
                coverParser.parse(it)
            }
        }
    }
}