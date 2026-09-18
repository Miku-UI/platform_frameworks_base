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

package com.android.systemui.deviceentry.domain.interactor

import android.content.Context
import android.content.Intent
import com.android.app.tracing.coroutines.launchTraced as launch
import com.android.systemui.bouncer.domain.interactor.AlternateBouncerInteractor
import com.android.systemui.bouncer.domain.interactor.PrimaryBouncerInteractor
import com.android.systemui.communal.domain.interactor.CommunalSceneInteractor
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.deviceentry.shared.model.BiometricMessage
import com.android.systemui.deviceentry.shared.model.FingerprintLockoutMessage
import com.android.systemui.keyguard.data.repository.DeviceEntryFingerprintAuthRepository
import com.android.systemui.keyguard.domain.interactor.KeyguardInteractor
import com.android.systemui.keyguard.shared.model.ErrorFingerprintAuthenticationStatus
import com.android.systemui.keyguard.shared.model.SuccessFingerprintAuthenticationStatus
import com.android.systemui.plugins.ActivityStarter
import com.android.systemui.power.domain.interactor.PowerInteractor
import com.android.systemui.res.R
import com.android.systemui.scene.domain.interactor.SceneInteractor
import com.android.systemui.scene.shared.flag.SceneContainerFlag
import com.android.systemui.scene.shared.model.Scenes
import dagger.Lazy
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNot
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/** Business logic for handling authentication events when an app is occluding the lockscreen. */
@SysUISingleton
class OccludingAppDeviceEntryInteractor
@Inject
constructor(
    biometricMessageInteractor: BiometricMessageInteractor,
    fingerprintAuthRepository: DeviceEntryFingerprintAuthRepository,
    keyguardInteractor: KeyguardInteractor,
    primaryBouncerInteractor: PrimaryBouncerInteractor,
    alternateBouncerInteractor: AlternateBouncerInteractor,
    @Application scope: CoroutineScope,
    private val context: Context,
    activityStarter: ActivityStarter,
    powerInteractor: PowerInteractor,
    sceneInteractor: Lazy<SceneInteractor>,
    communalSceneInteractor: CommunalSceneInteractor,
) {
    private val keyguardOccludedByAppOrDreaming: Flow<Boolean> =
        if (SceneContainerFlag.isEnabled) {
            combine(alternateBouncerInteractor.isVisible, sceneInteractor.get().currentScene) {
                    alternateBouncerVisible,
                    currentScene ->
                    !alternateBouncerVisible &&
                        (currentScene == Scenes.Dream || currentScene == Scenes.Occluded)
                }
                .distinctUntilChanged()
        } else {
            com.android.systemui.util.kotlin
                .combine(
                    keyguardInteractor.isKeyguardOccluded,
                    keyguardInteractor.isKeyguardShowing,
                    primaryBouncerInteractor.isShowing,
                    alternateBouncerInteractor.isVisible,
                    keyguardInteractor.isDozing,
                    communalSceneInteractor.isIdleOnCommunal,
                ) {
                    occluded,
                    showing,
                    primaryBouncerShowing,
                    alternateBouncerVisible,
                    dozing,
                    isIdleOnCommunal ->
                    occluded &&
                        showing &&
                        !primaryBouncerShowing &&
                        !alternateBouncerVisible &&
                        !dozing &&
                        !isIdleOnCommunal
                }
                .distinctUntilChanged()
        }

    private val fingerprintUnlockSuccessEvents: Flow<Unit> =
        fingerprintAuthRepository.authenticationStatus
            .ifKeyguardOccludedByAppOrDreaming()
            .filter { it is SuccessFingerprintAuthenticationStatus }
            .map {} // maps FingerprintAuthenticationStatus => Unit
    private val fingerprintLockoutEvents: Flow<Unit> =
        fingerprintAuthRepository.authenticationStatus
            .ifKeyguardOccludedByAppOrDreaming()
            .filter { it is ErrorFingerprintAuthenticationStatus && it.isLockoutError() }
            .map {} // maps FingerprintAuthenticationStatus => Unit
    val message: Flow<BiometricMessage?> =
        biometricMessageInteractor.fingerprintMessage
            .filterNot { fingerprintMessage ->
                // On lockout, the device will show the bouncer. Let's not show the message
                // before the transition or else it'll look flickery.
                fingerprintMessage is FingerprintLockoutMessage
            }
            .ifKeyguardOccludedByAppOrDreaming(/* elseFlow */ flowOf(null))

    init {
        // This seems undesirable in most cases, except when a video is playing and can PiP when
        // unlocked. It was originally added for tablets, so allow it there
        if (context.resources.getBoolean(R.bool.config_goToHomeFromOccludedApps)) {
            scope.launch {
                // On fingerprint success when the screen is on and not dreaming, go to the home
                // screen. From dream, a fingerprint success should bring the user to the last app.
                fingerprintUnlockSuccessEvents.collect {
                    val interactive = powerInteractor.isInteractive.value
                    val dreaming =
                        if (SceneContainerFlag.isEnabled) {
                            sceneInteractor.get().currentScene.value == Scenes.Dream
                        } else {
                            keyguardInteractor.isDreaming.value
                        }
                    if (interactive && !dreaming) {
                        goToHomeScreen()
                    }
                    // don't go to the home screen if the authentication is from
                    // AOD/dozing/off/dreaming
                }
            }
        }

        scope.launch {
            // On device fingerprint lockout, request the bouncer with a runnable to
            // go to the home screen. Without this, the bouncer won't proceed to the home
            // screen.
            fingerprintLockoutEvents.collect {
                activityStarter.dismissKeyguardThenExecute(
                    object : ActivityStarter.OnDismissAction {
                        override fun onDismiss(): Boolean {
                            goToHomeScreen()
                            return false
                        }

                        override fun willRunAnimationOnKeyguard(): Boolean {
                            return false
                        }
                    },
                    /* cancel= */ null,
                    /* afterKeyguardGone */ false,
                )
            }
        }
    }

    /** Launches an Activity which forces the current app to background by going home. */
    private fun goToHomeScreen() {
        context.startActivity(
            Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
        )
    }

    private fun <T> Flow<T>.ifKeyguardOccludedByAppOrDreaming(
        elseFlow: Flow<T> = emptyFlow()
    ): Flow<T> {
        return keyguardOccludedByAppOrDreaming.flatMapLatest { keyguardOccludedByAppOrDreaming ->
            if (keyguardOccludedByAppOrDreaming) {
                this
            } else {
                elseFlow
            }
        }
    }
}
