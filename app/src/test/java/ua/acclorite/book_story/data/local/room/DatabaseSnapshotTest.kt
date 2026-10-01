/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.data.local.room

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import ua.acclorite.book_story.data.di.AppModule
import java.io.File

/**
 * The copy's consistency check works only in WAL mode, so the app must get WAL
 * even where Room would not choose it, and a copy in any other mode must refuse.
 */
@RunWith(RobolectricTestRunner::class)
class DatabaseSnapshotTest {

    @get:Rule
    val temp = TemporaryFolder()

    private lateinit var app: Application
    private var database: BookDatabase? = null

    @Before
    fun setUp() {
        app = RuntimeEnvironment.getApplication()
        // Where Room's AUTOMATIC mode picks a rollback journal instead of WAL.
        val activityManager = app.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        shadowOf(activityManager).setIsLowRamDevice(true)
    }

    @After
    fun tearDown() {
        database?.close()
    }

    private fun journalMode(db: BookDatabase): String =
        db.openHelper.writableDatabase.query("PRAGMA journal_mode").use { cursor ->
            cursor.moveToFirst()
            cursor.getString(0).lowercase()
        }

    @Test
    fun `the app's database is in WAL mode on a low-RAM device`() {
        val db = AppModule.provideBookDatabase(app).also { database = it }

        assertEquals("wal", journalMode(db))
    }

    @Test
    fun `the app's database copies into a readable file`() {
        val db = AppModule.provideBookDatabase(app).also { database = it }
        val copy = File(temp.root, "copy.db")

        DatabaseSnapshot(db).copyTo(copy)

        // The default category is written at startup, so the copy holds a row.
        SQLiteDatabase.openDatabase(copy.path, null, SQLiteDatabase.OPEN_READONLY).use { opened ->
            opened.rawQuery("SELECT COUNT(*) FROM CategoryEntity", null).use { cursor ->
                cursor.moveToFirst()
                assertEquals(1, cursor.getInt(0))
            }
        }
    }

    @Test
    fun `a copy refuses to start outside WAL mode`() {
        // What the app got before it asked for WAL explicitly.
        val db = Room.databaseBuilder(app, BookDatabase::class.java, "automatic_db")
            .allowMainThreadQueries()
            .build()
            .also { database = it }
        val copy = File(temp.root, "copy.db")

        // A rollback journal: TRUNCATE on a device, MEMORY under Robolectric.
        assertNotEquals("wal", journalMode(db))
        assertThrows(IllegalStateException::class.java) { DatabaseSnapshot(db).copyTo(copy) }
        assertFalse(copy.exists())
    }
}
