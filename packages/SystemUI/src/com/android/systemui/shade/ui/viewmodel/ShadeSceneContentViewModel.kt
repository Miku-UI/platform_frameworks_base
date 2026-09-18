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

package com.android.systemui.shade.ui.viewmodel

import androidx.annotation.FloatRange
import androidx.lifecycle.LifecycleOwner
import com.android.app.tracing.coroutines.launchTraced as launch
import com.android.compose.animation.scene.content.state.TransitionState
import com.android.systemui.Flags
import com.android.systemui.dagger.qualifiers.Main
import com.android.systemui.deviceentry.domain.interactor.DeviceEntryInteractor
import com.android.systemui.keyguard.ui.transitions.BlurConfig
import com.android.systemui.lifecycle.HydratedActivatable
import com.android.systemui.media.controls.domain.pipeline.interactor.MediaCarouselInteractor
import com.android.systemui.media.controls.ui.controller.MediaHierarchyManager.Companion.LOCATION_QQS
import com.android.systemui.media.remedia.ui.compose.MediaUiBehavior
import com.android.systemui.media.remedia.ui.viewmodel.MediaCarouselVisibility
import com.android.systemui.media.remedia.ui.viewmodel.MediaViewModel
import com.android.systemui.qs.FooterActionsController
import com.android.systemui.qs.footer.ui.viewmodel.FooterActionsViewModel
import com.android.systemui.qs.panels.domain.interactor.TileSquishinessInteractor
import com.android.systemui.qs.panels.ui.viewmodel.MediaInRowInLandscapeViewModel
import com.android.systemui.qs.panels.ui.viewmodel.QuickQuickSettingsViewModel
import com.android.systemui.qs.ui.viewmodel.QuickSettingsContainerViewModel
import com.android.systemui.scene.domain.interactor.SceneInteractor
import com.android.systemui.scene.shared.model.Overlays
import com.android.systemui.scene.shared.model.SceneFamilies
import com.android.systemui.scene.shared.model.Scenes
import com.android.systemui.shade.domain.interactor.ShadeModeInteractor
import com.android.systemui.shade.domain.interactor.ShadeStatusBarComponentsInteractor
import com.android.systemui.shade.shared.model.ShadeMode
import com.android.systemui.unfold.domain.interactor.UnfoldTransitionInteractor
import com.android.systemui.window.domain.interactor.WindowRootViewBlurInteractor
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Models UI state used to render the content of the shade scene.
 *
 * Different from [ShadeUserActionsViewModel], which only models user actions that can be performed
 * to navigate to other scenes.
 */
class ShadeSceneContentViewModel
@AssistedInject
constructor(
    @Main private val mainDispatcher: CoroutineDispatcher,
    val qsContainerViewModelFactory: QuickSettingsContainerViewModel.Factory,
    val quickQuickSettingsViewModel: QuickQuickSettingsViewModel.Factory,
    val shadeHeaderViewModelFactory: ShadeHeaderViewModel.Factory,
    val mediaCarouselInteractor: MediaCarouselInteractor,
    private val shadeModeInteractor: ShadeModeInteractor,
    val mediaViewModelFactory: MediaViewModel.Factory,
    private val footerActionsViewModelFactory: FooterActionsViewModel.Factory,
    private val footerActionsController: FooterActionsController,
    private val blurConfig: BlurConfig,
    unfoldTransitionInteractor: UnfoldTransitionInteractor,
    deviceEntryInteractor: DeviceEntryInteractor,
    private val sceneInteractor: SceneInteractor,
    private val tileSquishinessInteractor: TileSquishinessInteractor,
    windowRootViewBlurInteractor: WindowRootViewBlurInteractor,
    mediaInRowInLandscapeViewModelFactory: MediaInRowInLandscapeViewModel.Factory,
    shadeStatusBarComponentsInteractor: ShadeStatusBarComponentsInteractor,
) : HydratedActivatable() {

    /**
     * Whether the shade container transparency effect should be enabled (`true`), or whether to
     * render a fully-opaque shade container (`false`).
     */
    val isTransparencyEnabled: Boolean by
        if (Flags.notificationShadeBlur()) {
                windowRootViewBlurInteractor.isBlurCurrentlySupported
            } else {
                MutableStateFlow(false)
            }
            .hydratedStateOf()

    val shadeMode: ShadeMode by shadeModeInteractor.shadeMode.hydratedStateOf()

    val isDeviceEntered: Boolean by deviceEntryInteractor.isDeviceEntered.hydratedStateOf()

    fun isEmptySpaceClickable(transitionState: TransitionState): Boolean {
        val isTransitioningToQs =
            transitionState.isTransitioningBetween(Scenes.Shade, Scenes.QuickSettings)
        return !isDeviceEntered && !isTransitioningToQs
    }

    val showMediaInRow: Boolean
        get() = qqsMediaInRowViewModel.shouldMediaShowInRow

    val showMedia: Boolean by
        // mediaCarouselInteractor.hasAnyMedia if in SplitShade.
        mediaCarouselInteractor.hasActiveMedia.hydratedStateOf()

    val isQsEnabled: Boolean by
        shadeStatusBarComponentsInteractor.disableFlags
            .map { it.isQuickSettingsEnabled() }
            .hydratedStateOf(
                initialValue =
                    shadeStatusBarComponentsInteractor.disableFlags.value.isQuickSettingsEnabled()
            )

    /**
     * Amount of X-axis translation to apply to various elements as the unfolded foldable is folded
     * slightly, in pixels.
     */
    val unfoldTranslationXForStartSide: Float by
        unfoldTransitionInteractor
            .unfoldTranslationX(isOnStartSide = true)
            .hydratedStateOf(initialValue = 0f)

    fun onMediaSwipeToDismiss() = mediaCarouselInteractor.onSwipeToDismiss()

    private val footerActionsControllerInitialized = AtomicBoolean(false)

    private val qqsMediaInRowViewModel =
        mediaInRowInLandscapeViewModelFactory.create(LOCATION_QQS, qqsMediaUiBehavior)

    override suspend fun onActivated() {
        coroutineScope { launch { qqsMediaInRowViewModel.activate() } }
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
                val loggingReason = "Unfold while on notifications shade"
                when (shadeMode) {
                    is ShadeMode.Dual -> {
                        sceneInteractor.snapToScene(SceneFamilies.Home, loggingReason)
                        sceneInteractor.instantlyShowOverlay(
                            Overlays.NotificationsShade,
                            loggingReason,
                        )
                    }
                    else -> Unit
                }
            }
        }
    }

    /**
     * Calculates the blur radius to apply to the scene UI.
     *
     * @param transitionState The current transition state of the scene (from its `ContentScope`)
     * @return The blur radius to apply to the scene UI, in pixels.
     */
    fun calculateBlur(transitionState: TransitionState): Float {
        return when {
            !isTransparencyEnabled -> 0f
            Scenes.Shade != transitionState.currentScene -> 0f
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

    /** Notifies that the empty space in the shade has been clicked. */
    fun onEmptySpaceClicked(transitionState: TransitionState) {
        if (!isEmptySpaceClickable(transitionState)) {
            return
        }

        sceneInteractor.changeScene(SceneFamilies.Home, "Shade empty space clicked.")
    }

    /**
     * Sets the squishiness for the tiles. The squishiness will be mapped between `[0.1, 1.0]` to
     * prevent visual artifacts caused by squishiness being too close to 0.
     */
    fun setTileSquishiness(@FloatRange(0.0, 1.0) squishiness: Float) {
        tileSquishinessInteractor.setSquishinessValue(squishiness.constrainSquishiness())
    }

    companion object {
        val qqsMediaUiBehavior =
            MediaUiBehavior(
                isCarouselDismissible = true,
                carouselVisibility = MediaCarouselVisibility.WhenAnyCardIsActive,
            )
    }

    @AssistedFactory
    interface Factory {
        fun create(): ShadeSceneContentViewModel
    }
}

private fun Float.constrainSquishiness(): Float = (0.1f + this * 0.9f).coerceIn(0f, 1f)
