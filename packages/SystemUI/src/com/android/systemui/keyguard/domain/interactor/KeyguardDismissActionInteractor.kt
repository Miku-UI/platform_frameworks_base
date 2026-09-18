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
 *
 */

package com.android.systemui.keyguard.domain.interactor

import com.android.keyguard.logging.KeyguardLogger
import com.android.systemui.bouncer.domain.interactor.PrimaryBouncerInteractor
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.deviceentry.domain.interactor.DeviceUnlockedInteractor
import com.android.systemui.keyguard.data.repository.KeyguardRepository
import com.android.systemui.keyguard.shared.model.DismissAction
import com.android.systemui.keyguard.shared.model.KeyguardDone
import com.android.systemui.keyguard.shared.model.KeyguardState.GONE
import com.android.systemui.lifecycle.ExclusiveActivatable
import com.android.systemui.log.core.LogLevel
import com.android.systemui.scene.data.model.contains
import com.android.systemui.scene.domain.interactor.SceneBackInteractor
import com.android.systemui.scene.domain.interactor.SceneInteractor
import com.android.systemui.scene.shared.flag.SceneContainerFlag
import com.android.systemui.scene.shared.model.Scenes
import com.android.systemui.shade.domain.interactor.ShadeInteractor
import dagger.Lazy
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Encapsulates business-logic for actions to run when the keyguard is dismissed. */
@SysUISingleton
class KeyguardDismissActionInteractor
@Inject
constructor(
    private val repository: KeyguardRepository,
    transitionInteractor: KeyguardTransitionInteractor,
    val dismissInteractor: KeyguardDismissInteractor,
    @Application private val applicationScope: CoroutineScope,
    val deviceUnlockedInteractor: Lazy<DeviceUnlockedInteractor>,
    shadeInteractor: Lazy<ShadeInteractor>,
    sceneInteractor: Lazy<SceneInteractor>,
    sceneBackInteractor: SceneBackInteractor,
    private val keyguardLogger: KeyguardLogger,
    private val primaryBouncerInteractor: PrimaryBouncerInteractor,
) : ExclusiveActivatable() {
    private val dismissAction: Flow<DismissAction> = repository.dismissAction

    // TODO (b/268240415): use message in alt + primary bouncer message
    // message to show to the user about the dismiss action, else empty string
    val message = dismissAction.map { it.message }

    /**
     * True if the dismiss action will run an animation on the lockscreen and requires any views
     * that would obscure this animation (ie: the primary bouncer) to immediately hide, so the
     * animation would be visible.
     */
    val willAnimateDismissActionOnLockscreen: StateFlow<Boolean> =
        dismissAction
            .map { it.willAnimateOnLockscreen }
            .stateIn(
                scope = applicationScope,
                started = SharingStarted.Eagerly,
                initialValue = false,
            )

    private val finishedTransitionToGone: Flow<Unit> =
        if (SceneContainerFlag.isEnabled) {
            // Using sceneInteractor instead of transitionInteractor because of a race
            // condition that forms between transitionInteractor (transitionState) and
            // isOnShadeWhileUnlocked where the latter emits false before the former emits
            // true, causing the merge to not emit until it's too late.
            sceneInteractor
                .get()
                .currentScene
                .map { it == Scenes.Gone }
                .distinctUntilChanged()
                .filter { it }
                .map {}
        } else {
            transitionInteractor
                .isFinishedIn(content = Scenes.Gone, stateWithoutSceneContainer = GONE)
                .filter { it }
                .map {}
        }

    /** Emits when the keyguard is dismissed behind the shade. */
    private val unlockedWhileShadeOpen: Flow<Unit> =
        if (SceneContainerFlag.isEnabled) {
            sceneBackInteractor.backStack
                .map { !it.contains(Scenes.Lockscreen) }
                .distinctUntilChanged()
                .map { lockscreenGoneFromBackStack ->
                    lockscreenGoneFromBackStack &&
                        shadeInteractor.get().isAnyExpanded.value &&
                        deviceUnlockedInteractor.get().isUnlocked
                }
                .filter { it }
                .map {}
        } else {
            flow { error("This should not be used when SceneContainerFlag is disabled") }
        }

    fun runDismissAnimationOnKeyguard(): Boolean {
        return willAnimateDismissActionOnLockscreen.value
    }

    fun runAfterKeyguardGone(runnable: Runnable) {
        if (SceneContainerFlag.isUnexpectedlyInLegacyMode()) return
        setDismissAction(
            DismissAction.RunAfterKeyguardGone(
                dismissAction = { runnable.run() },
                onCancelAction = {},
                message = "",
                willAnimateOnLockscreen = false,
            )
        )
    }

    fun setDismissAction(dismissAction: DismissAction) {
        if (SceneContainerFlag.isUnexpectedlyInLegacyMode()) return
        repository.dismissAction.value.onCancelAction.run()
        repository.setDismissAction(dismissAction)
    }

    /** Launch any relevant coroutines that are required by this interactor. */
    override suspend fun onActivated() {
        coroutineScope {
            launch {
                finishedTransitionToGone.collect {
                    log("finishedTransitionToGone")
                    runDismissAction()
                }
            }

            launch {
                unlockedWhileShadeOpen.collect {
                    log("unlockedWhileShadeOpen")
                    runDismissAction()
                }
            }

            launch {
                dismissInteractor.dismissKeyguardRequestWithImmediateDismissAction.collect {
                    log("eventsThatRequireKeyguardDismissal")
                    runDismissAction()
                }
            }

            launch { repository.dismissAction.collect { log("updatedDismissAction=$it") } }
        }
    }

    fun clearDismissAction() {
        setDismissAction(DismissAction.None)
    }

    /** Run the dismiss action and starts the dismiss keyguard transition. */
    private suspend fun runDismissAction() {
        val dismissAction = repository.setDismissAction(DismissAction.None)
        var keyguardDoneTiming: KeyguardDone = KeyguardDone.IMMEDIATE
        if (dismissAction != DismissAction.None) {
            keyguardDoneTiming = dismissAction.onDismissAction.invoke()
            dismissInteractor.setKeyguardDone(keyguardDoneTiming)
            // onCancel should be run when complete as well
            dismissAction.onCancelAction.run()
        }
        if (!SceneContainerFlag.isEnabled) {
            // This is required to reset some state flows in the repository which ideally should be
            // sharedFlows but are not due to performance concerns.
            primaryBouncerInteractor.notifyKeyguardAuthenticatedHandled()
        }
    }

    private fun log(message: String) {
        keyguardLogger.log(TAG, LogLevel.DEBUG, message)
    }

    companion object {
        private const val TAG = "KeyguardDismissAction"
    }
}
