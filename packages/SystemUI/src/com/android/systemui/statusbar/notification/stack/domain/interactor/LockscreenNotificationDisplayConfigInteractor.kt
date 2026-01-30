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

package com.android.systemui.statusbar.notification.stack.domain.interactor

import com.android.compose.animation.scene.ObservableTransitionState.Idle
import com.android.compose.animation.scene.ObservableTransitionState.Transition
import com.android.compose.animation.scene.SceneKey
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.scene.domain.interactor.SceneInteractor
import com.android.systemui.scene.shared.model.Overlays
import com.android.systemui.scene.shared.model.Scenes
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Data class representing a configuration for displaying Notifications on the Lockscreen.
 *
 * @param isOnLockscreen is the user on the lockscreen
 * @param maxNotifications Limit for the max number of top-level Notifications to be displayed. A
 *   value of -1 indicates no limit.
 */
data class LockscreenDisplayConfig(val isOnLockscreen: Boolean, val maxNotifications: Int)

@SysUISingleton
class LockscreenNotificationDisplayConfigInteractor
@Inject
constructor(
    private val sceneInteractor: SceneInteractor,
    private val sharedNotificationContainerInteractor: SharedNotificationContainerInteractor,
    private val notificationStackAppearanceInteractor: NotificationStackAppearanceInteractor,
) {
    private val showOnlyFullHeightNotifications: Flow<Boolean> =
        sceneInteractor.transitionState.map { transitionState ->
            when (transitionState) {
                is Idle ->
                    transitionState.currentScene in keyguardScenes &&
                        Overlays.NotificationsShade !in transitionState.currentOverlays

                is Transition.ChangeScene -> transitionState.fromScene in keyguardScenes

                is Transition.OverlayTransition ->
                    transitionState.currentScene in keyguardScenes &&
                        transitionState.fromContent != Overlays.NotificationsShade
            }
        }

    /**
     * When on keyguard, there is limited space to display notifications so calculate how many could
     * be shown. Otherwise, there is no limit since the vertical space will be scrollable.
     *
     * When expanding or when the user is interacting with the shade, keep the count stable; do not
     * emit a value.
     */
    fun getLockscreenDisplayConfig(
        calculateMaxNotifications: (Int, Boolean) -> Int
    ): Flow<LockscreenDisplayConfig> {
        @Suppress("UNCHECKED_CAST")
        return combine(
                showOnlyFullHeightNotifications,
                notificationStackAppearanceInteractor.constrainedAvailableSpace,
                sharedNotificationContainerInteractor.useExtraShelfSpace,
                sharedNotificationContainerInteractor.notificationStackChanged,
            ) { showLimited, availableHeight, useExtraShelfSpace, _ ->
                if (showLimited) {
                    LockscreenDisplayConfig(
                        isOnLockscreen = true,
                        maxNotifications =
                            calculateMaxNotifications(availableHeight, useExtraShelfSpace),
                    )
                } else {
                    LockscreenDisplayConfig(isOnLockscreen = false, maxNotifications = -1)
                }
            }
            .distinctUntilChanged()
    }

    companion object {
        /** Scenes where only full height notifications are allowed to be shown. */
        val keyguardScenes: Set<SceneKey> = setOf(Scenes.Lockscreen, Scenes.Communal, Scenes.Dream)
    }
}
