/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.systemui.qs.tiles.impl.modes.domain.model

import com.android.settingslib.notification.modes.ZenMode
import com.android.systemui.common.shared.model.Icon

data class ModesTileModel(
    val isActivated: Boolean,
    val activeModes: List<ActiveMode>,

    /**
     * Icon to be shown in the tile. Will be the icon of the highest-priority active mode, if any
     * modes are active, or the icon of the [quickMode] if no modes are active.
     *
     * `icon.res` will only be present if it is known to correspond to a resource with a known id in
     * SystemUI (such as resources from `android.R`, `com.android.internal.R`, or
     * `com.android.systemui.res` itself).
     */
    val icon: Icon.Loaded,

    /**
     * The [ZenMode] that should be activated if no modes are active and the user taps on the
     * secondary target of the tile.
     */
    // TODO: b/405988332 - When inlining modes_ui_tile_reactivates_last, this should be made
    //  non-nullable; right now it's nullable so that the unflagged path isn't forced to set it.
    val quickMode: ZenMode?,
) {
    // TODO: b/405988332 - When inlining modes_ui_tile_reactivates_last, `id` should be made
    //  non-nullable; right now it's nullable so that the unflagged path isn't forced to set it.
    data class ActiveMode(val id: String?, val name: String)
}
