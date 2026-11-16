/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.core.crash

import android.content.Context
import android.content.Intent
import android.os.Process
import ua.acclorite.book_story.core.log.logE
import ua.acclorite.book_story.core.log.withoutLocations
import ua.acclorite.book_story.presentation.crash.CrashActivity
import kotlin.system.exitProcess

private const val TAG = "CrashHandler"

/**
 * A crash this soon after the last saved one is taken for a loop: the crash
 * screen's own process failing again, before or inside [CrashActivity].
 */
const val CRASH_LOOP_WINDOW_MS = 5_000L

/**
 * Saves the crash log and shows it in [CrashActivity].
 *
 * When that cannot work, the crash goes to [previous] — the platform's handler,
 * which kills the process and shows the system's own "app has stopped" dialog.
 * Without it a failed save would leave a dead main thread in a live process
 * (an ANR), and a crash screen that crashes would restart itself forever.
 */
class CrashHandler(
    private val context: Context,
    private val previous: Thread.UncaughtExceptionHandler?,
    private val now: () -> Long = System::currentTimeMillis
) : Thread.UncaughtExceptionHandler {

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        val crashLog = throwable.stackTraceToString()
        logE(TAG, withoutLocations(crashLog))

        if (CrashActivity.isRunning || isLoop() || !CrashUtils.saveCrashLog(context, crashLog)) {
            previous?.uncaughtException(thread, throwable)
            // Only reached if there was no previous handler, or it returned.
            Process.killProcess(Process.myPid())
            exitProcess(1)
        }

        val intent = Intent(context, CrashActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        context.startActivity(intent)

        Process.killProcess(Process.myPid())
        exitProcess(1)
    }

    private fun isLoop(): Boolean {
        val last = CrashUtils.lastCrashTime(context) ?: return false
        return now() - last in 0 until CRASH_LOOP_WINDOW_MS
    }
}
