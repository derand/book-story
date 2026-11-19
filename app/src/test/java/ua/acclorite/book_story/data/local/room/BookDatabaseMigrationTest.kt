/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.data.local.room

import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File

/**
 * A database written by any supported older schema opens in this build with
 * its rows intact.
 *
 * "Supported" starts at [BASELINE], the schema every install and backup was on
 * when this test was added; the migrations below it are history and are not
 * tested. For each version from the baseline up to [DATABASE_VERSION] the test
 * creates that schema from its export in `app/schemas`, puts one row in every
 * table, and opens the file with Room and the app's own migrations
 * ([DatabaseHelper.MANUAL_MIGRATIONS] + the auto-migrations). Room checks the
 * migrated tables against the current entities on open and throws if they
 * differ; the test then checks that every row is still there with every column
 * the two schemas share unchanged.
 *
 * While [DATABASE_VERSION] equals the baseline this only checks that the
 * exported schema matches the entities. A new migration is covered as soon as
 * the version goes up; assert its new columns' values here as well. A
 * migration that rewrites a value on purpose says so in [expectedAfter].
 *
 * Not `MigrationTestHelper` (room-testing): it reads the schemas only from the
 * instrumentation's assets, which a JVM test gets only from the app's own
 * merged assets, i.e. by shipping the schemas in the debug APK.
 */
@RunWith(RobolectricTestRunner::class)
class BookDatabaseMigrationTest {

    private val context = RuntimeEnvironment.getApplication()

    @Test
    fun `every supported schema migrates to the current one with its rows`() {
        val current = schema(DATABASE_VERSION).associateBy { it.name }

        for (version in BASELINE..DATABASE_VERSION) {
            val name = "migration-from-$version"
            val written = createDatabase(name, version)

            val database = Room.databaseBuilder(context, BookDatabase::class.java, name)
                .addMigrations(*DatabaseHelper.MANUAL_MIGRATIONS)
                .allowMainThreadQueries()
                .build()
            try {
                val db = database.openHelper.writableDatabase
                assertEquals(DATABASE_VERSION, db.version)
                // Room checks only the tables it has entities for; a table a
                // migration should have dropped would linger unnoticed.
                assertEquals(
                    "$version → $DATABASE_VERSION: tables",
                    current.keys.map(String::lowercase).toSortedSet(),
                    db.tables().map(String::lowercase).toSortedSet()
                )

                for ((table, before) in written) {
                    val columns = current[table]?.columns?.map(Column::name)?.toSet() ?: continue
                    val after = db.rows(table)
                    assertEquals("$version → $DATABASE_VERSION: rows in $table", 1, after.size)
                    assertEquals(
                        "$version → $DATABASE_VERSION: values in $table",
                        expectedAfter(table, before.filterKeys { it in columns }),
                        after.single().filterKeys { it in before }
                    )
                }
            } finally {
                database.close()
            }
        }
    }

    /**
     * The shared columns of a row written as [before], as they should read
     * after the migration. A migration that rewrites a column on purpose
     * adjusts them here.
     */
    @Suppress("UNUSED_PARAMETER")
    private fun expectedAfter(table: String, before: Map<String, Any?>): Map<String, Any?> =
        before

    /**
     * Creates database [name] the way Room would have at [version], from the
     * export's own SQL, with one row in every table. Returns each table's row
     * as read back.
     */
    private fun createDatabase(name: String, version: Int): Map<String, Map<String, Any?>> {
        val file = context.getDatabasePath(name).apply { parentFile?.mkdirs(); delete() }
        val tables = schema(version)
        val db = SQLiteDatabase.openOrCreateDatabase(file, null)
        try {
            for (table in tables) {
                table.createSql.forEach { db.execSQL(it.replace("\${TABLE_NAME}", table.name)) }
            }
            setupQueries(version).forEach(db::execSQL)
            db.version = version
            return tables.associate { table -> table.name to db.insertRow(table) }
        } finally {
            db.close()
        }
    }

    private class Column(val name: String, val affinity: String, val default: String?)

    private class Table(val name: String, val columns: List<Column>, val createSql: List<String>)

    private fun export(version: Int): JsonObject {
        val file = File("schemas/${BookDatabase::class.java.name}/$version.json")
        return Json.parseToJsonElement(file.readText()).jsonObject["database"]!!.jsonObject
    }

    private fun setupQueries(version: Int): List<String> =
        export(version)["setupQueries"]!!.jsonArray.map { it.jsonPrimitive.content }

    private fun schema(version: Int): List<Table> =
        export(version)["entities"]!!.jsonArray.map { element ->
            val entity = element.jsonObject
            Table(
                name = entity.string("tableName")!!,
                columns = entity["fields"]!!.jsonArray.map {
                    val field = it.jsonObject
                    Column(
                        name = field.string("columnName")!!,
                        affinity = field.string("affinity")!!,
                        default = field.string("defaultValue")
                    )
                },
                createSql = listOf(entity.string("createSql")!!) +
                        (entity["indices"]?.jsonArray.orEmpty())
                            .map { it.jsonObject.string("createSql")!! }
            )
        }

    private fun JsonObject.string(key: String): String? = this[key]?.jsonPrimitive?.content

    /**
     * Inserts one row with a value in every column, nullable ones included, so
     * each column has something to lose. A column with a default gets it
     * (`categories` is JSON, `sortOrder` an enum name); otherwise INTEGER is 1
     * (every id and `bookId` points at the one book), REAL 0.5, TEXT the
     * column's name.
     */
    private fun SQLiteDatabase.insertRow(table: Table): Map<String, Any?> {
        val names = table.columns.joinToString { "`${it.name}`" }
        val values = table.columns.joinToString { column ->
            column.default ?: when (column.affinity) {
                "INTEGER" -> "1"
                "REAL" -> "0.5"
                else -> "'${column.name}'"
            }
        }
        execSQL("INSERT INTO `${table.name}` ($names) VALUES ($values)")
        return rawQuery("SELECT * FROM `${table.name}`", null).use { it.rows() }.single()
    }

    /** The app's own tables: not SQLite's, Room's or Android's bookkeeping. */
    private fun SupportSQLiteDatabase.tables(): List<String> =
        query(
            "SELECT name FROM sqlite_master WHERE type = 'table' " +
                    "AND name NOT LIKE 'sqlite_%' " +
                    "AND name NOT IN ('room_master_table', 'android_metadata')"
        ).use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.getString(0)) } }

    private fun SupportSQLiteDatabase.rows(table: String): List<Map<String, Any?>> =
        query("SELECT * FROM `$table`").use { it.rows() }

    private fun Cursor.rows(): List<Map<String, Any?>> = buildList {
        while (moveToNext()) {
            add(columnNames.withIndex().associate { (i, column) ->
                column to when (getType(i)) {
                    Cursor.FIELD_TYPE_NULL -> null
                    Cursor.FIELD_TYPE_INTEGER -> getLong(i)
                    Cursor.FIELD_TYPE_FLOAT -> getDouble(i)
                    Cursor.FIELD_TYPE_BLOB -> getBlob(i).toList()
                    else -> getString(i)
                }
            })
        }
    }

    private companion object {
        /** The oldest schema a database may still be on; see the class KDoc. */
        const val BASELINE = 24
    }
}
