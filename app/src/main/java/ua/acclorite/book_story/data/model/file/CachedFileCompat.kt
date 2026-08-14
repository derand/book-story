/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.data.model.file

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract

object CachedFileCompat {
    fun fromUri(context: Context, uri: Uri, builder: CachedFileBuilder? = null): CachedFile {
        return CachedFile(
            context = context,
            uri = when {
                DocumentsContract.isDocumentUri(context, uri) -> uri

                DocumentsContract.isTreeUri(uri) -> {
                    DocumentsContract.buildDocumentUriUsingTree(
                        uri,
                        DocumentsContract.getTreeDocumentId(uri)
                    )
                }

                else -> uri
            },
            builder = builder
        )
    }

    /**
     * A book the app owns, held as a plain file. No provider is involved, so
     * none of the URI machinery is: see [CachedFile]'s `localFile`.
     */
    fun fromFile(context: Context, file: java.io.File): CachedFile {
        return CachedFile(
            context = context,
            uri = Uri.fromFile(file),
            localFile = file
        )
    }

    fun build(
        name: String? = null,
        path: String? = null,
        size: Long? = null,
        lastModified: Long? = null,
        isDirectory: Boolean? = null
    ): CachedFileBuilder {
        return CachedFileBuilder(
            name = name,
            path = path,
            size = size,
            lastModified = lastModified,
            isDirectory = isDirectory
        )
    }
}