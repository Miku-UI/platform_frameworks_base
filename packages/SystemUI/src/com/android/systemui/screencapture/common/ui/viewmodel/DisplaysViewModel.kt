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
import androidx.compose.runtime.mutableStateOf
import com.android.systemui.lifecycle.HydratedActivatable
import com.android.systemui.screencapture.common.domain.interactor.ScreenCaptureDisplayInteractor
import com.android.systemui.screencapture.common.domain.model.ScreenCaptureDisplay
import com.android.systemui.screencapture.common.domain.model.TargetModel
import javax.inject.Inject

/**
 * Interface for view models concerned with displays.
 *
 * Example usage in a [HydratedActivatable]:
 * ```
 * class FooViewModel(
 *     viewModelProvider: Provider<DisplaysViewModel>,
 * ) : HydratedActivatable() {
 *
 *     private val viewModel = viewModelProvider.get()
 *
 *     override suspend fun onActivated() {
 *         coroutineScope {
 *             launchTraced("FooTraceName") { viewModel.activate() }
 *         }
 *     }
 * }
 * ```
 *
 * Example usage in a [Composable][androidx.compose.runtime.Composable]
 *
 * ```
 * @Composable
 * fun Foo(viewModelProvider: Provider<DisplaysViewModel>) {
 *     val viewModel = rememberViewModel("FooTraceName") { viewModelProvider.get() }
 * }
 * ```
 */
interface DisplaysViewModel : TargetsViewModel {
    override val targets: State<List<ScreenCaptureDisplay>?>
    override val selectedTarget: State<DisplayViewModel?>

    override fun createViewModelFor(target: TargetModel): DisplayViewModel
}

/** The default implementation of [DisplaysViewModel]. */
class DisplaysViewModelImpl
@Inject
constructor(
    interactor: ScreenCaptureDisplayInteractor,
    private val displayViewModelFactory: DisplayViewModel.Factory,
    drawableLoaderViewModel: DrawableLoaderViewModel,
    audioSwitchViewModel: AudioSwitchViewModel,
) :
    DisplaysViewModel,
    DrawableLoaderViewModel by drawableLoaderViewModel,
    AudioSwitchViewModel by audioSwitchViewModel,
    HydratedActivatable() {

    override val targets = interactor.displays.hydratedStateOf("DisplaysViewModel#displays", null)

    private val _selectedTarget = mutableStateOf<DisplayViewModel?>(null)
    override val selectedTarget: State<DisplayViewModel?> = _selectedTarget

    override fun setSelectedTarget(target: TargetViewModel?) {
        _selectedTarget.value = target as DisplayViewModel?
    }

    override fun createViewModelFor(target: TargetModel): DisplayViewModel =
        displayViewModelFactory.create(target as ScreenCaptureDisplay)
}
