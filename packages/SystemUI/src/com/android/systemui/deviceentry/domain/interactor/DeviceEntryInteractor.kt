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

package com.android.systemui.deviceentry.domain.interactor

import com.android.compose.animation.scene.OverlayKey
import com.android.compose.animation.scene.SceneKey
import com.android.compose.animation.scene.TransitionKey
import com.android.compose.animation.scene.content.state.TransitionState
import com.android.internal.logging.UiEventLogger
import com.android.internal.policy.IKeyguardDismissCallback
import com.android.keyguard.logging.DeviceEntryLogger
import com.android.systemui.authentication.domain.interactor.AuthenticationInteractor
import com.android.systemui.authentication.shared.model.AuthenticationMethodModel
import com.android.systemui.bouncer.domain.interactor.AlternateBouncerInteractor
import com.android.systemui.bouncer.domain.interactor.SimBouncerInteractor
import com.android.systemui.bouncer.shared.logging.BouncerUiEvent
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.deviceentry.data.repository.DeviceEntryRepository
import com.android.systemui.deviceentry.shared.model.DeviceUnlockSource
import com.android.systemui.deviceentry.shared.model.DeviceUnlockStatus
import com.android.systemui.keyguard.DismissCallbackRegistry
import com.android.systemui.keyguard.domain.interactor.KeyguardDismissActionInteractor
import com.android.systemui.keyguard.domain.interactor.KeyguardEnabledInteractor
import com.android.systemui.keyguard.domain.interactor.KeyguardInteractor
import com.android.systemui.keyguard.shared.model.BiometricUnlockMode
import com.android.systemui.keyguard.shared.model.BiometricUnlockModel
import com.android.systemui.keyguard.shared.model.KeyguardTransitionKeys.AodToGoneTransition
import com.android.systemui.keyguard.shared.model.KeyguardTransitionKeys.WithAnimationOverLockscreen
import com.android.systemui.keyguard.shared.model.toDeviceUnlockSource
import com.android.systemui.log.table.TableLogBuffer
import com.android.systemui.log.table.logDiffsForTable
import com.android.systemui.scene.data.model.asIterable
import com.android.systemui.scene.data.model.peek
import com.android.systemui.scene.domain.SceneFrameworkTableLog
import com.android.systemui.scene.domain.interactor.SceneBackInteractor
import com.android.systemui.scene.domain.interactor.SceneInteractor
import com.android.systemui.scene.shared.model.Overlays
import com.android.systemui.scene.shared.model.SceneFamilies
import com.android.systemui.scene.shared.model.Scenes
import com.android.systemui.scene.shared.model.isKeyguardScene
import com.android.systemui.shade.domain.interactor.ShadeInteractor
import com.android.systemui.shade.domain.interactor.ShadeModeInteractor
import com.android.systemui.statusbar.SysuiStatusBarStateController
import com.android.systemui.utils.coroutines.flow.mapLatestConflated
import dagger.Lazy
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.dropWhile
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Hosts application business logic related to device entry.
 *
 * Device entry occurs when the user successfully dismisses (or bypasses) the lockscreen, regardless
 * of the authentication method used.
 */
@SysUISingleton
class DeviceEntryInteractor
@Inject
constructor(
    @Application private val applicationScope: CoroutineScope,
    private val repository: Lazy<DeviceEntryRepository>,
    private val authenticationInteractor: Lazy<AuthenticationInteractor>,
    private val sceneInteractor: Lazy<SceneInteractor>,
    private val deviceUnlockedInteractor: Lazy<DeviceUnlockedInteractor>,
    private val alternateBouncerInteractor: Lazy<AlternateBouncerInteractor>,
    private val dismissCallbackRegistry: Lazy<DismissCallbackRegistry>,
    private val sceneBackInteractor: Lazy<SceneBackInteractor>,
    @SceneFrameworkTableLog private val tableLogBuffer: Lazy<TableLogBuffer>,
    private val keyguardDismissActionInteractor: Lazy<KeyguardDismissActionInteractor>,
    private val keyguardEnabledInteractor: Lazy<KeyguardEnabledInteractor>,
    private val statusBarStateController: SysuiStatusBarStateController,
    private val uiEventLogger: UiEventLogger,
    private val keyguardInteractor: KeyguardInteractor,
    private val shadeInteractor: Lazy<ShadeInteractor>,
    private val shadeModeInteractor: ShadeModeInteractor,
    private val deviceEntryLogger: DeviceEntryLogger,
    private val simBouncerInteractor: SimBouncerInteractor,
) {
    /**
     * Whether the device is unlocked.
     *
     * A device that is not yet unlocked requires unlocking by completing an authentication
     * challenge according to the current authentication method, unless in cases when the current
     * authentication method is not "secure" (for example, None and Swipe); in such cases, the value
     * of this flow will always be `true`, even if the lockscreen is showing and still needs to be
     * dismissed by the user to proceed.
     */
    val isUnlocked: StateFlow<Boolean> by lazy {
        deviceUnlockedInteractor
            .get()
            .deviceUnlockStatus
            .map { it.isUnlocked }
            .stateIn(
                scope = applicationScope,
                started = SharingStarted.WhileSubscribed(),
                initialValue = deviceUnlockedInteractor.get().deviceUnlockStatus.value.isUnlocked,
            )
    }

    /**
     * Emits `true` when the current scene switches to [Scenes.Gone] for the first time after having
     * been on [Scenes.Lockscreen] or any other keyguard scenes.
     *
     * Different from [isDeviceEnteredOnBackStack] such that the current scene must actually go
     * through [Scenes.Gone] to produce a `true`. [isDeviceEnteredOnBackStack] takes into account
     * the navigation back stack and will produce a `true` value when the bottommost entry of the
     * navigation back stack switched from [Scenes.Lockscreen] to [Scenes.Gone] while the user is
     * staring at another scene.
     */
    val isDeviceEnteredDirectly: StateFlow<Boolean> by lazy {
        sceneInteractor
            .get()
            .currentScene
            .filter { currentScene ->
                currentScene == Scenes.Gone || currentScene.isKeyguardScene()
            }
            .mapLatestConflated { scene ->
                if (scene == Scenes.Gone) {
                    // Make sure device unlock status is definitely unlocked before
                    // considering the device "entered".
                    deviceUnlockedInteractor.get().deviceUnlockStatus.first { it.isUnlocked }
                    true
                } else {
                    false
                }
            }
            .stateIn(
                scope = applicationScope,
                started = SharingStarted.Eagerly,
                initialValue = false,
            )
    }

    /**
     * Emits `true` when the bottom of the navigation back stack switches to [Scenes.Gone], and
     * `false` when it switches to [Scenes.Lockscreen].
     *
     * Different from [isDeviceEnteredDirectly] such that the current scene may not change while the
     * device becomes "entered" or "not entered" underneath. E.g. shade is open while device gets
     * unlocked underneath.
     */
    private val isDeviceEnteredOnBackStack: StateFlow<Boolean> by lazy {
        sceneBackInteractor
            .get()
            .backStack
            // The bottom of the back stack, which is Lockscreen, Gone, or null if empty.
            .map { it.asIterable().lastOrNull() }
            // Filter out cases where the stack changes but the bottom remains unchanged.
            .distinctUntilChanged()
            // Device is entered when the bottom of the back stack is Gone, and not entered when it
            // is Lockscreen or null.
            .map { bottomScene -> bottomScene == Scenes.Gone }
            .stateIn(
                scope = applicationScope,
                started = SharingStarted.Eagerly,
                initialValue = false,
            )
    }

    /**
     * Whether the device has been entered (i.e. the lockscreen has been dismissed, by any method).
     * This can be `false` when the device is unlocked, e.g. when the user still needs to swipe away
     * the non-secure lockscreen, even though they've already authenticated.
     *
     * Note: This does not imply that the lockscreen is visible or not.
     */
    val isDeviceEntered: StateFlow<Boolean> by lazy {
        combine(
                // This flow emits true when the currentScene switches to Gone for the first time
                // after having been on Lockscreen.
                isDeviceEnteredDirectly,
                // This flow emits true when the bottom of the navigation back stack switches to
                // Gone, and false when it switches to Lockscreen.
                isDeviceEnteredOnBackStack,
            ) { enteredDirectly, enteredOnBackStack ->
                enteredOnBackStack || enteredDirectly
            }
            .logDiffsForTable(
                tableLogBuffer = tableLogBuffer.get(),
                columnName = "isDeviceEntered",
                initialValue = false,
            )
            .stateIn(
                scope = applicationScope,
                started = SharingStarted.Eagerly,
                initialValue = false,
            )
    }

    /**
     * Whether it's currently possible to swipe up to enter the device without requiring
     * authentication or when the device is already authenticated using a passive authentication
     * mechanism like face or trust manager. This returns `false` whenever the lockscreen has been
     * dismissed.
     *
     * A value of `null` is meaningless and is used as placeholder while the actual value is still
     * being loaded in the background.
     *
     * Note: `true` doesn't mean the lockscreen is visible. It may be occluded or covered by other
     * UI.
     */
    val canSwipeToEnter: StateFlow<Boolean?> by lazy {
        combine(
                authenticationInteractor.get().authenticationMethod.map {
                    it == AuthenticationMethodModel.None
                },
                keyguardEnabledInteractor.get().isKeyguardEnabled,
                deviceUnlockedInteractor.get().deviceUnlockStatus,
                isDeviceEntered,
            ) { isNoneAuthMethod, isKeyguardEnabled, deviceUnlockStatus, isDeviceEntered ->
                val isSwipeAuthMethod = isNoneAuthMethod && isKeyguardEnabled
                (isSwipeAuthMethod ||
                    (deviceUnlockStatus.isUnlocked &&
                        deviceUnlockStatus.deviceUnlockSource?.dismissesLockscreen == false)) &&
                    !isDeviceEntered
            }
            .logDiffsForTable(
                tableLogBuffer = tableLogBuffer.get(),
                columnName = "canSwipeToEnter",
                initialValue = false,
            )
            .stateIn(
                scope = applicationScope,
                started = SharingStarted.Eagerly,
                // Starts as null to prevent downstream collectors from falsely assuming that the
                // user can or cannot swipe to enter the device while the real value is being loaded
                // from upstream data sources.
                initialValue = null,
            )
    }

    /**
     * Attempt to enter the device and dismiss the lockscreen. If authentication is required to
     * unlock the device it will transition to bouncer.
     *
     * @param callback An optional callback to invoke when the attempt succeeds, fails, or is
     *   canceled
     * @param skipShowingAlternateBouncer an optional setting - if true, will skip showing the
     *   alternate bouncer even if it could show. Instead, it will directly show the primary bouncer
     *   if authentication is required.
     */
    @JvmOverloads
    fun attemptDeviceEntry(
        loggingReason: String,
        callback: IKeyguardDismissCallback? = null,
        skipShowingAlternateBouncer: Boolean = false,
    ) {
        callback?.let { dismissCallbackRegistry.get().addCallback(it) }

        // Check if the device is already authenticated by trust agent/passive biometrics.
        // If authentication is required:
        //     Show SPFS/UDFPS bouncer if it is available AlternateBouncerInteractor.show, else
        //     transition to bouncer scene
        // If authentication is not required:
        //     Determine whether there is a need to transition to Gone, Shade, or to stay on the
        //     current Scene and remove the lockscreen from the backstack
        applicationScope.launch {
            if (isAuthenticationRequired()) {
                if (
                    !skipShowingAlternateBouncer &&
                        alternateBouncerInteractor.get().canShowAlternateBouncer.value
                ) {
                    alternateBouncerInteractor.get().forceShow()
                } else if (
                    !(sceneInteractor.get().currentScene.value == Scenes.Occluded &&
                        simBouncerInteractor.isAnySimSecure.value)
                ) {

                    // When an occluding activity like emergency call is launched when sim locked,
                    // do not show the bouncer. Otherwise always request on an attempted device
                    // entry
                    sceneInteractor
                        .get()
                        .showOverlay(
                            overlay = Overlays.Bouncer,
                            loggingReason =
                                "request to unlock device while authentication" +
                                    " required, original reason for request: $loggingReason",
                        )
                }
            } else {
                val willAnimateToGone =
                    keyguardDismissActionInteractor.get().willAnimateDismissActionOnLockscreen.value
                val currentScene = sceneInteractor.get().currentScene.value
                if (
                    !willAnimateToGone &&
                        (currentScene == Scenes.Shade || currentScene == Scenes.QuickSettings) &&
                        sceneBackInteractor.get().backStack.value.peek() != null
                ) {
                    // If the device doesn't need to animate to Gone, the current scene is Shade or
                    // QS, and the back stack is not empty, replacing the Lockscreen scene at
                    // the bottom of the stack triggers device entry without dismissing the Shade.
                    attemptToEnterDeviceByReplacingLockscreenOnBackStack(
                        reason =
                            "unlocking while on shade or qs, and animation isn't needed," +
                                " original reason for request: $loggingReason"
                    )
                } else if (statusBarStateController.leaveOpenOnKeyguardHide()) {
                    enterDeviceAndShowShade(
                        loggingReason =
                            "request to unlock and transition to shade (leaveOpenOnKeyguardHide)" +
                                " while authentication isn't required, original reason for" +
                                " request: $loggingReason",
                        willAnimateDismissActionOnLockscreen = willAnimateToGone,
                        isDualShade = shadeModeInteractor.isDualShade,
                        currentOverlays = sceneInteractor.get().currentOverlays.value,
                        targetScene = sceneInteractor.get().currentScene.value,
                        isAnyShadeFullyExpanded = shadeInteractor.get().isAnyFullyExpanded.value,
                    )
                } else {
                    sceneInteractor
                        .get()
                        .changeScene(
                            toScene = Scenes.Gone,
                            transitionKey =
                                WithAnimationOverLockscreen.takeIf { willAnimateToGone },
                            loggingReason =
                                "request to unlock device while authentication isn't " +
                                    "required, original reason for request: $loggingReason",
                        )
                }
            }
        }
    }

    /**
     * Returns `true` if the device currently requires authentication before entry is granted;
     * `false` if the device can be entered without authenticating first.
     */
    fun isAuthenticationRequired(): Boolean {
        return !deviceUnlockedInteractor.get().deviceUnlockStatus.value.isUnlocked &&
            authenticationInteractor.get().authenticationMethod.value.isSecure
    }

    /** Locks the device instantly. */
    fun lockNow(debuggingReason: String) {
        deviceUnlockedInteractor.get().lockNow(debuggingReason)

        applicationScope.launch {
            // The device unlock interactor can't lock the device if the device has SWIPE as screen
            // lock, so we need to manually transition to lockscreen in this case.
            if (
                keyguardEnabledInteractor.get().isKeyguardEnabledAndNotSuppressed() &&
                    !isAuthenticationRequired()
            ) {
                sceneInteractor.get().resolveSceneFamilyOrNull(SceneFamilies.Home)?.value?.let {
                    resolvedScene ->
                    // If the resolved scene is Gone, we should always show the lockscreen.
                    val toScene = resolvedScene.takeIf { it != Scenes.Gone } ?: Scenes.Lockscreen
                    if (toScene != Scenes.Lockscreen) {
                        // We should never be in a state where the current scene is the
                        // [Scenes.Lockscreen] and the lockscreen is also on the back stack.
                        sceneBackInteractor
                            .get()
                            .addLockscreenToBackStack(
                                reason = "lock requested when authentication is not required"
                            )
                    }
                    sceneInteractor
                        .get()
                        .changeScene(
                            toScene = toScene,
                            loggingReason =
                                "lock now with SWIPE auth method, reason: $debuggingReason",
                        )
                }
            }
        }
    }

    /**
     * Handles scene transitions when the device is already in a stale biometric unlocked state from
     * a previous device entry. If the device wasn't already in a biometric-unlocked state that's
     * being re-triggered to enter the device, [handleDeviceUnlockStatusChange] will handle the
     * device entry.
     */
    fun handleDeviceEntryFromBiometricWhenAlreadyUnlocked() {
        applicationScope.launch {
            deviceUnlockedInteractor
                .get()
                .deviceUnlockStatus
                .flatMapLatest { unlockStatus ->
                    // If we unlocked:
                    if (unlockStatus.isUnlocked) {
                        // Wait until the biometric unlock state source is null
                        keyguardInteractor.biometricUnlockState
                            .dropWhile { it.source != null }
                            .map { unlockStatus to it }
                    } else {
                        emptyFlow()
                    }
                }
                .filter { (unlockStatus, biometricState) ->
                    // When a subsequent unlock from a biometric occurs that should
                    // dismiss the keyguard while unlockStatus's source is already the
                    // same source
                    BiometricUnlockMode.dismissesKeyguard(biometricState.mode) &&
                        unlockStatus.deviceUnlockSource != null &&
                        isSameUnlockSource(biometricState, unlockStatus.deviceUnlockSource)
                }
                .collect { (unlockStatus, _) ->
                    // THEN enter the device
                    val state = captureCurrentState(unlockStatus)
                    deviceEntryLogger.d("handleDeviceEntryFromBiometricWhenAlreadyUnlocked $state")
                    handleDeviceEntry(state)
                }
        }
    }

    private fun isSameUnlockSource(
        biometricUnlockModel: BiometricUnlockModel,
        deviceUnlockSource: DeviceUnlockSource,
    ): Boolean {
        return biometricUnlockModel.toDeviceUnlockSource() == deviceUnlockSource
    }

    /**
     * Handles scene transitions whenever the unlocked state changes. On unlock state changes:
     * 1. Captures the latest [DeviceEntryState]
     * 2. Handles scene, overlay and backstack based on the latest [DeviceEntryState]
     */
    fun handleDeviceUnlockStatusChange() {
        applicationScope.launch {
            deviceUnlockedInteractor.get().deviceUnlockStatus.collect { unlockStatus ->
                // Capture the latest DeviceEntryState
                val state = captureCurrentState(unlockStatus)

                deviceEntryLogger.d("handleDeviceUnlockStatusChange $state")

                // Handle scene, overlay, and backstack changes based on latest DeviceEntryState
                if (state.unlockStatus.isUnlocked) {
                    // UNLOCKED
                    handleDeviceEntry(state)
                } else {
                    // LOCKED
                    if (
                        state.renderedScenes.any { it.isKeyguardScene() } ||
                            state.isOnPrimaryBouncer
                    ) {
                        sceneBackInteractor
                            .get()
                            .replaceGoneSceneOnBackStack("locked on scenes=${state.renderedScenes}")
                    } else {
                        switchToScene(
                            Scenes.Lockscreen,
                            "locked on scenes=${state.renderedScenes} & not on primary bouncer",
                        )
                    }
                }
            }
        }
    }

    private fun handleDeviceEntry(state: DeviceEntryState) {
        when {
            state.isAlternateBouncerVisible -> handleUnlockOnAlternateBouncer(state)
            state.isOnPrimaryBouncer -> handleUnlockOnPrimaryBouncer(state)
            state.isOnCommunalOrLockscreen -> handleUnlockOnLockscreenOrCommunal(state)
            state.isOnSingleOrSplitShade -> handleUnlockOnShadeScene(state)
            else -> attemptToEnterDeviceByReplacingLockscreenOnBackStack("unlocked on other scene")
        }
    }

    fun handleDeviceEntryMetricsLogging() {
        applicationScope.launch {
            isDeviceEntered
                .filter { it }
                .collect {
                    if (
                        deviceUnlockedInteractor
                            .get()
                            .deviceUnlockStatus
                            .value
                            .deviceUnlockSource == DeviceUnlockSource.TrustAgent
                    ) {
                        uiEventLogger.log(BouncerUiEvent.BOUNCER_DISMISS_EXTENDED_ACCESS)
                    }
                }
        }
    }

    private fun handleUnlockOnShadeScene(state: DeviceEntryState) {
        if (state.unlockStatus.deviceUnlockSource is DeviceUnlockSource.Fingerprint) {
            // This represents the case when the fingerprint will cause the
            // device to enter while the shade is expanded.
            switchToScene(
                targetSceneKey = Scenes.Gone,
                loggingReason = "unlocked on shade from fingerprint",
            )
        } else {
            // Remain in the shade but replace the Lockscreen scene from
            // the bottom of the navigation with the Gone scene since the
            // device is unlocked.
            attemptToEnterDeviceByReplacingLockscreenOnBackStack(reason = "unlocked on shade")
        }
    }

    private fun handleUnlockOnLockscreenOrCommunal(state: DeviceEntryState) {
        // The lockscreen should be dismissed automatically when:
        // 1. Face auth bypass is enabled and authentication happens while
        //    the user is on the lockscreen.
        // 2. The user authenticates using an active authentication
        //    mechanism like fingerprint auth.
        if (state.unlockStatus.deviceUnlockSource?.dismissesLockscreen == true) {
            switchToScene(
                targetSceneKey = Scenes.Gone,
                transitionKey =
                    when {
                        state.willAnimateDismissActionOnLockscreen -> WithAnimationOverLockscreen
                        BiometricUnlockMode.isWakeAndDismiss(state.biometricUnlockMode) ->
                            AodToGoneTransition
                        else -> null
                    },
                loggingReason =
                    "unlocked on lockscreen using an active authentication mechanism: " +
                        "${state.unlockStatus.deviceUnlockSource}",
            )
        }
    }

    private fun handleUnlockOnPrimaryBouncer(state: DeviceEntryState) {
        // When the device becomes unlocked in primary bouncer, transition to
        // Gone, Shade, or remain in the current scene.
        // If the transition is a scene change, take the targetScene or Shade.
        val targetScene = state.renderedScenes.last()
        val enterDeviceAndShowShade = state.shouldShowShadeAfterEntry
        val loggingReason =
            buildLoggingReasonString(
                "primary bouncer",
                state.shouldShowShadeAfterEntry,
                state.willAnimateDismissActionOnLockscreen,
            )

        if (enterDeviceAndShowShade) {
            enterDeviceAndShowShade(loggingReason = loggingReason, state = state)
        } else if (
            targetScene == Scenes.Lockscreen ||
                targetScene == Scenes.Communal ||
                targetScene == Scenes.QuickSettings ||
                targetScene == Scenes.Shade
        ) {
            if (
                state.willAnimateDismissActionOnLockscreen ||
                    // If the device is switching to Gone mid-transition from
                    // Ls -> Bouncer, animate the scene change to
                    // avoid a jump-cut from partially visible LS/Bouncer to
                    // Gone.
                    state.isTransitioningFromLsToBouncer
            ) {
                // Do not snap to scene here or this will break the notification
                // animation on lockscreen, but instantly hide the bouncer to prevent any
                // unwanted overlap.
                sceneInteractor
                    .get()
                    .instantlyHideOverlay(Overlays.Bouncer, "Instant hide bouncer for animation")
                switchToScene(
                    targetSceneKey = Scenes.Gone,
                    transitionKey =
                        WithAnimationOverLockscreen.takeIf {
                            state.willAnimateDismissActionOnLockscreen
                        },
                    loggingReason = loggingReason,
                    instantlySnapScenes = false,
                )
            } else {
                // Snap to scene to avoid any flicker of the current scene.
                // The scene transition needs to happen before the overlay is hidden.
                sceneInteractor
                    .get()
                    .snapToScene(
                        toScene = Scenes.Gone,
                        loggingReason = loggingReason,
                        hideOverlays = SceneInteractor.HideOverlayCommand.HideNone,
                    )
                sceneInteractor.get().hideOverlay(Overlays.Bouncer, loggingReason)
            }
        } else {
            attemptToEnterDeviceByReplacingLockscreenOnBackStack(
                reason = "unlocked on primary bouncer"
            )
            switchToScene(
                targetSceneKey = targetScene,
                loggingReason =
                    "unlocked on primary bouncer from sceneKey=${targetScene.debugName}",
                instantlySnapScenes = true,
            )
        }
    }

    private fun handleUnlockOnAlternateBouncer(state: DeviceEntryState) {
        // When the device becomes unlocked on the alternate bouncer, always immediately hide the
        // alternate bouncer
        alternateBouncerInteractor.get().hide()

        // ... and go to Shade, Gone or stay on the current scene
        if (state.shouldShowShadeAfterEntry) {
            enterDeviceAndShowShade(
                loggingReason =
                    buildLoggingReasonString(
                        currentState = "alternate bouncer",
                        showShade = true,
                        willAnimateDismissAction = state.willAnimateDismissActionOnLockscreen,
                    ),
                state = state,
            )
        } else if (state.isOnCommunalOrLockscreen || state.isOnSingleOrSplitShade) {
            switchToScene(
                targetSceneKey = Scenes.Gone,
                transitionKey =
                    WithAnimationOverLockscreen.takeIf {
                        state.willAnimateDismissActionOnLockscreen
                    },
                loggingReason =
                    buildLoggingReasonString(
                        currentState = "alternate bouncer over communal, lockscreen or shade scene",
                        showShade = false,
                        willAnimateDismissAction = state.willAnimateDismissActionOnLockscreen,
                    ),
            )
        } else {
            // Remain on the current scene
            attemptToEnterDeviceByReplacingLockscreenOnBackStack(
                reason =
                    buildLoggingReasonString(
                        currentState =
                            "alternate bouncer over other scene " + "!(communal or lockscreen)",
                        showShade = false,
                        willAnimateDismissAction = state.willAnimateDismissActionOnLockscreen,
                    )
            )
        }
    }

    private fun captureCurrentState(deviceUnlockStatus: DeviceUnlockStatus): DeviceEntryState {
        val transitionState = sceneInteractor.get().transitionState
        val (renderedScenes, renderedOverlays) = getRenderedContent(transitionState)

        return DeviceEntryState(
            unlockStatus = deviceUnlockStatus,
            renderedScenes = renderedScenes,
            renderedOverlays = renderedOverlays,
            isAlternateBouncerVisible = alternateBouncerInteractor.get().isVisibleState(),
            shouldShowShadeAfterEntry = statusBarStateController.leaveOpenOnKeyguardHide(),
            willAnimateDismissActionOnLockscreen =
                keyguardDismissActionInteractor.get().willAnimateDismissActionOnLockscreen.value,
            isDualShade = shadeModeInteractor.isDualShade,
            isTransitioningFromLsToBouncer =
                transitionState.isTransitioning(from = Scenes.Lockscreen, to = Overlays.Bouncer),
            biometricUnlockMode = keyguardInteractor.biometricUnlockState.value.mode,
            isAnyShadeFullyExpanded = shadeInteractor.get().isAnyFullyExpanded.value,
        )
    }

    private fun enterDeviceAndShowShade(loggingReason: String, state: DeviceEntryState) {
        enterDeviceAndShowShade(
            loggingReason = loggingReason,
            willAnimateDismissActionOnLockscreen = state.willAnimateDismissActionOnLockscreen,
            isDualShade = state.isDualShade,
            currentOverlays = state.renderedOverlays,
            targetScene = state.renderedScenes.last(),
            isAnyShadeFullyExpanded = state.isAnyShadeFullyExpanded,
        )
    }

    /**
     * Performs scene, overlay and backstack changes to enter the device. On device entry, this
     * method ensures the shade will be showing if it isn't already.
     */
    private fun enterDeviceAndShowShade(
        loggingReason: String,
        willAnimateDismissActionOnLockscreen: Boolean,
        isDualShade: Boolean,
        currentOverlays: Set<OverlayKey>,
        targetScene: SceneKey?, // currentScene or scene currently being transitioned to
        isAnyShadeFullyExpanded: Boolean,
    ) {
        val isPrimaryBouncerShowing = Overlays.Bouncer in currentOverlays

        // Once the device is entered, will the shade be visible without intervention?
        val shadeWillShowOnDeviceEntry =
            if (isPrimaryBouncerShowing) {
                if (isDualShade) {
                    Overlays.QuickSettingsShade in currentOverlays ||
                        Overlays.NotificationsShade in currentOverlays
                } else {
                    targetScene == Scenes.Shade || targetScene == Scenes.QuickSettings
                }
            } else {
                isAnyShadeFullyExpanded
            }

        if (isDualShade) {
            // For DualShade:
            //     Shade is an Overlay.
            //     To enter the device, switch the scene to Gone.
            if (isPrimaryBouncerShowing) {
                sceneInteractor.get().hideOverlay(Overlays.Bouncer, loggingReason)
            }
            if (!shadeWillShowOnDeviceEntry) {
                // Assume if the shade isn't already showing, the request is to show
                // the NotificationsShade (not QS).
                shadeInteractor.get().expandNotificationsShade(loggingReason = loggingReason)
            }
            sceneInteractor
                .get()
                .changeScene(
                    toScene = Scenes.Gone,
                    loggingReason = loggingReason,
                    transitionKey =
                        WithAnimationOverLockscreen.takeIf { willAnimateDismissActionOnLockscreen },
                    hideOverlays = SceneInteractor.HideOverlayCommand.HideNone,
                )
        } else {
            // For SingleShade or SplitShade:
            //     Shade is a Scene.
            //     To enter the device, call replaceLockscreenSceneOnBackStack.
            if (shadeWillShowOnDeviceEntry) {
                attemptToEnterDeviceByReplacingLockscreenOnBackStack(reason = loggingReason)
                sceneInteractor.get().hideOverlay(Overlays.Bouncer, loggingReason)
            } else {
                // SceneContainerStartable#hydrateBackStack will replaceLockscreenSceneOnBackStack
                // once the scene changes to Shade. Doing this in hydrateBackStack's onSceneChange
                // will ensure the lockscreen is on the backstack when
                // replaceLockscreenSceneOnBackStack is called.
                sceneInteractor
                    .get()
                    .changeScene(
                        toScene = Scenes.Shade,
                        loggingReason = loggingReason,
                        transitionKey =
                            WithAnimationOverLockscreen.takeIf {
                                willAnimateDismissActionOnLockscreen
                            },
                        hideOverlays =
                            SceneInteractor.HideOverlayCommand
                                .HideAll, // hides bouncer overlay if showing
                    )
            }
        }
    }

    private fun buildLoggingReasonString(
        currentState: String,
        showShade: Boolean,
        willAnimateDismissAction: Boolean,
    ): String {
        return buildString {
            append("unlocked on $currentState")
            if (showShade) {
                append(" and shade needs to show")
            } else {
                append(" and shade doesn't need to show")
                if (willAnimateDismissAction) {
                    append(" and will animate dismiss action")
                } else {
                    append(" and will not animate dismiss action")
                }
            }
        }
    }

    private fun getRenderedContent(
        transitionState: TransitionState
    ): Pair<List<SceneKey>, Set<OverlayKey>> {
        return when (transitionState) {
            is TransitionState.Idle ->
                listOf(transitionState.currentScene) to transitionState.currentOverlays
            is TransitionState.Transition.ChangeScene ->
                listOf(transitionState.fromScene, transitionState.toScene) to
                    transitionState.currentOverlays
            is TransitionState.Transition.OverlayTransition ->
                listOf(transitionState.currentScene) to
                    setOfNotNull(
                        transitionState.toContent as? OverlayKey,
                        transitionState.fromContent as? OverlayKey,
                    )
        }
    }

    /**
     * Attempts to enter the device by replacing the lockscreen on the backStack with Scenes.Gone.
     * If the lockscreen is not on the backStack (for example, if Scenes.Lockscreen is the
     * currentScene), then this is a NoOp and [isDeviceEntered] won't change.
     */
    private fun attemptToEnterDeviceByReplacingLockscreenOnBackStack(reason: String) {
        sceneBackInteractor.get().replaceLockscreenSceneOnBackStack(reason = reason)
    }

    private fun switchToScene(
        targetSceneKey: SceneKey,
        loggingReason: String,
        transitionKey: TransitionKey? = null,
        instantlySnapScenes: Boolean = false,
    ) {
        if (instantlySnapScenes) {
            sceneInteractor
                .get()
                .snapToScene(toScene = targetSceneKey, loggingReason = loggingReason)
        } else {
            sceneInteractor
                .get()
                .changeScene(
                    toScene = targetSceneKey,
                    loggingReason = loggingReason,
                    transitionKey = transitionKey,
                )
        }
    }

    data class DeviceEntryState(
        val unlockStatus: DeviceUnlockStatus,
        val renderedScenes: List<SceneKey>,
        val renderedOverlays: Set<OverlayKey>,
        val isAlternateBouncerVisible: Boolean,
        val shouldShowShadeAfterEntry: Boolean, // leaveOpenOnKeyguardHide
        val willAnimateDismissActionOnLockscreen: Boolean,
        val isDualShade: Boolean,
        val isTransitioningFromLsToBouncer: Boolean,
        val biometricUnlockMode: BiometricUnlockMode,
        val isAnyShadeFullyExpanded: Boolean,
    )

    private val DeviceEntryState.isOnPrimaryBouncer: Boolean
        get() = Overlays.Bouncer in renderedOverlays

    private val DeviceEntryState.isOnLockscreen: Boolean
        get() = Scenes.Lockscreen in renderedScenes

    private val DeviceEntryState.isOnSingleOrSplitShade: Boolean
        get() = Scenes.Shade in renderedScenes || Scenes.QuickSettings in renderedScenes

    private val DeviceEntryState.isOnCommunal: Boolean
        get() = Scenes.Communal in renderedScenes

    private val DeviceEntryState.isOnCommunalOrLockscreen: Boolean
        get() = isOnCommunal || isOnLockscreen
}
