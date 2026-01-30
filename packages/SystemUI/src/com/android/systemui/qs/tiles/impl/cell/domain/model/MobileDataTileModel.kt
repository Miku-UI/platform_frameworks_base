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

package com.android.systemui.qs.tiles.impl.cell.domain.model

import com.android.systemui.common.shared.model.Icon

/** A data-only model representing the icon for the Mobile Data tile. */
sealed interface MobileDataTileIcon {
    data class SignalIcon(val level: Int) : MobileDataTileIcon

    data class ResourceIcon(val resourceIcon: Icon.Resource) : MobileDataTileIcon
}

/**
 * Model for the Mobile Data QS tile.
 *
 * @param isSimActive True if a data sim is active.
 * @param isEnabled True if mobile data is enabled.
 * @param icon The icon to display for the tile.
 */
data class MobileDataTileModel(
    val isSimActive: Boolean,
    val isEnabled: Boolean,
    val icon: MobileDataTileIcon,
)
