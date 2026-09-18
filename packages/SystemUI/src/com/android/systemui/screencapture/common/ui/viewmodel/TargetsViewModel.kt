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

package com.android.systemui.screencapture.common.ui.viewmodel

import androidx.compose.runtime.State
import com.android.systemui.lifecycle.Activatable
import com.android.systemui.screencapture.common.domain.model.TargetModel

/** Interface for view models that provide capture targets. */
interface TargetsViewModel : Activatable, DrawableLoaderViewModel, AudioSwitchViewModel {
    /** The currently available targets. */
    val targets: State<List<TargetModel>?>
    /** The view model of the currently selected target. */
    val selectedTarget: State<TargetViewModel?>

    /**
     * Sets the view model for the currently selected target.
     *
     * This should only be called with [TargetViewModel]s created using this instance's
     * [createViewModelFor] method.
     */
    fun setSelectedTarget(target: TargetViewModel?)

    /**
     * Creates a view model for the given [target]. The caller assumes ownership of the returned
     * [TargetViewModel] and needs to manage its activation.
     *
     * This should only be called with elements retrieved from this instance's [targets].
     */
    fun createViewModelFor(target: TargetModel): TargetViewModel
}
