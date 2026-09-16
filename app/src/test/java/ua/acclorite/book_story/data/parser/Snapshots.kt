/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.data.parser

import org.junit.Assert.fail
import java.io.File

/** A test resource as a file on disk. */
fun resourceFile(path: String): File {
    val url = object {}.javaClass.classLoader!!.getResource(path)
        ?: error("test resource not found: $path")
    return File(url.toURI())
}

/**
 * Compares [actual] with the snapshot stored under test resources. On a
 * mismatch the actual output is written to build/snapshots/, so a deliberate
 * change is accepted by copying that file over the snapshot and reviewing the
 * diff.
 */
fun assertSnapshot(path: String, actual: String) {
    val expected = object {}.javaClass.classLoader!!.getResource(path)?.readText()
    if (expected == actual) return

    val written = File("build/snapshots", path.replace(".txt", ".actual.txt"))
    written.parentFile?.mkdirs()
    written.writeText(actual)

    val message = if (expected == null) "no snapshot at src/test/resources/$path"
    else "output differs from src/test/resources/$path"
    fail("$message; actual output written to ${written.absolutePath}")
}
