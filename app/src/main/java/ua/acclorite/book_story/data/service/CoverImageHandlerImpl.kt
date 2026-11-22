/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.data.service

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.net.toFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ua.acclorite.book_story.core.CoverImage
import ua.acclorite.book_story.core.helpers.runCatchingCancellable
import ua.acclorite.book_story.data.parser.cover.decodeCover
import ua.acclorite.book_story.domain.service.CoverImageHandler
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import javax.inject.Inject

class CoverImageHandlerImpl @Inject constructor(
    private val application: Application
) : CoverImageHandler {

    private val filesDir: File = application.filesDir
    private val coversDir = File(filesDir, "covers")

    override suspend fun decodeCover(uri: Uri): Result<CoverImage> = runCatchingCancellable {
        withContext(Dispatchers.IO) {
            decodeCover(application.contentResolver, uri)
                ?: throw IllegalStateException("Could not read the chosen image.")
        }
    }

    override suspend fun saveCover(coverImage: CoverImage): Result<File> = runCatchingCancellable {
        val coverUri = "${UUID.randomUUID()}.webp"
        val cover = File(coversDir, coverUri)

        withContext(Dispatchers.IO) {
            if (!coversDir.exists()) {
                coversDir.mkdirs()
            }

            BufferedOutputStream(FileOutputStream(cover)).use { output ->
                coverImage
                    .compress(Bitmap.CompressFormat.WEBP, 20, output)
                    .let { success ->
                        if (success) return@let
                        throw Exception("Couldn't save cover image.")
                    }
            }
        }

        cover
    }

    override suspend fun deleteCover(coverImage: Uri): Result<Unit> = runCatchingCancellable {
        withContext(Dispatchers.IO) {
            coverImage.toFile().apply {
                if (exists() && !delete()) throw Exception("Couldn't delete cover image.")
            }
        }
    }

    override suspend fun isSavedAs(stored: Uri, coverImage: CoverImage): Result<Boolean> =
        runCatchingCancellable {
            val compressed = withContext(Dispatchers.Default) {
                val stream = ByteArrayOutputStream()
                coverImage.compress(Bitmap.CompressFormat.WEBP, 20, stream)
                val byteArray = stream.toByteArray()
                BitmapFactory.decodeByteArray(byteArray, 0, byteArray.size)
            }
            val current = withContext(Dispatchers.IO) {
                application.contentResolver.openInputStream(stored)
                    ?.use { BitmapFactory.decodeStream(it) }
            }
            compressed.sameAs(current)
        }
}