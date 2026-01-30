/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.systemui.qs.ui.viewmodel

import androidx.compose.runtime.getValue
import androidx.lifecycle.LifecycleOwner
import com.android.app.tracing.coroutines.launchTraced as launch
import com.android.compose.animation.scene.content.state.TransitionState
import com.android.systemui.Flags
import com.android.systemui.dagger.qualifiers.Main
import com.android.systemui.keyguard.ui.transitions.BlurConfig
import com.android.systemui.lifecycle.ExclusiveActivatable
import com.android.systemui.lifecycle.Hydrator
import com.android.systemui.qs.FooterActionsController
import com.android.systemui.qs.footer.ui.viewmodel.FooterActionsViewModel
import com.android.systemui.scene.domain.interactor.SceneInteractor
import com.android.systemui.scene.shared.model.Overlays
import com.android.systemui.scene.shared.model.SceneFamilies
import com.android.systemui.scene.shared.model.Scenes
import com.android.systemui.shade.domain.interactor.ShadeModeInteractor
import com.android.systemui.shade.shared.model.ShadeMode
import com.android.systemui.shade.ui.viewmodel.ShadeHeaderViewModel
import com.android.systemui.window.domain.interactor.WindowRootViewBlurInteractor
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext

/**
 * Models UI state needed for rendering the content of the quick settings scene.
 *
 * Different from [QuickSettingsUserActionsViewModel] that models the UI state needed to figure out
 * which user actions can trigger navigation to other scenes.
 */
class QuickSettingsSceneContentViewModel
@AssistedInject
constructor(
    val shadeHeaderViewModelFactory: ShadeHeaderViewModel.Factory,
    qsContainerViewModelFactory: QuickSettingsContainerViewModel.Factory,
    private val footerActionsViewModelFactory: FooterActionsViewModel.Factory,
    private val footerActionsController: FooterActionsController,
    private val shadeModeInteractor: ShadeModeInteractor,
    private val sceneInteractor: SceneInteractor,
    @Main private val mainDispatcher: CoroutineDispatcher,
    windowRootViewBlurInteractor: WindowRootViewBlurInteractor,
    private val blurConfig: BlurConfig,
) : ExclusiveActivatable() {
    val qsContainerViewModel =
        qsContainerViewModelFactory.create(supportsBrightnessMirroring = true)

    private val hydrator = Hydrator("QuickSettingsSceneContentViewModel.hydrator")

    /**
     * Whether the shade container transparency effect should be enabled (`true`), or whether to
     * render a fully-opaque shade container (`false`).
     */
    val isTransparencyEnabled: Boolean by
        hydrator.hydratedStateOf(
            traceName = "isTransparencyEnabled",
            source =
                if (Flags.notificationShadeBlur()) {
                    windowRootViewBlurInteractor.isBlurCurrentlySupported
                } else {
                    MutableStateFlow(false)
                },
        )

    private val footerActionsControllerInitialized = AtomicBoolean(false)

    /**
     * Calculates the blur radius to apply to the scene UI.
     *
     * @param transitionState The current transition state of the scene (from its `ContentScope`)
     * @return The blur radius to apply to the scene UI, in pixels.
     */
    fun calculateBlur(transitionState: TransitionState): Float {
        return when {
            !isTransparencyEnabled -> 0f
            Scenes.QuickSettings != transitionState.currentScene -> 0f
            Overlays.Bouncer in transitionState.currentOverlays -> blurConfig.maxBlurRadiusPx
            else -> 0f
        }
    }

    fun getFooterActionsViewModel(lifecycleOwner: LifecycleOwner): FooterActionsViewModel {
        if (footerActionsControllerInitialized.compareAndSet(false, true)) {
            footerActionsController.init()
        }
        return footerActionsViewModelFactory.create(lifecycleOwner)
    }

    override suspend fun onActivated(): Nothing {
        coroutineScope {
            launch { hydrator.activate() }

            launch { qsContainerViewModel.activate() }

            awaitCancellation()
        }
    }

    /**
     * Monitors changes to the shade mode that would make this scene stale, and snaps to the
     * appropriate scene/overlay instead.
     *
     * This function must only run while the scene is shown. Therefore, it shouldn't be part of
     * [onActivated()] while this scene uses `alwaysCompose`.
     */
    suspend fun detectShadeModeChanges(): Nothing {
        shadeModeInteractor.shadeMode.collect { shadeMode ->
            withContext(mainDispatcher) {
                val loggingReason = "Unfold while on Quick Settings"
                when (shadeMode) {
                    is ShadeMode.Split -> sceneInteractor.snapToScene(Scenes.Shade, loggingReason)
                    is ShadeMode.Dual -> {
                        sceneInteractor.snapToScene(SceneFamilies.Home, loggingReason)
                        sceneInteractor.instantlyShowOverlay(
                            Overlays.QuickSettingsShade,
                            loggingReason,
                        )
                    }

                    else -> Unit
                }
            }
        }
    }

    @AssistedFactory
    interface Factory {
        fun create(): QuickSettingsSceneContentViewModel
    }
}
