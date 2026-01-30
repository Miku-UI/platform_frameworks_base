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
import android.provider.SearchIndexableResource
import com.android.settingslib.search.Indexable
import com.android.settingslib.search.SearchIndexableData
import com.android.settingslib.search.SearchIndexableRaw

/**
 * A converter which could provide search data through
 * [com.android.settingslib.search.SearchIndexableResources].
 *
 * @param intentAction The Intent action for the search landing activity.
 * @param intentTargetClass The Intent target class for the search landing activity.
 */
class SearchIndexableDataConverter(
    private val intentAction: String,
    private val intentTargetClass: String,
) {
    fun toSearchIndexableData(searchIndexablePage: SpaSearchIndexablePage): SearchIndexableData {
        val targetClass = searchIndexablePage.targetClass
        val searchIndexProvider =
            object : Indexable.SearchIndexProvider {
                override fun getXmlResourcesToIndex(
                    context: Context,
                    enabled: Boolean,
                ): List<SearchIndexableResource> = emptyList()

                override fun getRawDataToIndex(
                    context: Context,
                    enabled: Boolean,
                ): List<SearchIndexableRaw> = emptyList()

                override fun getDynamicRawDataToIndex(
                    context: Context,
                    enabled: Boolean,
                ): List<SearchIndexableRaw> =
                    searchIndexablePage.itemsProvider(context).map { item ->
                        item.toSearchIndexableRaw(context, targetClass)
                    }

                override fun getNonIndexableKeys(context: Context): List<String> = emptyList()
            }
        return SearchIndexableData(targetClass, searchIndexProvider)
    }

    private fun SpaSearchIndexableItem.toSearchIndexableRaw(
        context: Context,
        targetClass: Class<*>,
    ): SearchIndexableRaw =
        SearchIndexableRaw(context).apply {
            key = searchLandingKey.encodeToString()
            title = itemTitle
            keywords = this@toSearchIndexableRaw.keywords
            screenTitle = pageTitle
            intentAction = this@SearchIndexableDataConverter.intentAction
            intentTargetClass = this@SearchIndexableDataConverter.intentTargetClass
            packageName = context.packageName
            className = targetClass.name
        }
}
