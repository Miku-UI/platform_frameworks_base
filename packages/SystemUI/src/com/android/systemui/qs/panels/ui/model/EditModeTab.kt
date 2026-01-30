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

package com.android.systemui.qs.panels.ui.model

import androidx.annotation.StringRes
import androidx.compose.runtime.Stable
import androidx.compose.ui.graphics.vector.ImageVector
import com.android.systemui.common.ui.icons.Edit
import com.android.systemui.common.ui.icons.TileMedium
import com.android.systemui.res.R

/** Represents a tab from Quick Settings edit mode. */
@Stable
sealed interface EditModeTab {
    /** Icon to use next to the title in the tabs group. */
    val titleIcon: ImageVector

    /** Title to display in the tabs group. */
    @get:StringRes val titleResId: Int

    /** Text to display on top of the edit page. */
    @get:StringRes val headerResId: Int

    /** Whether tiles can be removed and added. */
    val isTilesEditingAllowed: Boolean

    /** Whether tiles can be resized and reordered. */
    val isTilesLayoutAllowed: Boolean

    /** A tab where tiles can be removed/added only. */
    data object EditingTab : EditModeTab {
        override val titleIcon: ImageVector = Edit
        override val titleResId: Int = R.string.qs_edit_edit_tab
        override val headerResId: Int = R.string.tap_to_remove_tiles
        override val isTilesEditingAllowed: Boolean = true
        override val isTilesLayoutAllowed: Boolean = false
    }

    /** A tab where tiles can be resized/reordered only. */
    data object LayoutTab : EditModeTab {
        override val titleIcon: ImageVector = TileMedium
        override val titleResId: Int = R.string.qs_edit_layout_tab
        override val headerResId: Int = R.string.resize_and_reorder_tiles
        override val isTilesEditingAllowed: Boolean = false
        override val isTilesLayoutAllowed: Boolean = true
    }
}
