/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.core.ui

import android.content.Context
import android.os.Parcelable
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.res.stringResource
import kotlinx.parcelize.Parcelize
import java.io.Serializable

@Parcelize
@Immutable
sealed class UIText : Parcelable {
    data class StringValue(val value: String) : UIText()
    class StringResource(@StringRes val resId: Int, vararg val args: Serializable) : UIText() {
        // By value, as for StringValue: args is an array, which compares by identity.
        override fun equals(other: Any?): Boolean {
            return other is StringResource &&
                resId == other.resId &&
                args.contentEquals(other.args)
        }

        override fun hashCode(): Int = 31 * resId + args.contentHashCode()

        override fun toString(): String = "StringResource($resId, ${args.contentToString()})"
    }

    @Composable
    fun asString(): String {
        return when (this) {
            is StringValue -> value
            is StringResource -> stringResource(resId, *args)
        }
    }

    fun asString(context: Context): String {
        return when (this) {
            is StringValue -> value
            is StringResource -> context.getString(resId, *args)
        }
    }

    /** The text itself for a [StringValue]; null for a [StringResource]. */
    fun literalOrNull(): String? {
        return when (this) {
            is StringValue -> value
            is StringResource -> null
        }
    }
}