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

package com.android.settingslib.spa.search.test

import com.android.settingslib.spa.search.SpaSearchIndexableItem
import com.android.settingslib.spa.search.SpaSearchIndexablePage
import com.android.settingslib.spa.search.SpaSearchIndexablesProvider
import com.android.settingslib.spa.search.SpaSearchLanding.SpaSearchLandingKey
import com.android.settingslib.spa.search.SpaSearchLanding.SpaSearchLandingSpaPage

class TestSpaSearchIndexablesProvider : SpaSearchIndexablesProvider() {
    override val intentAction = INTENT_ACTION

    override fun getSpaSearchIndexablePageList() =
        listOf(SpaSearchIndexablePage(TestSpaSearchIndexablesProvider::class.java) { listOf(Item) })

    companion object {
        const val INTENT_ACTION = "intent.Action"
        const val PAGE_NAME = "PageName"
        const val HIGHLIGHT_ITEM_KEY = "highlight_item_key"
        const val PAGE_TITLE = "Page Title"
        const val ITEM_TITLE = "Item Title"
        const val KEYWORDS = "item title"

        val SearchLandingKey: SpaSearchLandingKey =
            SpaSearchLandingKey.newBuilder()
                .setSpaPage(
                    SpaSearchLandingSpaPage.newBuilder()
                        .setDestination(PAGE_NAME)
                        .setHighlightItemKey(HIGHLIGHT_ITEM_KEY)
                )
                .build()

        val Item =
            SpaSearchIndexableItem(
                searchLandingKey = SearchLandingKey,
                pageTitle = PAGE_TITLE,
                itemTitle = ITEM_TITLE,
                keywords = KEYWORDS,
            )
    }
}
