/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.ui.reader

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import ua.acclorite.book_story.domain.model.reader.ReaderText.Table
import ua.acclorite.book_story.presentation.reader.model.ReaderFontThickness
import ua.acclorite.book_story.ui.common.components.common.StyledText
import ua.acclorite.book_story.ui.reader.model.FontWithName

private val CELL_PADDING = 8.dp

/**
 * A table drawn as a full grid: a border around every cell, columns of equal
 * width sharing the page width, the first row bold when it is a header.
 */
@Composable
fun LazyItemScope.ReaderLayoutTextTable(
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

    Column(
        modifier = Modifier
            .animateItem(fadeInSpec = null, fadeOutSpec = null)
            .fillMaxWidth()
            .padding(horizontal = sidePadding)
    ) {
        HorizontalDivider(color = lineColor)

        table.rows.forEachIndexed { rowIndex, row ->
            val header = rowIndex == 0 && table.hasHeader

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min)
            ) {
                VerticalDivider(color = lineColor)

                repeat(columns) { columnIndex ->
                    StyledText(
                        text = row.getOrNull(columnIndex) ?: AnnotatedString(""),
                        modifier = Modifier
                            .weight(1f)
                            .padding(CELL_PADDING),
                        style = TextStyle(
                            fontFamily = fontFamily.font,
                            fontWeight = if (header) FontWeight.Bold else fontThickness.thickness,
                            fontStyle = fontStyle,
                            letterSpacing = letterSpacing,
                            fontSize = fontSize,
                            lineHeight = lineHeight,
                            color = fontColor
                        )
                    )

                    VerticalDivider(color = lineColor)
                }
            }

            HorizontalDivider(color = lineColor)
        }
    }
}
