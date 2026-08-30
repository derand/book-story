/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.ui.common.components.settings

import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Done
import androidx.compose.material3.Icon
import androidx.compose.material3.SegmentedButtonColors
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ua.acclorite.book_story.R
import ua.acclorite.book_story.ui.common.components.common.AnimatedVisibility
import ua.acclorite.book_story.ui.common.components.common.StyledText
import ua.acclorite.book_story.ui.common.model.ListItem
import ua.acclorite.book_story.ui.settings.components.SettingsSubcategoryTitle

/**
 * What the tick in front of the selected button costs in width: the icon plus
 * the gap after it. Exactly one button carries it at a time, so it is the whole
 * difference between the widest and the narrowest state of the row.
 */
private val SELECTION_TICK_WIDTH = 18.dp + 8.dp

@Composable
fun <T> SegmentedButtonWithTitle(
    modifier: Modifier = Modifier,
    title: String,
    buttons: List<ListItem<T>>,
    enabled: Boolean = true,
    horizontalPadding: Dp = 18.dp,
    verticalPadding: Dp = 8.dp,
    onClick: (T) -> Unit
) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalPadding, vertical = verticalPadding)
    ) {
        SettingsSubcategoryTitle(title = title, padding = 0.dp)

        Spacer(modifier = Modifier.height(8.dp))

        SegmentedButtonsOrChips(
            buttons = buttons,
            enabled = enabled,
            onClick = onClick
        )
    }
}

/**
 * The segmented row where it fits, the chips where it does not.
 *
 * The row is laid out at the natural width of its labels, and that width is not
 * a property of the setting but of the translation and the reader's font scale:
 * the four alignment options fit English with 5 dp to spare, and neither German
 * nor Ukrainian. What used to happen then was the worst of the options — the row
 * scrolled, with nothing to say so, and what fell off the right edge could be
 * the selected value itself, leaving a setting that reads as if nothing were
 * chosen. Chips wrap instead, which is the one thing a row of fixed height
 * cannot do.
 *
 * The fit is measured with **no button selected, plus the width of the tick**,
 * rather than as the row currently stands. Measuring the current state would
 * make the decision depend on which label happens to be selected, and a settings
 * row that turns into chips because you tapped a longer option — and back when
 * you tap a shorter one — is worse than the overflow it fixes.
 */
@Composable
private fun <T> SegmentedButtonsOrChips(
    buttons: List<ListItem<T>>,
    enabled: Boolean,
    onClick: (T) -> Unit
) {
    val unselected = buttons.map { it.copy(selected = false) }

    SubcomposeLayout(Modifier.fillMaxWidth()) { constraints ->
        val widest = subcompose(SlotId.Measure) {
            SegmentedButtons(buttons = unselected, enabled = enabled, onClick = {})
        }.first().measure(Constraints()).width + SELECTION_TICK_WIDTH.roundToPx()

        val fits = widest <= constraints.maxWidth

        // `minWidth` is dropped on the way down: the row is as wide as its
        // buttons and no wider, and passing the incoming minimum on would
        // stretch the pill across the whole line.
        val content = subcompose(if (fits) SlotId.Buttons else SlotId.Chips) {
            when (fits) {
                true -> SegmentedButtons(
                    buttons = buttons,
                    enabled = enabled,
                    onClick = onClick
                )

                false -> ChipsFlow(
                    chips = buttons,
                    enabled = enabled,
                    onClick = onClick
                )
            }
        }.first().measure(constraints.copy(minWidth = 0))

        // Reported back at the width that was asked for, even though the row
        // is narrower: a node that reports less than the incoming minimum has
        // its content centred, which would push the whole control off to the
        // middle of the line. Placed relatively, so that a row narrower than
        // the line still starts where the title does — the right edge in a
        // right-to-left locale.
        layout(content.width.coerceAtLeast(constraints.minWidth), content.height) {
            content.placeRelative(0, 0)
        }
    }
}

/**
 * The slots [SegmentedButtonsOrChips] subcomposes. [SlotId.Measure] is measured
 * and never placed; it exists only to ask the row how wide it wants to be.
 */
private enum class SlotId { Measure, Buttons, Chips }

@Composable
private fun <T> SegmentedButtons(
    buttons: List<ListItem<T>>,
    enabled: Boolean,
    onClick: (T) -> Unit
) {
    Row(
        Modifier
            .clip(CircleShape)
            .border(
                width = 0.5.dp,
                color = SegmentedButtonDefaults.colors().activeBorderColor,
                shape = CircleShape
            )
            .padding(0.5.dp)
    ) {
        buttons.forEachIndexed { index, item ->
            SegmentedButton(
                button = item,
                enabled = enabled,
                shape = when (index) {
                    buttons.lastIndex -> RoundedCornerShape(
                        topEndPercent = 100,
                        bottomEndPercent = 100
                    )

                    0 -> RoundedCornerShape(
                        topStartPercent = 100,
                        bottomStartPercent = 100
                    )

                    else -> RoundedCornerShape(0)
                },
                onClick = { onClick(item.item) }
            )
        }
    }
}

@Composable
private fun <T> SegmentedButton(
    button: ListItem<T>,
    enabled: Boolean,
    shape: RoundedCornerShape,
    colors: SegmentedButtonColors = SegmentedButtonDefaults.colors(),
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .height(40.dp)
            .clip(shape)
            .clickable(enabled = enabled && !button.selected) {
                onClick()
            }
            .border(
                width = 0.5.dp,
                color = colors.activeBorderColor,
                shape = shape
            )
            .padding(0.5.dp)
            .background(
                if (button.selected) colors.activeContainerColor
                else Color.Transparent,
                shape = shape
            )
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AnimatedVisibility(
            visible = button.selected,
            enter = expandHorizontally()
                    + slideInVertically(initialOffsetY = { it / 2 })
                    + scaleIn()
                    + fadeIn(),
            exit = shrinkHorizontally()
                    + slideOutVertically(targetOffsetY = { it / 2 })
                    + scaleOut()
                    + fadeOut()
        ) {
            Row {
                Icon(
                    imageVector = Icons.Default.Done,
                    contentDescription = stringResource(id = R.string.selected_content_desc),
                    modifier = Modifier
                        .size(18.dp),
                    tint = colors.activeContentColor
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
        }

        StyledText(
            text = button.title,
            style = button.textStyle().copy(
                color = if (button.selected) colors.activeContentColor
                else colors.inactiveContentColor
            )
        )
    }
}
