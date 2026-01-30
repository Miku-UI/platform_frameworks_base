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

package com.android.systemui.qs.tiles.impl.wifi.domain.model

import com.android.systemui.statusbar.connectivity.WifiIcons
import com.android.systemui.statusbar.pipeline.wifi.ui.model.WifiTileIconModel

/** Model describing the state that the QS Wifi Toggle Internet tile should be in. */
sealed interface WifiTileModel {
    val secondaryLabel: CharSequence?
    val icon: WifiTileIconModel

    data class Active(
        override val icon: WifiTileIconModel = WifiTileIconModel(WifiIcons.WIFI_NO_NETWORK),
        override val secondaryLabel: CharSequence? = null,
    ) : WifiTileModel

    data class Inactive(
        override val icon: WifiTileIconModel = WifiTileIconModel(WifiIcons.WIFI_NO_NETWORK),
        override val secondaryLabel: CharSequence? = null,
    ) : WifiTileModel
}
