/*
 * Copyright (C) 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.notifications.ui.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Rect
import com.android.app.tracing.coroutines.launchTraced as launch
import com.android.compose.animation.scene.content.state.TransitionState
import com.android.systemui.Flags
import com.android.systemui.dagger.qualifiers.Main
import com.android.systemui.desktop.domain.interactor.DesktopInteractor
import com.android.systemui.keyguard.ui.transitions.BlurConfig
import com.android.systemui.lifecycle.ExclusiveActivatable
import com.android.systemui.lifecycle.Hydrator
import com.android.systemui.media.controls.domain.pipeline.interactor.MediaCarouselInteractor
import com.android.systemui.media.remedia.ui.compose.MediaUiBehavior
import com.android.systemui.media.remedia.ui.viewmodel.MediaCarouselVisibility
import com.android.systemui.media.remedia.ui.viewmodel.MediaViewModel
import com.android.systemui.scene.domain.interactor.SceneInteractor
import com.android.systemui.scene.shared.model.Overlays
import com.android.systemui.scene.shared.model.Scenes
import com.android.systemui.shade.domain.interactor.ShadeInteractor
import com.android.systemui.shade.domain.interactor.ShadeModeInteractor
import com.android.systemui.shade.shared.model.ShadeMode
import com.android.systemui.shade.ui.viewmodel.ShadeHeaderViewModel
import com.android.systemui.statusbar.core.StatusBarForDesktop
import com.android.systemui.statusbar.disableflags.domain.interactor.DisableFlagsInteractor
import com.android.systemui.statusbar.notification.stack.domain.interactor.NotificationStackAppearanceInteractor
import com.android.systemui.statusbar.notification.stack.ui.viewmodel.NotificationsPlaceholderViewModel
import com.android.systemui.utils.coroutines.flow.flatMapLatestConflated
import com.android.systemui.window.domain.interactor.WindowRootViewBlurInteractor
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Models UI state used to render the content of the notifications shade overlay.
 *
 * Different from [NotificationsShadeOverlayActionsViewModel], which only models user actions that
 * can be performed to navigate to other scenes.
 */
class NotificationsShadeOverlayContentViewModel
@AssistedInject
constructor(
    @Main private val mainDispatcher: CoroutineDispatcher,
    val shadeHeaderViewModelFactory: ShadeHeaderViewModel.Factory,
    val notificationsPlaceholderViewModelFactory: NotificationsPlaceholderViewModel.Factory,
    notificationStackAppearanceInteractor: NotificationStackAppearanceInteractor,
    desktopInteractor: DesktopInteractor,
    val sceneInteractor: SceneInteractor,
    private val shadeInteractor: ShadeInteractor,
    private val shadeModeInteractor: ShadeModeInteractor,
    disableFlagsInteractor: DisableFlagsInteractor,
    private val mediaCarouselInteractor: MediaCarouselInteractor,
    val mediaViewModelFactory: MediaViewModel.Factory,
    private val blurConfig: BlurConfig,
    windowRootViewBlurInteractor: WindowRootViewBlurInteractor,
) : ExclusiveActivatable() {

    private val hydrator = Hydrator("NotificationsShadeOverlayContentViewModel.hydrator")

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

    val showMedia: Boolean by
        hydrator.hydratedStateOf(
            traceName = "showMedia",
            initialValue =
                disableFlagsInteractor.disableFlags.value.isQuickSettingsEnabled() &&
                    mediaCarouselInteractor.hasActiveMedia.value,
            source =
                disableFlagsInteractor.disableFlags.flatMapLatestConflated {
                    if (it.isQuickSettingsEnabled()) {
                        mediaCarouselInteractor.hasActiveMedia
                    } else {
                        flowOf(false)
                    }
                },
        )

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
     * The horizontal alignment of the notifications shade panel. Ignored on narrow screens, where
     * the panel is always center-aligned.
     */
    val alignmentOnWideScreens: Alignment.Horizontal by
        hydrator.hydratedStateOf(
            traceName = "horizontalAlignment",
            initialValue = Alignment.Start,
            source = notificationStackAppearanceInteractor.notificationStackHorizontalAlignment,
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
            Overlays.NotificationsShade !in transitionState.currentOverlays -> 0f
            Overlays.Bouncer in transitionState.currentOverlays -> blurConfig.maxBlurRadiusPx
            else -> 0f
        }
    }

    val mediaUiBehavior =
        MediaUiBehavior(
            isCarouselDismissible = true,
            carouselVisibility = MediaCarouselVisibility.WhenAnyCardIsActive,
        )

    fun onMediaSwipeToDismiss() = mediaCarouselInteractor.onSwipeToDismiss()

    override suspend fun onActivated(): Nothing {
        coroutineScope {
            launch { hydrator.activate() }

            launch {
                shadeInteractor.isShadeTouchable
                    .distinctUntilChanged()
                    .filter { !it }
                    .collect {
                        withContext(mainDispatcher) {
                            shadeInteractor.collapseNotificationsShade(
                                loggingReason = "device became non-interactive"
                            )
                        }
                    }
            }

            launch {
                shadeModeInteractor.shadeMode
                    .filter { it !is ShadeMode.Dual }
                    .distinctUntilChanged()
                    .collect {
                        withContext(mainDispatcher) {
                            sceneInteractor.snapToScene(
                                Scenes.Shade,
                                loggingReason = "Fold or rotate while on notifications shade",
                            )
                        }
                    }
            }
        }
        awaitCancellation()
    }

    fun onScrimClicked() {
        shadeInteractor.collapseNotificationsShade(loggingReason = "shade scrim clicked")
    }

    /** Notifies that the bounds of the shade panel have changed. */
    fun onShadeOverlayBoundsChanged(bounds: Rect?) {
        val boundsRect =
            bounds?.let {
                android.graphics.Rect(
                    it.left.roundToInt(),
                    it.top.roundToInt(),
                    it.right.roundToInt(),
                    it.bottom.roundToInt(),
                )
            }
        shadeInteractor.setShadeOverlayBounds(boundsRect)
    }

    @AssistedFactory
    interface Factory {
        fun create(): NotificationsShadeOverlayContentViewModel
    }
}
