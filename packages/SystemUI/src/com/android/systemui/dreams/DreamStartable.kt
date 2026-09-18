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

package com.android.systemui.dreams

import android.Manifest.permission.WRITE_DREAM_STATE
import android.app.DreamManager
import androidx.annotation.RequiresPermission
import com.android.compose.animation.scene.SceneKey
import com.android.systemui.CoreStartable
import com.android.systemui.authentication.domain.interactor.AuthenticationInteractor
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.keyguard.KeyguardViewMediator.KEYGUARD_LOCK_AFTER_DELAY_DEFAULT
import com.android.systemui.keyguard.domain.interactor.KeyguardInteractor
import com.android.systemui.keyguard.shared.DriveDreamStateFromOcclusion
import com.android.systemui.log.LogBuffer
import com.android.systemui.log.core.Logger
import com.android.systemui.log.dagger.DreamLog
import com.android.systemui.scene.domain.interactor.SceneBackInteractor
import com.android.systemui.scene.domain.interactor.SceneInteractor
import com.android.systemui.scene.domain.resolver.SceneResolver
import com.android.systemui.scene.shared.flag.SceneContainerFlag
import com.android.systemui.scene.shared.model.Overlays
import com.android.systemui.scene.shared.model.SceneFamilies
import com.android.systemui.scene.shared.model.Scenes
import com.android.systemui.util.kotlin.pairwise
import com.android.systemui.util.kotlin.sample
import dagger.Lazy
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@SysUISingleton
class DreamStartable
@Inject
constructor(
    @Application private val applicationScope: CoroutineScope,
    private val sceneInteractor: SceneInteractor,
    private val sceneBackInteractor: SceneBackInteractor,
    private val dreamManager: DreamManager,
    private val keyguardInteractor: KeyguardInteractor,
    private val authenticationInteractor: AuthenticationInteractor,
    @DreamLog private val logBuffer: LogBuffer,
    private val sceneFamilyResolvers: Lazy<Map<SceneKey, @JvmSuppressWildcards SceneResolver>>,
) : CoreStartable {

    private val logger = Logger(logBuffer, TAG)

    @RequiresPermission(WRITE_DREAM_STATE)
    override fun start() {
        if (SceneContainerFlag.isEnabled) {
            handleStopDreamWhenGoingToGone()
            if (!DriveDreamStateFromOcclusion.isEnabled) {
                handleDreamState()
            }
            handleShowLockscreenAfterDreamWhenUnsecured()
        }
    }

    /** Manages scene transitions to and from the Dream scene. */
    @RequiresPermission(WRITE_DREAM_STATE)
    private fun handleDreamState() {
        DriveDreamStateFromOcclusion.assertInLegacyMode()
        applicationScope.launch {
            keyguardInteractor.isDreamingNotDozing.collect { isDreaming ->
                if (isDreaming) {
                    if (
                        sceneInteractor.currentScene.value == Scenes.Lockscreen &&
                            Overlays.Bouncer in sceneInteractor.currentOverlays.value
                    ) {
                        // Apply "Snap, then Animate" fix for bouncer to dream transition.
                        sceneInteractor.snapToScene(
                            toScene = Scenes.Dream,
                            loggingReason = "Snap to dream behind bouncer",
                            hideOverlays = SceneInteractor.HideOverlayCommand.HideNone,
                        )
                        sceneInteractor.hideOverlay(
                            overlay = Overlays.Bouncer,
                            loggingReason = "Hiding bouncer to reveal dream",
                        )
                    } else {
                        // Standard transition to dream.
                        sceneInteractor.changeScene(
                            toScene = Scenes.Dream,
                            loggingReason = "Dream started",
                        )
                    }
                } else {
                    // Since we just got a signal that dream stopped, it is not guaranteed that the
                    // home scene family resolver has received the update. Wait until it no longer
                    // resolves to the Dream scene before requesting the scene change.
                    sceneFamilyResolvers.get()[SceneFamilies.Home]?.resolvedScene?.first {
                        it != Scenes.Dream
                    }
                    if (
                        sceneInteractor.currentScene.value == Scenes.Dream &&
                            Overlays.Bouncer in sceneInteractor.currentOverlays.value
                    ) {
                        // If the bouncer is showing over the dream, we should snap to the home
                        // scene while keeping the bouncer open, to avoid animating the lockscreen
                        // underneath the bouncer.
                        sceneInteractor.snapToScene(
                            toScene = SceneFamilies.Home,
                            loggingReason = "Snap to home behind bouncer",
                            hideOverlays = SceneInteractor.HideOverlayCommand.HideNone,
                        )
                    } else {
                        sceneInteractor.changeScene(
                            toScene = SceneFamilies.Home,
                            loggingReason = "Dream stopped",
                            hideOverlays =
                                if (!keyguardInteractor.isKeyguardShowing.value) {
                                    SceneInteractor.HideOverlayCommand.HideAll
                                } else {
                                    SceneInteractor.HideOverlayCommand.HideNone
                                },
                        )
                    }
                }
            }
        }
    }

    /**
     * Stops dream when going from dream to gone.
     *
     * This can happen when launching activities from the dream, and once device is authenticated
     * and goes to the home screen, dream should be stopped.
     */
    @RequiresPermission(WRITE_DREAM_STATE)
    private fun handleStopDreamWhenGoingToGone() {
        applicationScope.launch {
            sceneInteractor.currentScene
                .pairwise()
                .map { (prev, current) -> prev == Scenes.Dream && current == Scenes.Gone }
                .distinctUntilChanged()
                .sample(keyguardInteractor.isDreamingNotDozing, ::Pair)
                .collect { (dreamToGone, isDreaming) ->
                    if (dreamToGone && isDreaming) {
                        logger.i("Stopping dream due to going from Dream to Gone")
                        dreamManager.stopDream()
                    }
                }
        }
    }

    /**
     * Makes sure that when device has an unsecured authentication method, we go to the lockscreen
     * when dream is awaken.
     *
     * When dream has started, we delay by [KEYGUARD_LOCK_AFTER_DELAY_DEFAULT], and then add the
     * lockscreen to the bottom of the scene stack, if it is not already there. This makes sure when
     * dream is woken up we go to the lockscreen instead of the home screen.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun handleShowLockscreenAfterDreamWhenUnsecured() {
        applicationScope.launch {
            authenticationInteractor.authenticationMethod
                .flatMapLatest { authMethod ->
                    if (authMethod.isSecure) {
                        emptyFlow()
                    } else {
                        keyguardInteractor.isDreamingNotDozing
                    }
                }
                .distinctUntilChanged()
                .flatMapLatest { isDreaming ->
                    if (isDreaming) {
                        flow {
                            delay(KEYGUARD_LOCK_AFTER_DELAY_DEFAULT.toLong())
                            emit(Unit)
                        }
                    } else {
                        emptyFlow()
                    }
                }
                .collect {
                    sceneBackInteractor.addLockscreenToBackStack(
                        reason = "keyguard lock timeout after dream started"
                    )
                }
        }
    }

    private companion object {
        const val TAG = "DreamStartable"
    }
}
