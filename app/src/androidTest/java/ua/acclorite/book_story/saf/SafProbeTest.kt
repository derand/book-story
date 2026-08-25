/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.saf

import android.app.Application
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.anggrayudi.storage.file.DocumentFileCompat
import com.anggrayudi.storage.file.getAbsolutePath
import com.anggrayudi.storage.file.getBasePath
import com.anggrayudi.storage.file.getRootPath
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * NOT A TEST — a diagnostic for issue #49, to be thrown away with its branch.
 *
 * Reports what every granted documents provider actually answers, so the
 * identity a book row should carry can be chosen from evidence instead of from
 * what one provider happens to do. Lives in androidTest because that runs in
 * the app's own process, under the app's UID, and therefore holds the app's
 * persisted URI grants — no production code is touched and nothing ships.
 *
 * Run:
 *   ./gradlew :app:connectedDebugAndroidTest \
 *       -Pandroid.testInstrumentationRunnerArguments.class=\
 *        ua.acclorite.book_story.saf.SafProbeTest
 * Read:
 *   adb pull /sdcard/Android/data/ua.acclorite.book_story.debug/files/saf-probe.txt
 */
@RunWith(AndroidJUnit4::class)
class SafProbeTest {

    private val out = StringBuilder()

    private fun line(text: String = "") {
        out.appendLine(text)
        Log.i(TAG, text)
    }

    @Test
    fun probe() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val resolver = app.contentResolver

        line("=== SAF probe ===")
        line("when      : ${stamp(System.currentTimeMillis())}")
        line("package   : ${app.packageName}")
        line("device    : ${Build.MANUFACTURER} ${Build.MODEL}, API ${Build.VERSION.SDK_INT}")
        line("boot id   : ${bootId()}")
        line()

        val grants = resolver.persistedUriPermissions
        line("persisted grants: ${grants.size}")
        line()

        // One roots dump per authority: it names the source ("Google Drive",
        // "Internal storage") and says whether the provider can answer
        // isChildDocument at all, which is the only non-string way to ask about
        // nesting.
        grants.map { it.uri.authority }.distinct().forEach { authority ->
            dumpRoots(app, authority ?: "?")
        }

        val trees = grants.map { it.uri }

        nestingMatrix(app, trees)
        productionFilter(app, trees)

        grants.forEachIndexed { index, grant ->
            line("--------------------------------------------------------------")
            line("[$index] tree ${grant.uri}")
            line("     authority  : ${grant.uri.authority}")
            line("     treeDocId  : ${DocumentsContract.getTreeDocumentId(grant.uri)}")
            line("     read/write : ${grant.isReadPermission}/${grant.isWritePermission}")
            line("     granted at : ${stamp(grant.persistedTime)}")
            dumpTree(app, grant.uri, trees)
        }

        line()
        line("=== end ===")

        val target = File(app.getExternalFilesDir(null), "saf-probe.txt")
        target.writeText(out.toString())
        Log.i(TAG, "report written to $target")
    }

    /**
     * Nesting asked of the providers, every root against every other. This is the
     * question `getStorageFiles` answers with `startsWith` on synthesised paths;
     * here it is answered by whoever actually knows.
     */
    private fun nestingMatrix(app: Application, trees: List<Uri>) {
        line("=== isChildDocument, every root against every other ===")
        trees.forEach { parent ->
            trees.forEach inner@{ child ->
                if (parent == child) return@inner
                val parentDoc = DocumentsContract.buildDocumentUriUsingTree(
                    parent, DocumentsContract.getTreeDocumentId(parent)
                )
                // The child addressed within its OWN tree, which is the only URI
                // the app would ever hold for it.
                val childDoc = DocumentsContract.buildDocumentUriUsingTree(
                    child, DocumentsContract.getTreeDocumentId(child)
                )
                val answer = runCatching {
                    DocumentsContract.isChildDocument(app.contentResolver, parentDoc, childDoc)
                }.fold({ it.toString() }, { "THREW ${it.javaClass.simpleName}: ${it.message}" })
                line("     ${short(child)} inside ${short(parent)} -> $answer")
            }
        }
        line()
    }

    /**
     * The live shape of defect 1: the paths `getStorageFiles` compares, and which
     * sources its filter keeps. Copied from FileProviderImpl deliberately — the
     * point is to show what today's code does, not to call it.
     */
    private fun productionFilter(app: Application, trees: List<Uri>) {
        line("=== what getStorageFiles sees today ===")
        val paths = trees.map { tree ->
            val file = DocumentFileCompat.fromUri(app, tree)
            tree to (file?.getAbsolutePath(app)?.trimEnd('/') ?: "")
        }
        paths.forEach { (tree, path) -> line("     \"$path\"  <- ${short(tree)}") }

        val kept = paths.filter { (_, path) ->
            paths.none { (_, other) ->
                other != path && path.startsWith(other, ignoreCase = true)
            }
        }
        line("     kept by the filter: ${kept.size} of ${paths.size}")
        kept.forEach { (tree, path) -> line("       \"$path\"  <- ${short(tree)}") }
        val dropped = paths - kept.toSet()
        dropped.forEach { (tree, path) -> line("       DROPPED \"$path\"  <- ${short(tree)}") }
        line()

        // The other half of the identity problem: the paths a book row would get.
        line("=== composed child paths, looking for collisions ===")
        val composed = mutableMapOf<String, MutableList<String>>()
        paths.forEach { (tree, path) ->
            val treeDocId = DocumentsContract.getTreeDocumentId(tree)
            listChildren(app, tree, treeDocId, quiet = true).forEach { child ->
                composed.getOrPut("$path/${child.name}") { mutableListOf() }
                    .add("${short(tree)}:${child.documentId}")
            }
        }
        val collisions = composed.filterValues { it.size > 1 }
        line("     ${composed.size} paths, ${collisions.size} of them claimed twice or more")
        collisions.forEach { (path, owners) ->
            line("     ! \"$path\"")
            owners.forEach { owner -> line("         $owner") }
        }
        line()
    }

    private fun short(tree: Uri): String {
        val id = DocumentsContract.getTreeDocumentId(tree)
        return "${tree.authority?.substringAfterLast('.')}/${id.takeLast(12)}"
    }

    /** What the provider says about itself: name, flags, local-only, is-child. */
    private fun dumpRoots(app: Application, authority: String) {
        line("=== roots of $authority ===")
        // A name for the source that does not come from a fabricated path. The
        // roots cursor is the documented place for one and is denied to us, so
        // this asks the package manager who owns the authority instead.
        val info = runCatching {
            app.packageManager.resolveContentProvider(authority, 0)
        }.getOrNull()
        line("     provider app: ${info?.applicationInfo?.loadLabel(app.packageManager)}" +
                " (${info?.packageName})")
        val uri = DocumentsContract.buildRootsUri(authority)
        try {
            app.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                line("     rows: ${cursor.count}")
                while (cursor.moveToNext()) {
                    val title = cursor.stringOf(DocumentsContract.Root.COLUMN_TITLE)
                    val summary = cursor.stringOf(DocumentsContract.Root.COLUMN_SUMMARY)
                    val rootId = cursor.stringOf(DocumentsContract.Root.COLUMN_ROOT_ID)
                    val docId = cursor.stringOf(DocumentsContract.Root.COLUMN_DOCUMENT_ID)
                    val flags = cursor.longOf(DocumentsContract.Root.COLUMN_FLAGS) ?: 0
                    line("     - title=$title summary=$summary")
                    line("       rootId=$rootId docId=$docId")
                    line("       flags=${rootFlags(flags.toInt())}")
                }
            } ?: line("     CURSOR IS NULL")
        } catch (e: Exception) {
            line("     THREW ${e.javaClass.simpleName}: ${e.message}")
        }
        line()
    }

    private fun dumpTree(app: Application, treeUri: Uri, allTrees: List<Uri>) {
        val treeDocId = DocumentsContract.getTreeDocumentId(treeUri)
        val rootDoc = DocumentsContract.buildDocumentUriUsingTree(treeUri, treeDocId)

        line("  -- root document --")
        line("     uri: $rootDoc")
        val rootColumns = dumpDocument(app, rootDoc)

        // What the app uses as identity today, and what Settings shows.
        line("  -- what anggrayudi/storage synthesises --")
        val documentFile = DocumentFileCompat.fromUri(app, treeUri)
        if (documentFile == null) {
            line("     DocumentFileCompat.fromUri returned NULL")
        } else {
            line("     getAbsolutePath: \"${documentFile.getAbsolutePath(app)}\"")
            line("     getBasePath    : \"${documentFile.getBasePath(app)}\"")
            line("     getRootPath    : \"${documentFile.getRootPath(app)}\"")
            line("     name           : \"${documentFile.name}\"")
        }

        line("  -- children --")
        val children = listChildren(app, treeUri, treeDocId)
        if (children.isEmpty()) {
            line("     none (or unreadable)")
            return
        }

        children.take(MAX_CHILDREN).forEach { child ->
            line("     - name=\"${child.name}\" mime=${child.mimeType}")
            line("       docId=${child.documentId}")
            line("       size=${child.size} lastModified=${child.lastModified}" +
                    " (${child.lastModified?.let { stamp(it) } ?: "—"})")
            line("       flags=${documentFlags(child.flags ?: 0)}")
            line("       docId starts with treeDocId: " +
                    "${child.documentId?.startsWith(treeDocId) == true}")
        }
        if (children.size > MAX_CHILDREN) {
            line("     … ${children.size - MAX_CHILDREN} more")
        }

        // Nesting, asked of the provider rather than of two strings.
        val firstChild = children.firstOrNull()?.documentId
        if (firstChild != null) {
            val childUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, firstChild)
            line("  -- isChildDocument(root, first child) --")
            line("     ${runCatching {
                DocumentsContract.isChildDocument(app.contentResolver, rootDoc, childUri)
            }.fold({ it.toString() }, { "THREW ${it.javaClass.simpleName}: ${it.message}" })}")

            // The proposal under discussion: keep authority + documentId, and
            // build the URI through whatever tree is granted at the time. Does a
            // document id issued under one tree open under another?
            allTrees.filter { it != treeUri && it.authority == treeUri.authority }
                .forEach { other ->
                    val rebuilt = DocumentsContract.buildDocumentUriUsingTree(other, firstChild)
                    line("  -- same docId through another tree of this authority --")
                    line("     other tree: ${DocumentsContract.getTreeDocumentId(other)}")
                    line("     readable  : ${canRead(app, rebuilt)}")
                }
        }

        // Is a second reading of size/lastModified the same as the first? A cache
        // key made of them is only as good as their stability.
        line("  -- root document, queried a second time --")
        val second = dumpDocument(app, rootDoc, quiet = true)
        line("     identical to first read: ${second == rootColumns}")

        // The one thing a reader ultimately needs: bytes.
        val book = children.firstOrNull { child ->
            BOOK_EXTENSIONS.any { child.name?.endsWith(it, ignoreCase = true) == true }
        }
        if (book != null) {
            line("  -- opening \"${book.name}\" --")
            val bookUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, book.documentId!!)
            line("     ${runCatching {
                app.contentResolver.openInputStream(bookUri)?.use { stream ->
                    val head = ByteArray(16)
                    val read = stream.read(head)
                    "read $read bytes: ${head.take(maxOf(read, 0)).joinToString(" ") {
                        "%02x".format(it)
                    }}"
                } ?: "openInputStream returned NULL"
            }.fold({ it }, { "THREW ${it.javaClass.simpleName}: ${it.message}" })}")
        } else {
            line("  -- no book-shaped child to open --")
        }
    }

    /** Every column the provider actually returns for one document. */
    private fun dumpDocument(app: Application, uri: Uri, quiet: Boolean = false): String {
        val report = StringBuilder()
        try {
            app.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (!cursor.moveToFirst()) {
                    report.appendLine("     cursor is EMPTY (count=${cursor.count})")
                    return@use
                }
                report.appendLine("     columns: ${cursor.columnCount}")
                repeat(cursor.columnCount) { i ->
                    val name = cursor.getColumnName(i)
                    val value = when {
                        cursor.isNull(i) -> "NULL"
                        name == DocumentsContract.Document.COLUMN_FLAGS ->
                            documentFlags(cursor.getInt(i))
                        else -> cursor.getString(i)
                    }
                    report.appendLine("       $name = $value")
                }
            } ?: report.appendLine("     CURSOR IS NULL")
        } catch (e: Exception) {
            report.appendLine("     THREW ${e.javaClass.simpleName}: ${e.message}")
        }
        val text = report.toString().trimEnd()
        if (!quiet) line(text)
        return text
    }

    private data class Child(
        val documentId: String?,
        val name: String?,
        val mimeType: String?,
        val size: Long?,
        val lastModified: Long?,
        val flags: Int?
    )

    private fun listChildren(
        app: Application,
        treeUri: Uri,
        parentId: String,
        quiet: Boolean = false
    ): List<Child> {
        val uri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentId)
        val children = mutableListOf<Child>()
        try {
            app.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (!quiet) line("     cursor count: ${cursor.count}")
                while (cursor.moveToNext()) {
                    children.add(
                        Child(
                            documentId = cursor.stringOf(
                                DocumentsContract.Document.COLUMN_DOCUMENT_ID
                            ),
                            name = cursor.stringOf(
                                DocumentsContract.Document.COLUMN_DISPLAY_NAME
                            ),
                            mimeType = cursor.stringOf(
                                DocumentsContract.Document.COLUMN_MIME_TYPE
                            ),
                            size = cursor.longOf(DocumentsContract.Document.COLUMN_SIZE),
                            lastModified = cursor.longOf(
                                DocumentsContract.Document.COLUMN_LAST_MODIFIED
                            ),
                            flags = cursor.longOf(
                                DocumentsContract.Document.COLUMN_FLAGS
                            )?.toInt()
                        )
                    )
                }
            } ?: line("     CHILDREN CURSOR IS NULL")
        } catch (e: Exception) {
            line("     THREW ${e.javaClass.simpleName}: ${e.message}")
        }
        return children
    }

    private fun canRead(app: Application, uri: Uri): String = runCatching {
        app.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                "yes — \"${cursor.stringOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME)}\""
            } else "cursor empty"
        } ?: "CURSOR IS NULL"
    }.fold({ it }, { "THREW ${it.javaClass.simpleName}: ${it.message}" })

    private fun android.database.Cursor.stringOf(column: String): String? =
        getColumnIndex(column).takeIf { it >= 0 && !isNull(it) }?.let { getString(it) }

    private fun android.database.Cursor.longOf(column: String): Long? =
        getColumnIndex(column).takeIf { it >= 0 && !isNull(it) }?.let { getLong(it) }

    private fun stamp(millis: Long): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(millis))

    /** Changes on every boot, so two reports can be told apart across a restart. */
    private fun bootId(): String = runCatching {
        File("/proc/sys/kernel/random/boot_id").readText().trim()
    }.getOrElse { "unavailable" }

    private fun documentFlags(flags: Int): String = describe(
        flags,
        listOf(
            1 shl 0 to "THUMBNAIL",
            1 shl 1 to "WRITE",
            1 shl 2 to "DELETE",
            1 shl 3 to "DIR_CREATE",
            1 shl 4 to "DIR_PREFERS_GRID",
            1 shl 5 to "DIR_PREFERS_LAST_MODIFIED",
            1 shl 6 to "RENAME",
            1 shl 7 to "COPY",
            1 shl 8 to "MOVE",
            1 shl 9 to "VIRTUAL",
            1 shl 10 to "REMOVE",
            1 shl 11 to "SETTINGS",
            1 shl 12 to "WEB_LINKABLE",
            1 shl 13 to "PARTIAL",
            1 shl 14 to "METADATA",
            1 shl 15 to "DIR_BLOCKS_OPEN_DOCUMENT_TREE"
        )
    )

    private fun rootFlags(flags: Int): String = describe(
        flags,
        listOf(
            1 shl 0 to "CREATE",
            1 shl 1 to "LOCAL_ONLY",
            1 shl 2 to "RECENTS",
            1 shl 3 to "SEARCH",
            1 shl 4 to "IS_CHILD",
            1 shl 5 to "EJECT",
            1 shl 6 to "EMPTY",
            1 shl 16 to "ADVANCED",
            1 shl 17 to "HAS_SETTINGS",
            1 shl 18 to "REMOVABLE_SD",
            1 shl 19 to "REMOVABLE_USB"
        )
    )

    private fun describe(flags: Int, names: List<Pair<Int, String>>): String {
        val set = names.filter { flags and it.first != 0 }.map { it.second }
        return "0x${Integer.toHexString(flags)} [${set.joinToString(" ")}]"
    }

    private companion object {
        const val TAG = "SafProbe"
        const val MAX_CHILDREN = 12
        val BOOK_EXTENSIONS = listOf(".fb2", ".epub", ".txt", ".html", ".pdf", ".zip")
    }
}
