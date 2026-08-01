/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.data.parser.cover

import android.graphics.BitmapFactory
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import ua.acclorite.book_story.core.CoverImage
import ua.acclorite.book_story.core.helpers.rethrowIfCancellation
import ua.acclorite.book_story.core.log.logE
import ua.acclorite.book_story.data.model.file.CachedFile
import javax.inject.Inject

private const val TAG = "Fb2CoverParser"

class Fb2CoverParser @Inject constructor() : CoverParser {

    override suspend fun parse(cachedFile: CachedFile): CoverImage? {
        return try {
            withContext(Dispatchers.IO) {
                cachedFile.openInputStream()?.use { stream ->
                    val document = Jsoup.parse(stream, null, "", Parser.xmlParser())

                    val coverId = document.selectFirst("coverpage image")
                        ?.run {
                            attr("xlink:href")
                                .ifBlank { attr("l:href") }
                                .ifBlank { attr("href") }
                        }
                        ?.trim()
                        ?.removePrefix("#")

                    val binary = document.select("binary").firstOrNull { binary ->
                        binary.attr("id").trim().equals(coverId, ignoreCase = true)
                    } ?: document.selectFirst("binary[content-type*=image]")
                    ?: return@use null

                    val bytes = Base64.decode(binary.wholeText(), Base64.DEFAULT)
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                }
            }
        } catch (e: Exception) {
            e.rethrowIfCancellation()
            logE(TAG, "Could not parse cover with message: ${e.message}.")
            null
        }
    }
}
