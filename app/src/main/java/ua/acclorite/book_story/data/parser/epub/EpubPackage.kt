/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.data.parser.epub

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.parser.Parser
import ua.acclorite.book_story.core.data.ExtensionsData
import ua.acclorite.book_story.core.log.logW
import ua.acclorite.book_story.core.log.messageForLog
import ua.acclorite.book_story.data.parser.document.DocumentImages
import ua.acclorite.book_story.data.parser.resolveHref
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

private const val TAG = "EpubPackage"

private const val CONTAINER = "META-INF/container.xml"
private const val OPF_MEDIA_TYPE = "application/oebps-package+xml"
private const val NCX_MEDIA_TYPE = "application/x-dtbncx+xml"

/**
 * An EPUB read the way the format defines it, not guessed from file names:
 * `META-INF/container.xml` names the package document (OPF) — the first
 * `rootfile` (OCF 2.0.1 §3.5.1, EPUB 3.3 Reading Systems §4.1.3) — and every
 * href in the OPF, the TOC and a content document is resolved against the file
 * it appears in, to a full zip path ([resolveHref]).
 *
 * One per open of the book: the zip is listed once and looked up by path.
 * A lookup that misses the exact path tries again ignoring case, as some
 * generators write hrefs in another case than the file names, and then by the
 * file name alone when exactly one entry has it — a broken relative path
 * (`../Images/a.jpg` one level too high) used to work when everything was
 * matched by file name, and books that relied on it keep working. A name two
 * entries share is never guessed.
 *
 * Broken archives still open: without a usable `container.xml` the OPF is the
 * first `.opf` entry, without an OPF the spine is every HTML document ordered
 * by the digits in its name — both logged, as neither is a valid EPUB.
 */
class EpubPackage private constructor(
    private val zip: ZipFile,
    private val entries: Map<String, ZipEntry>,
    /** Full path of the package document; null when the archive has none. */
    val opfPath: String?,
    /** The package document; null when the archive has none or it cannot be read. */
    val opf: Document?
) {

    private val entriesIgnoringCase: Map<String, ZipEntry> =
        entries.values.associateBy { it.name.lowercase() }

    /** Entries by lowercased file name, only for names no other entry has. */
    private val entriesByUniqueName: Map<String, ZipEntry> =
        entries.values.groupBy { fileName(it.name) }
            .filterValues { it.size == 1 }
            .mapValues { (_, sameName) -> sameName.single() }

    /** Manifest items by id, with full zip paths. */
    val manifest: Map<String, Item> = opf?.select("manifest > item")
        ?.mapNotNull { item ->
            val id = item.attr("id").trim()
            val path = resolveHref(opfPath.orEmpty(), item.attr("href"))
            if (id.isEmpty() || path == null) return@mapNotNull null
            Item(
                id = id,
                path = path,
                mediaType = item.attr("media-type").trim().lowercase(),
                properties = item.attr("properties").split(WHITESPACE)
                    .filter { it.isNotEmpty() }
                    .toSet()
            )
        }
        ?.associateBy { it.id }
        .orEmpty()

    /**
     * The documents to read, in reading order: the spine. Without an OPF or a
     * spine, every HTML document ordered by the digits in its name.
     */
    val spine: List<ZipEntry> = run {
        val fromSpine = opf?.select("spine > itemref")
            ?.mapNotNull { itemRef -> manifest[itemRef.attr("idref").trim()]?.path?.let(::entry) }
            .orEmpty()
        if (fromSpine.isNotEmpty()) return@run fromSpine

        logW(TAG, "No spine, reading every HTML document in the archive.")
        entries.values
            .filter { entry -> HTML_EXTENSIONS.any { entry.name.endsWith(it, ignoreCase = true) } }
            .sortedBy { entry -> entry.name.filter { it.isDigit() }.toBigIntegerOrNull() }
    }

    /** The EPUB 3 navigation document: the manifest item with the `nav` property. */
    val navDocument: ZipEntry?
        get() = manifest.values.firstOrNull { "nav" in it.properties }?.path?.let(::entry)

    /**
     * The EPUB 2 NCX: the item `spine@toc` names, else the item of the NCX media
     * type, else (no OPF) the first `.ncx` in the archive.
     */
    val ncx: ZipEntry?
        get() {
            val item = opf?.selectFirst("spine")?.attr("toc")?.trim()?.let { manifest[it] }
                ?: manifest.values.firstOrNull { it.mediaType == NCX_MEDIA_TYPE }
            return item?.path?.let(::entry)
                ?: entries.values.firstOrNull { it.name.endsWith(".ncx", ignoreCase = true) }
        }

    /**
     * The cover image: the item with the EPUB 3 `cover-image` property, else the
     * one `<meta name="cover">` names (not in any spec, but what EPUB 2 books
     * carry), else the first image in the manifest. The first that exists wins.
     */
    val coverImage: ZipEntry?
        get() {
            val candidates = sequence {
                yieldAll(manifest.values.filter { "cover-image" in it.properties })
                opf?.select("metadata > meta[name=cover]")?.forEach { meta ->
                    manifest[meta.attr("content").trim()]?.let { yield(it) }
                }
                yieldAll(manifest.values.filter { it.mediaType.startsWith("image/") })
            }
            return candidates.firstNotNullOfOrNull { item -> imageEntry(item.path) }
        }

    /**
     * The entry at the full zip [path]: exact, else ignoring case, else the
     * only entry with that file name.
     */
    fun entry(path: String): ZipEntry? =
        entries[path]
            ?: entriesIgnoringCase[path.lowercase()]
            ?: entriesByUniqueName[fileName(path)]

    /** Reads the whole text of [entry]. */
    fun read(entry: ZipEntry): String =
        zip.getInputStream(entry).bufferedReader().use { it.readText() }

    /**
     * The images of the content document [document]: a reference is resolved
     * against the document's own path, and the key is the image's full zip path.
     */
    fun imagesOf(document: ZipEntry): DocumentImages = object : DocumentImages {
        override fun resolve(ref: String): String? =
            resolveHref(document.name, ref)?.let(::imageEntry)?.name

        override fun bytes(key: String): ByteArray? = imageBytes(key)
    }

    /** The bytes of the image at the full zip path [key]; null if there is none. */
    fun imageBytes(key: String): ByteArray? =
        imageEntry(key)?.let { entry -> zip.getInputStream(entry).use { it.readBytes() } }

    private fun imageEntry(path: String): ZipEntry? =
        entry(path)?.takeIf { entry ->
            ExtensionsData.imageExtensions.any { entry.name.endsWith(it, ignoreCase = true) }
        }

    data class Item(
        val id: String,
        val path: String,
        val mediaType: String,
        val properties: Set<String>
    )

    companion object {
        private val WHITESPACE = Regex("\\s+")

        private fun fileName(path: String): String = path.substringAfterLast('/').lowercase()
        private val HTML_EXTENSIONS = listOf(".html", ".htm", ".xhtml")

        /** Reads the container and the package document of [zip]. Blocking I/O. */
        fun open(zip: ZipFile): EpubPackage {
            val entries = zip.entries().asSequence()
                .filterNot { it.isDirectory }
                .associateBy { it.name }

            val opfPath = rootfile(zip, entries)
                ?: entries.keys.firstOrNull { it.endsWith(".opf", ignoreCase = true) }
                    ?.also { logW(TAG, "No usable $CONTAINER, took the first .opf: [$it].") }

            val opf = opfPath?.let { entries[it] }?.let { entry ->
                val content = zip.getInputStream(entry).bufferedReader().use { it.readText() }
                Jsoup.parse(content, "", Parser.xmlParser())
            }

            return EpubPackage(zip, entries, opfPath, opf)
        }

        /** The full path of the package document `container.xml` names, if it exists. */
        private fun rootfile(zip: ZipFile, entries: Map<String, ZipEntry>): String? {
            val container = entries[CONTAINER] ?: return null
            val document = try {
                val content = zip.getInputStream(container).bufferedReader().use { it.readText() }
                Jsoup.parse(content, "", Parser.xmlParser())
            } catch (e: Exception) {
                logW(TAG, "Could not read $CONTAINER: ${e.messageForLog()}")
                return null
            }

            val rootfiles = document.select("rootfile")
            val rootfile = rootfiles.firstOrNull { it.attr("media-type").trim() == OPF_MEDIA_TYPE }
                ?: rootfiles.firstOrNull()
                ?: return null

            // full-path is relative to the container root, not to META-INF
            return resolveHref("", rootfile.attr("full-path"))
                ?.let { path -> entries[path]?.name ?: entries.keys.firstOrNull { it.equals(path, ignoreCase = true) } }
        }
    }
}
