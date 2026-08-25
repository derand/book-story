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
import ua.acclorite.book_story.core.helpers.mapCatchingCancellable
import ua.acclorite.book_story.core.helpers.runCatchingCancellable
import ua.acclorite.book_story.core.log.bookTimingNote
import ua.acclorite.book_story.core.log.logE
import ua.acclorite.book_story.core.log.timed
import ua.acclorite.book_story.data.cache.ImageMemoryBudget
import ua.acclorite.book_story.data.cache.ParseCache
import ua.acclorite.book_story.data.cache.ReaderImageFiles
import ua.acclorite.book_story.data.local.dto.BookEntity
import ua.acclorite.book_story.data.local.room.BookDatabase
import ua.acclorite.book_story.data.model.file.CachedFile
import ua.acclorite.book_story.data.settings.SettingsManager
import ua.acclorite.book_story.data.storage.OwnedBookFiles
import ua.acclorite.book_story.data.mapper.book.BookMapper
import ua.acclorite.book_story.data.mapper.file.FileMapper
import ua.acclorite.book_story.data.parser.cover.CoverParser
import ua.acclorite.book_story.data.parser.image.BookImageLoader
import ua.acclorite.book_story.data.parser.text.TextParser
import ua.acclorite.book_story.domain.model.file.File
import ua.acclorite.book_story.domain.model.library.Book
import ua.acclorite.book_story.domain.model.library.findForFile
import ua.acclorite.book_story.domain.model.reader.BookImage
import ua.acclorite.book_story.domain.model.reader.ParsedText
import ua.acclorite.book_story.domain.model.reader.ReaderText
import ua.acclorite.book_story.domain.repository.BookRepository
import ua.acclorite.book_story.domain.service.FileProvider
import kotlin.coroutines.coroutineContext
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "BookRepository"

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
    private val ownedBookFiles: OwnedBookFiles,
    private val bookImageLoader: BookImageLoader,
    private val settings: SettingsManager
) : BookRepository {

    /** The book, and the file [getText] resolved for it. */
    private data class OpenedBookFile(val bookId: Int, val file: CachedFile)

    /**
     * What the last [getText] resolved. [loadBookImages] runs a moment later for
     * the same book, and resolving it again is not free: [FileProvider] finds a
     * book by walking every persisted SAF tree, a ContentResolver query per
     * directory and the largest named step of an open, and for EPUB a second
     * [CachedFile] means a second copy of the whole book, since
     * [CachedFile.rawFile] is a per-instance `lazy`.
     *
     * Deliberately one entry rather than a cache keyed by path. It is read only by
     * the image pass that follows the open that wrote it, so it cannot serve a
     * stale grant or a path the user has since edited — the next open overwrites
     * it, and every other caller still asks [FileProvider].
     */
    @Volatile
    private var lastOpenedFile: OpenedBookFile? = null

    /**
     * The file this book names, and the identity it turned out to have.
     *
     * A row written before a document id was stored — every book already in a
     * library — learns its identity the first time it is resolved, which is the
     * first time anything can know it. Nothing is derived from the path: this is
     * what the provider answered about the file actually found. From then on the
     * book is reached by that id in one query, instead of by descending a tree a
     * listing at a time.
     *
     * The limit is inherited, not introduced: where a path is ambiguous the
     * fallback can find the wrong file, and now it remembers the wrong file. The
     * same open would have shown the same wrong book before, and only a path
     * could ever have been ambiguous.
     */
    private suspend fun fileOf(book: Book): CachedFile {
        val file = fileProvider.getFileFromBook(book).getOrThrow()

        val documentId = file.documentId
        if (documentId != null &&
            book.id > 0 &&
            (book.documentId != documentId || book.documentAuthority != file.documentAuthority)
        ) {
            runCatchingCancellable {
                database.bookDao.findBookById(book.id)?.let { entity ->
                    database.bookDao.updateBook(
                        entity.copy(
                            documentAuthority = file.documentAuthority,
                            documentId = documentId
                        )
                    )
                }
            }.onFailure { logE(TAG, "Could not remember the identity: ${it.message}") }
        }

        return file
    }

    override suspend fun getLibraryBooks(): Result<List<Book>> = runCatchingCancellable {
        withContext(Dispatchers.IO) {
            database.bookDao.getLibraryBooks().map { bookMapper.toBook(it) }
        }
    }

    override suspend fun getBook(bookId: Int): Result<Book> = runCatchingCancellable {
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
                .mapCatchingCancellable { book ->
                    // Walks every persisted SAF tree looking for the book's path,
                    // a ContentResolver query per directory — hence timed on its own.
                    timed("  open: find file") {
                        fileOf(book)
                    }.also { lastOpenedFile = OpenedBookFile(bookId, it) }
                        .let { book to it }
                }
                .mapCatchingCancellable { (book, cachedFile) ->
                    // A size-cap of 0 means the parse cache is disabled entirely.
                    val capMb = settings.parseCacheSizeMb.lastValue
                    // A source that reports neither its size nor its modification
                    // time cannot be cached against: the two exist to notice the
                    // file changing, and an entry that can never go stale can
                    // never be invalidated either.
                    val cachingEnabled = capMb > 0 && cachedFile.hasKnownMetadata
                    val maxBytes = capMb.toLong() * 1024 * 1024
                    val cacheImages = settings.cacheImagesInBooks.lastValue

                    // The parse-cache key, resolved before the cache is consulted
                    // so its cost is not counted as cache time. Each of the three
                    // is a lazy property that may be a ContentResolver round-trip
                    // against the document URI; `size` and `lastModified` share
                    // one query, so whichever is asked for first pays for both.
                    val key = timed("  open: key") { cachedFile.cacheKey }
                    val size = timed("  open: key size") { cachedFile.size }
                    val lastModified = timed("  open: key modified") {
                        cachedFile.lastModified
                    }

                    // The book has just learned its identity, so the entry
                    // written under the old one is moved rather than left to be
                    // parsed again into the very same text.
                    if (cachingEnabled && book.documentId == null &&
                        cachedFile.documentId != null
                    ) {
                        parseCache.rekey(
                            fromKey = cachedFile.legacyCacheKey,
                            toKey = key,
                            size = size,
                            lastModified = lastModified
                        )
                    }

                    timed("getText", describe = { it.describeForTiming() }) {
                        val cached = if (cachingEnabled) parseCache.read(
                            key, size, lastModified
                        ) else null

                        bookTimingNote {
                            val state = when {
                                capMb <= 0 -> "cache off"
                                !cachedFile.hasKnownMetadata ->
                                    "cache skipped — the provider reports no size or date"
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
                                            key,
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
            resolveOpenedFile(bookId)
                .mapCatchingCancellable { cachedFile ->
                    val capMb = settings.parseCacheSizeMb.lastValue
                    val maxBytes = capMb.toLong() * 1024 * 1024
                    // The same condition the text obeys: an entry can only be
                    // kept against a source that says how big it is and when it
                    // changed, and a blob lives inside the text's entry.
                    val cachingEnabled = capMb > 0 && cachedFile.hasKnownMetadata
                    val cacheImages = cachingEnabled && settings.cacheImagesInBooks.lastValue

                    val fromBlobs = if (cachingEnabled) parseCache.imageBlobFiles(
                        cachedFile.cacheKey, cachedFile.size, cachedFile.lastModified, srcs
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
                            cachedFile.cacheKey, cachedFile.size, cachedFile.lastModified,
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
                        cachedFile.cacheKey, cachedFile.size, cachedFile.lastModified, maxBytes
                    )
                }
        }
    }

    /**
     * The book's file for the image pass: the one the open resolved a moment ago
     * when it is the same book, and the ordinary walk otherwise — the reader can be
     * entered by a restored back stack, which this process never opened.
     */
    private suspend fun resolveOpenedFile(bookId: Int): Result<CachedFile> {
        lastOpenedFile?.takeIf { it.bookId == bookId }?.let { opened ->
            bookTimingNote { "images: reused the file the open resolved" }
            return Result.success(opened.file)
        }
        return getBook(bookId).mapCatchingCancellable {
            timed("  images: find file") { fileOf(it) }
        }
    }

    override suspend fun keepOnlyBookImages(bookId: Int): Result<Unit> = runCatchingCancellable {
        withContext(Dispatchers.IO) {
            readerImageFiles.keepOnly(bookId)
        }
    }

    override suspend fun dropBookImages(bookId: Int): Result<Unit> = runCatchingCancellable {
        withContext(Dispatchers.IO) {
            readerImageFiles.drop(bookId)
        }
    }

    override suspend fun storeBookFile(book: Book): Result<String> = runCatchingCancellable {
        withContext(Dispatchers.IO) {
            val source = fileOf(book)
            val sourceKey = source.cacheKey
            val size = source.size
            val lastModified = source.lastModified

            val stored = ownedBookFiles.store(source, book.id)
                ?: throw IllegalStateException("Could not store ${source.name}.")

            // The preview parsed this book a moment ago and cached the result
            // under the path it had then. Moving the entry is what keeps the
            // second open instant; without it the book is parsed again to
            // produce exactly the same text.
            parseCache.rekey(
                fromKey = sourceKey,
                toKey = stored.absolutePath,
                size = size,
                lastModified = lastModified
            )

            stored.absolutePath
        }
    }

    override suspend fun deleteBookFile(bookId: Int): Result<Unit> = runCatchingCancellable {
        withContext(Dispatchers.IO) {
            ownedBookFiles.delete(bookId)
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
                .mapCatchingCancellable { fileOf(it) }
                .mapCatchingCancellable { fileMapper.toFile(it) }
        }
    }

    override suspend fun addBook(book: Book): Result<Int> = runCatchingCancellable {
        withContext(Dispatchers.IO) {
            database.bookDao.insertBook(bookMapper.toBookEntity(book)).toInt()
        }
    }

    override suspend fun findLibraryBookForFile(
        filePath: String,
        fileName: String,
        documentAuthority: String?,
        documentId: String?
    ): Result<Book?> = runCatchingCancellable {
        withContext(Dispatchers.IO) {
            // Matched in Kotlin rather than in SQL: a LIKE over the path would
            // treat '%' and '_' in a file name as wildcards, and the library is
            // the same handful of rows every other screen already reads whole.
            database.bookDao.getLibraryBooks()
                .map(bookMapper::toBook)
                .findForFile(
                    filePath = filePath,
                    fileName = fileName,
                    documentAuthority = documentAuthority,
                    documentId = documentId
                )
        }
    }

    override suspend fun findPreviews(): Result<List<Book>> = runCatchingCancellable {
        withContext(Dispatchers.IO) {
            database.bookDao.findPreviews().map(bookMapper::toBook)
        }
    }

    override suspend fun updateBook(book: Book): Result<Unit> = runCatchingCancellable {
        withContext(Dispatchers.IO) {
            database.bookDao.updateBook(withStoredIdentity(book)).also {
                if (it == 0) throw Exception("Could not update book in database.")
            }
        }
    }

    /**
     * The row to write, with the document identity that belongs to it rather
     * than the one the caller happens to be holding.
     *
     * Every screen updates a book by writing the whole row back from a [Book] it
     * loaded earlier, and those copies disagree about the identity: the reader's
     * predates the open that learned it, the info screen's is current. The rule
     * cannot be about which of them is talking, so [identityToWrite] states it
     * about the book instead.
     */
    private suspend fun withStoredIdentity(book: Book): BookEntity {
        val entity = bookMapper.toBookEntity(book)
        val stored = database.bookDao.findBookById(book.id)

        val identity = identityToWrite(
            incoming = BookIdentity(book.filePath, book.documentAuthority, book.documentId),
            stored = stored?.let {
                BookIdentity(it.filePath, it.documentAuthority, it.documentId)
            }
        )

        return entity.copy(
            documentAuthority = identity.documentAuthority,
            documentId = identity.documentId
        )
    }

    override suspend fun deleteBook(book: Book): Result<Unit> = runCatchingCancellable {
        withContext(Dispatchers.IO) {
            database.bookDao.deleteBook(bookMapper.toBookEntity(book)).also {
                if (it == 0) throw Exception("Could not delete book in database.")
            }
        }
    }

    override suspend fun getDefaultCover(book: Book): Result<CoverImage?> = runCatchingCancellable {
        return withContext(Dispatchers.IO) {
            runCatchingCancellable { coverParser.parse(fileOf(book)) }
        }
    }
}