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
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.settingslib.spa.search.SpaSearchLanding.SpaSearchLandingKey
import com.android.settingslib.spa.search.SpaSearchLanding.SpaSearchLandingSpaPage
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SearchIndexableDataConverterTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    private val converter =
        SearchIndexableDataConverter(
            intentAction = INTENT_ACTION,
            intentTargetClass = INTENT_TARGET_CLASS,
        )

    @Test
    fun toSearchIndexableData() {
        val searchLandingKey =
            SpaSearchLandingKey.newBuilder()
                .setSpaPage(
                    SpaSearchLandingSpaPage.newBuilder()
                        .setDestination(PAGE_NAME)
                        .setHighlightItemKey(HIGHLIGHT_ITEM_KEY)
                )
                .build()
        val item =
            SpaSearchIndexableItem(
                searchLandingKey = searchLandingKey,
                pageTitle = PAGE_TITLE,
                itemTitle = ITEM_TITLE,
            )
        val searchIndexablePage =
            SpaSearchIndexablePage(targetClass = SearchIndexableDataConverterTest::class.java) {
                listOf(item)
            }

        val searchIndexableData = converter.toSearchIndexableData(searchIndexablePage)

        assertThat(searchIndexableData.targetClass)
            .isSameInstanceAs(SearchIndexableDataConverterTest::class.java)
        val searchIndexableRaws =
            searchIndexableData.searchIndexProvider.getDynamicRawDataToIndex(context, false)
        assertThat(searchIndexableRaws).hasSize(1)
        val rawData = searchIndexableRaws.single()
        assertThat(decodeToSpaSearchLandingKey(rawData.key)).isEqualTo(searchLandingKey)
        assertThat(rawData.title).isEqualTo(ITEM_TITLE)
        assertThat(rawData.intentAction).isEqualTo(INTENT_ACTION)
        assertThat(rawData.intentTargetClass).isEqualTo(INTENT_TARGET_CLASS)
        assertThat(rawData.className).isEqualTo(SearchIndexableDataConverterTest::class.java.name)
        assertThat(rawData.screenTitle).isEqualTo(PAGE_TITLE)
    }

    private companion object {
        const val INTENT_ACTION = "intent.Action"
        const val INTENT_TARGET_CLASS = "target.Class"
        const val PAGE_NAME = "PageName"
        const val HIGHLIGHT_ITEM_KEY = "highlight_item_key"
        const val PAGE_TITLE = "Page Title"
        const val ITEM_TITLE = "Item Title"
    }
}
