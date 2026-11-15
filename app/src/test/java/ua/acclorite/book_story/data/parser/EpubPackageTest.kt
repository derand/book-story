/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.data.parser

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import ua.acclorite.book_story.data.model.file.CachedFileCompat
import ua.acclorite.book_story.data.parser.epub.EpubPackage
import ua.acclorite.book_story.data.parser.file.EpubFileParser
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * What [EpubPackage] reads from the package document besides the text: the
 * cover and the metadata of an import (#136).
 */
@RunWith(RobolectricTestRunner::class)
class EpubPackageTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val image = byteArrayOf(1, 2, 3)

    // --- cover ---

    @Test
    fun theCoverImagePropertyWinsOverMetaAndOrder() {
        val cover = coverOf(
            manifest = """
                <item id="first" href="Images/first.jpg" media-type="image/jpeg"/>
                <item id="old" href="Images/old.jpg" media-type="image/jpeg"/>
                <item id="new" href="Images/new.jpg" media-type="image/jpeg" properties="cover-image"/>
            """,
            meta = """<meta name="cover" content="old"/>""",
            "OEBPS/Images/first.jpg", "OEBPS/Images/old.jpg", "OEBPS/Images/new.jpg"
        )

        assertEquals("OEBPS/Images/new.jpg", cover)
    }

    @Test
    fun withoutThePropertyTheMetaCoverIsUsed() {
        val cover = coverOf(
            manifest = """
                <item id="first" href="Images/first.jpg" media-type="image/jpeg"/>
                <item id="cov" href="Images/cover.jpg" media-type="image/jpeg"/>
            """,
            meta = """<meta name="cover" content="cov"/>""",
            "OEBPS/Images/first.jpg", "OEBPS/Images/cover.jpg"
        )

        assertEquals("OEBPS/Images/cover.jpg", cover)
    }

    @Test
    fun withNeitherTheFirstImageInTheManifestIsUsed() {
        val cover = coverOf(
            manifest = """
                <item id="c1" href="one.xhtml" media-type="application/xhtml+xml"/>
                <item id="a" href="Images/a.png" media-type="image/png"/>
                <item id="b" href="Images/b.png" media-type="image/png"/>
            """,
            meta = "",
            "OEBPS/Images/a.png", "OEBPS/Images/b.png"
        )

        assertEquals("OEBPS/Images/a.png", cover)
    }

    @Test
    fun aCoverItemWithoutItsFileFallsThroughToTheNext() {
        // The old lookup threw from first {} and gave no cover at all
        val cover = coverOf(
            manifest = """
                <item id="gone" href="Images/gone.jpg" media-type="image/jpeg" properties="cover-image"/>
                <item id="a" href="Images/a.jpg" media-type="image/jpeg"/>
            """,
            meta = "",
            "OEBPS/Images/a.jpg"
        )

        assertEquals("OEBPS/Images/a.jpg", cover)
    }

    @Test
    fun aCoverIsMatchedByItsPathNotBySuffix() {
        // endsWith("cover.jpg") used to take xcover.jpg
        val cover = coverOf(
            manifest = """<item id="cov" href="Images/cover.jpg" media-type="image/jpeg"/>""",
            meta = """<meta name="cover" content="cov"/>""",
            "OEBPS/Images/xcover.jpg", "OEBPS/Images/cover.jpg"
        )

        assertEquals("OEBPS/Images/cover.jpg", cover)
    }

    @Test
    fun aBookWithoutAnOpfHasNoCover() {
        val book = zip("OEBPS/one.xhtml" to "<html/>".toByteArray(), "OEBPS/a.jpg" to image)

        ZipFile(book).use { assertNull(EpubPackage.open(it).coverImage) }
    }

    // --- metadata ---

    @Test
    fun theImportReadsTheOpfContainerXmlNames() {
        val book = zip(
            "backup.opf" to opf(title = "Стара назва").toByteArray(),
            "META-INF/container.xml" to container("OPS/content.opf").toByteArray(),
            "OPS/content.opf" to opf(title = "Справжня назва").toByteArray()
        )

        val parsed = runBlocking {
            EpubFileParser().parse(CachedFileCompat.fromFile(RuntimeEnvironment.getApplication(), book))
        }

        assertEquals("Справжня назва", parsed?.title)
    }

    // --- helpers ---

    private fun coverOf(manifest: String, meta: String, vararg images: String): String? {
        val book = zip(
            "META-INF/container.xml" to container("OEBPS/content.opf").toByteArray(),
            "OEBPS/content.opf" to """
                <?xml version="1.0" encoding="UTF-8"?>
                <package xmlns="http://www.idpf.org/2007/opf" version="3.0">
                  <metadata>$meta</metadata>
                  <manifest>$manifest</manifest>
                  <spine/>
                </package>
            """.trimIndent().toByteArray(),
            *images.map { it to image }.toTypedArray()
        )
        return ZipFile(book).use { EpubPackage.open(it).coverImage?.name }
    }

    private fun opf(title: String): String = """
        <?xml version="1.0" encoding="UTF-8"?>
        <package xmlns="http://www.idpf.org/2007/opf" version="2.0">
          <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
            <dc:title>$title</dc:title>
          </metadata>
          <manifest/>
          <spine/>
        </package>
    """.trimIndent()

    private fun container(opfPath: String): String = """
        <?xml version="1.0" encoding="UTF-8"?>
        <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
          <rootfiles>
            <rootfile full-path="$opfPath" media-type="application/oebps-package+xml"/>
          </rootfiles>
        </container>
    """.trimIndent()

    private fun zip(vararg entries: Pair<String, ByteArray>): File {
        val book = temp.newFile("book.epub")
        ZipOutputStream(book.outputStream()).use { out ->
            entries.forEach { (name, content) ->
                out.putNextEntry(ZipEntry(name))
                out.write(content)
                out.closeEntry()
            }
        }
        return book
    }
}
