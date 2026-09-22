/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.core.log

private const val ELLIPSIS = "…"

/**
 * A location, in the two shapes one reaches a log line in.
 *
 * The first branch is a URI, kept as far as its authority: the authority names
 * the provider a file came from, which is the whole diagnostic value of the
 * line, and it can name no folder and no book.
 *
 * The second is an absolute path — two segments at least, so that a lone
 * `/dev` in a sentence is not mistaken for one, and not starting inside a word,
 * so that `and/or` is not either.
 *
 * A path does not always end at its last slash: file names hold spaces, and
 * `/storage/emulated/0/Books/Vol. 1 — Sci-Fi.epub` would keep everything after
 * `Vol.` if the run stopped at the first of them. So when the run does not
 * already end in something shaped like an extension, it goes on to the furthest
 * one that does, and no further than the `:` `(` `[` `,` that every
 * `FileNotFoundException` puts between the path and its reason. The reason
 * therefore survives, and a name with spaces does not.
 */
private val LOCATION = Regex(
    """([a-zA-Z][a-zA-Z0-9+.\-]*://[^/\s]*)\S*""" +
        """|(?<![^\s:(\[="'])(?:/[^/\s:()\[\],]+){2,}""" +
        """(?:(?<!\.[A-Za-z0-9]{1,5})[^:()\[\],]*\.[A-Za-z0-9]{1,5})?"""
)

/**
 * [text] with every path and every URI path in it replaced by an ellipsis.
 *
 * Nothing here is a leak between apps — logcat is readable only by adb and by a
 * system app holding `READ_LOGS`. It is a leak into a `bugreport`, a file the
 * user may hand to someone, where a list of paths reads as a list of what they
 * have been reading.
 */
fun withoutLocations(text: String): String = LOCATION.replace(text) { match ->
    val uri = match.groupValues[1]
    when {
        uri.isEmpty() -> ELLIPSIS
        match.value.length > uri.length -> "$uri/$ELLIPSIS"
        else -> uri
    }
}

/**
 * What a throwable says, without the locations it says it about.
 *
 * The platform writes the path into the message — `FileNotFoundException` is
 * `<path>: open failed: ENOENT` — so a line that logs `message` names the book
 * as plainly as one that logs the title. The exception's own name is put in
 * front of what survives, because after a path is taken out of a message the
 * class is often the only diagnosis left; it is also what is logged when there
 * is no message at all, in place of the `null` these lines used to print.
 */
fun Throwable.messageForLog(): String {
    val name = javaClass.simpleName
    return message?.let { "$name: ${withoutLocations(it)}" } ?: name
}
