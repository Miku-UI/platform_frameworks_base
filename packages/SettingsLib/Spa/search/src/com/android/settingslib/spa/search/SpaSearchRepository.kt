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
import android.util.Log
import com.android.settingslib.spa.framework.common.SettingsPageProvider
import com.android.settingslib.spa.framework.common.SpaEnvironmentFactory
import com.android.settingslib.spa.search.SearchablePage.SearchItem
import com.android.settingslib.spa.search.SpaSearchLanding.SpaSearchLandingKey
import com.android.settingslib.spa.search.SpaSearchLanding.SpaSearchLandingSpaPage

class SpaSearchRepository() {
    /** Gets the search indexable data list */
    fun getSearchIndexablePageList(): List<SpaSearchIndexablePage> {
        Log.d(TAG, "getSearchIndexablePage")
        return SpaEnvironmentFactory.instance.pageProviderRepository.value
            .getAllProviders()
            .mapNotNull { page ->
                if (page is SearchablePage) {
                    page.createSpaSearchIndexablePage(
                        getPageTitleForSearch = page::getPageTitleForSearch,
                        getSearchItems = page::getSearchItems,
                    )
                } else null
            }
    }

    companion object {
        private const val TAG = "SpaSearchRepository"

        private fun SettingsPageProvider.createSpaSearchIndexablePage(
            getPageTitleForSearch: (context: Context) -> String,
            getSearchItems: (context: Context) -> List<SearchItem>,
        ) =
            SpaSearchIndexablePage(targetClass = this::class.java) { context ->
                val pageTitle = getPageTitleForSearch(context)
                getSearchItems(context).map { searchItem ->
                    SpaSearchIndexableItem(
                        searchLandingKey = spaPageLandingKey(name, searchItem.highlightItemKey),
                        pageTitle = pageTitle,
                        itemTitle = searchItem.itemTitle,
                        keywords = searchItem.keywords,
                    )
                }
            }

        private fun spaPageLandingKey(name: String, highlightItemKey: String) =
            SpaSearchLandingKey.newBuilder()
                .setSpaPage(
                    SpaSearchLandingSpaPage.newBuilder()
                        .setDestination(name)
                        .setHighlightItemKey(highlightItemKey)
                )
                .build()
    }
}
