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

package com.android.systemui.qs.panels.ui.viewmodel

import android.content.Context
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString
import com.android.systemui.common.shared.model.Icon
import com.android.systemui.common.shared.model.Text
import com.android.systemui.common.ui.compose.toAnnotatedString
import com.android.systemui.qs.panels.ui.viewmodel.EditTileViewModelConstants.APP_ICON_INLINE_CONTENT_ID
import com.android.systemui.qs.pipeline.shared.TileSpec
import com.android.systemui.qs.shared.model.CategoryAndName
import com.android.systemui.qs.shared.model.TileCategory

/**
 * View model for each tile that is available to be added/removed/moved in Edit mode.
 *
 * [isCurrent] indicates whether this tile is part of the current set of tiles that the user sees in
 * Quick Settings.
 */
data class UnloadedEditTileViewModel(
    val tileSpec: TileSpec,
    val icon: Icon,
    val label: Text,
    val appName: Text?,
    val appIcon: Icon?,
    val isCurrent: Boolean,
    val isDualTarget: Boolean,
    val availableEditActions: Set<AvailableEditActions>,
    val category: TileCategory,
) {
    fun load(context: Context): EditTileViewModel {
        val loadedLabel = label.toAnnotatedString(context) ?: AnnotatedString(tileSpec.spec)
        val inlinedLabel =
            if (appIcon != null) {
                buildAnnotatedString {
                    appendInlineContent(APP_ICON_INLINE_CONTENT_ID)
                    append(' ')
                    append(loadedLabel)
                }
            } else {
                null
            }
        return EditTileViewModel(
            tileSpec = tileSpec,
            icon = icon,
            label = loadedLabel,
            inlinedLabel = inlinedLabel,
            appName = appName?.toAnnotatedString(context),
            appIcon = appIcon,
            isCurrent = isCurrent,
            isDualTarget = isDualTarget,
            availableEditActions = availableEditActions,
            category = category,
        )
    }
}

/**
 * Viewmodel for a loaded tile within Quick Settings edit mode.
 *
 * This represents a tile that has been loaded with localized resources and is ready to be displayed
 * in the UI.
 *
 * @property tileSpec The [TileSpec] for this tile.
 * @property icon The icon for the tile.
 * @property label The main label for the tile.
 * @property inlinedLabel An optional [AnnotatedString] with an inlined app icon.
 * @property appName The name of the associated app, if applicable.
 * @property appIcon The icon of the associated app, if applicable.
 * @property isCurrent True if the tile is in the user's active set of tiles, false otherwise.
 * @property isDualTarget True if the tile supports dual-target clicks, false otherwise.
 * @property availableEditActions A set of [AvailableEditActions] that can be performed on this tile
 *   in edit mode.
 * @property category The [TileCategory] the tile belongs to.
 */
@Immutable
data class EditTileViewModel(
    val tileSpec: TileSpec,
    val icon: Icon,
    val label: AnnotatedString,
    val inlinedLabel: AnnotatedString?,
    val appName: AnnotatedString?,
    val appIcon: Icon?,
    val isCurrent: Boolean,
    val isDualTarget: Boolean,
    val availableEditActions: Set<AvailableEditActions>,
    override val category: TileCategory,
) : CategoryAndName {
    override val name
        get() = label.text

    val isRemovable
        get() = availableEditActions.contains(AvailableEditActions.REMOVE)
}

enum class AvailableEditActions {
    ADD,
    REMOVE,
    MOVE,
}

object EditTileViewModelConstants {
    const val APP_ICON_INLINE_CONTENT_ID = "appIcon"
}
