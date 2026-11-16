/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.core.crash

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File
import java.io.FileNotFoundException
import java.time.LocalDateTime

/**
 * The saved crash log is what the crash screen shares, so it must not name a
 * book; there must not be more of them than [CRASH_LOGS_KEPT]; and whenever the
 * crash screen cannot be shown, the crash must reach the platform's handler
 * instead of leaving the process hanging or restarting the screen in a loop.
 */
@RunWith(RobolectricTestRunner::class)
class CrashHandlerTest {

    private val context: Context = RuntimeEnvironment.getApplication()
    private val crashDir get() = File(context.filesDir, "crash")

    /** Stands in for the platform's handler, which kills the process and never returns. */
    private class Delegated : RuntimeException()

    private class RecordingHandler : Thread.UncaughtExceptionHandler {
        var received: Throwable? = null
        override fun uncaughtException(thread: Thread, throwable: Throwable) {
            received = throwable
            throw Delegated()
        }
    }

    @Before
    fun clean() {
        crashDir.deleteRecursively()
    }

    private fun time(second: Int) = LocalDateTime.of(2026, 11, 16, 12, 0, second)

    @Test
    fun `the saved log keeps the reason and loses the path`() {
        val crash = FileNotFoundException(
            "/storage/emulated/0/Download/books_story/Some Title.epub: open failed: ENOENT"
        )

        assertTrue(CrashUtils.saveCrashLog(context, crash.stackTraceToString()))

        val saved = CrashUtils.getCrashLog(context)!!
        assertFalse(saved, saved.contains("Some Title"))
        assertFalse(saved, saved.contains("/storage"))
        assertTrue(saved, saved.contains("open failed: ENOENT"))
        assertTrue(saved, saved.contains("CrashHandlerTest"))
    }

    @Test
    fun `only the newest logs are kept, and the newest is the one shown`() {
        repeat(CRASH_LOGS_KEPT + 2) { i ->
            assertTrue(CrashUtils.saveCrashLog(context, "crash $i", time(i)))
        }
        File(crashDir, "crashLog_left.txt.tmp").writeText("half")
        CrashUtils.saveCrashLog(context, "crash last", time(30))

        val left = crashDir.list()!!.sorted()
        assertEquals(CRASH_LOGS_KEPT, left.size)
        assertTrue(left.toString(), left.none { it.endsWith(".tmp") })
        assertEquals("crash last", CrashUtils.getCrashLog(context))
    }

    @Test
    fun `a log that cannot be saved reports failure and leaves nothing behind`() {
        crashDir.parentFile!!.mkdirs()
        crashDir.writeText("a file where the directory should be")

        assertFalse(CrashUtils.saveCrashLog(context, "crash"))
        assertNull(CrashUtils.getCrashLog(context))
    }

    @Test
    fun `when the log cannot be saved, the crash goes to the previous handler`() {
        crashDir.parentFile!!.mkdirs()
        crashDir.writeText("a file where the directory should be")
        val previous = RecordingHandler()
        val crash = IllegalStateException("boom")

        try {
            CrashHandler(context, previous).uncaughtException(Thread.currentThread(), crash)
        } catch (_: Delegated) {
        }

        assertSame(crash, previous.received)
    }

    @Test
    fun `a crash right after the last one goes to the previous handler and saves nothing`() {
        CrashUtils.saveCrashLog(context, "first")
        val last = CrashUtils.lastCrashTime(context)!!
        val previous = RecordingHandler()
        val crash = IllegalStateException("again")

        try {
            CrashHandler(context, previous, now = { last + CRASH_LOOP_WINDOW_MS - 1 })
                .uncaughtException(Thread.currentThread(), crash)
        } catch (_: Delegated) {
        }

        assertSame(crash, previous.received)
        assertEquals("first", CrashUtils.getCrashLog(context))
        assertEquals(1, crashDir.list()!!.size)
    }
}
