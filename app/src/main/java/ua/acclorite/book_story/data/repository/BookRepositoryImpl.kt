/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.data.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ua.acclorite.book_story.core.CoverImage
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
import ua.acclorite.book_story.domain.model.reader.ParsedText
import ua.acclorite.book_story.domain.model.reader.ReaderText
import ua.acclorite.book_story.domain.repository.BookRepository
import ua.acclorite.book_story.domain.service.FileProvider
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
            getBook(bookId)
                .mapCatching { fileProvider.getFileFromBook(it).getOrThrow() }
                .mapCatching { cachedFile ->
                    // A size-cap of 0 means the parse cache is disabled entirely.
                    val capMb = settings.parseCacheSizeMb.lastValue
                    val cachingEnabled = capMb > 0
                    val maxBytes = capMb.toLong() * 1024 * 1024
                    val cacheImages = settings.cacheImagesInBooks.lastValue

                    val cached = if (cachingEnabled) parseCache.read(
                        cachedFile.path, cachedFile.size, cachedFile.lastModified
                    ) else null

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
                        textParser.parse(
                            cachedFile,
                            keepImageBytes = settings.images.lastValue
                        ).also { fresh ->
                            // Best-effort caching; skip empty/failed parses.
                            if (cachingEnabled && fresh.text.isNotEmpty()) {
                                parseCache.write(
                                    cachedFile.path,
                                    cachedFile.size,
                                    cachedFile.lastModified,
                                    fresh,
                                    maxBytes = maxBytes,
                                    images = if (cacheImages) fresh.collectImageBytes() else null
                                )
                            }
                        }
                    }
                }
        }
    }

    /**
     * Resolves [srcs] to files, reporting each through [onImage] as soon as it
     * is available, cheapest source first:
     *
     * 1. cached blobs — already on disk, so nothing is read or written;
     * 2. [parsed] bytes a fresh parse just produced;
     * 3. a scan of the source book file for whatever is left.
     *
     * Bytes from 2 and 3 are written as parse-cache blobs when image caching is
     * on (so the next open lands in 1), otherwise to the transient session
     * directory. Either way the reader is handed a file and holds no bytes.
     *
     * Runs off the reader's critical path — the text is already on screen.
     */
    override suspend fun loadBookImages(
        bookId: Int,
        srcs: Set<String>,
        parsed: Map<String, ByteArray>,
        onImage: (src: String, file: java.io.File) -> Unit
    ): Result<Unit> {
        if (srcs.isEmpty()) return Result.success(Unit)
        return withContext(Dispatchers.IO) {
            getBook(bookId)
                .mapCatching { fileProvider.getFileFromBook(it).getOrThrow() }
                .mapCatching { cachedFile ->
                    val capMb = settings.parseCacheSizeMb.lastValue
                    val maxBytes = capMb.toLong() * 1024 * 1024
                    val cacheImages = capMb > 0 && settings.cacheImagesInBooks.lastValue

                    val fromBlobs = if (capMb > 0) parseCache.imageBlobFiles(
                        cachedFile.path, cachedFile.size, cachedFile.lastModified, srcs
                    ) else emptyMap()
                    fromBlobs.forEach { (src, file) -> onImage(src, file) }

                    var wroteBlob = false
                    fun publish(src: String, bytes: ByteArray) {
                        val blob = if (cacheImages) parseCache.writeImageBlob(
                            cachedFile.path, cachedFile.size, cachedFile.lastModified,
                            src, bytes
                        ) else null
                        wroteBlob = wroteBlob || blob != null
                        val file = blob ?: readerImageFiles.write(bookId, src, bytes)
                        if (file != null) onImage(src, file)
                    }

                    var stillMissing = srcs - fromBlobs.keys
                    // A fresh parse already read these; spilling them now is what
                    // lets the reader drop them from memory.
                    stillMissing.forEach { src -> parsed[src]?.let { publish(src, it) } }

                    stillMissing = stillMissing - parsed.keys
                    if (stillMissing.isNotEmpty()) {
                        bookImageLoader.loadImages(cachedFile, stillMissing, ::publish)
                    }

                    // One cap check for the whole pass, rather than one per image.
                    if (wroteBlob) parseCache.trimToSizeKeeping(
                        cachedFile.path, cachedFile.size, cachedFile.lastModified, maxBytes
                    )
                }
        }
    }

    override suspend fun clearBookImages(bookId: Int): Result<Unit> = runCatching {
        withContext(Dispatchers.IO) {
            readerImageFiles.clear(bookId)
        }
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