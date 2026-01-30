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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.lifecycle.LifecycleOwner
import com.android.app.tracing.coroutines.launchTraced as launch
import com.android.systemui.Flags
import com.android.systemui.dagger.qualifiers.Main
import com.android.systemui.deviceentry.domain.interactor.DeviceEntryInteractor
import com.android.systemui.keyguard.domain.interactor.KeyguardInteractor
import com.android.systemui.keyguard.ui.transitions.BlurConfig
import com.android.systemui.lifecycle.ExclusiveActivatable
import com.android.systemui.lifecycle.Hydrator
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
import com.android.systemui.shade.shared.model.ShadeMode
import com.android.systemui.statusbar.disableflags.domain.interactor.DisableFlagsInteractor
import com.android.systemui.unfold.domain.interactor.UnfoldTransitionInteractor
import com.android.systemui.window.domain.interactor.WindowRootViewBlurInteractor
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filter
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
    disableFlagsInteractor: DisableFlagsInteractor,
    private val footerActionsViewModelFactory: FooterActionsViewModel.Factory,
    private val footerActionsController: FooterActionsController,
    keyguardInteractor: KeyguardInteractor,
    blurConfig: BlurConfig,
    unfoldTransitionInteractor: UnfoldTransitionInteractor,
    deviceEntryInteractor: DeviceEntryInteractor,
    private val sceneInteractor: SceneInteractor,
    private val tileSquishinessInteractor: TileSquishinessInteractor,
    windowRootViewBlurInteractor: WindowRootViewBlurInteractor,
    mediaInRowInLandscapeViewModelFactory: MediaInRowInLandscapeViewModel.Factory,
) : ExclusiveActivatable() {

    private val hydrator = Hydrator("ShadeSceneContentViewModel.hydrator")

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

    val shadeMode: ShadeMode by
        hydrator.hydratedStateOf(traceName = "shadeMode", source = shadeModeInteractor.shadeMode)

    val isShadeBlurred: Boolean by
        hydrator.hydratedStateOf(
            traceName = "isShadeBlurred",
            source = keyguardInteractor.primaryBouncerShowing,
        )

    val shadeBlurRadius: Float by mutableFloatStateOf(blurConfig.maxBlurRadiusPx)

    /** Whether clicking on the empty area of the shade should do something. */
    val isEmptySpaceClickable: Boolean by
        hydrator.hydratedStateOf(
            traceName = "isEmptySpaceClickable",
            initialValue = !deviceEntryInteractor.isDeviceEntered.value,
            source = deviceEntryInteractor.isDeviceEntered.map { !it },
        )

    val showMediaInRow: Boolean
        get() = qqsMediaInRowViewModel.shouldMediaShowInRow

    val showMedia: Boolean by
        hydrator.hydratedStateOf(
            traceName = "isMediaVisible",
            // mediaCarouselInteractor.hasAnyMedia if in SplitShade.
            source = mediaCarouselInteractor.hasActiveMedia,
        )

    val isQsEnabled: Boolean by
        hydrator.hydratedStateOf(
            traceName = "isQsEnabled",
            initialValue = disableFlagsInteractor.disableFlags.value.isQuickSettingsEnabled(),
            source = disableFlagsInteractor.disableFlags.map { it.isQuickSettingsEnabled() },
        )

    /**
     * Amount of X-axis translation to apply to various elements as the unfolded foldable is folded
     * slightly, in pixels.
     */
    val unfoldTranslationXForStartSide: Float by
        hydrator.hydratedStateOf(
            traceName = "unfoldTranslationXForStartSide",
            initialValue = 0f,
            source = unfoldTransitionInteractor.unfoldTranslationX(isOnStartSide = true),
        )

    fun onMediaSwipeToDismiss() = mediaCarouselInteractor.onSwipeToDismiss()

    private val footerActionsControllerInitialized = AtomicBoolean(false)

    private val qqsMediaInRowViewModel =
        mediaInRowInLandscapeViewModelFactory.create(LOCATION_QQS, qqsMediaUiBehavior)

    override suspend fun onActivated(): Nothing {
        coroutineScope {
            launch { hydrator.activate() }
            launch { qqsMediaInRowViewModel.activate() }

            launch {
                shadeModeInteractor.shadeMode
                    .filter { it is ShadeMode.Dual }
                    .collect {
                        withContext(mainDispatcher) {
                            val loggingReason = "Unfold while on notifications shade"
                            sceneInteractor.snapToScene(SceneFamilies.Home, loggingReason)
                            sceneInteractor.instantlyShowOverlay(
                                Overlays.NotificationsShade,
                                loggingReason,
                            )
                        }
                    }
            }

            awaitCancellation()
        }
    }

    fun getFooterActionsViewModel(lifecycleOwner: LifecycleOwner): FooterActionsViewModel {
        if (footerActionsControllerInitialized.compareAndSet(false, true)) {
            footerActionsController.init()
        }
        return footerActionsViewModelFactory.create(lifecycleOwner)
    }

    /** Notifies that the empty space in the shade has been clicked. */
    fun onEmptySpaceClicked() {
        if (!isEmptySpaceClickable) {
            return
        }

        sceneInteractor.changeScene(Scenes.Lockscreen, "Shade empty space clicked.")
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
