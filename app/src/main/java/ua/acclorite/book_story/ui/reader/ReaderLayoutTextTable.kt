/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.ui.reader

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.MultiParagraphIntrinsics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import ua.acclorite.book_story.domain.model.reader.ReaderText.Table
import ua.acclorite.book_story.domain.model.reader.TableAlignment
import ua.acclorite.book_story.domain.model.reader.SearchMatch
import ua.acclorite.book_story.presentation.reader.model.ReaderFontThickness
import ua.acclorite.book_story.ui.common.components.common.StyledText
import ua.acclorite.book_story.ui.reader.model.FontWithName
import kotlin.math.ceil

private val CELL_PADDING = 8.dp
private val LINE_THICKNESS = 1.dp

/**
 * A table drawn as a full grid: a border around every cell, the first row bold
 * when it is a header, and columns sized by their content — see
 * [resolveColumnWidths] — so the table still spans the page width exactly.
 */
@Composable
fun LazyItemScope.ReaderLayoutTextTable(
    searchMatches: List<SearchMatch>,
    currentSearchMatch: SearchMatch?,
    table: Table,
    fontFamily: FontWithName,
    fontColor: Color,
    lineHeight: TextUnit,
    fontThickness: ReaderFontThickness,
    fontStyle: FontStyle,
    fontSize: TextUnit,
    letterSpacing: TextUnit,
    sidePadding: Dp
) {
    val lineColor = fontColor.copy(alpha = 0.4f)
    val columns = table.rows.maxOfOrNull { it.size } ?: 0
    if (columns == 0) return

    val density = LocalDensity.current
    val fontFamilyResolver = LocalFontFamilyResolver.current

    val bodyStyle = TextStyle(
        fontFamily = fontFamily.font,
        fontWeight = fontThickness.thickness,
        fontStyle = fontStyle,
        letterSpacing = letterSpacing,
        fontSize = fontSize,
        lineHeight = lineHeight,
        color = fontColor,
        // StyledText forces this too — keep the measured and drawn styles equal.
        textDirection = TextDirection.Content
    )
    val headerStyle = bodyStyle.copy(fontWeight = FontWeight.Bold)

    // Alignment does not affect either intrinsic width, so the columns are
    // still measured with the plain styles above — these only draw.
    val bodyStyles = remember(bodyStyle, table, columns) {
        table.alignedStyles(bodyStyle, columns)
    }
    val headerStyles = remember(headerStyle, table, columns) {
        table.alignedStyles(headerStyle, columns)
    }

    BoxWithConstraints(
        modifier = Modifier
            .animateItem(fadeInSpec = null, fadeOutSpec = null)
            .fillMaxWidth()
            .padding(horizontal = sidePadding)
    ) {
        val gridWidth = constraints.maxWidth
        val columnWidths = remember(
            table, columns, bodyStyle, headerStyle, gridWidth, density, fontFamilyResolver
        ) {
            measureColumnWidths(
                table = table,
                columns = columns,
                bodyStyle = bodyStyle,
                headerStyle = headerStyle,
                gridWidth = gridWidth,
                density = density,
                fontFamilyResolver = fontFamilyResolver
            )
        }

        Column(modifier = Modifier.fillMaxWidth()) {
            HorizontalDivider(thickness = LINE_THICKNESS, color = lineColor)

            table.rows.forEachIndexed { rowIndex, row ->
                val header = rowIndex == 0 && table.hasHeader

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min)
                ) {
                    VerticalDivider(thickness = LINE_THICKNESS, color = lineColor)

                    repeat(columns) { columnIndex ->
                        StyledText(
                            text = (row.getOrNull(columnIndex) ?: AnnotatedString(""))
                                .withSearchHighlights(
                                    matches = searchMatches,
                                    current = currentSearchMatch,
                                    fontColor = fontColor,
                                    // The same numbering [forEachCell] hands the
                                    // scanner: a position in the drawn grid.
                                    part = rowIndex * columns + columnIndex
                                ),
                            modifier = Modifier
                                .width(with(density) { columnWidths[columnIndex].toDp() })
                                .padding(CELL_PADDING),
                            style = if (header) {
                                headerStyles[columnIndex]
                            } else {
                                bodyStyles[columnIndex]
                            }
                        )

                        VerticalDivider(thickness = LINE_THICKNESS, color = lineColor)
                    }
                }

                HorizontalDivider(thickness = LINE_THICKNESS, color = lineColor)
            }
        }
    }
}

/**
 * One drawing style per column: [style] with the column's alignment applied.
 * A column that states nothing reuses [style] itself, so a table without
 * alignment allocates nothing and draws exactly as it did before.
 */
private fun Table.alignedStyles(style: TextStyle, columns: Int): List<TextStyle> =
    List(columns) { column ->
        when (alignmentAt(column)) {
            TableAlignment.Unspecified -> style
            TableAlignment.Start -> style.copy(textAlign = TextAlign.Start)
            TableAlignment.Center -> style.copy(textAlign = TextAlign.Center)
            TableAlignment.End -> style.copy(textAlign = TextAlign.End)
        }
    }

/**
 * Measures every cell to learn how much room each column wants, then asks
 * [resolveColumnWidths] to fit those wishes into [gridWidth].
 *
 * Text intrinsics give both numbers without laying anything out:
 * `maxIntrinsicWidth` is the cell on a single line, `minIntrinsicWidth` is its
 * widest unbreakable run. Widths are measured once for the whole table, never
 * per row, so the column boundaries line up on every row.
 */
private fun measureColumnWidths(
    table: Table,
    columns: Int,
    bodyStyle: TextStyle,
    headerStyle: TextStyle,
    gridWidth: Int,
    density: Density,
    fontFamilyResolver: FontFamily.Resolver
): IntArray {
    val natural = IntArray(columns)
    val minimum = IntArray(columns)

    table.rows.forEachIndexed { rowIndex, row ->
        val style = if (rowIndex == 0 && table.hasHeader) headerStyle else bodyStyle
        for (columnIndex in 0 until columns) {
            val text = row.getOrNull(columnIndex) ?: continue
            if (text.isEmpty()) continue

            val intrinsics = MultiParagraphIntrinsics(
                annotatedString = text,
                style = style,
                placeholders = emptyList(),
                density = density,
                fontFamilyResolver = fontFamilyResolver
            )
            natural[columnIndex] = maxOf(
                natural[columnIndex], ceil(intrinsics.maxIntrinsicWidth).toInt()
            )
            minimum[columnIndex] = maxOf(
                minimum[columnIndex], ceil(intrinsics.minIntrinsicWidth).toInt()
            )
        }
    }

    val padding = with(density) { (CELL_PADDING * 2).roundToPx() }
    for (columnIndex in 0 until columns) {
        natural[columnIndex] += padding
        // A cell can be squeezed down to its widest word, never past it.
        minimum[columnIndex] = (minimum[columnIndex] + padding)
            .coerceAtMost(natural[columnIndex])
    }

    // One divider between every pair of columns, plus the two outer ones.
    val dividers = with(density) { LINE_THICKNESS.roundToPx() } * (columns + 1)
    return resolveColumnWidths(
        natural = natural,
        minimum = minimum,
        available = gridWidth - dividers
    )
}
