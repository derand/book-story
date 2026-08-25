/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.data.service

import android.app.Application
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import androidx.core.net.toUri
import ua.acclorite.book_story.core.helpers.rethrowIfCancellation
import ua.acclorite.book_story.core.helpers.runCatchingCancellable
import ua.acclorite.book_story.core.log.bookTimingNote
import ua.acclorite.book_story.data.model.file.CachedFile
import ua.acclorite.book_story.data.model.file.CachedFileCompat
import ua.acclorite.book_story.data.storage.OwnedBookFiles
import ua.acclorite.book_story.domain.model.library.Book
import ua.acclorite.book_story.domain.service.FileProvider
import javax.inject.Inject

class FileProviderImpl @Inject constructor(
    private val application: Application,
    private val ownedBookFiles: OwnedBookFiles
) : FileProvider {

    /**
     * The last nesting decision, against the set of roots it was made for. Browse
     * re-reads its list on every refresh, and each pair of roots can cost a round
     * trip to a provider that is not on this device. Adding or removing a grant,
     * or a source going dark, changes the set and so the key.
     */
    private var nestingCache: Pair<Set<SafRoot>, List<SafRoot>>? = null

    /**
     * Which granted tree last authorised a document of each authority, so the
     * next book is not offered to the wrong one first. Nothing depends on it
     * being right: a stale entry costs the same round trip a wrong guess always
     * did, and the loop moves on.
     */
    private val lastServingTree = mutableMapOf<String?, Uri>()

    override fun getFileFromBook(book: Book): Result<CachedFile> = runCatchingCancellable {
        // A book being previewed is reached by the URI another app handed over,
        // not by descending a granted tree: there is no grant, and that is the
        // whole reason it is a preview. The URI is good for as long as the task
        // holding the intent, which is exactly as long as the preview itself —
        // a killed process takes both, and the start-up sweep clears the row.
        book.previewUri?.let { uri ->
            val file = CachedFileCompat.fromUri(application, uri.toUri())
            if (file.canAccess()) return@runCatchingCancellable file
            throw IllegalStateException("The preview's grant on $uri is gone.")
        }

        // A book the app holds a copy of, because the app that handed it over
        // exposed no location to remember. Nothing is granted and nothing is
        // descended: the path is a real path, in our own directory.
        if (ownedBookFiles.owns(book.filePath)) {
            val file = CachedFileCompat.fromFile(application, java.io.File(book.filePath))
            if (file.canAccess()) return@runCatchingCancellable file
            throw NoSuchElementException("The stored copy of ${book.title} is gone.")
        }

        // The identity the provider itself issued, when the book carries one: the
        // document URI is built from it and tried against each granted tree of
        // that authority, so finding the file costs one query instead of a
        // listing per level. Only a tree above the document authorises it — the
        // others fail with "is not a descendant of", which is a refusal and not
        // an answer, so the loop simply moves on.
        book.documentId?.let { documentId ->
            application.contentResolver.persistedUriPermissions
                .map { it.uri }
                .filter { it.authority == book.documentAuthority }
                // The tree that answered last, first. Only a tree above the
                // document authorises it, and the refusal from one that is not
                // above it costs a round trip like any other query; with several
                // trees granted on one provider, most books sit under the same one.
                .sortedByDescending { it == lastServingTree[book.documentAuthority] }
                .forEach { tree ->
                    val file = CachedFileCompat.fromUri(
                        application,
                        DocumentsContract.buildDocumentUriUsingTree(tree, documentId)
                    )
                    if (file.canAccess()) {
                        lastServingTree[book.documentAuthority] = tree
                        return@runCatchingCancellable file
                    }
                }
            bookTimingNote { "find file: the document id is granted by no tree" }
        }

        val storages = application.contentResolver.persistedUriPermissions
            .map { permission -> CachedFileCompat.fromUri(application, permission.uri) }
            .filter { it.isDirectory }
            // Deepest root first. Roots from different providers can be prefixes of
            // one another — Google Drive reports "/storage" for a Drive folder,
            // which prefixes every path on the device — and the shallow one then
            // matches books it does not hold, costing a listing before the tree
            // that does. For a cloud provider that listing is a network round trip:
            // measured 379 ms against 101 ms for the same book with the deepest
            // root tried first.
            .sortedByDescending { it.path.length }

        storages.forEach { storage ->
            val segments = pathSegmentsUnder(storage.path, book.filePath) ?: return@forEach
            storage.descend(segments)?.let { return@runCatchingCancellable it }
        }

        // Three things the descent cannot follow, all of them properties of a
        // particular documents provider rather than of SAF: a display name
        // containing '/', which it would split into two segments; two children of
        // one directory sharing a name, where it commits to the first while a walk
        // enumerates both subtrees; and a tree whose root reports no path at all,
        // which leaves no route to split. None is reachable on external storage,
        // and none can be tested against a single provider, so the walk stays as
        // the fallback at the price every open used to pay, in the case that would
        // otherwise fail outright.
        //
        // The condition below is the *old* one, deliberately looser than the
        // descent's: a root with no path composes its children as "/<name>" and the
        // import wrote exactly that into the book row, so the walk still matches
        // where the descent had nothing to follow. A fallback that filters more
        // than what it falls back from is not a fallback.
        bookTimingNote { "find file: descent found nothing, walking the tree" }
        storages.forEach { storage ->
            if (!book.filePath.startsWith(storage.path, ignoreCase = true)) return@forEach
            storage.walk().forEach { file ->
                if (book.filePath.equals(file.path, ignoreCase = true)) {
                    return@runCatchingCancellable file
                }
            }
        }

        throw NoSuchElementException("Could not find file from book.")
    }

    /**
     * Walks [segments] down from this directory, one ContentResolver query per
     * level, and returns the file they name — or null if any level is missing, is
     * not a directory where the route needs one, or the route ends on a directory.
     *
     * Each child arrives from the directory cursor with its name already filled in,
     * so matching a segment costs nothing beyond the listing itself.
     */
    private fun CachedFile.descend(segments: List<String>): CachedFile? {
        var current = this
        segments.forEachIndexed { index, segment ->
            val child = current.listFiles().firstOrNull {
                it.name.equals(segment, ignoreCase = true)
            } ?: return null

            if (index == segments.lastIndex) return child.takeIf { !it.isDirectory }
            if (!child.isDirectory) return null
            current = child
        }
        return null
    }

    override fun getStorageFiles(): Result<List<CachedFile>> = runCatchingCancellable {
        // A tree that answers nothing is indistinguishable from an empty one: the
        // query returns a null cursor and `isDirectory` falls back to false.
        val readable = LinkedHashMap<SafRoot, CachedFile>()
        val trees = LinkedHashMap<SafRoot, Uri>()
        application.contentResolver.persistedUriPermissions.forEach { permission ->
            val storage = CachedFileCompat.fromUri(application, permission.uri)
            if (!storage.isDirectory) return@forEach

            val root = safRootOf(permission.uri)
            readable.putIfAbsent(root, storage)
            trees.putIfAbsent(root, permission.uri)
        }

        val listed = nestingCache
            ?.takeIf { (asked, _) -> asked == readable.keys }
            ?.second
            ?: rootsToList(readable.keys.toList()) { parent, child ->
                isChildDocument(trees.getValue(parent), trees.getValue(child))
            }.also { nestingCache = readable.keys.toSet() to it }

        listed.mapNotNull { readable[it] }
    }

    /**
     * Whether the provider considers [childTree] to sit inside [parentTree], or
     * null when it will not say — unsupported, or gone since the grant was taken.
     */
    private fun isChildDocument(parentTree: Uri, childTree: Uri): Boolean? {
        // Public only since API 29, and `minSdk` is 26. Below it there is no way
        // to ask at all, so the question goes unanswered and the id structure
        // decides — the same route a provider that will not answer takes.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null

        return try {
            DocumentsContract.isChildDocument(
                application.contentResolver,
                documentUriOf(parentTree),
                documentUriOf(childTree)
            )
        } catch (e: Throwable) {
            e.rethrowIfCancellation()
            null
        }
    }

    private fun documentUriOf(treeUri: Uri): Uri = DocumentsContract.buildDocumentUriUsingTree(
        treeUri,
        DocumentsContract.getTreeDocumentId(treeUri)
    )

    private fun safRootOf(treeUri: Uri) = SafRoot(
        authority = treeUri.authority,
        treeDocumentId = DocumentsContract.getTreeDocumentId(treeUri)
    )
}

/**
 * The route from [rootPath] down to [filePath] — one display name per element — or
 * null when [filePath] does not name something under [rootPath].
 *
 * This is what lets a book be found by descending instead of by enumerating: a
 * [CachedFile]'s path is composed as `<parent>/<display name>`, so the segments of
 * a path and the names in a directory listing are the same strings by
 * construction.
 *
 * Deliberately strict — a route it refuses is a route the walk would not have
 * matched either, since the walk compares against those same composed paths. In
 * particular a prefix only counts when it ends on a separator, so a tree at
 * `/Books` does not claim `/BooksOld/novel.fb2`; the old code compared prefixes
 * without that boundary and was saved only by the full-path comparison that
 * followed.
 */
internal fun pathSegmentsUnder(rootPath: String, filePath: String): List<String>? {
    val root = rootPath.trim().trimEnd('/')
    val path = filePath.trim().trimEnd('/')

    if (root.isEmpty() || path.length <= root.length) return null
    if (!path.startsWith(root, ignoreCase = true)) return null
    if (path[root.length] != '/') return null

    val segments = path.substring(root.length + 1).split('/')
    // An empty segment means a path no listing can produce ("a//b"), so there is
    // nothing to descend to.
    if (segments.any { it.isBlank() }) return null

    return segments
}
/**
 * A granted tree, named the way its provider names it rather than by a path.
 *
 * A path is the wrong identity for a tree: `DocumentFileCompat.getAbsolutePath`
 * synthesises one for any provider that is not `com.android.externalstorage`, and
 * Google Drive answers `/storage` for every folder it hosts — a string that is a
 * prefix of every real path on the device and identical for two unrelated folders.
 * The authority and the tree's document id are what the platform actually
 * promises: unique within a provider, and durable, since long-term permission
 * grants are issued against them.
 */
internal data class SafRoot(val authority: String?, val treeDocumentId: String)

/**
 * The roots worth listing: duplicates collapsed, and any root that lies inside
 * another dropped, so a book under two granted trees is offered once.
 *
 * [isDescendant] is the provider's own answer and is asked **only** about two
 * roots of one authority — a folder on Drive cannot be inside a folder on device
 * storage whatever their strings say, and `DocumentsContract.isChildDocument`
 * throws when asked across authorities. It may return null where the provider
 * cannot say, and then [documentIdContains] decides.
 *
 * The rule **fails open**: anything unknown keeps both roots. Listing a book
 * twice is a small harm; the alternative, which is what comparing invented paths
 * did, is the whole device library disappearing from Browse.
 */
internal fun rootsToList(
    roots: List<SafRoot>,
    isDescendant: (parent: SafRoot, child: SafRoot) -> Boolean?
): List<SafRoot> {
    val unique = roots.distinct()
    if (unique.size < 2) return unique

    // Each ordered pair is asked at most once: an answer can be a round trip to a
    // provider that is not on this device.
    val answers = mutableMapOf<Pair<SafRoot, SafRoot>, Boolean>()
    fun contains(parent: SafRoot, child: SafRoot): Boolean =
        answers.getOrPut(parent to child) {
            if (parent.authority != child.authority) false
            else isDescendant(parent, child)
                ?: documentIdContains(parent.treeDocumentId, child.treeDocumentId)
        }

    return unique.filter { child ->
        unique.none { parent ->
            // A pair that claims to contain one another cannot be resolved into a
            // parent and a child, and dropping both would empty the list — the
            // very failure this function exists to prevent.
            parent != child && contains(parent, child) && !contains(child, parent)
        }
    }
}

/**
 * Whether [childId] names something under [parentId] by the structure of the ids
 * themselves.
 *
 * This is the fallback for a provider that will not answer `isChildDocument`, and
 * it is safe because it reads the provider's own id rather than a fabricated
 * path: `externalstorage` composes ids hierarchically (`primary:Download` holds
 * `primary:Download/books_story`), while an opaque id is never a prefix of
 * another — every Drive child observed reported the tree's id as no prefix of its
 * own.
 *
 * Compared exactly, not case-insensitively: an id is opaque, and only its issuer
 * knows whether two spellings mean one document.
 */
internal fun documentIdContains(parentId: String, childId: String): Boolean {
    if (parentId.isEmpty() || childId.length <= parentId.length) return false
    if (!childId.startsWith(parentId)) return false

    // A boundary, so that `primary:Download` does not claim `primary:DownloadOld`.
    // A root id ending in ':' is a whole volume, whose children follow immediately.
    return childId[parentId.length] == '/' || parentId.endsWith(':')
}
