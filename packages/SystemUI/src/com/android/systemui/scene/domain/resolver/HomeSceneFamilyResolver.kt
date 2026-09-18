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

package com.android.systemui.scene.domain.resolver

import com.android.compose.animation.scene.SceneKey
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.deviceentry.domain.interactor.DeviceEntryInteractor
import com.android.systemui.keyguard.data.model.ShowWhenLockedActivityInfoModel
import com.android.systemui.keyguard.domain.interactor.KeyguardEnabledInteractor
import com.android.systemui.keyguard.domain.interactor.KeyguardInteractor
import com.android.systemui.keyguard.domain.interactor.KeyguardOcclusionInteractor
import com.android.systemui.keyguard.domain.model.OcclusionStateModel
import com.android.systemui.keyguard.shared.DriveDreamStateFromOcclusion
import com.android.systemui.power.domain.interactor.PowerInteractor
import com.android.systemui.scene.data.model.SceneStack
import com.android.systemui.scene.data.model.asIterable
import com.android.systemui.scene.domain.interactor.SceneBackInteractor
import com.android.systemui.scene.shared.model.SceneFamilies
import com.android.systemui.scene.shared.model.Scenes
import com.android.systemui.util.kotlin.combine
import dagger.Binds
import dagger.Module
import dagger.multibindings.IntoSet
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/**
 * Resolver for [SceneFamilies.Home]. The "home" scene family resolves to the scene that is
 * currently underneath any "overlay" scene, such as shades or bouncer.
 */
@SysUISingleton
class HomeSceneFamilyResolver
@Inject
constructor(
    @Application private val applicationScope: CoroutineScope,
    deviceEntryInteractor: DeviceEntryInteractor,
    keyguardInteractor: KeyguardInteractor,
    keyguardEnabledInteractor: KeyguardEnabledInteractor,
    keyguardOcclusionInteractor: KeyguardOcclusionInteractor,
    powerInteractor: PowerInteractor,
    sceneBackInteractor: SceneBackInteractor,
) : SceneResolver {
    override val targetFamily: SceneKey = SceneFamilies.Home

    override val resolvedScene: StateFlow<SceneKey> =
        combine(
                keyguardOcclusionInteractor.showWhenLockedActivityInfo,
                keyguardOcclusionInteractor.occlusionState,
                keyguardEnabledInteractor.isKeyguardEnabled,
                deviceEntryInteractor.canSwipeToEnter,
                deviceEntryInteractor.isDeviceEntered,
                deviceEntryInteractor.isUnlocked,
                keyguardInteractor.isDreamingNotDozing,
                keyguardInteractor.isAodAvailable,
                powerInteractor.isAwake,
                sceneBackInteractor.backStack,
                transform = ::homeScene,
            )
            .stateIn(
                scope = applicationScope,
                started = SharingStarted.Eagerly,
                initialValue =
                    homeScene(
                        showWhenLockedActivityInfo =
                            keyguardOcclusionInteractor.showWhenLockedActivityInfo.value,
                        occlusionState = keyguardOcclusionInteractor.occlusionState.value,
                        isKeyguardEnabled = keyguardEnabledInteractor.isKeyguardEnabled.value,
                        canSwipeToEnter = deviceEntryInteractor.canSwipeToEnter.value,
                        isDeviceEntered = deviceEntryInteractor.isDeviceEntered.value,
                        isUnlocked = deviceEntryInteractor.isUnlocked.value,
                        isDreamingNotDozing = false,
                        isAodAvailable = false,
                        isAwake = true,
                        backStack = sceneBackInteractor.backStack.value,
                    ),
            )

    private fun resolvedOcclusionScene(
        showWhenLockedActivityInfo: ShowWhenLockedActivityInfoModel,
        occlusionState: OcclusionStateModel,
        isDreamingNotDozing: Boolean,
    ): SceneKey? {
        val isDream =
            if (DriveDreamStateFromOcclusion.isEnabled) {
                // Use showWhenLocked activity info instead of OcclusionStateModel here to also
                // handle the case where the dream is showing and the device is unlocked / keyguard
                // is not showing.
                showWhenLockedActivityInfo.isDream()
            } else {
                isDreamingNotDozing
            }
        val isOccluded =
            if (DriveDreamStateFromOcclusion.isEnabled) {
                occlusionState == OcclusionStateModel.APP
            } else {
                occlusionState == OcclusionStateModel.LEGACY_OCCLUDED_GENERIC
            }

        return when {
            isDream -> Scenes.Dream
            isOccluded -> Scenes.Occluded
            else -> null
        }
    }

    private fun homeScene(
        showWhenLockedActivityInfo: ShowWhenLockedActivityInfoModel,
        occlusionState: OcclusionStateModel,
        isKeyguardEnabled: Boolean,
        canSwipeToEnter: Boolean?,
        isDeviceEntered: Boolean,
        isUnlocked: Boolean,
        isDreamingNotDozing: Boolean,
        isAodAvailable: Boolean,
        isAwake: Boolean,
        backStack: SceneStack,
    ): SceneKey {
        val occlusionScene =
            resolvedOcclusionScene(showWhenLockedActivityInfo, occlusionState, isDreamingNotDozing)
        if (occlusionScene != null) {
            return occlusionScene
        }

        return when {
            // If we're asleep on AOD, show Lockscreen scene even if keyguard is disabled.
            !isAwake && isAodAvailable -> Scenes.Lockscreen
            !isKeyguardEnabled -> Scenes.Gone
            canSwipeToEnter == true -> Scenes.Lockscreen
            !isDeviceEntered -> Scenes.Lockscreen
            !isUnlocked -> Scenes.Lockscreen
            // If we have SWIPE security, isUnlocked will never be false. Locking the device
            // actually means showing the lockscreen. If we are unable to show the lockscreen
            // immediately and we end up adding it to the backstack, we need to resolve the
            // home scene to lockscreen.
            backStack.asIterable().lastOrNull() == Scenes.Lockscreen -> Scenes.Lockscreen
            else -> Scenes.Gone
        }
    }
}

@Module
interface HomeSceneFamilyResolverModule {
    @Binds @IntoSet fun provideSceneResolver(interactor: HomeSceneFamilyResolver): SceneResolver
}
