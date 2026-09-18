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

package com.android.systemui.accessibility.accessibilitymenu.search

import android.database.Cursor
import android.database.MatrixCursor
import android.provider.SearchIndexablesContract
import com.android.settingslib.metadata.PreferenceSearchIndexablesProvider
import com.android.systemui.accessibility.accessibilitymenu.Flags

/** Provides search indexables for the preferences in Accessibility Menu. */
class SearchIndexablesProvider : PreferenceSearchIndexablesProvider() {
    override val isCatalystSearchEnabled: Boolean
        get() = Flags.catalystA11yMenu()

    override fun queryXmlResources(projection: Array<out String?>?): Cursor? {
        // Just return empty as queryRawData ignores conditional available preferences recursively
        return MatrixCursor(SearchIndexablesContract.INDEXABLES_XML_RES_COLUMNS)
    }

    override fun onCreate(): Boolean {
        return true
    }
}
