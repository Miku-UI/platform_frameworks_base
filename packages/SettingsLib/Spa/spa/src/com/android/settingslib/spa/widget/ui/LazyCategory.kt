/*
 * Copyright (C) 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.settingslib.spa.widget.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import com.android.settingslib.spa.framework.theme.SettingsRadius
import com.android.settingslib.spa.framework.theme.SettingsSpace
import com.android.settingslib.spa.framework.theme.SettingsTheme
import com.android.settingslib.spa.widget.preference.Preference
import com.android.settingslib.spa.widget.preference.PreferenceModel

/**
 * A container that is used to group items with lazy loading.
 *
 * @param count the items count
 * @param key Optional. The key for each item in list to provide unique item identifiers, making the
 *   list more efficient.
 * @param bottomPadding Optional. Bottom outside padding of the category.
 * @param state Optional. State of LazyList.
 * @param header Optional. Content to be shown at the top of the category.
 * @param footer Optional. Content to be shown at the bottom of the category.
 * @param groupTitle Optional. A function to get the title for a group. Items with the same non-null
 *   title are considered part of the same group. A title is displayed before the first item of each
 *   group.
 * @param content - the content displayed by a single item
 */
@Composable
fun LazyCategory(
    count: Int,
    key: (Int) -> Any,
    bottomPadding: Dp = SettingsSpace.extraSmall4,
    state: LazyListState = rememberLazyListState(),
    header: @Composable () -> Unit = {},
    footer: @Composable () -> Unit = {},
    groupTitle: ((index: Int) -> String?)? = null,
    content: @Composable (index: Int) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding =
            PaddingValues(
                start = SettingsSpace.small1,
                top = SettingsSpace.extraSmall4,
                end = SettingsSpace.small1,
                bottom = bottomPadding,
            ),
        verticalArrangement = Arrangement.spacedBy(SettingsSpace.extraSmall1),
        state = state,
    ) {
        item(contentType = "header") { header() }

        data class GroupTitles(val current: String?, val previous: String?, val next: String?)

        items(count = count, key = key) { index ->
            val groupTitles =
                remember(groupTitle, index) {
                    GroupTitles(
                        current = groupTitle?.invoke(index),
                        previous = if (index > 0) groupTitle?.invoke(index - 1) else null,
                        next = if (index + 1 < count) groupTitle?.invoke(index + 1) else null,
                    )
                }
            val isFirstInGroup = index == 0 || groupTitles.current != groupTitles.previous
            val isLastInGroup = index == count - 1 || groupTitles.current != groupTitles.next

            // Display group title if:
            // 1. The groupTitle feature is enabled (groupTitle parameter is not null).
            // 2. The current item has a non-null title.
            // 3. The current item's title is different from the last displayed title.
            if (isFirstInGroup) {
                groupTitles.current?.let { CategoryTitle(it) }
            }

            val topRadius = if (isFirstInGroup) SettingsRadius.large2 else SettingsRadius.none
            val bottomRadius = if (isLastInGroup) SettingsRadius.large2 else SettingsRadius.none
            val shape = RoundedCornerShape(topRadius, topRadius, bottomRadius, bottomRadius)
            Box(modifier = Modifier.clip(shape)) {
                CompositionLocalProvider(LocalIsInCategory provides true) { content(index) }
            }
        }

        item(contentType = "footer") { footer() }
    }
}

@Preview
@Composable
private fun LazyCategoryPreview() {
    fun groupTitle(index: Int): String? =
        when (index) {
            0,
            1 -> "General"

            2,
            3 -> "Display"

            4 -> "Sound" // Single item group

            // Item 5 will have null title, thus no explicit group title before it.
            else -> null
        }

    SettingsTheme {
        LazyCategory(
            count = 6,
            key = { index -> index },
            header = { SettingsIntro("All My Settings") },
            footer = { Footer("End of settings list") },
            groupTitle = ::groupTitle,
        ) { index ->
            Preference(
                object : PreferenceModel {
                    override val title = "Preference Item $index"
                    override val summary = {
                        "This is item number $index in group '${groupTitle(index)}'."
                    }
                }
            )
        }
    }
}

@Preview
@Composable
private fun LazyCategoryNoGroupTitlePreview() {
    SettingsTheme {
        LazyCategory(
            count = 3,
            key = { index -> index },
            header = { SettingsIntro("Simple List") },
            footer = { Footer("Simple Footer") },
            groupTitle = null,
        ) { index ->
            Preference(
                object : PreferenceModel {
                    override val title = "Item $index (No Groups)"
                }
            )
        }
    }
}
