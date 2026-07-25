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
import ua.acclorite.book_story.data.local.room.BookDatabase
import ua.acclorite.book_story.data.model.file.CachedFile
import ua.acclorite.book_story.data.settings.SettingsManager
import ua.acclorite.book_story.data.mapper.book.BookMapper
import ua.acclorite.book_story.data.mapper.file.FileMapper
import ua.acclorite.book_story.data.parser.cover.CoverParser
import ua.acclorite.book_story.data.parser.image.BookImageLoader
import ua.acclorite.book_story.data.parser.text.TextParser
import ua.acclorite.book_story.domain.model.file.File
import ua.acclorite.book_story.domain.model.library.Book
import ua.acclorite.book_story.domain.model.reader.ParsedText
import ua.acclorite.book_story.domain.model.reader.ReaderImage
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
                        // Hit: the stored text carries image metadata only —
                        // refill any image bytes from cached blobs or the source.
                        fillImages(cached, cachedFile, maxBytes, cacheImages)
                    } else {
                        textParser.parse(cachedFile).also { fresh ->
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
     * Refills empty image bytes on a cache hit: cached blobs first, then the
     * source book (one pass). When [cacheImages] is on, bytes read from the
     * source are persisted as blobs for the next hit. Returns [parsed] unchanged
     * when there are no images to fill or none could be resolved.
     */
    private fun fillImages(
        parsed: ParsedText,
        cachedFile: CachedFile,
        maxBytes: Long,
        cacheImages: Boolean
    ): ParsedText {
        val missingSrcs = parsed.text.asSequence()
            .filterIsInstance<ReaderText.Image>()
            .map { it.image }
            .filter { it.bytes.isEmpty() }
            .map { it.src }
            .toSet()
        if (missingSrcs.isEmpty()) return parsed

        val fromBlobs = parseCache.readImageBlobs(
            cachedFile.path, cachedFile.size, cachedFile.lastModified, missingSrcs
        )
        val stillMissing = missingSrcs - fromBlobs.keys
        val fromSource = if (stillMissing.isNotEmpty()) {
            bookImageLoader.loadImageBytes(cachedFile, stillMissing)
        } else {
            emptyMap()
        }

        if (cacheImages && fromSource.isNotEmpty()) {
            parseCache.writeImageBlobs(
                cachedFile.path, cachedFile.size, cachedFile.lastModified,
                fromSource, maxBytes
            )
        }

        val bytesBySrc = fromBlobs + fromSource
        if (bytesBySrc.isEmpty()) return parsed

        return parsed.copy(
            text = parsed.text.map { element ->
                if (element is ReaderText.Image && element.image.bytes.isEmpty()) {
                    val bytes = bytesBySrc[element.image.src] ?: return@map element
                    element.copy(
                        image = ReaderImage(
                            id = element.image.id,
                            src = element.image.src,
                            bytes = bytes,
                            width = element.image.width,
                            height = element.image.height
                        )
                    )
                } else {
                    element
                }
            }
        )
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