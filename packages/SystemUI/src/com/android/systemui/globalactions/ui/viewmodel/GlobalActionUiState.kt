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

package com.android.systemui.globalactions.ui.viewmodel

import androidx.annotation.StringRes
import com.android.systemui.common.shared.model.Icon
import com.android.systemui.globalactions.shared.model.GlobalActionType

/** State for a single global action item. */
sealed interface GlobalActionUiState {
    /**
     * Identifier for this action. Used for uniqueness in UI lists and identifying the specific
     * action type.
     */
    val key: GlobalActionType

    /** The action is unavailable and should not be displayed. */
    data class Hidden(override val key: GlobalActionType) : GlobalActionUiState

    /** The action is available for user interaction. */
    data class Visible(
        override val key: GlobalActionType,
        val icon: Icon,
        @param:StringRes val textResId: Int,
        val onClick: () -> Unit,
        val onLongClick: (() -> Unit)? = null,
    ) : GlobalActionUiState
}
