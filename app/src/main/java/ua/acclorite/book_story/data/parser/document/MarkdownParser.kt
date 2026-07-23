/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.data.parser.document

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.em
import org.commonmark.node.Code
import org.commonmark.node.Emphasis
import org.commonmark.node.Heading
import org.commonmark.node.Link
import org.commonmark.node.Node
import org.commonmark.node.StrongEmphasis
import org.commonmark.node.Text
import org.commonmark.parser.Parser
import ua.acclorite.book_story.core.helpers.clearMarkdown
import javax.inject.Inject

/** Span styles toggled by the private-use marks embedded in the text. */
private val MARK_STYLES = mapOf(
    STRIKETHROUGH_MARK.single() to SpanStyle(
        textDecoration = TextDecoration.LineThrough
    ),
    SUBSCRIPT_MARK.single() to SpanStyle(
        baselineShift = BaselineShift.Subscript,
        fontSize = 0.75.em
    ),
    SUPERSCRIPT_MARK.single() to SpanStyle(
        baselineShift = BaselineShift.Superscript,
        fontSize = 0.75.em
    )
)

class MarkdownParser @Inject constructor(
    private val commonmarkParser: Parser
) {
    /**
     * Parses markdown text to [AnnotatedString].
     *
     * @return Parsed annotated string.
     */
    fun parse(markdown: String): AnnotatedString {
        return try {
            val annotatedString = buildAnnotatedString {
                parseNode(commonmarkParser.parse(markdown))
            }.ifBlank { buildAnnotatedString { append(markdown) } }
                .trim() as AnnotatedString

            annotatedString
        } catch (e: Exception) {
            e.printStackTrace()
            buildAnnotatedString { append(markdown) }
        }
    }

    /**
     * Parses [Node].
     * Appends text and applies styles to the target [AnnotatedString.Builder].
     */
    private fun AnnotatedString.Builder.parseNode(node: Node) {
        when (node) {
            is Heading, is StrongEmphasis -> {
                withStyle(SpanStyle(fontWeight = FontWeight.Medium)) {
                    parseChildren(node)
                }
            }

            is Emphasis -> {
                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                    parseChildren(node)
                }
            }

            is Code -> {
                withStyle(SpanStyle(fontFamily = FontFamily.Monospace)) {
                    append(node.literal)
                }
            }

            is Link -> {
                withLink(
                    LinkAnnotation.Url(
                        node.destination,
                        styles = TextLinkStyles(style = SpanStyle(textDecoration = TextDecoration.Underline))
                    )
                ) {
                    parseChildren(node)
                    append(" (${node.destination})")
                }
            }

            is Text -> {
                appendMarked(node.literal.clearMarkdown())
                parseChildren(node)
            }

            else -> {
                parseChildren(node)
            }
        }
    }

    /**
     * Appends [text], turning any run wrapped in a private-use mark
     * ([STRIKETHROUGH_MARK], [SUBSCRIPT_MARK], [SUPERSCRIPT_MARK]) into the
     * corresponding styled span. Each mark toggles its own state, so the
     * styles compose with each other and with whatever emphasis the
     * surrounding nodes already applied.
     */
    private fun AnnotatedString.Builder.appendMarked(text: String) {
        if (text.none { char -> char in MARK_STYLES }) {
            append(text)
            return
        }

        val active = mutableSetOf<Char>()
        val segment = StringBuilder()

        fun flush() {
            if (segment.isEmpty()) return
            active
                .map { mark -> MARK_STYLES.getValue(mark) }
                .reduceOrNull { merged, style -> merged.merge(style) }
                ?.let { style -> withStyle(style) { append(segment.toString()) } }
                ?: append(segment.toString())
            segment.clear()
        }

        text.forEach { char ->
            if (char in MARK_STYLES) {
                flush()
                if (!active.remove(char)) active.add(char)
            } else {
                segment.append(char)
            }
        }
        flush()
    }

    private fun AnnotatedString.Builder.parseChildren(node: Node) {
        var child = node.firstChild
        while (child != null) {
            parseNode(child)
            child = child.next
        }
    }
}