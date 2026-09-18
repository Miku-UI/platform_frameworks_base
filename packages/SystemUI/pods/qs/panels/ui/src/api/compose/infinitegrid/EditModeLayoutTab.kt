/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.systemui.qs.panels.ui.compose.infinitegrid

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.android.systemui.qs.panels.ui.viewmodel.EditModeLayoutTabViewModel

/** Interface for the Layout tab composer. */
public interface EditModeLayoutTab {
    /** Renders the edit mode Layout tab UI. */
    @Composable
    public fun Content(
        viewmodel: EditModeLayoutTabViewModel,
        brightness: @Composable () -> Unit,
        tilesGrid: @Composable () -> Unit,
        media: @Composable () -> Unit,
        modifier: Modifier,
    )

    /**
     * Renders the drag shadow while a component is dragged on the layout tab.
     *
     * Ideally this would be included directly in [Content], but offering it separately allows to
     * display the drag shadow outside of clipping composables such as Scaffold/Surface. Otherwise,
     * the shadow is automatically clipped by the parent's bounds.
     *
     * For the offset to be accurate, this needs to share the same root composable as [Content]
     */
    @Composable
    public fun DragShadow(
        viewmodel: EditModeLayoutTabViewModel,
        brightness: @Composable () -> Unit,
        tilesGrid: @Composable () -> Unit,
        media: @Composable () -> Unit,
        modifier: Modifier,
    )
}
