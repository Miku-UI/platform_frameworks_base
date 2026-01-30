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

import android.database.Cursor
import android.database.MatrixCursor
import android.provider.SearchIndexablesContract
import android.provider.SearchIndexablesContract.RawData
import android.provider.SearchIndexablesProvider

/** A abstract class which could provide search data through [SearchIndexablesProvider]. */
abstract class SpaSearchIndexablesProvider : SearchIndexablesProvider() {
    override fun onCreate() = true

    override fun queryXmlResources(projection: Array<out String?>?) =
        MatrixCursor(SearchIndexablesContract.INDEXABLES_XML_RES_COLUMNS)

    override fun queryRawData(projection: Array<out String?>?) =
        MatrixCursor(SearchIndexablesContract.INDEXABLES_RAW_COLUMNS)

    override fun queryNonIndexableKeys(projection: Array<out String?>?) =
        MatrixCursor(SearchIndexablesContract.NON_INDEXABLES_KEYS_COLUMNS)

    override fun queryDynamicRawData(projection: Array<out String?>?): Cursor {
        val context = requireContext()
        val cursor = MatrixCursor(SearchIndexablesContract.INDEXABLES_RAW_COLUMNS)
        for (searchIndexablePage in getSpaSearchIndexablePageList()) {
            for (item in searchIndexablePage.itemsProvider(context)) {
                cursor
                    .newRow()
                    .add(RawData.COLUMN_KEY, item.searchLandingKey.encodeToString())
                    .add(RawData.COLUMN_TITLE, item.itemTitle)
                    .add(RawData.COLUMN_KEYWORDS, item.keywords)
                    .add(RawData.COLUMN_SCREEN_TITLE, item.pageTitle)
                    .add(RawData.COLUMN_INTENT_ACTION, intentAction)
            }
        }
        return cursor
    }

    /** The Intent action for the search landing activity. */
    abstract val intentAction: String

    /** The search indexable page data. */
    open fun getSpaSearchIndexablePageList(): List<SpaSearchIndexablePage> =
        SpaSearchRepository().getSearchIndexablePageList()
}
