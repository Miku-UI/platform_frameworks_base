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
import android.provider.SearchIndexablesContract.RawData
import androidx.core.net.toUri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.settingslib.spa.search.test.TestSpaSearchIndexablesProvider.Companion.INTENT_ACTION
import com.android.settingslib.spa.search.test.TestSpaSearchIndexablesProvider.Companion.ITEM_TITLE
import com.android.settingslib.spa.search.test.TestSpaSearchIndexablesProvider.Companion.KEYWORDS
import com.android.settingslib.spa.search.test.TestSpaSearchIndexablesProvider.Companion.PAGE_TITLE
import com.android.settingslib.spa.search.test.TestSpaSearchIndexablesProvider.Companion.SearchLandingKey
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SpaSearchIndexablesProviderTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private val uri =
        "content://com.android.settingslib.spa.search.test.provider/settings/dynamic_indexables_raw"
            .toUri()

    @Test
    fun queryDynamicRawData() {
        context.contentResolver.query(uri, null, null, null)!!.use { cursor ->
            cursor.moveToFirst()
            val key = cursor.getString(cursor.getColumnIndex(RawData.COLUMN_KEY))
            assertThat(decodeToSpaSearchLandingKey(key)).isEqualTo(SearchLandingKey)
            assertThat(cursor.getString(cursor.getColumnIndex(RawData.COLUMN_TITLE)))
                .isEqualTo(ITEM_TITLE)
            assertThat(cursor.getString(cursor.getColumnIndex(RawData.COLUMN_KEYWORDS)))
                .isEqualTo(KEYWORDS)
            assertThat(cursor.getString(cursor.getColumnIndex(RawData.COLUMN_SCREEN_TITLE)))
                .isEqualTo(PAGE_TITLE)
            assertThat(cursor.getString(cursor.getColumnIndex(RawData.COLUMN_INTENT_ACTION)))
                .isEqualTo(INTENT_ACTION)
        }
    }
}
