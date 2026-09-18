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

import android.media.projection.IAppContentProjectionCallback
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import com.android.systemui.lifecycle.HydratedActivatable
import com.android.systemui.screencapture.common.domain.interactor.ScreenCaptureAppContentInteractor
import com.android.systemui.screencapture.common.domain.model.ScreenCaptureAppContent
import com.android.systemui.screencapture.common.domain.model.TargetModel
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import java.lang.ref.WeakReference

/**
 * Interface for view models concerned with app contents.
 *
 * Example usage in a [HydratedActivatable]:
 * ```
 * class FooViewModel(
 *     viewModelFactory: AppContentsViewModel.Factory,
 * ) : HydratedActivatable() {
 *
 *     private val viewModel = viewModelFactory.create(200, 100, 24)
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
 * fun Foo(viewModelFactory: AppContentsViewModel.Factory) {
 *     val viewModel = rememberViewModel("FooTraceName") {  viewModelFactory.create(200, 100, 24) }
 * }
 * ```
 */
interface AppContentsViewModel : TargetsViewModel {

    override val targets: State<List<ScreenCaptureAppContent>?>
    override val selectedTarget: State<AppContentViewModel?>

    /**
     * The projection callback for the currently selected app content target. This is a
     * [WeakReference] to avoid leaking the Binder.
     */
    val projectionCallback: State<WeakReference<IAppContentProjectionCallback>?>

    override fun createViewModelFor(target: TargetModel): AppContentViewModel

    interface Factory {
        fun create(
            hostPackageName: String,
            thumbnailWidthPx: Int,
            thumbnailHeightPx: Int,
            iconSizePx: Int,
        ): AppContentsViewModel
    }
}

class AppContentsViewModelImpl
@AssistedInject
constructor(
    appContentInteractor: ScreenCaptureAppContentInteractor,
    private val appContentViewModelFactory: AppContentViewModel.Factory,
    drawableLoaderViewModel: DrawableLoaderViewModel,
    audioSwitchViewModel: AudioSwitchViewModel,
    @Assisted("hostPackageName") private val hostPackageName: String,
    @Assisted("thumbnailWidthPx") private val thumbnailWidthPx: Int,
    @Assisted("thumbnailHeightPx") private val thumbnailHeightPx: Int,
    @Assisted("iconSizePx") private val iconSizePx: Int,
) :
    AppContentsViewModel,
    DrawableLoaderViewModel by drawableLoaderViewModel,
    AudioSwitchViewModel by audioSwitchViewModel,
    HydratedActivatable() {

    /** The app content result from the interactor for the host package. */
    private val appContent =
        appContentInteractor
            .appContentsFor(
                packageName = hostPackageName,
                thumbnailWidthPx = thumbnailWidthPx,
                thumbnailHeightPx = thumbnailHeightPx,
                iconSizePx = iconSizePx,
            )
            .hydratedStateOf("AppContentsViewModel#getAppContents", null)

    override val projectionCallback: State<WeakReference<IAppContentProjectionCallback>?> =
        derivedStateOf {
            selectedTarget.value?.let { appContent.value?.getOrNull()?.projectionCallback }
        }

    override val targets: State<List<ScreenCaptureAppContent>?> = derivedStateOf {
        appContent.value?.getOrNull()?.contents
    }

    private val _selectedTarget = mutableStateOf<AppContentViewModel?>(null)
    override val selectedTarget: State<AppContentViewModel?> = _selectedTarget

    override fun setSelectedTarget(target: TargetViewModel?) {
        _selectedTarget.value = target as AppContentViewModel?
    }

    override fun createViewModelFor(target: TargetModel): AppContentViewModel =
        appContentViewModelFactory.create(target as ScreenCaptureAppContent)

    @AssistedFactory
    interface Factory : AppContentsViewModel.Factory {
        override fun create(
            @Assisted("hostPackageName") hostPackageName: String,
            @Assisted("thumbnailWidthPx") thumbnailWidthPx: Int,
            @Assisted("thumbnailHeightPx") thumbnailHeightPx: Int,
            @Assisted("iconSizePx") iconSizePx: Int,
        ): AppContentsViewModelImpl
    }
}
