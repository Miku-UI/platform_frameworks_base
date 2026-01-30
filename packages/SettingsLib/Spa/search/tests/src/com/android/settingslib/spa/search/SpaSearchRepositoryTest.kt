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
import com.android.settingslib.spa.framework.common.SettingsPageProvider
import com.android.settingslib.spa.framework.common.SettingsPageProviderRepository
import com.android.settingslib.spa.framework.common.SpaEnvironment
import com.android.settingslib.spa.framework.common.SpaEnvironmentFactory
import com.android.settingslib.spa.search.SpaSearchLanding.SpaSearchLandingKey
import com.android.settingslib.spa.search.SpaSearchLanding.SpaSearchLandingSpaPage
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SpaSearchRepositoryTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    val pageProvider: SettingsPageProvider =
        object : SettingsPageProvider, SearchablePage {
            override val name = PAGE_NAME

            override fun getPageTitleForSearch(context: Context) = PAGE_TITLE

            override fun getSearchItems(context: Context) =
                listOf(
                    SearchablePage.SearchItem(
                        highlightItemKey = HIGHLIGHT_ITEM_KEY,
                        itemTitle = ITEM_TITLE,
                    )
                )
        }

    private val repository = SpaSearchRepository()

    @Before
    fun setUp() {
        SpaEnvironmentFactory.reset(
            object : SpaEnvironment(context) {
                override val pageProviderRepository = lazy {
                    SettingsPageProviderRepository(allPageProviders = listOf(pageProvider))
                }
            }
        )
    }

    @Test
    fun getSearchIndexablePageList() {
        val searchIndexablePageList = repository.getSearchIndexablePageList()

        assertThat(searchIndexablePageList).hasSize(1)
        val searchIndexablePage = searchIndexablePageList.single()
        assertThat(searchIndexablePage.targetClass).isEqualTo(pageProvider::class.java)
        val items = searchIndexablePage.itemsProvider(context)
        assertThat(items).hasSize(1)
        assertThat(items.single())
            .isEqualTo(
                SpaSearchIndexableItem(
                    searchLandingKey =
                        SpaSearchLandingKey.newBuilder()
                            .setSpaPage(
                                SpaSearchLandingSpaPage.newBuilder()
                                    .setDestination(PAGE_NAME)
                                    .setHighlightItemKey(HIGHLIGHT_ITEM_KEY)
                            )
                            .build(),
                    pageTitle = PAGE_TITLE,
                    itemTitle = ITEM_TITLE,
                )
            )
    }

    private companion object {
        const val HIGHLIGHT_ITEM_KEY = "highlight_item_key"
        const val PAGE_NAME = "PageName"
        const val PAGE_TITLE = "Page Title"
        const val ITEM_TITLE = "Item Title"
    }
}
