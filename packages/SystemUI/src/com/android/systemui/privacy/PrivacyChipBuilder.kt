/*
 * Copyright (C) 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package com.android.systemui.privacy

import android.content.Context

class PrivacyChipBuilder(private val context: Context, itemsList: List<PrivacyItem>) {

    private val contentDescriptionGenerator = PrivacyChipContentDescriptionGenerator(context)

    val appsAndTypes: List<Pair<PrivacyApplication, List<PrivacyType>>>
    val types: List<PrivacyType>

    init {
        appsAndTypes =
            itemsList
                .groupBy({ it.application }, { it.privacyType })
                .toList()
                .sortedWith(
                    compareBy(
                        { -it.second.size }, // Sort by number of AppOps
                        { it.second.minOrNull() },
                    )
                ) // Sort by "smallest" AppOpp (Location is largest)
        types = itemsList.map { it.privacyType }.distinct().sorted()
    }

    fun generateIcons() = types.map { it.getIcon(context) }

    fun joinTypes(): String {
        return contentDescriptionGenerator.joinTypesForContentDescription(types)
    }
}
