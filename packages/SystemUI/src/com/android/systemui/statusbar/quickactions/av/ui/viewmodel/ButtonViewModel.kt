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

package com.android.systemui.statusbar.quickactions.av.ui.viewmodel

interface ButtonViewModel {
    val state: ButtonUiState

    suspend fun onClick()
}

data class ButtonUiState(
    /** Is the button toggled */
    val isEnabled: Boolean = false,
    /** To be shown on first line within the button */
    val mainTitle: Int? = null,
    /** To be shown on second line within the button */
    val subTitle: Int? = null,
    /** Argument for the subTitle */
    val subTitleArg: Int? = null,
    /** To be displayed under the button */
    val subText: Int? = null,
    /** Icon to be shown within the button surface */
    val image: Int? = null,
)
