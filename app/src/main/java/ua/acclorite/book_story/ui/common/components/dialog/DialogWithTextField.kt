/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.ui.common.components.dialog

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import ua.acclorite.book_story.R
import ua.acclorite.book_story.ui.common.components.common.StyledText
import ua.acclorite.book_story.ui.theme.ExpandingTransition

/**
 * A dialog with one text field.
 *
 * [lengthLimit] cuts what does not fit (see [limitedEdit]); the counter shows up once the text
 * is within a tenth of the limit. With [allowBlank] = false, OK on a blank field shows an error
 * and keeps the dialog open. OK on an unchanged value only dismisses.
 */
@Composable
fun DialogWithTextField(
    initialValue: String,
    title: String = stringResource(id = R.string.edit),
    label: String? = null,
    lengthLimit: Int = Int.MAX_VALUE,
    allowBlank: Boolean = true,
    onDismiss: () -> Unit,
    onAction: (String) -> Unit
) {
    val state = remember(initialValue) {
        mutableStateOf(
            TextFieldValue(
                text = initialValue,
                selection = TextRange(0, initialValue.length)
            )
        )
    }
    val error = remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val focused = remember { mutableStateOf(false) }

    val length = state.value.text.length
    val showCounter = lengthLimit != Int.MAX_VALUE && length >= lengthLimit - lengthLimit / 10

    Dialog(
        title = title,
        description = null,
        onDismiss = { onDismiss() },
        actionEnabled = true,
        disableOnClick = false,
        onAction = {
            if (!allowBlank && state.value.text.isBlank()) {
                error.value = true
                return@Dialog
            }
            if (state.value.text == initialValue) {
                onDismiss()
                return@Dialog
            }

            onAction(state.value.text)
            onDismiss()
        },
        withContent = true,
        items = {
            item {
                OutlinedTextField(
                    value = state.value,
                    onValueChange = {
                        error.value = false
                        state.value = limitedEdit(state.value, it, lengthLimit)
                    },
                    keyboardOptions = KeyboardOptions(KeyboardCapitalization.Sentences),
                    isError = error.value,
                    modifier = Modifier
                        .padding(horizontal = 24.dp)
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                        .onGloballyPositioned {
                            if (!focused.value) {
                                focusRequester.requestFocus()
                                focused.value = true
                            }
                        },
                    colors = TextFieldDefaults.colors(
                        errorContainerColor = Color.Transparent,
                        focusedContainerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent
                    ),
                    label = label?.let {
                        {
                            StyledText(
                                text = label,
                                maxLines = 1
                            )
                        }
                    },
                    supportingText = {
                        // The field colours this: error colour while isError.
                        ExpandingTransition(error.value || showCounter) {
                            StyledText(
                                text = if (error.value) {
                                    stringResource(id = R.string.error_field_empty)
                                } else {
                                    "$length / $lengthLimit"
                                }
                            )
                        }
                    }
                )
            }
        }
    )
}

/**
 * Applies the edit [old] → [new] under [limit].
 *
 * What would go over the limit is cut from the inserted part, so a long paste keeps what fits
 * instead of being refused whole. A value that is already longer than the limit (a title from
 * the book's metadata) is never cut by an edit: the cap is then its current length, so it can
 * only get shorter.
 */
internal fun limitedEdit(old: TextFieldValue, new: TextFieldValue, limit: Int): TextFieldValue {
    val cap = maxOf(limit, old.text.length)
    if (new.text.length <= cap) return new

    val oldText = old.text
    val newText = new.text
    val prefix = oldText.commonPrefixWith(newText).length
    val suffixMax = minOf(oldText.length, newText.length) - prefix
    var suffix = 0
    while (
        suffix < suffixMax &&
        oldText[oldText.length - 1 - suffix] == newText[newText.length - 1 - suffix]
    ) {
        suffix++
    }

    // prefix + suffix <= old length <= cap, so the room is never negative.
    var cutEnd = prefix + (cap - prefix - suffix)
    if (cutEnd > prefix && newText[cutEnd - 1].isHighSurrogate()) cutEnd--

    return TextFieldValue(
        text = newText.substring(0, cutEnd) + newText.substring(newText.length - suffix),
        selection = TextRange(cutEnd)
    )
}
