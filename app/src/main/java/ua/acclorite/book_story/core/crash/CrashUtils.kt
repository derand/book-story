/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.core.crash

import android.content.Context
import ua.acclorite.book_story.core.log.logE
import ua.acclorite.book_story.core.log.messageForLog
import ua.acclorite.book_story.core.log.withoutLocations
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private const val TAG = "CrashUtils"
private const val PREFIX = "crashLog_"
private const val SUFFIX = ".txt"

/** How many crash logs are kept; the oldest go once a new one is saved. */
const val CRASH_LOGS_KEPT = 5

/**
 * The name sorts in time order: the crash screen shows the last one by name,
 * and pruning drops the first ones.
 */
private val NAME_TIME = DateTimeFormatter.ofPattern("yyyy.MM.dd_HH.mm.ss.SSS")

object CrashUtils {

    private fun crashDir(context: Context) = File(context.filesDir, "crash")

    /**
     * Saves [crashLog] without the paths and URIs in it — the saved log is what
     * the crash screen copies and shares, often into a public issue — and keeps
     * only the newest [CRASH_LOGS_KEPT] logs.
     */
    fun saveCrashLog(
        context: Context,
        crashLog: String,
        now: LocalDateTime = LocalDateTime.now()
    ): Boolean {
        val crashDir = crashDir(context)
        val fileName = "$PREFIX${now.format(NAME_TIME)}$SUFFIX"
        val crashFile = File(crashDir, fileName)
        val tempFile = File(crashDir, "$fileName.tmp")

        try {
            if (!crashDir.isDirectory && !crashDir.mkdirs()) {
                throw IllegalStateException("Failed to create the crash directory.")
            }
            tempFile.writeText(withoutLocations(crashLog))
            if (!tempFile.renameTo(crashFile)) {
                throw IllegalStateException("Failed to rename .tmp file.")
            }
        } catch (e: Exception) {
            logE(TAG, "Could not save the crash log: ${e.messageForLog()}")
            tempFile.delete()
            return false
        }

        prune(crashDir)
        return true
    }

    /** The newest crash log, or null if there is none. */
    fun getCrashLog(context: Context): String? {
        return crashLogs(crashDir(context)).lastOrNull()?.readText()
    }

    /** When the newest crash log was written, in epoch millis, or null. */
    fun lastCrashTime(context: Context): Long? {
        return crashLogs(crashDir(context)).lastOrNull()?.lastModified()
    }

    private fun crashLogs(crashDir: File): List<File> {
        return crashDir.listFiles { file ->
            file.isFile && file.name.startsWith(PREFIX) && file.name.endsWith(SUFFIX)
        }.orEmpty().sortedBy { it.name }
    }

    /** Drops all but the newest logs, and any `.tmp` left by a write that died. */
    private fun prune(crashDir: File) {
        crashLogs(crashDir).dropLast(CRASH_LOGS_KEPT).forEach { it.delete() }
        crashDir.listFiles { file -> file.name.endsWith(".tmp") }
            .orEmpty()
            .forEach { it.delete() }
    }
}
