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

package com.android.systemui.ambient.statusbar.ui

import android.graphics.RectF
import com.android.systemui.lifecycle.ExclusiveActivatable
import com.android.systemui.lifecycle.Hydrator
import com.android.systemui.statusbar.chips.ui.model.MultipleOngoingActivityChipsModel
import com.android.systemui.statusbar.chips.ui.viewmodel.OngoingActivityChipsViewModel
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.StateFlow

/** View model for the dream status bar. */
class AmbientStatusBarViewModel
@AssistedInject
constructor(@Assisted private val ongoingActivityChipsViewModel: OngoingActivityChipsViewModel) :
    ExclusiveActivatable() {

    private val hydrator = Hydrator("AmbientStatusBarViewModel.hydrator")

    /** A flow of all the ongoing activity chips including active, overflow, and inactive chips. */
    val ongoingActivityChips: StateFlow<MultipleOngoingActivityChipsModel> =
        ongoingActivityChipsViewModel.chips

    /** Invoked each time a chip's on-screen bounds have changed. */
    fun onChipBoundsChanged(key: String, bounds: RectF) {
        ongoingActivityChipsViewModel.onChipBoundsChanged(key, bounds)
    }

    override suspend fun onActivated(): Nothing {
        hydrator.activate()
    }

    @AssistedFactory
    interface Factory {
        fun create(
            ongoingActivityChipsViewModel: OngoingActivityChipsViewModel
        ): AmbientStatusBarViewModel
    }
}
