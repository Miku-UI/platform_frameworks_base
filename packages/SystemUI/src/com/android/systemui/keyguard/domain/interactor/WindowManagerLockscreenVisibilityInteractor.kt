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

package com.android.systemui.keyguard.domain.interactor

import com.android.compose.animation.scene.ObservableTransitionState.Idle
import com.android.compose.animation.scene.ObservableTransitionState.Transition
import com.android.compose.animation.scene.SceneKey
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.deviceentry.domain.interactor.DeviceEntryInteractor
import com.android.systemui.keyguard.data.repository.KeyguardTransitionRepository
import com.android.systemui.keyguard.shared.model.Edge
import com.android.systemui.keyguard.shared.model.KeyguardState
import com.android.systemui.keyguard.shared.model.KeyguardState.Companion.deviceIsAsleepInState
import com.android.systemui.keyguard.shared.model.TransitionState
import com.android.systemui.power.domain.interactor.PowerInteractor
import com.android.systemui.scene.domain.interactor.SceneInteractor
import com.android.systemui.scene.shared.flag.SceneContainerFlag
import com.android.systemui.scene.shared.model.Overlays
import com.android.systemui.scene.shared.model.Scenes
import com.android.systemui.statusbar.notification.domain.interactor.NotificationLaunchAnimationInteractor
import com.android.systemui.statusbar.policy.domain.interactor.DeviceProvisioningInteractor
import com.android.systemui.util.kotlin.Utils.Companion.toQuad
import com.android.systemui.utils.coroutines.flow.flatMapLatestConflated
import dagger.Lazy
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

@SysUISingleton
class WindowManagerLockscreenVisibilityInteractor
@Inject
constructor(
    keyguardInteractor: KeyguardInteractor,
    transitionRepository: KeyguardTransitionRepository,
    transitionInteractor: KeyguardTransitionInteractor,
    surfaceBehindInteractor: KeyguardSurfaceBehindInteractor,
    fromLockscreenInteractor: FromLockscreenTransitionInteractor,
    fromBouncerInteractor: FromPrimaryBouncerTransitionInteractor,
    fromAlternateBouncerInteractor: FromAlternateBouncerTransitionInteractor,
    notificationLaunchAnimationInteractor: NotificationLaunchAnimationInteractor,
    sceneInteractor: Lazy<SceneInteractor>,
    deviceEntryInteractor: Lazy<DeviceEntryInteractor>,
    wakeToGoneInteractor: KeyguardWakeDirectlyToGoneInteractor,
    deviceProvisioningInteractor: Lazy<DeviceProvisioningInteractor>,
    powerInteractor: PowerInteractor,
) {
    private val defaultSurfaceBehindVisibility =
        combine(
            transitionInteractor.isFinishedIn(
                content = Scenes.Gone,
                stateWithoutSceneContainer = KeyguardState.GONE,
            ),
            wakeToGoneInteractor.canWakeDirectlyToGone,
        ) { isOnGone, canWakeDirectlyToGone ->
            isOnGone || canWakeDirectlyToGone
        }

    /**
     * Surface visibility provided by the From*TransitionInteractor responsible for the currently
     * RUNNING transition, or null if the current transition does not require special surface
     * visibility handling.
     *
     * An example of transition-specific visibility is swipe to unlock, where the surface should
     * only be visible after swiping 20% of the way up the screen, and should become invisible again
     * if the user swipes back down.
     */
    private val transitionSpecificSurfaceBehindVisibility: Flow<Boolean?> =
        transitionInteractor.startedKeyguardTransitionStep
            .flatMapLatest { startedStep ->
                SceneContainerFlag.assertInLegacyMode()
                when (startedStep.from) {
                    KeyguardState.LOCKSCREEN -> {
                        fromLockscreenInteractor.surfaceBehindVisibility
                    }
                    KeyguardState.PRIMARY_BOUNCER -> {
                        fromBouncerInteractor.surfaceBehindVisibility
                    }
                    KeyguardState.ALTERNATE_BOUNCER -> {
                        fromAlternateBouncerInteractor.surfaceBehindVisibility
                    }
                    KeyguardState.OCCLUDED -> {
                        // OCCLUDED -> GONE occurs when an app is on top of the keyguard, and then
                        // requests manual dismissal of the keyguard in the background. The app will
                        // remain visible on top of the stack throughout this transition, so we
                        // should not trigger the keyguard going away animation by returning
                        // surfaceBehindVisibility = true.
                        flowOf(false)
                    }
                    else -> flowOf(null)
                }
            }
            .distinctUntilChanged()

    private val isDeviceEnteredDirectly by lazy {
        deviceEntryInteractor.get().isDeviceEnteredDirectly
    }
    private val isDeviceNotEnteredDirectly by lazy { isDeviceEnteredDirectly.map { !it } }

    /**
     * Surface visibility, which is either determined by the default visibility when not
     * transitioning between [KeyguardState]s or [Scenes] or the transition-specific visibility used
     * during certain ongoing transitions.
     */
    val surfaceBehindVisibility: Flow<Boolean> =
        if (SceneContainerFlag.isEnabled) {
                sceneInteractor.get().transitionState.flatMapLatestConflated { state ->
                    when {
                        state.isTransitioning(from = Scenes.Lockscreen, to = Scenes.Gone) ->
                            isDeviceEnteredDirectly
                        state.isTransitioning(from = Overlays.Bouncer, to = Scenes.Gone) ->
                            (state as Transition).progress.map { progress ->
                                progress >
                                    FromPrimaryBouncerTransitionInteractor
                                        .TO_GONE_SURFACE_BEHIND_VISIBLE_THRESHOLD
                            }
                        else -> lockscreenVisibilityWithScenes.map { !it.first }
                    }
                }
            } else {
                transitionInteractor.isInTransition.flatMapLatest { isInTransition ->
                    if (!isInTransition) {
                        defaultSurfaceBehindVisibility
                    } else {
                        combine(
                            transitionSpecificSurfaceBehindVisibility,
                            defaultSurfaceBehindVisibility,
                        ) { transitionVisibility, defaultVisibility ->
                            // Defer to the transition-specific visibility since we're RUNNING a
                            // transition, but fall back to the default visibility if the current
                            // transition's interactor did not specify a visibility.
                            transitionVisibility ?: defaultVisibility
                        }
                    }
                }
            }
            .distinctUntilChanged()

    /**
     * Whether we're animating, or intend to animate, the surface behind the keyguard via remote
     * animation. This is used to keep the RemoteAnimationTarget alive until we're done using it.
     */
    val usingKeyguardGoingAwayAnimation: Flow<Boolean> =
        if (SceneContainerFlag.isEnabled) {
            combine(
                    sceneInteractor.get().transitionState,
                    surfaceBehindInteractor.isAnimatingSurface,
                    notificationLaunchAnimationInteractor.isLaunchAnimationRunning,
                ) { transition, isAnimatingSurface, isLaunchAnimationRunning ->
                    // Using the animation if we're animating it directly, or if the
                    // ActivityLaunchAnimator is in the process of animating it.
                    val isAnyAnimationRunning = isAnimatingSurface || isLaunchAnimationRunning
                    // We may still be animating the surface after the keyguard is fully GONE, since
                    // some animations (like the translation spring) are not tied directly to the
                    // transition step amount.
                    transition.isTransitioning(to = Scenes.Gone) ||
                        (isAnyAnimationRunning &&
                            (transition.isIdle(Scenes.Gone) ||
                                transition.isTransitioning(from = Scenes.Gone)))
                }
                .distinctUntilChanged()
        } else {
            combine(
                    transitionInteractor.isInTransition(
                        edge = Edge.create(to = Scenes.Gone),
                        edgeWithoutSceneContainer = Edge.create(to = KeyguardState.GONE),
                    ),
                    transitionInteractor.isFinishedIn(
                        content = Scenes.Gone,
                        stateWithoutSceneContainer = KeyguardState.GONE,
                    ),
                    surfaceBehindInteractor.isAnimatingSurface,
                    notificationLaunchAnimationInteractor.isLaunchAnimationRunning,
                ) { isInTransitionToGone, isOnGone, isAnimatingSurface, notifLaunchRunning ->
                    // Using the animation if we're animating it directly, or if the
                    // ActivityLaunchAnimator is in the process of animating it.
                    val animationsRunning = isAnimatingSurface || notifLaunchRunning
                    // We may still be animating the surface after the keyguard is fully GONE, since
                    // some animations (like the translation spring) are not tied directly to the
                    // transition step amount.
                    isInTransitionToGone || (isOnGone && animationsRunning)
                }
                .distinctUntilChanged()
        }

    /**
     * WM lockscreen visibility during Gone -> Lockscreen.
     *
     * Lockscreen only needs to remain non-visible (unlocked app content visible) in order to play
     * the screen off animation. If we become awake during the transition, that means the animation
     * was cancelled and we should hide the unlocked content so it's not visible behind the
     * lockscreen during Gone -> Lockscreen.
     *
     * This also covers the Lockdown case, where Gone -> Lockscreen starts while awake. There is no
     * lockdown animation, so we should immediately hide the unlocked content.
     *
     * We also need Lockscreen to remain non-visible at all times if the unlocked power button
     * gesture is triggered, since we'll be returning to Gone as if we never tried to lock in the
     * first place.
     */
    private val goneToLockscreenLsVisibility =
        powerInteractor.detailedWakefulness
            .distinctUntilChangedBy { it.isAwake() }
            .map {
                if (it.powerButtonLaunchGestureTriggered) {
                    false to "ChangeScene Gone -> LS, power button launch"
                } else {
                    it.isAwake() to "ChangeScene Gone -> LS, awake w/ no power button launch"
                }
            }

    private val lockscreenVisibilityWithScenes: Flow<Pair<Boolean, String>> =
        deviceProvisioningInteractor.get().isDeviceProvisioned.flatMapLatestConflated {
            isProvisioned ->
            if (isProvisioned) {
                combine(
                        sceneInteractor.get().transitionState.flatMapLatestConflated {
                            when (it) {
                                is Idle ->
                                    when {
                                        // If idle on one of the keyguard scenes, report that the
                                        // keyguard is visible.
                                        it.currentScene in keyguardScenes ->
                                            flowOf(true to "Idle on keyguard scene")
                                        // If showing the bouncer overlay, report that the keyguard
                                        // is visible.
                                        it.currentOverlays.contains(Overlays.Bouncer) ->
                                            flowOf(true to "Idle w/ bouncer showing")
                                        // If showing a shade scene, report that the keyguard is
                                        // visible if the device hasn't been entered yet.
                                        it.currentScene in shadeScenes ->
                                            isDeviceNotEnteredDirectly.map {
                                                it to "Idle in shade and deviceNotEntered=$it"
                                            }
                                        // If idle on any other scene, report that the keyguard is
                                        // not visible.
                                        else -> flowOf(false to "Idle on non-keyguard scene")
                                    }

                                is Transition.ChangeScene ->
                                    when {
                                        // If transitioning between keyguard and another scene, keep
                                        // lockscreen visible until the transition ends.
                                        it.fromScene in keyguardScenes ->
                                            flowOf(true to "ChangeScene *from* keyguard scene")
                                        // If transitioning between two non-keyguard scenes but the
                                        // bouncer overlay is showing, report that the keyguard is
                                        // visible.
                                        it.currentOverlays.contains(Overlays.Bouncer) ->
                                            flowOf(true to "ChangeScene while bouncer showing")
                                        // If we're transitioning to Lockscreen from Gone, special
                                        // cases apply.
                                        it.fromScene == Scenes.Gone &&
                                            it.toScene == Scenes.Lockscreen ->
                                            goneToLockscreenLsVisibility
                                        // Otherwise, default to showing the lockscreen if the
                                        // device is not yet entered, or leaving it not showing if
                                        // the device was entered. This covers two requirements:
                                        // - For LS -> Gone and vice versa, lockscreen visibility
                                        //   state needs to not change until the end of the
                                        //   transition, so that animations can play on the LS UI
                                        //   elements (or over the unlocked app content).
                                        // - For transitions such as Shade -> LS, which can occur
                                        //   both while locked and unlocked, the lockscreen
                                        //   visibility should simply not change.
                                        else ->
                                            isDeviceNotEnteredDirectly.map {
                                                it to "ChangeScene default, deviceNotEntered=$it"
                                            }
                                    }

                                is Transition.OverlayTransition ->
                                    when {
                                        // If showing, hiding, or replacing an overlay and the
                                        // current scene under those overlays is one of the keyguard
                                        // scenes, report that the keyguard is showing.
                                        it.currentScene in keyguardScenes ->
                                            flowOf(true to "keyguard underneath overlays")
                                        // While animating away the bouncer overlay, report that the
                                        // keyguard is still being shown.
                                        it.fromContent == Overlays.Bouncer ->
                                            flowOf(true to "animating away bouncer")
                                        // While animating in the bouncer overlay, report that the
                                        // keyguard is still being shown.
                                        it.toContent == Overlays.Bouncer ->
                                            flowOf(true to "animating in bouncer")
                                        // In all other cases, report that the keyguard is visible
                                        // while the bouncer overlay is visible.
                                        else ->
                                            it.currentOverlays.map { currentOverlays ->
                                                currentOverlays.contains(Overlays.Bouncer) to
                                                    "bouncer visible"
                                            }
                                    }
                            }
                        },
                        wakeToGoneInteractor.canWakeDirectlyToGone,
                        ::Pair,
                    )
                    .map { (lockscreenVisibilityByTransitionState, canWakeDirectlyToGone) ->
                        if (canWakeDirectlyToGone) {
                            false to "can wake directly to gone, LS must never show"
                        } else {
                            lockscreenVisibilityByTransitionState
                        }
                    }
            } else {
                // Lockscreen is never visible when the device isn't provisioned.
                flowOf(false to "device is not provisioned")
            }
        }

    private val lockscreenVisibilityLegacy =
        combine(
                transitionInteractor.currentKeyguardState,
                transitionInteractor.startedStepWithPrecedingStep,
                wakeToGoneInteractor.canWakeDirectlyToGone,
                surfaceBehindVisibility,
                ::toQuad,
            )
            .map { (currentState, startedWithPrev, canWakeDirectlyToGone, surfaceBehindVis) ->
                val startedFromStep = startedWithPrev.previousValue
                val startedStep = startedWithPrev.newValue
                val returningToGoneAfterCancellation =
                    startedStep.to == KeyguardState.GONE &&
                        startedFromStep.transitionState == TransitionState.CANCELED &&
                        startedFromStep.from == KeyguardState.GONE

                val transitionInfo = transitionRepository.currentTransitionInfo
                val wakingDirectlyToGone =
                    deviceIsAsleepInState(transitionInfo.from) &&
                        transitionInfo.to == KeyguardState.GONE

                if (returningToGoneAfterCancellation || wakingDirectlyToGone) {
                    // GONE -> AOD/DOZING (cancel) -> GONE is the camera launch transition,
                    // which means we never want to show the lockscreen throughout the
                    // transition. Same for waking directly to gone, due to the lockscreen being
                    // disabled or because the device was woken back up before the lock timeout
                    // duration elapsed.
                    false to "returning to gone or waking directly to gone"
                } else if (canWakeDirectlyToGone) {
                    // Never show the lockscreen if we can wake directly to GONE. This means
                    // that the lock timeout has not yet elapsed, or the keyguard is disabled.
                    // In either case, we don't show the activity lock screen until one of those
                    // conditions changes.
                    false to "can wake directly to gone"
                } else if (
                    currentState == KeyguardState.DREAMING &&
                        if (SceneContainerFlag.isEnabled) {
                            deviceEntryInteractor.get().isUnlocked.value
                        } else {
                            keyguardInteractor.isKeyguardDismissible.value
                        }
                ) {
                    // Dreams dismiss keyguard and return to GONE if they can.
                    false to "dreaming and can return to gone"
                } else if (
                    startedWithPrev.newValue.from == KeyguardState.OCCLUDED &&
                        startedWithPrev.newValue.to == KeyguardState.GONE
                ) {
                    // OCCLUDED -> GONE directly, without transiting a *_BOUNCER state, occurs
                    // when an app uses intent flags to launch over an insecure keyguard without
                    // dismissing it, and then manually requests keyguard dismissal while
                    // OCCLUDED. This transition is not user-visible; the device unlocks in the
                    // background and the app remains on top, while we're now GONE. In this case
                    // we should simply tell WM that the lockscreen is no longer visible, and
                    // *not* play the going away animation or related animations.
                    false to "occluded -> gone directly"
                } else if (!surfaceBehindVis) {
                    // If the surface behind is not visible, then the lockscreen has to be visible
                    // since there's nothing to show. The surface behind will never be invisible if
                    // the lockscreen is disabled or suppressed.
                    true to "surface behind not visible"
                } else {
                    (currentState != KeyguardState.GONE) to "current state != gone"
                }
            }

    /**
     * Whether the lockscreen is visible, from the Window Manager (WM) perspective.
     *
     * Note: This may briefly be true even if the lockscreen UI has animated out (alpha = 0f), as we
     * only inform WM once we're done with the keyguard and we're fully GONE. Don't use this if you
     * want to know if the AOD/clock/notifs/etc. are visible.
     */
    val lockscreenVisibility: Flow<Pair<Boolean, String>> =
        if (SceneContainerFlag.isEnabled) {
                lockscreenVisibilityWithScenes
            } else {
                lockscreenVisibilityLegacy
            }
            .distinctUntilChangedBy { it.first }

    /**
     * Whether always-on-display (AOD) is visible when the lockscreen is visible, from window
     * manager's perspective.
     *
     * Note: This may be true even if AOD is not user-visible, such as when the light sensor
     * indicates the device is in the user's pocket. Don't use this if you want to know if the AOD
     * clock/smartspace/notif icons are visible.
     */
    val aodVisibility: Flow<Boolean> =
        transitionInteractor
            .transitionValue(KeyguardState.AOD)
            .map { it == 1f }
            .distinctUntilChanged()

    companion object {
        /**
         * Content that is part of the keyguard and are shown when the device is locked or when the
         * keyguard still needs to be dismissed.
         */
        val keyguardScenes: Set<SceneKey> =
            setOf(Scenes.Lockscreen, Scenes.Communal, Scenes.Dream, Scenes.Occluded)

        /**
         * Scenes that show "shade" like content and can show whether the device is entered or not.
         */
        private val shadeScenes: Set<SceneKey> = setOf(Scenes.Shade, Scenes.QuickSettings)
    }
}
