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

package com.android.systemui.scene.domain.interactor

import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import com.android.app.tracing.coroutines.flow.stateInTraced
import com.android.compose.animation.scene.ContentKey
import com.android.compose.animation.scene.ObservableTransitionState
import com.android.compose.animation.scene.OverlayKey
import com.android.compose.animation.scene.SceneKey
import com.android.compose.animation.scene.TransitionKey
import com.android.compose.animation.scene.UserAction
import com.android.compose.animation.scene.UserActionResult
import com.android.compose.animation.scene.content.state.TransitionState
import com.android.systemui.Flags.blackScreenOnSceneContainerStartFix
import com.android.systemui.authentication.domain.interactor.AuthenticationInteractor
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.deviceentry.domain.interactor.DeviceUnlockedInteractor
import com.android.systemui.deviceentry.domain.interactor.RestrictedModeInteractor
import com.android.systemui.keyguard.domain.interactor.KeyguardEnabledInteractor
import com.android.systemui.keyguard.domain.interactor.scenetransition.LockscreenSceneTransitionInteractor
import com.android.systemui.keyguard.shared.model.KeyguardState
import com.android.systemui.log.table.Diffable
import com.android.systemui.log.table.TableLogBuffer
import com.android.systemui.log.table.TableRowLogger
import com.android.systemui.scene.data.repository.SceneContainerRepository
import com.android.systemui.scene.domain.resolver.SceneResolver
import com.android.systemui.scene.shared.logger.SceneLogger
import com.android.systemui.scene.shared.model.Overlays
import com.android.systemui.scene.shared.model.SceneFamilies
import com.android.systemui.scene.shared.model.Scenes
import com.android.systemui.shade.domain.interactor.ShadeModeInteractor
import com.android.systemui.util.asIndenting
import com.android.systemui.util.kotlin.pairwise
import com.android.systemui.util.println
import dagger.Lazy
import java.io.PrintWriter
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * Generic business logic and app state accessors for the scene framework.
 *
 * Note that this class should not depend on state or logic of other modules or features. Instead,
 * other feature modules should depend on and call into this class when their parts of the
 * application state change.
 */
@SysUISingleton
@Stable
class SceneInteractor
@Inject
constructor(
    @Application private val applicationScope: CoroutineScope,
    private val repository: SceneContainerRepository,
    private val logger: SceneLogger,
    private val sceneFamilyResolvers: Lazy<Map<SceneKey, @JvmSuppressWildcards SceneResolver>>,
    private val deviceUnlockedInteractor: Lazy<DeviceUnlockedInteractor>,
    private val keyguardEnabledInteractor: Lazy<KeyguardEnabledInteractor>,
    private val restrictedModeInteractor: Lazy<RestrictedModeInteractor>,
    private val disabledContentInteractor: DisabledContentInteractor,
    private val shadeModeInteractor: ShadeModeInteractor,
    private val authenticationInteractor: Lazy<AuthenticationInteractor>,
    private val lockscreenSceneTransitionInteractor: Lazy<LockscreenSceneTransitionInteractor>,
) {

    /**
     * The keys of all scenes and overlays in the container.
     *
     * They will be sorted in z-order such that the last one is the one that should be rendered on
     * top of all previous ones.
     */
    val allContentKeys: List<ContentKey> = repository.allContentKeys

    /**
     * The current scene.
     *
     * Note that during a transition between scenes, more than one scene might be rendered but only
     * one is considered the committed/current scene.
     */
    val currentScene: StateFlow<SceneKey> = repository.currentScene

    val transitionState: TransitionState
        get() = repository.transitionState

    val currentSceneAsState: SceneKey
        get() = transitionState.currentScene

    /**
     * The current set of overlays to be shown (may be empty).
     *
     * Note that during a transition between overlays, a different set of overlays may be rendered -
     * but only the ones in this set are considered the current overlays.
     */
    @Deprecated(
        "Prefer the more performant non-Flow version.",
        ReplaceWith("transitionState.currentOverlays"),
    )
    val currentOverlays: StateFlow<Set<OverlayKey>> = repository.currentOverlays

    @Deprecated("Prefer the more performant non-Flow version.", ReplaceWith("transitionState"))
    val transitionStateFlow: StateFlow<ObservableTransitionState> =
        repository.transitionStateFlow
            .onEach { logger.logSceneTransition(it) }
            .stateInTraced(
                name = "transitionState",
                scope = applicationScope,
                started = SharingStarted.Eagerly,
                initialValue = repository.transitionStateFlow.value,
            )

    /**
     * The key of the content that the UI is currently transitioning to or `null` if there is no
     * active transition at the moment.
     *
     * This is a convenience wrapper around [transitionStateFlow], meant for flow-challenged
     * consumers like Java code.
     */
    val transitioningTo: StateFlow<ContentKey?> =
        transitionStateFlow
            .map { state ->
                when (state) {
                    is ObservableTransitionState.Idle -> null
                    is ObservableTransitionState.Transition -> state.toContent
                }
            }
            .stateInTraced(
                name = "transitioningTo",
                scope = applicationScope,
                started = SharingStarted.Eagerly,
                initialValue = null,
            )

    /**
     * Whether user input is ongoing for the current transition. For example, if the user is swiping
     * their finger to transition between scenes, this value will be true while their finger is on
     * the screen, then false for the rest of the transition.
     */
    val isTransitionUserInputOngoing: StateFlow<Boolean> =
        transitionStateFlow
            .flatMapLatest {
                when (it) {
                    is ObservableTransitionState.Transition -> it.isUserInputOngoing
                    is ObservableTransitionState.Idle -> flowOf(false)
                }
            }
            .stateInTraced(
                name = "isTransitionUserInputOngoing",
                scope = applicationScope,
                started = SharingStarted.Eagerly,
                initialValue = false,
            )

    private var previouslyLoggedIsVisible: IsVisibleWithLoggingReason? = null
    /**
     * Whether the scene container is visible.
     *
     * This is snapshot-state backed; it will have new values as appropriate, after any of its
     * upstream states has new values.
     *
     * One way to change the value here is to post an [event][Event] through [handleEvent]. There
     * are other ways for this state to have new values, one such way is by transitioning between
     * scenes and/or showing/hiding overlays or by calling other methods in this class like
     * [onTransitionAnimationStart], [onTransitionAnimationCancelled], or
     * [onTransitionAnimationEnd].
     *
     * See the implementation to discover the full list of upstream states that affect this value
     * and the methods that ends up mutating those states.
     */
    val isVisible: Boolean by derivedStateOf {
        val calculated = isVisibleWithLoggingReason()
        if (calculated != previouslyLoggedIsVisible) {
            if (calculated.value != previouslyLoggedIsVisible?.value) {
                logger.logVisibilityChange(
                    from = previouslyLoggedIsVisible?.value ?: true,
                    to = calculated.value,
                    reason = calculated.loggingReason,
                )
            } else {
                logger.logVisibilityRejection(
                    to = calculated.value,
                    reason = calculated.loggingReason,
                )
            }

            previouslyLoggedIsVisible = calculated
        }

        calculated.value
    }

    @Deprecated("Prefer the more performant, non-Flow version.", ReplaceWith("isVisible"))
    val isVisibleFlow: StateFlow<Boolean> =
        snapshotFlow { isVisible }
            .stateInTraced(
                name = "isVisible",
                scope = applicationScope,
                started = SharingStarted.WhileSubscribed(),
                initialValue = isVisible,
            )

    /** Whether there's an ongoing remotely-initiated user interaction. */
    val isRemoteUserInteractionOngoing: StateFlow<Boolean> =
        snapshotFlow { repository.isRemoteUserInputOngoing }
            .stateInTraced(
                name = "isRemoteUserInteractorOngoing",
                scope = applicationScope,
                started = SharingStarted.Eagerly,
                initialValue = repository.isRemoteUserInputOngoing,
            )

    /**
     * Whether there's an ongoing user interaction started in the scene container Compose hierarchy.
     */
    val isSceneContainerUserInputOngoing: StateFlow<Boolean> =
        snapshotFlow { repository.isSceneContainerUserInputOngoing }
            .stateInTraced(
                name = "isSceneContainerUserInputOngoing",
                scope = applicationScope,
                started = SharingStarted.Eagerly,
                initialValue = repository.isSceneContainerUserInputOngoing,
            )

    /**
     * The current content that has the highest z-order out of all currently shown scenes and
     * overlays.
     *
     * Note that during a transition between content, a different content may have the highest z-
     * order. Only the one provided by this flow is considered the current logical topmost content.
     */
    @Deprecated("Only to be used for compatibility with KeyguardTransitionFramework")
    val topmostContent: StateFlow<ContentKey> =
        combine(currentScene, currentOverlays, ::determineTopmostContent)
            .stateInTraced(
                name = "topmostContent",
                scope = applicationScope,
                started = SharingStarted.Eagerly,
                initialValue = determineTopmostContent(currentScene.value, currentOverlays.value),
            )

    /**
     * The amount of transition into or out of the given [content].
     *
     * The value will be `0` if not in this scene or `1` when fully in the given scene.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun transitionProgress(content: ContentKey): Flow<Float> {
        return transitionStateFlow.flatMapLatest { transition ->
            when (transition) {
                is ObservableTransitionState.Idle -> {
                    flowOf(
                        if (
                            transition.currentScene == content ||
                                content in transition.currentOverlays
                        ) {
                            1f
                        } else {
                            0f
                        }
                    )
                }
                is ObservableTransitionState.Transition -> {
                    when {
                        transition.toContent == content -> transition.progress
                        transition.fromContent == content -> transition.progress.map { 1f - it }
                        else -> flowOf(0f)
                    }
                }
            }
        }
    }

    /**
     * Requests a scene change to the given scene.
     *
     * The change is animated. Therefore, it will be some time before the UI will switch to the
     * desired scene. Once enough of the transition has occurred, the [currentScene] will become
     * [toScene] (unless the transition is canceled by user action or another call to this method).
     *
     * If [keyguardState] is provided, we'll notify KeyguardTransitionRepository to transition to
     * that state as part of this scene change.
     *
     * If [forceSettleToTargetScene] is `true` and the target scene is the same as the current
     * scene, any current transition will be canceled and an animation to the target scene will be
     * started.
     *
     * If [hideOverlays] is not [HideOverlayCommand.HideNone], any visible overlays (all or some)
     * will be hidden (with transition), even if the scene change is rejected.
     */
    @JvmOverloads
    fun changeScene(
        toScene: SceneKey,
        loggingReason: String,
        transitionKey: TransitionKey? = null,
        keyguardState: KeyguardState? = null,
        forceSettleToTargetScene: Boolean = false,
        hideOverlays: HideOverlayCommand = HideOverlayCommand.HideAll,
    ) {
        if (keyguardState != null) {
            lockscreenSceneTransitionInteractor.get().setNextLockscreenTargetState(keyguardState)
        }

        val currentSceneKey = currentSceneAsState
        val resolvedScene = sceneFamilyResolvers.get()[toScene]?.resolvedScene?.value ?: toScene

        if (resolvedScene == currentSceneKey && forceSettleToTargetScene) {
            logger.logSceneChangeCancellation(scene = resolvedScene, keyguardState = keyguardState)
            repository.freezeAndAnimateToCurrentState()
        }

        when (hideOverlays) {
            HideOverlayCommand.HideAll -> {
                transitionState.currentOverlays.forEach {
                    hideOverlay(it, "Hiding overlay ${it.debugName} due to scene change request")
                }
            }
            is HideOverlayCommand.HideSome -> {
                hideOverlays.overlays.forEach {
                    hideOverlay(it, "Hiding overlay ${it.debugName} due to scene change request")
                }
            }
            HideOverlayCommand.HideNone -> {}
        }
        restrictedModeInteractor.get().modifyOverlaysOnSceneChange(toScene)

        if (
            !validateSceneChange(
                from = currentSceneKey,
                to = resolvedScene,
                loggingReason = loggingReason,
            )
        ) {
            return
        }

        logger.logSceneChanged(
            from = currentSceneKey,
            to = resolvedScene,
            keyguardState = keyguardState,
            reason = loggingReason,
            isInstant = false,
        )

        repository.changeScene(resolvedScene, transitionKey)
    }

    /**
     * Snaps to [Scenes.Gone] and does not validate the scene change, since the unlocked power
     * gesture has the authority to unilaterally unlock the device. We don't want to wait for device
     * unlocked flows to emit, so we start the scene transition as we unlock.
     *
     * DO NOT USE THIS FOR ANY OTHER REASON. The unlocked power gesture is a special case since the
     * first power button tap can instantly lock the device, while the second tap re-unlocks it
     * without any actual authentication.
     */
    fun snapToGoneForUnlockedPowerLaunchGesture(
        loggingReason: String,
        hideOverlays: HideOverlayCommand = HideOverlayCommand.HideAll,
        keyguardState: KeyguardState? = null,
    ) {
        snapToScene(
            Scenes.Gone,
            loggingReason,
            hideOverlays,
            keyguardState,
            // Only allowed for unlocked power gesture! Do not emulate this!
            skipValidateSceneChange = true,
        )
    }

    /**
     * Requests a scene change to the given scene.
     *
     * The change is instantaneous and not animated; it will be observable in the next frame and
     * there will be no transition animation.
     *
     * If [keyguardState] is provided, we'll notify KeyguardTransitionRepository to transition to
     * that state as part of this scene change.
     *
     * Override the [hideOverlays] to avoid hiding any overlays or to specify which ones should be
     * hidden.
     */
    fun snapToScene(
        toScene: SceneKey,
        loggingReason: String,
        hideOverlays: HideOverlayCommand = HideOverlayCommand.HideAll,
        keyguardState: KeyguardState? = null,
    ) {
        snapToScene(
            toScene,
            loggingReason,
            hideOverlays,
            keyguardState,
            skipValidateSceneChange = false,
        )
    }

    private fun snapToScene(
        toScene: SceneKey,
        loggingReason: String,
        hideOverlays: HideOverlayCommand = HideOverlayCommand.HideAll,
        keyguardState: KeyguardState? = null,
        // Whether to skip validating the scene change. This should *always* be false unless we're
        // doing the unlocked power launch gesture, which is the only case where System UI can
        // unilaterally unlock the device and return to Gone despite the device being locked.
        skipValidateSceneChange: Boolean = false,
    ) {
        if (keyguardState != null) {
            lockscreenSceneTransitionInteractor.get().setNextLockscreenTargetState(keyguardState)
        }

        val currentSceneKey = currentSceneAsState
        val resolvedScene = sceneFamilyResolvers.get()[toScene]?.resolvedScene?.value ?: toScene
        if (
            !skipValidateSceneChange &&
                !validateSceneChange(
                    from = currentSceneKey,
                    to = resolvedScene,
                    loggingReason = loggingReason,
                )
        ) {
            when (hideOverlays) {
                HideOverlayCommand.HideAll ->
                    repository.instantlyTransitionTo(overlays = emptySet())
                HideOverlayCommand.HideNone -> Unit
                is HideOverlayCommand.HideSome -> {
                    repository.instantlyTransitionTo(
                        overlays = transitionState.currentOverlays - hideOverlays.overlays.toSet()
                    )
                }
            }
            return
        }

        logger.logSceneChanged(
            from = currentSceneKey,
            to = resolvedScene,
            keyguardState = keyguardState,
            reason =
                loggingReason +
                    if (skipValidateSceneChange) {
                        " - skipped validation"
                    } else {
                        ""
                    },
            isInstant = true,
        )

        val overlaysToSet =
            when (hideOverlays) {
                HideOverlayCommand.HideAll -> emptySet()
                HideOverlayCommand.HideNone -> null
                is HideOverlayCommand.HideSome ->
                    transitionState.currentOverlays - hideOverlays.overlays.toSet()
            }
        repository.instantlyTransitionTo(scene = resolvedScene, overlays = overlaysToSet)
    }

    /**
     * Request to show [overlay] so that it animates in from [currentScene] and ends up being
     * visible on screen.
     *
     * After this returns, this overlay will be included in [currentOverlays]. This does nothing if
     * [overlay] is already shown.
     *
     * @param overlay The overlay to be shown
     * @param loggingReason The reason why the transition is requested, for logging purposes
     * @param transitionKey The transition key for this animated transition
     */
    @JvmOverloads
    fun showOverlay(
        overlay: OverlayKey,
        loggingReason: String,
        transitionKey: TransitionKey? = null,
    ) {
        if (!validateOverlayChange(to = overlay, loggingReason = loggingReason)) {
            return
        }

        logger.logOverlayChangeRequested(to = overlay, reason = loggingReason)

        repository.showOverlay(overlay = overlay, transitionKey = transitionKey)
    }

    /**
     * Request to hide [overlay] so that it animates out to [currentScene] and ends up *not* being
     * visible on screen.
     *
     * After this returns, this overlay will not be included in [currentOverlays]. This does nothing
     * if [overlay] is already hidden.
     *
     * @param overlay The overlay to be hidden
     * @param loggingReason The reason why the transition is requested, for logging purposes
     * @param transitionKey The transition key for this animated transition
     */
    @JvmOverloads
    fun hideOverlay(
        overlay: OverlayKey,
        loggingReason: String,
        transitionKey: TransitionKey? = null,
    ) {
        if (!validateOverlayChange(from = overlay, loggingReason = loggingReason)) {
            return
        }

        logger.logOverlayChangeRequested(from = overlay, reason = loggingReason)

        repository.hideOverlay(overlay = overlay, transitionKey = transitionKey)
    }

    /**
     * Instantly shows [overlay].
     *
     * The change is instantaneous and not animated; it will be observable in the next frame and
     * there will be no transition animation.
     */
    fun instantlyShowOverlay(overlay: OverlayKey, loggingReason: String) {
        if (!validateOverlayChange(to = overlay, loggingReason = loggingReason)) {
            return
        }

        logger.logOverlayChangeRequested(to = overlay, reason = loggingReason)

        repository.instantlyTransitionTo(overlays = transitionState.currentOverlays + overlay)
    }

    /**
     * Instantly hides [overlay].
     *
     * The change is instantaneous and not animated; it will be observable in the next frame and
     * there will be no transition animation.
     */
    fun instantlyHideOverlay(overlay: OverlayKey, loggingReason: String) {
        if (!validateOverlayChange(from = overlay, loggingReason = loggingReason)) {
            return
        }

        logger.logOverlayChangeRequested(from = overlay, reason = loggingReason)

        repository.instantlyTransitionTo(overlays = transitionState.currentOverlays - overlay)
    }

    /**
     * Replace [from] by [to] so that [from] ends up not being visible on screen and [to] ends up
     * being visible.
     *
     * This throws if [from] is not currently shown or if [to] is already shown.
     *
     * @param from The overlay to be hidden, if any
     * @param to The overlay to be shown, if any
     * @param loggingReason The reason why the transition is requested, for logging purposes
     * @param transitionKey The transition key for this animated transition
     */
    @JvmOverloads
    fun replaceOverlay(
        from: OverlayKey,
        to: OverlayKey,
        loggingReason: String,
        transitionKey: TransitionKey? = null,
    ) {
        if (!validateOverlayChange(from = from, to = to, loggingReason = loggingReason)) {
            return
        }

        logger.logOverlayChangeRequested(from = from, to = to, reason = loggingReason)

        repository.replaceOverlay(from = from, to = to, transitionKey = transitionKey)
    }

    /**
     * Synchronously handles the given [event], updating snapshot-backed states as needed.
     *
     * Once this call returns, it's guaranteed that [isVisible] will have the correct value, given
     * the event.
     */
    fun handleEvent(event: Event) {
        logger.logEvent(event)

        // Apply the event to modify all the necessary states so the next read from isVisible
        // reflects the right value.
        updateStates(event)
    }

    fun dump(printWriter: PrintWriter) {
        with(printWriter.asIndenting()) {
            with(isVisibleWithLoggingReason()) {
                println("isVisible", "$value (reason=\"$loggingReason\")")
            }
            println("currentScene", transitionState.currentScene.debugName)
            println(
                "currentOverlays",
                transitionState.currentOverlays.joinToString { it.debugName },
            )
        }
    }

    /**
     * Takes the given [event] and updates the [repository][SceneContainerRepository] with the
     * necessary states that will be needed to calculate the visibility state later.
     */
    private fun updateStates(event: Event) {
        when (event) {
            is Event.RemoteUserInputStart -> {
                logger.logRemoteUserInputStarted(event.loggingReason)
                repository.isRemoteUserInputOngoing = true
            }

            Event.SceneContainerUserInputStart -> {
                logger.logUserInputFinished(transitionState)
                repository.isSceneContainerUserInputOngoing = true
            }

            Event.UserInputEnd -> {
                repository.isRemoteUserInputOngoing = false
                repository.isSceneContainerUserInputOngoing = false
            }

            Event.TransitionAnimationStart -> {
                (repository.activeTransitionAnimationCount++).also {
                    check(it < 10) {
                        "Number of active transition animations is too high. Something must be" +
                            " calling onTransitionAnimationStart too many times!"
                    }
                }
            }

            Event.TransitionAnimationEnd,
            Event.TransitionAnimationCancel -> {
                (repository.activeTransitionAnimationCount--).also {
                    check(it >= 0) {
                        "Number of active transition animations is negative. Something must be" +
                            " calling onTransitionAnimationEnd or onTransitionAnimationCancelled too" +
                            " many times!"
                    }
                }
            }

            is Event.DeviceProvisioningChange -> {
                repository.isDeviceProvisioned = event.isDeviceProvisioned
            }

            is Event.DeviceUnlockChange -> {
                repository.isDeviceUnlocked = event.isDeviceUnlocked
            }

            is Event.AlternateBouncerVisibilityChange -> {
                repository.isAlternateBouncerVisible = event.isVisible
            }

            is Event.HeadsUpNotificationVisibilityChange -> {
                repository.isHeadsUpVisible = event.isVisible
            }

            is Event.SurfaceBehindAnimationChange -> {
                repository.isSurfaceBehindAnimating = event.isAnimating
            }

            is Event.IdleSceneEnteredComposition -> {
                repository.composedIdleScene = event.scene
            }

            is Event.IdleSceneExitedComposition -> {
                if (repository.composedIdleScene == event.scene) {
                    repository.composedIdleScene = null
                }
            }
        }
    }

    private fun isVisibleWithLoggingReason(): IsVisibleWithLoggingReason {
        return when {
            !repository.isDeviceProvisioned && repository.isDeviceUnlocked ->
                IsVisibleWithLoggingReason(false, "device unlocked and not provisioned")

            transitionState.isTransitioningFromOrTo(Scenes.Communal) ||
                transitionState.isIdle(Scenes.Communal) ->
                IsVisibleWithLoggingReason(true, "on or transitioning to/from communal")

            repository.isSurfaceBehindAnimating ->
                IsVisibleWithLoggingReason(true, "animating surface behind")

            repository.isHeadsUpVisible -> IsVisibleWithLoggingReason(true, "showing a HUN")

            repository.isAlternateBouncerVisible ->
                IsVisibleWithLoggingReason(true, "showing alternate bouncer")

            repository.isRemoteUserInputOngoing ->
                IsVisibleWithLoggingReason(true, "remote user input ongoing")

            repository.activeTransitionAnimationCount > 0 ->
                IsVisibleWithLoggingReason(true, "active transition animation")

            else ->
                when (transitionState) {
                    is TransitionState.Idle ->
                        when {
                            hasNonTransparentContent(transitionState.currentScene) ->
                                IsVisibleWithLoggingReason(
                                    true,
                                    "scene has non-transparent content",
                                )

                            transitionState.currentOverlays.isNotEmpty() ->
                                IsVisibleWithLoggingReason(true, "overlay is shown")

                            // If the current scene is idle and transparent, the SceneWindowRootView
                            // should be made INVISIBLE. When SceneWindowRootView is INVISIBLE,
                            // composition of its contents will be paused (see ViewLifecycleOwner in
                            // RepeatWhenAttached - it will toggle to lifecycle state CREATED). If
                            // we made the SceneWindowRootView invisible immediately here, this
                            // could happen before the transparent scene has composed.
                            //
                            // However, the handling of swipe gestures on scenes currently relies on
                            // SceneContainer composing before it can react on the configured
                            // UserActions of the scene's UserActionsViewModel. This applies to
                            // transparent scenes too.
                            //
                            // Thus we delay toggling the View to INVISIBLE until the transparent
                            // scene has composed (and is ready to handle input events).
                            blackScreenOnSceneContainerStartFix() &&
                                repository.composedIdleScene != transitionState.currentScene ->
                                IsVisibleWithLoggingReason(
                                    true,
                                    "waiting for ${transitionState.currentScene.debugName} to be composed (currently composed: ${repository.composedIdleScene?.debugName ?: "<none>"})",
                                )

                            transitionState.currentScene == Scenes.Occluded ->
                                IsVisibleWithLoggingReason(false, "occluded")

                            else -> IsVisibleWithLoggingReason(false, "scene is Gone")
                        }

                    is TransitionState.Transition -> {
                        IsVisibleWithLoggingReason(true, "in transition")
                    }
                }
        }
    }

    /**
     * Notifies that a scene container user interaction has begun.
     *
     * This is a user interaction that originates within the Composable hierarchy of the scene
     * container.
     */
    fun onSceneContainerUserInputStarted() {
        handleEvent(Event.SceneContainerUserInputStart)
    }

    /**
     * Notifies that a remote user interaction has begun.
     *
     * This is a user interaction that originates outside of the UI of the scene container and
     * possibly outside of the System UI process itself.
     *
     * As an example, consider the dragging that can happen in the launcher that expands the shade.
     * This is a user interaction that begins remotely (as it starts in the launcher process) and is
     * then rerouted by window manager to System UI. While the user interaction definitely continues
     * within the System UI process and code, it also originates remotely.
     */
    fun onRemoteUserInputStarted(loggingReason: String) {
        handleEvent(Event.RemoteUserInputStart(loggingReason))
    }

    /**
     * Notifies that the current user interaction (internally or remotely started, see
     * [onSceneContainerUserInputStarted] and [onRemoteUserInputStarted]) has finished.
     */
    fun onUserInputFinished() {
        handleEvent(Event.UserInputEnd)
    }

    /**
     * Binds the given flow so the system remembers it.
     *
     * Note that you must call is with `null` when the UI is done or risk a memory leak.
     */
    fun setTransitionState(transitionState: Flow<ObservableTransitionState>?) {
        repository.setTransitionState(transitionState)
    }

    /**
     * Returns the [concrete scene][Scenes] for [sceneKey] if it is a [scene family][SceneFamilies],
     * otherwise returns a singleton [Flow] containing [sceneKey].
     */
    fun resolveSceneFamily(sceneKey: SceneKey): Flow<SceneKey> = flow {
        emitAll(resolveSceneFamilyOrNull(sceneKey) ?: flowOf(sceneKey))
    }

    /**
     * Returns the [concrete scene][Scenes] for [sceneKey] if it is a [scene family][SceneFamilies],
     * otherwise returns `null`.
     */
    fun resolveSceneFamilyOrNull(sceneKey: SceneKey): StateFlow<SceneKey>? =
        sceneFamilyResolvers.get()[sceneKey]?.resolvedScene

    /** Called when SceneContainer has currently composed [scene] with TransitionState.Idle. */
    fun onIdleSceneEnteredComposition(scene: SceneKey) {
        handleEvent(Event.IdleSceneEnteredComposition(scene))
    }

    /**
     * Called when SceneContainer has re-composed, and previously it was showing [scene] with
     * TransitionState.Idle - this means that [scene] (at least in its idle state) has exited
     * composition.
     */
    fun onIdleSceneExitedComposition(scene: SceneKey) {
        handleEvent(Event.IdleSceneExitedComposition(scene))
    }

    fun startTransitionImmediately(transition: TransitionState.Transition) {
        repository.startTransitionImmediately(transition)
    }

    /**
     * Validates that the given scene change is allowed.
     *
     * Will throw a runtime exception for illegal states (for example, attempting to change to a
     * scene that's not part of the current scene framework configuration).
     *
     * @param from The current scene being transitioned away from
     * @param to The desired destination scene to transition to
     * @param loggingReason The reason why the transition is requested, for logging purposes
     * @return `true` if the scene change is valid; `false` if it shouldn't happen
     */
    private fun validateSceneChange(from: SceneKey, to: SceneKey, loggingReason: String): Boolean {
        check(
            !shadeModeInteractor.isDualShade || (to != Scenes.Shade && to != Scenes.QuickSettings)
        ) {
            "Can't change scene to ${to.debugName} when dual shade is on!"
        }
        check(!shadeModeInteractor.isSplitShade || (to != Scenes.QuickSettings)) {
            "Can't change scene to ${to.debugName} in split shade mode!"
        }

        if (from == to) {
            logger.logContentChangeRejection(
                from = from,
                to = to,
                originalChangeReason = loggingReason,
                rejectionReason = "${from.debugName} is the same as ${to.debugName}",
            )
            return false
        }

        if (to !in repository.allContentKeys) {
            logger.logContentChangeRejection(
                from = from,
                to = to,
                originalChangeReason = loggingReason,
                rejectionReason = "${to.debugName} isn't present in allContentKeys",
            )
            return false
        }

        if (disabledContentInteractor.isDisabled(to)) {
            logger.logContentChangeRejection(
                from = from,
                to = to,
                originalChangeReason = loggingReason,
                rejectionReason = "${to.debugName} is currently disabled",
            )
            return false
        }

        val inMidTransitionFromGone = transitionState.isTransitioning(from = Scenes.Gone)
        val isChangeAllowed =
            to != Scenes.Gone ||
                inMidTransitionFromGone ||
                deviceUnlockedInteractor.get().deviceUnlockStatus.value.isUnlocked ||
                !authenticationInteractor.get().authenticationMethod.value.isSecure ||
                !keyguardEnabledInteractor.get().isKeyguardEnabled.value
        check(isChangeAllowed) {
            "Cannot change to the Gone scene while the device is locked/secured and not currently" +
                " transitioning from Gone. Current transition state is $transitionState." +
                " Logging reason for scene change was: $loggingReason"
        }

        return true
    }

    /**
     * Validates that the given overlay change is allowed.
     *
     * Will throw a runtime exception for illegal states.
     *
     * @param from The overlay to be hidden, if any
     * @param to The overlay to be shown, if any
     * @param loggingReason The reason why the transition is requested, for logging purposes
     * @return `true` if the scene change is valid; `false` if it shouldn't happen
     */
    private fun validateOverlayChange(
        from: OverlayKey? = null,
        to: OverlayKey? = null,
        loggingReason: String,
    ): Boolean {
        check(from != null || to != null) {
            "No overlay key provided for requested change." +
                " Current transition state is $transitionState." +
                " Logging reason for overlay change was: $loggingReason"
        }

        check(
            shadeModeInteractor.isDualShade ||
                (to != Overlays.NotificationsShade && to != Overlays.QuickSettingsShade)
        ) {
            "Can't show overlay ${to?.debugName} when dual shade is off!"
        }

        if (to != null && disabledContentInteractor.isDisabled(to)) {
            logger.logContentChangeRejection(
                from = from,
                to = to,
                originalChangeReason = loggingReason,
                rejectionReason = "${to.debugName} is currently disabled",
            )
            return false
        }

        return when {
            to != null && from != null && to == from -> {
                logger.logContentChangeRejection(
                    from = from,
                    to = to,
                    originalChangeReason = loggingReason,
                    rejectionReason = "${from.debugName} is the same as ${to.debugName}",
                )
                false
            }

            to != null && to !in repository.allContentKeys -> {
                logger.logContentChangeRejection(
                    from = from,
                    to = to,
                    originalChangeReason = loggingReason,
                    rejectionReason = "${to.debugName} is not in allContentKeys",
                )
                false
            }

            from != null && from !in transitionState.currentOverlays -> {
                logger.logContentChangeRejection(
                    from = from,
                    to = to,
                    originalChangeReason = loggingReason,
                    rejectionReason = "${from.debugName} is not a current overlay",
                )
                false
            }

            to != null && to in transitionState.currentOverlays -> {
                logger.logContentChangeRejection(
                    from = from,
                    to = to,
                    originalChangeReason = loggingReason,
                    rejectionReason = "${to.debugName} is already a current overlay",
                )
                false
            }

            to == Overlays.Bouncer && deviceUnlockedInteractor.get().isUnlocked -> {
                logger.logContentChangeRejection(
                    from = from,
                    to = to,
                    originalChangeReason = loggingReason,
                    rejectionReason = "Cannot show Bouncer when device already unlocked",
                )
                false
            }

            else -> true
        }
    }

    /**
     * Returns a filtered version of [unfiltered], without action-result entries that would navigate
     * to disabled scenes.
     */
    fun filteredUserActions(
        unfiltered: Flow<Map<UserAction, UserActionResult>>
    ): Flow<Map<UserAction, UserActionResult>> {
        return restrictedModeInteractor
            .get()
            .filteredUserActions(disabledContentInteractor.filteredUserActions(unfiltered))
    }

    /**
     * Notifies that a transition animation has started.
     *
     * The scene container will remain visible while any transition animation is running within it.
     */
    fun onTransitionAnimationStart() {
        handleEvent(Event.TransitionAnimationStart)
    }

    /**
     * Notifies that a transition animation has ended.
     *
     * The scene container will remain visible while any transition animation is running within it.
     */
    fun onTransitionAnimationEnd() {
        handleEvent(Event.TransitionAnimationEnd)
    }

    /**
     * Notifies that a transition animation has been canceled.
     *
     * The scene container will remain visible while any transition animation is running within it.
     */
    fun onTransitionAnimationCancelled() {
        handleEvent(Event.TransitionAnimationCancel)
    }

    suspend fun hydrateTableLogBuffer(tableLogBuffer: TableLogBuffer) {
        coroutineScope {
            launch {
                currentScene
                    .map { sceneKey -> DiffableSceneKey(key = sceneKey) }
                    .pairwise()
                    .collect { (prev, current) ->
                        tableLogBuffer.logDiffs(prevVal = prev, newVal = current)
                    }
            }

            launch {
                snapshotFlow { transitionState.currentOverlays }
                    .map { overlayKeys -> DiffableOverlayKeys(keys = overlayKeys) }
                    .pairwise()
                    .collect { (prev, current) ->
                        tableLogBuffer.logDiffs(prevVal = prev, newVal = current)
                    }
            }
        }
    }

    private class DiffableSceneKey(private val key: SceneKey) : Diffable<DiffableSceneKey> {
        override fun logDiffs(prevVal: DiffableSceneKey, row: TableRowLogger) {
            row.logChange(columnName = "currentScene", value = key.debugName)
        }
    }

    private class DiffableOverlayKeys(private val keys: Set<OverlayKey>) :
        Diffable<DiffableOverlayKeys> {
        override fun logDiffs(prevVal: DiffableOverlayKeys, row: TableRowLogger) {
            row.logChange(
                columnName = "currentOverlays",
                value = keys.joinToString { key -> key.debugName },
            )
        }
    }

    /**
     * Based off of the ordering of [allContentKeys], returns the key of the highest z-order content
     * out of [content].
     */
    private fun determineTopmostContent(content: Set<ContentKey>): ContentKey {
        // Assuming allContentKeys is sorted by ascending z-order.
        return checkNotNull(allContentKeys.findLast { it in content }) {
            "Could not find unknown content $content in allContentKeys $allContentKeys"
        }
    }

    /** Optimization for common case where overlays is empty. */
    private fun determineTopmostContent(scene: SceneKey, overlays: Set<OverlayKey>): ContentKey {
        return if (overlays.isEmpty()) {
            scene
        } else {
            determineTopmostContent(overlays)
        }
    }

    /**
     * Returns `true` if the scene can ever have non-transparent content; `false` otherwise.
     *
     * A scene is not transparent when it contains content that can render, even if only sometimes.
     * A scene does not have non-transparent content when its own layout never shows any UI
     * elements.
     *
     * NOTE: this is not the same as the visibility of the shade window-view as that could become
     * visible when floating elements outside the scene but inside the window-view become visible
     * (examples of such "floating" elements: alternate bouncer, heads-up notifications); these
     * floating elements becoming visible will make the shade window-view visible even if the scene
     * itself never renders anything.
     */
    private fun hasNonTransparentContent(scene: SceneKey): Boolean {
        return when (scene) {
            // THE FOLLOWING SCENES RENDER CONTENT, ALWAYS OR SOMETIMES:
            Scenes.Lockscreen,
            Scenes.QuickSettings,
            Scenes.Shade,
            Scenes.Communal -> true
            // THE FOLLOWING SCENES NEVER RENDER ANY CONTENT, THEIR LAYOUT IS ALWAYS TRANSPARENT:
            Scenes.Gone,
            Scenes.Occluded,
            Scenes.Dream -> false
            // Note: this is purposely not using the else branch for "all other scenes are false"
            // because we want future devs who add a new SceneKey to have to modify this list and,
            // since SceneKeys are not an enum or a sealed interface/class, there's no other way to
            // enforce that.
            else -> error("Scene ${scene.debugName} needs to be added to a branch above!")
        }
    }

    private data class IsVisibleWithLoggingReason(val value: Boolean, val loggingReason: String)

    sealed interface HideOverlayCommand {
        data object HideAll : HideOverlayCommand

        data object HideNone : HideOverlayCommand

        class HideSome(val overlays: List<OverlayKey>) : HideOverlayCommand {
            constructor(overlay: OverlayKey) : this(listOf(overlay))
        }
    }

    /**
     * Defines interface for classes that represents events that are of interest to the scene
     * framework.
     */
    sealed interface Event {
        /** User input has started, directly on the UI of the scene container. */
        data object SceneContainerUserInputStart : Event

        /**
         * User input has started, outside the UI of the scene container or even in another process.
         */
        data class RemoteUserInputStart(val loggingReason: String) : Event

        /** User input (native or remote) has finished. */
        data object UserInputEnd : Event

        /** A transition animation (for example, to show an activity) has started. */
        data object TransitionAnimationStart : Event

        /** A transition animation (for example, to show an activity) has ended. */
        data object TransitionAnimationEnd : Event

        /** A transition animation (for example, to show an activity) has been canceled. */
        data object TransitionAnimationCancel : Event

        /** A change to the device provisioning state (setup wizard started or finished). */
        data class DeviceProvisioningChange(val isDeviceProvisioned: Boolean) : Event

        /** The scene [scene] has entered the composition with TransitionState.Idle . */
        data class IdleSceneEnteredComposition(val scene: SceneKey) : Event

        /** The scene [scene] with TransitionState.Idle has exited the composition. */
        data class IdleSceneExitedComposition(val scene: SceneKey) : Event

        /** The device has become locked or unlocked. */
        data class DeviceUnlockChange(val isDeviceUnlocked: Boolean) : Event

        /**
         * A heads-up notification (is either visible or not). It's considered visible while
         * animating away.
         */
        data class HeadsUpNotificationVisibilityChange(val isVisible: Boolean) : Event

        /** The alternate bouncer has become visible or invisible. */
        data class AlternateBouncerVisibilityChange(val isVisible: Boolean) : Event

        /** The surface behind System UI has begun or stopped animating. */
        data class SurfaceBehindAnimationChange(val isAnimating: Boolean) : Event
    }
}
