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

package com.android.systemui.qs.panels.ui.viewmodel

import com.android.systemui.lifecycle.ExclusiveActivatable
import com.android.systemui.media.controls.ui.controller.MediaHierarchyManager.Companion.LOCATION_QQS
import com.android.systemui.media.controls.ui.controller.MediaHierarchyManager.Companion.LOCATION_QS
import com.android.systemui.qs.panels.domain.interactor.InFirstPageInteractor
import com.android.systemui.qs.ui.viewmodel.QuickSettingsContainerViewModel
import com.android.systemui.shade.ui.viewmodel.ShadeSceneContentViewModel
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * View model to determine if the tiles should animate (move) when going from QQS to QS. If
 * [animateQsTiles] is `false`, the tiles should probably be faded instead.
 *
 * Only use this with scene container.
 */
class AnimateQsTilesViewModel
@AssistedInject
constructor(
    private val inFirstPageInteractor: InFirstPageInteractor,
    mediaInRowInLandscapeViewModelFactory: MediaInRowInLandscapeViewModel.Factory,
) : ExclusiveActivatable() {
    val animateQsTiles
        get() = inFirstPageInteractor.inFirstPage && !mediaAppearsSuddenly

    private val qqsMediaInRowViewModel =
        mediaInRowInLandscapeViewModelFactory.create(
            LOCATION_QQS,
            ShadeSceneContentViewModel.qqsMediaUiBehavior,
        )

    private val qsMediaInRowViewModel =
        mediaInRowInLandscapeViewModelFactory.create(
            LOCATION_QS,
            QuickSettingsContainerViewModel.mediaUiBehavior,
        )

    private val mediaAppearsSuddenly: Boolean
        get() =
            !qqsMediaInRowViewModel.shouldMediaShowInRow &&
                qsMediaInRowViewModel.shouldMediaShowInRow

    override suspend fun onActivated() {
        coroutineScope {
            launch { qsMediaInRowViewModel.activate() }
            launch { qqsMediaInRowViewModel.activate() }
        }
    }

    @AssistedFactory
    interface Factory {
        fun create(): AnimateQsTilesViewModel
    }
}
