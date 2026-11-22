/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.data.model.file

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.ProviderInfo
import android.content.pm.ResolveInfo
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import kotlin.coroutines.cancellation.CancellationException

private const val AUTHORITY = "test.walk.documents"

/**
 * A walk is blocking I/O, one provider query per directory: cancelling the
 * coroutine around it stops nothing unless the walk itself asks. Asked, it has
 * to stop at once — and the stop has to get out, not end one directory's
 * listing as a failure the walk then carries on past.
 */
@RunWith(RobolectricTestRunner::class)
class CachedFileWalkTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        // What makes `isDocumentUri` true for its URIs, as on a device: without
        // it every child resolves back to the tree's root.
        shadowOf(context.packageManager).addResolveInfoForIntent(
            Intent(DocumentsContract.PROVIDER_INTERFACE),
            ResolveInfo().apply {
                providerInfo = ProviderInfo().apply {
                    authority = AUTHORITY
                    packageName = context.packageName
                }
            }
        )
        Robolectric.buildContentProvider(TreeProvider::class.java).create(AUTHORITY)
        TreeProvider.childQueries = 0
    }

    private fun root(): CachedFile {
        val tree = DocumentsContract.buildTreeDocumentUri(AUTHORITY, "root")
        return CachedFile(
            context = context,
            uri = DocumentsContract.buildDocumentUriUsingTree(tree, "root"),
            builder = CachedFileCompat.build(name = "root", path = "/root", isDirectory = true)
        )
    }

    @Test
    fun `a walk lists every file, one query per directory`() {
        val files = root().walk().map { it.path }.sorted()

        assertEquals(
            listOf("/root/a/a.fb2", "/root/b/b.fb2", "/root/c/c.fb2", "/root/top.fb2"),
            files
        )
        assertEquals(4, TreeProvider.childQueries)
    }

    @Test
    fun `a cancelled walk stops before the next directory and says so`() {
        var checks = 0

        assertThrows(CancellationException::class.java) {
            root().walk(checkCancelled = {
                // Cancelled once the first subdirectory has been listed.
                if (++checks > 2) throw CancellationException("left")
            })
        }

        // The root and the first subdirectory, nothing after the cancel.
        assertEquals(2, TreeProvider.childQueries)
    }

    /** `root` holds three directories with one book each, and one book of its own. */
    class TreeProvider : ContentProvider() {

        companion object {
            var childQueries = 0

            private val children = mapOf(
                "root" to listOf("a" to true, "b" to true, "c" to true, "top.fb2" to false),
                "a" to listOf("a.fb2" to false),
                "b" to listOf("b.fb2" to false),
                "c" to listOf("c.fb2" to false)
            )
        }

        override fun onCreate() = true

        override fun query(
            uri: Uri,
            projection: Array<out String>?,
            selection: String?,
            selectionArgs: Array<out String>?,
            sortOrder: String?
        ): Cursor {
            val columns = projection ?: arrayOf(Document.COLUMN_DOCUMENT_ID)
            val cursor = MatrixCursor(columns)
            // content://authority/tree/root/document/<id>[/children]
            if (uri.lastPathSegment != "children") {
                cursor.addRow(columns.map { null })
                return cursor
            }

            childQueries++
            children[uri.pathSegments[3]].orEmpty().forEach { (name, isDirectory) ->
                cursor.addRow(columns.map { column ->
                    when (column) {
                        Document.COLUMN_DISPLAY_NAME -> name
                        Document.COLUMN_DOCUMENT_ID -> name
                        Document.COLUMN_SIZE -> 1L
                        Document.COLUMN_LAST_MODIFIED -> 1L
                        Document.COLUMN_MIME_TYPE ->
                            if (isDirectory) Document.MIME_TYPE_DIR else "application/x-fictionbook+xml"

                        else -> null
                    }
                })
            }
            return cursor
        }

        override fun getType(uri: Uri): String? = null
        override fun insert(uri: Uri, values: ContentValues?): Uri? = null
        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
        override fun update(
            uri: Uri,
            values: ContentValues?,
            selection: String?,
            selectionArgs: Array<out String>?
        ) = 0
    }
}
