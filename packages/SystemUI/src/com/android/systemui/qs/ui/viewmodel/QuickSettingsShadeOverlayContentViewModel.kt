/*
 * Copyright (C) 2024 The Android Open Source Project
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

import android.content.Context
import android.graphics.Rect
import android.media.AudioManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import com.android.compose.animation.scene.content.state.TransitionState
import com.android.settingslib.volume.shared.model.AudioStream
import com.android.systemui.Flags
import com.android.systemui.dagger.qualifiers.Main
import com.android.systemui.desktop.domain.interactor.DesktopInteractor
import com.android.systemui.development.ui.viewmodel.BuildNumberViewModel
import com.android.systemui.keyguard.ui.transitions.BlurConfig
import com.android.systemui.lifecycle.ExclusiveActivatable
import com.android.systemui.lifecycle.Hydrator
import com.android.systemui.qs.flags.QsDetailedView
import com.android.systemui.qs.panels.domain.interactor.QSPanelAppearanceInteractor
import com.android.systemui.qs.panels.ui.viewmodel.toolbar.ToolbarViewModel
import com.android.systemui.qs.tiles.dialog.AudioDetailsViewModel
import com.android.systemui.res.R
import com.android.systemui.scene.domain.interactor.SceneInteractor
import com.android.systemui.scene.shared.model.Overlays
import com.android.systemui.scene.shared.model.Scenes
import com.android.systemui.shade.ShadeDisplayAware
import com.android.systemui.shade.domain.interactor.ShadeInteractor
import com.android.systemui.shade.domain.interactor.ShadeModeInteractor
import com.android.systemui.shade.shared.model.ShadeMode
import com.android.systemui.statusbar.core.StatusBarForDesktop
import com.android.systemui.statusbar.notification.stack.domain.interactor.NotificationStackAppearanceInteractor
import com.android.systemui.statusbar.notification.stack.shared.model.ShadeScrimShape
import com.android.systemui.volume.panel.component.volume.domain.model.SliderType
import com.android.systemui.volume.panel.component.volume.slider.ui.viewmodel.AudioStreamSliderViewModel
import com.android.systemui.window.domain.interactor.WindowRootViewBlurInteractor
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Models UI state used to render the content of the quick settings shade overlay.
 *
 * Different from [QuickSettingsShadeOverlayActionsViewModel], which only models user actions that
 * can be performed to navigate to other scenes.
 */
class QuickSettingsShadeOverlayContentViewModel
@AssistedInject
constructor(
    @Main private val mainDispatcher: CoroutineDispatcher,
    val desktopInteractor: DesktopInteractor,
    audioStreamSliderViewModelFactory: AudioStreamSliderViewModel.Factory,
    val audioDetailsViewModelFactory: AudioDetailsViewModel.Factory,
    val buildNumberViewModelFactory: BuildNumberViewModel.Factory,
    @ShadeDisplayAware shadeContext: Context,
    val shadeInteractor: ShadeInteractor,
    val shadeModeInteractor: ShadeModeInteractor,
    val sceneInteractor: SceneInteractor,
    val notificationStackAppearanceInteractor: NotificationStackAppearanceInteractor,
    @Assisted private val volumeSliderCoroutineScope: CoroutineScope?,
    val toolbarViewModelFactory: ToolbarViewModel.Factory,
    private val blurConfig: BlurConfig,
    windowRootViewBlurInteractor: WindowRootViewBlurInteractor,
    private val qsPanelAppearanceInteractor: QSPanelAppearanceInteractor,
) : ExclusiveActivatable() {

    private val hydrator = Hydrator("QuickSettingsShadeOverlayContentViewModel.hydrator")

    /**
     * The Shade header can only be shown if usingDesktopStatusBar is disabled. This is because the
     * desktop status bar is always visible when usingDesktopStatusBar is enabled.
     */
    val showHeader: Boolean by
        if (StatusBarForDesktop.isEnabled) {
            hydrator.hydratedStateOf(
                traceName = "showHeader",
                initialValue = !desktopInteractor.useDesktopStatusBar.value,
                source = desktopInteractor.useDesktopStatusBar.map { !it },
            )
        } else {
            mutableStateOf(true)
        }

    /**
     * Whether the shade container transparency effect should be enabled (`true`), or whether to
     * render a fully-opaque shade container (`false`).
     */
    val isTransparencyEnabled: Boolean by
        hydrator.hydratedStateOf(
            traceName = "transparencyEnabled",
            initialValue =
                Flags.notificationShadeBlur() &&
                    windowRootViewBlurInteractor.isBlurCurrentlySupported.value,
            source =
                if (Flags.notificationShadeBlur()) {
                    windowRootViewBlurInteractor.isBlurCurrentlySupported
                } else {
                    flowOf(false)
                },
        )

    /**
     * Calculates the blur radius to apply to the overlay.
     *
     * @param transitionState The current transition state of the scene (from its `ContentScope`)
     * @return The blur radius to apply to the scene UI, in pixels.
     */
    fun calculateTargetBlurRadius(transitionState: TransitionState): Float {
        return when {
            !isTransparencyEnabled -> 0f
            Overlays.QuickSettingsShade !in transitionState.currentOverlays -> 0f
            Overlays.Bouncer in transitionState.currentOverlays -> blurConfig.maxBlurRadiusPx
            else -> 0f
        }
    }

    private val showVolumeSlider =
        QsDetailedView.isEnabled &&
            shadeContext.resources.getBoolean(R.bool.config_enableDesktopAudioTileDetailsView)

    val volumeSliderViewModel =
        if (showVolumeSlider && volumeSliderCoroutineScope != null)
            audioStreamSliderViewModelFactory.create(
                AudioStreamSliderViewModel.FactoryAudioStreamWrapper(
                    SliderType.Stream(AudioStream(AudioManager.STREAM_MUSIC)).stream
                ),
                volumeSliderCoroutineScope,
            )
        else {
            null
        }

    override suspend fun onActivated(): Nothing {
        coroutineScope {
            launch { hydrator.activate() }
            launch {
                shadeInteractor.isShadeTouchable
                    .distinctUntilChanged()
                    .filter { !it }
                    .collect {
                        withContext(mainDispatcher) {
                            shadeInteractor.collapseQuickSettingsShade(
                                loggingReason = "device became non-interactive"
                            )
                        }
                    }
            }
        }

        awaitCancellation()
    }

    /**
     * Monitors changes to the shade mode that would make this overlay stale, and snaps to the
     * appropriate scene/overlay instead.
     *
     * This function must only run while the overlay is shown. Therefore, it shouldn't be part of
     * [onActivated()] while this overlay uses `alwaysCompose`.
     */
    suspend fun detectShadeModeChanges(): Nothing {
        shadeModeInteractor.shadeMode.collect { shadeMode ->
            withContext(mainDispatcher) {
                val loggingReason = "Fold while on quick settings shade"
                when (shadeMode) {
                    is ShadeMode.Single ->
                        sceneInteractor.snapToScene(Scenes.QuickSettings, loggingReason)

                    is ShadeMode.Split -> sceneInteractor.snapToScene(Scenes.Shade, loggingReason)

                    is ShadeMode.Dual -> Unit // Standard case, nothing to do
                }
            }
        }
    }

    /** Notifies that the bounds of the QuickSettings panel have changed. */
    fun onPanelShapeInWindowChanged(shape: ShadeScrimShape?) {
        qsPanelAppearanceInteractor.setQsPanelShape(shape)
        notificationStackAppearanceInteractor.setQsPanelShapeInWindow(shape)
    }

    /** Notifies that the bounds of the shade panel have changed. */
    fun onShadeOverlayBoundsChanged(bounds: androidx.compose.ui.geometry.Rect?) {
        val boundsRect =
            bounds?.let {
                Rect(
                    it.left.roundToInt(),
                    it.top.roundToInt(),
                    it.right.roundToInt(),
                    it.bottom.roundToInt(),
                )
            }
        shadeInteractor.setShadeOverlayBounds(boundsRect)
    }

    fun onScrimClicked() {
        shadeInteractor.collapseQuickSettingsShade(loggingReason = "shade scrim clicked")
    }

    @AssistedFactory
    interface Factory {
        fun create(
            volumeSliderCoroutineScope: CoroutineScope? = null
        ): QuickSettingsShadeOverlayContentViewModel
    }
}
