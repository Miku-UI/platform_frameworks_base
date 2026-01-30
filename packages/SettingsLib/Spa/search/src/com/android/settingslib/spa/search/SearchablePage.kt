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

package com.android.settingslib.spa.search

import android.content.Context
import com.android.settingslib.spa.framework.compose.HighlightBox

interface SearchablePage {
    data class SearchItem(
        /**
         * The key to highlight the item in the page.
         *
         * The corresponding item in the page should be wrapped with [HighlightBox] and pass this
         * key to [HighlightBox]'s `highlightItemKey` parameter. When search result item is clicked,
         * the item with this key will be highlighted.
         */
        val highlightItemKey: String,
        val itemTitle: String,
        val keywords: String? = null,
    )

    /** Gets the title of the page. */
    fun getPageTitleForSearch(context: Context): String

    /** Gets the titles of the searchable items at the current moment. */
    fun getSearchItems(context: Context): List<SearchItem>
}
