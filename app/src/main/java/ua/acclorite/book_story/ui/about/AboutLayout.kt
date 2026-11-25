/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.ui.about

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ua.acclorite.book_story.BuildConfig
import ua.acclorite.book_story.R
import ua.acclorite.book_story.core.data.LinkData

/** About's list: the app icon, links, Licenses, Credits and the badges. */
fun LazyListScope.aboutItems(
    navigateToBrowserPage: (String) -> Unit,
    navigateToLicenses: () -> Unit,
    navigateToCredits: () -> Unit
) {
    item {
        Spacer(modifier = Modifier.height(16.dp))
    }

    item {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painterResource(id = R.drawable.app_icon),
                contentDescription = stringResource(id = R.string.app_icon_content_desc),
                modifier = Modifier
                    .padding(14.dp)
                    .size(120.dp),
                tint = MaterialTheme.colorScheme.secondary
            )
        }
    }

    item {
        Spacer(modifier = Modifier.height(36.dp))
        HorizontalDivider(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.outlineVariant
        )
    }

    item {
        AboutItem(
            title = stringResource(id = R.string.app_version_option),
            description = "${stringResource(R.string.app_name)} v${BuildConfig.VERSION_NAME}",
        ) {
            navigateToBrowserPage(LinkData.RELEASES)
        }
    }

    item {
        AboutItem(
            title = stringResource(id = R.string.report_bug_option),
            description = null
        ) {
            navigateToBrowserPage(LinkData.ISSUES)
        }
    }


    item {
        AboutItem(
            title = stringResource(id = R.string.contributors_option),
            description = null
        ) {
            navigateToBrowserPage(LinkData.CONTRIBUTORS)
        }
    }

    item {
        AboutItem(
            title = stringResource(id = R.string.licenses_option),
            description = null
        ) {
            navigateToLicenses()
        }
    }

    item {
        AboutItem(
            title = stringResource(id = R.string.credits_option),
            description = null
        ) {
            navigateToCredits()
        }
    }

    item {
        AboutItem(
            title = stringResource(id = R.string.support_original_author_option),
            description = null
        ) {
            navigateToBrowserPage(LinkData.SUPPORT_ORIGINAL_AUTHOR)
        }
    }

    item {
        AboutBadges(
            navigateToBrowserPage = navigateToBrowserPage
        )
    }
}
