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

package com.android.systemui.statusbar.notification.row.data.repository

import android.service.notification.Adjustment
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.android.compose.animation.scene.MutableSceneTransitionLayoutState
import com.android.systemui.statusbar.notification.row.data.model.AppData
import kotlinx.coroutines.flow.MutableStateFlow

/** Holds information about a BundleEntry that is relevant to UI. */
class BundleRepository(
    @StringRes val titleText: Int,
    @DrawableRes val bundleIcon: Int,
    @StringRes val summaryText: Int,
    @Adjustment.Types val bundleType: Int,
) {

    var numberOfChildren by mutableStateOf<Int?>(0)

    /**
     * Cleared on bundle expand; does not update while expanded; updated while bundle is closed.
     * Guaranteed only one AppData per app, with timeAddedToBundle being the latest time that a
     * notification from this app was added to this bundle.
     */
    var appDataList = MutableStateFlow<List<AppData>>(emptyList())

    var state by mutableStateOf<MutableSceneTransitionLayoutState?>(null)

    /**
     * Uptime millis when this specific bundle was last collapsed by the user. 0 if never. Used to
     * filter for app icons of notifications that arrived since the bundle was last collapsed by
     * user. Use last collapsed time instead of last expansion time because notifications arrived
     * while the bundle was open are implicitly seen.
     */
    var lastCollapseTime by mutableStateOf(0L)
}
