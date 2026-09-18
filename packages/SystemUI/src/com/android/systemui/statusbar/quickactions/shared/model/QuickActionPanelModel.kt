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

package com.android.systemui.statusbar.quickactions.shared.model

import android.graphics.RectF
import com.android.systemui.statusbar.quickactions.popups.ui.viewmodel.StatusBarPopupViewModel

/**
 * A Model representing the currently active quick action panel. QuickActionPanels are panels
 * anchored to a specific QuickActionChip in the status bar.
 */
data class QuickActionPanelModel(
    val chipId: QuickActionChipId,
    val anchorBounds: RectF,
    val panelContentViewModelFactory: StatusBarPopupViewModel.Factory,
)
