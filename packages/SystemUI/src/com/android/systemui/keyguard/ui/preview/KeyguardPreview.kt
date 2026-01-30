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
 *
 */

package com.android.systemui.keyguard.ui.preview

import android.os.Bundle
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.keyguard.data.repository.KeyguardPreviewRepository
import com.android.systemui.keyguard.data.repository.KeyguardPreviewRepositoryFactory
import com.android.systemui.keyguard.domain.interactor.KeyguardPreviewInteractor
import com.android.systemui.keyguard.domain.interactor.KeyguardPreviewInteractorFactory
import com.android.systemui.keyguard.ui.viewmodel.KeyguardPreviewClockViewModel
import com.android.systemui.keyguard.ui.viewmodel.KeyguardPreviewClockViewModelFactory
import com.android.systemui.keyguard.ui.viewmodel.KeyguardPreviewSmartspaceViewModel
import com.android.systemui.keyguard.ui.viewmodel.KeyguardPreviewSmartspaceViewModelFactory
import com.android.systemui.keyguard.ui.viewmodel.KeyguardPreviewViewModel
import com.android.systemui.keyguard.ui.viewmodel.KeyguardPreviewViewModelFactory
import javax.inject.Inject

data class KeyguardPreview(
    val repository: KeyguardPreviewRepository,
    val interactor: KeyguardPreviewInteractor,
    val viewModel: KeyguardPreviewViewModel,
    val clockModel: KeyguardPreviewClockViewModel,
    val smartspaceModel: KeyguardPreviewSmartspaceViewModel,
    val renderer: KeyguardPreviewRenderer,
) {
    /**
     * Returns a key that should make the KeyguardPreviewRenderer unique and if two of them have the
     * same key they will be treated as the same KeyguardPreviewRenderer. Primary this is used to
     * prevent memory leaks by allowing removal of the old KeyguardPreviewRenderer.
     */
    val id = Pair(repository.hostToken, repository.displayId)
}

@SysUISingleton
class KeyguardPreviewFactory
@Inject
constructor(
    private val repositoryFactory: KeyguardPreviewRepositoryFactory,
    private val interactorFactory: KeyguardPreviewInteractorFactory,
    private val viewModelFactory: KeyguardPreviewViewModelFactory,
    private val clockModelFactory: KeyguardPreviewClockViewModelFactory,
    private val smartspaceModelFactory: KeyguardPreviewSmartspaceViewModelFactory,
    private val rendererFactory: KeyguardPreviewRendererFactory,
) {
    fun create(request: Bundle): KeyguardPreview {
        val repository = repositoryFactory.create(request)
        val interactor = interactorFactory.create(repository)
        val viewModel = viewModelFactory.create(interactor)
        val clockModel = clockModelFactory.create(interactor)
        val smartspaceModel = smartspaceModelFactory.create(interactor, clockModel)
        val renderer = rendererFactory.create(viewModel, clockModel, smartspaceModel)
        return KeyguardPreview(
            repository,
            interactor,
            viewModel,
            clockModel,
            smartspaceModel,
            renderer,
        )
    }
}
