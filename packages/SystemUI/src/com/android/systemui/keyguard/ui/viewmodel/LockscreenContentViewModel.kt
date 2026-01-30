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

package com.android.systemui.keyguard.ui.viewmodel

import androidx.compose.runtime.getValue
import com.android.app.tracing.coroutines.launchTraced as launch
import com.android.compose.animation.scene.content.state.TransitionState
import com.android.systemui.deviceentry.domain.interactor.DeviceEntryBypassInteractor
import com.android.systemui.deviceentry.domain.interactor.DeviceEntryUdfpsInteractor
import com.android.systemui.keyguard.domain.interactor.KeyguardBlueprintInteractor
import com.android.systemui.keyguard.shared.transition.KeyguardTransitionAnimationCallback
import com.android.systemui.keyguard.shared.transition.KeyguardTransitionAnimationCallbackDelegator
import com.android.systemui.lifecycle.ExclusiveActivatable
import com.android.systemui.lifecycle.Hydrator
import com.android.systemui.scene.shared.model.Overlays
import com.android.systemui.scene.shared.model.Scenes
import com.android.systemui.shade.domain.interactor.ShadeModeInteractor
import com.android.systemui.shade.shared.model.ShadeMode
import com.android.systemui.statusbar.notification.stack.domain.interactor.NotificationStackAppearanceInteractor
import com.android.systemui.wallpapers.domain.interactor.WallpaperFocalAreaInteractor
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class LockscreenContentViewModel
@AssistedInject
constructor(
    interactor: KeyguardBlueprintInteractor,
    val touchHandlingFactory: KeyguardTouchHandlingViewModel.Factory,
    shadeModeInteractor: ShadeModeInteractor,
    deviceEntryBypassInteractor: DeviceEntryBypassInteractor,
    deviceEntryUdfpsInteractor: DeviceEntryUdfpsInteractor,
    private val keyguardTransitionAnimationCallbackDelegator:
        KeyguardTransitionAnimationCallbackDelegator,
    private val wallpaperFocalAreaInteractor: WallpaperFocalAreaInteractor,
    private val notificationStackAppearanceInteractor: NotificationStackAppearanceInteractor,
    private val lockscreenAlphaViewModelFactory: LockscreenAlphaViewModel.Factory,
    @Assisted private val keyguardTransitionAnimationCallback: KeyguardTransitionAnimationCallback,
    @Assisted private val viewStateAccessor: ViewStateAccessor,
) : ExclusiveActivatable() {

    private val hydrator = Hydrator("LockscreenContentViewModel.hydrator")

    /** @see ShadeModeInteractor.isFullWidthShade */
    val isFullWidthShade: Boolean by
        hydrator.hydratedStateOf(
            traceName = "isFullWidthShade",
            source = shadeModeInteractor.isFullWidthShade,
        )

    /** @see ShadeModeInteractor.shadeMode */
    val shadeMode: ShadeMode by
        hydrator.hydratedStateOf(traceName = "shadeMode", source = shadeModeInteractor.shadeMode)

    /** @see DeviceEntryBypassInteractor.isBypassEnabled */
    val isBypassEnabled: Boolean by
        hydrator.hydratedStateOf(
            traceName = "isBypassEnabled",
            source = deviceEntryBypassInteractor.isBypassEnabled,
        )

    val blueprintId: String by
        hydrator.hydratedStateOf(
            traceName = "blueprintId",
            initialValue = interactor.getCurrentBlueprint().id,
            source = interactor.blueprint.map { it.id }.distinctUntilChanged(),
        )

    /** Whether udfps is supported. */
    val isUdfpsSupported: Boolean by
        hydrator.hydratedStateOf(
            traceName = "isUdfpsSupported",
            source = deviceEntryUdfpsInteractor.isUdfpsSupported,
            initialValue = deviceEntryUdfpsInteractor.isUdfpsSupported.value,
        )

    /** Alpha value applied to all LockscreenElements. */
    val alpha: Float
        get() = lockscreenAlphaViewModel.alpha

    private val lockscreenAlphaViewModel: LockscreenAlphaViewModel by lazy {
        lockscreenAlphaViewModelFactory.create(viewStateAccessor)
    }

    override suspend fun onActivated(): Nothing {
        coroutineScope {
            try {
                launch { hydrator.activate() }
                launch { lockscreenAlphaViewModel.activate() }

                keyguardTransitionAnimationCallbackDelegator.delegate =
                    keyguardTransitionAnimationCallback

                awaitCancellation()
            } finally {
                keyguardTransitionAnimationCallbackDelegator.delegate = null
            }
        }
    }

    fun setMediaPlayerBottom(bottom: Float) {
        wallpaperFocalAreaInteractor.setMediaPlayerBottom(bottom)
    }

    fun setShortcutTop(top: Float) {
        wallpaperFocalAreaInteractor.setShortcutTop(top)
    }

    fun setSmallClockBottom(bottom: Float) {
        wallpaperFocalAreaInteractor.setSmallClockBottom(bottom)
    }

    fun setSmartspaceCardBottom(bottom: Float) {
        wallpaperFocalAreaInteractor.setSmartspaceCardBottom(bottom)
    }

    /** Sets the alpha to apply to the NSSL for fade-in on lockscreen */
    fun setContentAlphaForLockscreenFadeIn(alpha: Float) {
        notificationStackAppearanceInteractor.setAlphaForLockscreenFadeIn(alpha)
    }

    /** Should a content reveal animation run for the given transition */
    fun shouldContentFadeIn(currentTransition: TransitionState.Transition): Boolean {
        return shadeMode != ShadeMode.Dual &&
            currentTransition.isInitiatedByUserInput &&
            (currentTransition.isTransitioning(from = Scenes.Shade, to = Scenes.Lockscreen) ||
                currentTransition.isTransitioning(from = Overlays.Bouncer, to = Scenes.Lockscreen))
    }

    @AssistedFactory
    interface Factory {
        fun create(
            keyguardTransitionAnimationCallback: KeyguardTransitionAnimationCallback,
            viewState: ViewStateAccessor,
        ): LockscreenContentViewModel
    }
}
