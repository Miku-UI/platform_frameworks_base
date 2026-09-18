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

@file:OptIn(ExperimentalCoroutinesApi::class)

package com.android.systemui.statusbar.notification.stack.ui.viewmodel

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import com.android.compose.animation.scene.ContentKey
import com.android.compose.animation.scene.ObservableTransitionState
import com.android.compose.animation.scene.ObservableTransitionState.Idle
import com.android.compose.animation.scene.ObservableTransitionState.Transition
import com.android.compose.animation.scene.ObservableTransitionState.Transition.ChangeScene
import com.android.compose.animation.scene.ObservableTransitionState.Transition.OverlayTransition
import com.android.compose.animation.scene.OverlayKey
import com.android.compose.animation.scene.SceneKey
import com.android.systemui.brightness.domain.interactor.BrightnessMirrorShowingInteractor
import com.android.systemui.common.ui.ConfigurationState
import com.android.systemui.common.ui.domain.interactor.ConfigurationInteractor
import com.android.systemui.dump.DumpManager
import com.android.systemui.keyguard.domain.interactor.KeyguardInteractor
import com.android.systemui.lifecycle.ExclusiveActivatable
import com.android.systemui.notifications.ui.NotificationPlaceholderStateStorage
import com.android.systemui.notifications.ui.YSpace
import com.android.systemui.res.R
import com.android.systemui.scene.domain.interactor.SceneInteractor
import com.android.systemui.scene.shared.flag.SceneContainerFlag
import com.android.systemui.scene.shared.model.Overlays
import com.android.systemui.scene.shared.model.Scenes
import com.android.systemui.scene.shared.model.TransitionKeys.ToAlwaysOnDisplay
import com.android.systemui.shade.ShadeDisplayAware
import com.android.systemui.shade.domain.interactor.ShadeInteractor
import com.android.systemui.shade.domain.interactor.ShadeModeInteractor
import com.android.systemui.shade.shared.model.ShadeMode
import com.android.systemui.statusbar.domain.interactor.RemoteInputInteractor
import com.android.systemui.statusbar.notification.domain.interactor.HeadsUpNotificationInteractor
import com.android.systemui.statusbar.notification.stack.domain.interactor.LockscreenDisplayConfig
import com.android.systemui.statusbar.notification.stack.domain.interactor.LockscreenNotificationsInteractor
import com.android.systemui.statusbar.notification.stack.domain.interactor.NotificationStackAppearanceInteractor
import com.android.systemui.statusbar.notification.stack.shared.model.AccessibilityScrollEvent
import com.android.systemui.statusbar.notification.stack.shared.model.ShadeScrimClipping
import com.android.systemui.statusbar.notification.stack.shared.model.ShadeScrimShape
import com.android.systemui.statusbar.notification.stack.shared.model.ShadeScrollState
import com.android.systemui.statusbar.notification.stack.ui.viewmodel.NotificationTransitionThresholds.EXPANSION_FOR_DELAYED_STACK_FADE_IN
import com.android.systemui.statusbar.notification.stack.ui.viewmodel.NotificationTransitionThresholds.EXPANSION_FOR_MAX_SCRIM_ALPHA
import com.android.systemui.util.kotlin.ActivatableFlowDumper
import com.android.systemui.util.kotlin.ActivatableFlowDumperImpl
import com.android.systemui.util.state.ObservableState
import com.android.systemui.util.state.combine
import com.android.systemui.utils.coroutines.flow.flatMapLatestConflated
import dagger.Lazy
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull

/** ViewModel which represents the state of the NSSL/Controller in the world of flexiglass */
@SuppressLint("FlowExposedFromViewModel") // because all flows from this class are bound to a View
class NotificationScrollViewModel
@AssistedInject
constructor(
    dumpManager: DumpManager,
    @ShadeDisplayAware private val configuration: ConfigurationState,
    placeholderStateStorage: NotificationPlaceholderStateStorage,
    private val stackAppearanceInteractor: NotificationStackAppearanceInteractor,
    private val lockscreenNotificationsInteractor: LockscreenNotificationsInteractor,
    brightnessMirrorShowingInteractorLazy: Lazy<BrightnessMirrorShowingInteractor>,
    private val shadeInteractor: ShadeInteractor,
    shadeModeInteractor: ShadeModeInteractor,
    private val remoteInputInteractor: RemoteInputInteractor,
    headsUpNotificationInteractor: HeadsUpNotificationInteractor,
    sceneInteractor: SceneInteractor,
    // TODO(b/336364825) Remove Lazy when SceneContainerFlag is released. While the flag is off,
    //  creating this object too early results in a crash.
    keyguardInteractor: Lazy<KeyguardInteractor>,
    @ShadeDisplayAware configurationInteractor: ConfigurationInteractor,
    @ShadeDisplayAware private val context: Context,
) :
    ActivatableFlowDumper by ActivatableFlowDumperImpl(dumpManager, "NotificationScrollViewModel"),
    ExclusiveActivatable() {

    override suspend fun onActivated() {
        activateFlowDumper()
    }

    private fun expandFractionWhileIdle(
        currentScene: SceneKey,
        currentOverlays: Set<OverlayKey>,
    ): Float =
        if (currentScene.showsNotifications() || Overlays.NotificationsShade in currentOverlays) {
            1f
        } else {
            0f
        }

    private fun expandFractionDuringSceneChange(sceneChange: ChangeScene): Flow<Float> =
        with(sceneChange) {
            when {
                // Remain fully expanded when transitioning to AOD, to enable fading out in place
                // (i.e. no collapse animation).
                (key == ToAlwaysOnDisplay) && fromScene.showsNotifications() -> flowOf(1f)

                // Transitions following variable Shade expansion
                isTransitioningBetween(Scenes.Gone, Scenes.Shade) ||
                    isTransitioningBetween(Scenes.Occluded, Scenes.Shade) ||
                    isTransitioning(from = Scenes.Shade, to = Scenes.Lockscreen) ->
                    shadeInteractor.shadeExpansion

                // Transitions following delayed variable QS expansion
                isTransitioningBetween(Scenes.Gone, Scenes.QuickSettings) ->
                    shadeInteractor.qsExpansion
                        .map { qsExpansion ->
                            // During QS expansion, increase fraction at same rate as scrim alpha,
                            // but start when scrim alpha is at EXPANSION_FOR_DELAYED_STACK_FADE_IN.
                            (qsExpansion / EXPANSION_FOR_MAX_SCRIM_ALPHA -
                                    EXPANSION_FOR_DELAYED_STACK_FADE_IN)
                                .coerceIn(0f, 1f)
                        }
                        .distinctUntilChanged()

                // Special case: Keep collapsed when entering Lockscreen from Gone to avoid showing
                // the stack early.
                isTransitioning(from = Scenes.Gone, to = Scenes.Lockscreen) -> flowOf(0f)

                // Stay fully expanded if Lockscreen is involved or both scenes show notifications
                // (Covers: Lockscreen <-> Occluded|Communal|Dream, Shade <-> QuickSettings, etc.)
                isTransitioningFromOrTo(Scenes.Lockscreen) ||
                    (fromScene.showsNotifications() && toScene.showsNotifications()) -> flowOf(1f)

                // Default to collapsed
                // TODO(b/356596436): If notification shade overlay is open, we'll reach this point
                //  and the expansion fraction in that case should be `shadeExpansion`.
                else -> flowOf(0f)
            }
        }

    val lockScreenToShadeTransitionProgress: Flow<Float> =
        combine(shadeInteractor.shadeExpansion, sceneInteractor.transitionStateFlow) {
                shadeExp,
                transitionState ->
                when (transitionState) {
                    is Idle -> 0f
                    is ChangeScene ->
                        if (
                            transitionState.isTransitioning(
                                from = Scenes.Lockscreen,
                                to = Scenes.Shade,
                            )
                        ) {
                            shadeExp
                        } else {
                            0f
                        }

                    is Transition.OverlayTransition ->
                        if (
                            transitionState.currentScene == Scenes.Lockscreen &&
                                transitionState.isTransitioning(to = Overlays.NotificationsShade)
                        ) {
                            shadeExp
                        } else {
                            0f
                        }
                }
            }
            .distinctUntilChanged()
            .dumpWhileCollecting("lockScreenToShadeTransitionProgress")

    private fun expandFractionDuringOverlayTransition(
        transitionState: Transition,
        currentScene: SceneKey,
        currentOverlays: Set<OverlayKey>,
    ): Flow<Float> =
        when {
            currentScene.showsNotifications() -> flowOf(1f)
            transitionState.isTransitioningFromOrTo(Overlays.NotificationsShade) ->
                shadeInteractor.shadeExpansion
            Overlays.NotificationsShade in currentOverlays -> flowOf(1f)
            else -> flowOf(0f)
        }

    val qsExpandFraction: Flow<Float> =
        shadeInteractor.qsExpansion.dumpWhileCollecting("qsExpandFraction")

    /**
     * Level of height suppression state. Higher suppressionLevel number represents stricter
     * suppression rule.
     */
    enum class HeightSuppressionState(private val suppressionLevel: Int) {
        /** No height updates suppressed. */
        None(0),

        /**
         * Only suppress the stack end height update. This replaces the pre-flexi version of
         * getQsExpansionFraction() <= 0 check in NotificationStackScrollLayout.
         */
        EndHeightOnly(1),

        /** Suppress all height updates. */
        All(2);

        /**
         * When changing from a stricter suppress state to a looser suppress state, force an update.
         */
        fun forceUpdateWhenChangeTo(newState: HeightSuppressionState): Boolean {
            return newState.suppressionLevel < suppressionLevel
        }
    }

    /** Are notification stack height updates suppressed? */
    val suppressHeightUpdates: Flow<HeightSuppressionState> =
        sceneInteractor.transitionStateFlow
            .map { state: ObservableTransitionState ->
                when (state) {
                    is Idle -> {
                        if (state.currentScene == Scenes.QuickSettings) {
                            HeightSuppressionState.EndHeightOnly
                        } else HeightSuppressionState.None
                    }
                    is Transition -> {
                        if (
                            state.fromContent == Scenes.Lockscreen &&
                                (state.toContent == Overlays.Bouncer ||
                                    state.toContent == Scenes.Gone)
                        ) {
                            HeightSuppressionState.All
                        } else if (
                            state.isTransitioningFromOrTo(Scenes.QuickSettings) &&
                                !state.isTransitioning(to = Scenes.Shade)
                        ) {
                            HeightSuppressionState.EndHeightOnly
                        } else HeightSuppressionState.None
                    }
                }
            }
            .dumpWhileCollecting("suppressHeightUpdates")

    /**
     * The expansion fraction of the notification stack. It should go from 0 to 1 when transitioning
     * from Gone to Shade scenes, and remain at 1 when in Lockscreen or Shade scenes and while
     * transitioning from Shade to QuickSettings scenes.
     */
    val expandFraction: Flow<Float> =
        combine(sceneInteractor.transitionStateFlow, sceneInteractor.currentOverlays) {
                transitionState,
                currentOverlays ->
                Pair(transitionState, currentOverlays)
            }
            .flatMapLatestConflated { (transitionState, currentOverlays) ->
                when (transitionState) {
                    is Idle ->
                        flowOf(
                            expandFractionWhileIdle(transitionState.currentScene, currentOverlays)
                        )

                    is ChangeScene -> expandFractionDuringSceneChange(transitionState)

                    is OverlayTransition ->
                        expandFractionDuringOverlayTransition(
                            transitionState = transitionState,
                            currentScene = transitionState.currentScene,
                            currentOverlays = currentOverlays,
                        )
                }
            }
            .distinctUntilChanged()
            .dumpWhileCollecting("expandFraction")

    val animationsEnabled
        get() = shadeInteractor.isShadeTouchable.dumpWhileCollecting("animationsEnabled")

    /** Whether or not Split Shade is enabled. */
    val isSplitShade: Flow<Boolean> =
        shadeModeInteractor.shadeMode
            .map { shadeMode -> shadeMode is ShadeMode.Split }
            .distinctUntilChanged()

    data class SidePaddingConfig(
        /** The base side paddings without aligning to the QQS tiles. */
        val basePadding: Int,

        /**
         * Controls whether the notification panel should inset its left and right paddings to
         * visually align with the second tile from each edge in the QQS above notifications.
         *
         * Currently only effective for SingleShade since that's the only place QQS tiles are shown
         * above notifications.
         */
        val alignToInnerQqsTiles: Boolean,
    )

    val sidePaddingConfig: Flow<SidePaddingConfig> =
        combine(shadeModeInteractor.shadeMode, configurationInteractor.onAnyConfigurationChange) {
                shadeMode,
                _ ->
                with(context.resources) {
                    val orientation = configuration.orientation
                    val baseSidePaddings =
                        getDimensionPixelSize(
                            when (shadeMode) {
                                ShadeMode.Single -> R.dimen.notification_side_paddings_single
                                ShadeMode.Dual -> R.dimen.notification_side_paddings_dual
                                ShadeMode.Split -> R.dimen.notification_side_paddings_split
                            }
                        )
                    SidePaddingConfig(
                        basePadding = baseSidePaddings,
                        alignToInnerQqsTiles =
                            shadeMode is ShadeMode.Single &&
                                orientation == Configuration.ORIENTATION_LANDSCAPE,
                    )
                }
            }
            .distinctUntilChanged()
            .dumpWhileCollecting("sidePaddingsConfig")

    /**
     * Scale of the blur effect that should be applied to Notifications.
     *
     * 0 -> don't blur (default, removes all blur render effects) 1 -> do the full blur (apply a
     * render effect with the max blur radius)
     */
    private val blurFraction: Flow<Float> =
        if (SceneContainerFlag.isEnabled) {
            shadeModeInteractor.shadeMode
                .flatMapLatest { shadeMode ->
                    when (shadeMode) {
                        ShadeMode.Dual -> shadeInteractor.qsExpansion
                        else -> flowOf(0f)
                    }
                }
                .distinctUntilChanged()
                .dumpWhileCollecting("blurFraction")
        } else {
            flowOf(0f)
        }

    /** Blur radius to be applied to Notifications. */
    val blurRadius: Flow<Float> =
        combine(
            blurFraction,
            configuration.getDimensionPixelSize(R.dimen.max_shade_content_blur_radius),
        ) { fraction, maxRadius ->
            fraction * maxRadius
        }

    private val brightnessMirrorShowing: Flow<Boolean> =
        if (SceneContainerFlag.isEnabled) {
            brightnessMirrorShowingInteractorLazy.get().isShowing
        } else {
            flowOf(false)
        }

    /**
     * Whether the Notifications are interactive for touches, accessibility, and focus. When false,
     * scene container will handle touches.
     */
    val interactive: Flow<Boolean> =
        combine(
                blurFraction.map { it != 1f }.distinctUntilChanged(),
                brightnessMirrorShowing,
                headsUpNotificationInteractor.hasPinnedRows,
            ) { blurIsPartial, brightnessMirrorShowing, hasPinnedHun ->
                (blurIsPartial || hasPinnedHun) && !brightnessMirrorShowing
            }
            .distinctUntilChanged()
            .dumpWhileCollecting("interactive")

    /** Whether we should close any open notification guts. */
    val shouldCloseGuts: Flow<Boolean> = stackAppearanceInteractor.shouldCloseGuts

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
        return lockscreenNotificationsInteractor.getLockscreenDisplayConfig {
            availableSpace,
            useExtraShelfSpace ->
            calculateMaxNotifications(availableSpace, useExtraShelfSpace)
        }
    }

    /** Whether the Notification Stack is visibly on the lockscreen scene. */
    val isShowingStackOnLockscreen: Flow<Boolean> =
        sceneInteractor.transitionStateFlow
            .mapNotNull { state ->
                state.isIdle(Scenes.Lockscreen) ||
                    state.isTransitioning(from = Scenes.Lockscreen, to = Scenes.Shade)
            }
            .distinctUntilChanged()

    /** The alpha of the Notification Stack for lockscreen fade-in */
    val alphaForLockscreenFadeIn = stackAppearanceInteractor.alphaForLockscreenFadeIn

    /** Whether the current scene is lockscreen */
    val isCurrentSceneLockscreen =
        sceneInteractor.currentScene.map { it == Scenes.Lockscreen }.distinctUntilChanged()

    val allowScrimClipping: Flow<Boolean> =
        shadeModeInteractor.shadeMode
            .flatMapLatestConflated { shadeMode ->
                @Suppress("DEPRECATION") // to handle split shade
                when (shadeMode) {
                    is ShadeMode.Dual ->
                        // Don't clip notifications while we are opening the DualShade panel to
                        // enable the shared element transition.
                        sceneInteractor.transitionStateFlow.map { transition ->
                            !transition.isTransitioning(to = Overlays.NotificationsShade)
                        }

                    is ShadeMode.Split -> flowOf(true)
                    is ShadeMode.Single -> shadeInteractor.qsExpansion.map { it < 0.5f }
                }
            }
            .distinctUntilChanged()

    /** The bounds of the notification stack in the current scene. */
    private val shadeScrimClipping: Flow<ShadeScrimClipping?> =
        allowScrimClipping
            .flatMapLatestConflated { allowScrimClipping ->
                if (!allowScrimClipping) {
                    flowOf(null)
                } else {
                    combine(
                        stackAppearanceInteractor.notificationShadeScrimBounds,
                        stackAppearanceInteractor.shadeScrimRounding,
                    ) { bounds, rounding ->
                        bounds?.let { ShadeScrimClipping(it, rounding) }
                    }
                }
            }
            .distinctUntilChanged()
            .dumpWhileCollecting("stackClipping")

    fun notificationScrimShape(
        cornerRadius: Flow<Int>,
        viewLeftOffset: Flow<Int>,
    ): Flow<ShadeScrimShape?> =
        combine(shadeScrimClipping, cornerRadius, viewLeftOffset) { clipping, radius, leftOffset ->
                if (clipping == null) return@combine null
                ShadeScrimShape(
                    bounds = clipping.bounds.minus(leftOffset = leftOffset),
                    topRadius = radius.takeIf { clipping.rounding.isTopRounded } ?: 0,
                    bottomRadius = radius.takeIf { clipping.rounding.isBottomRounded } ?: 0,
                )
            }
            .dumpWhileCollecting("shadeScrimShape")

    /**
     * Gets an observable state for the qs scrim shape within the view coordinates, given the
     * [viewLeft] state.
     */
    fun getQsScrimShape(viewLeft: ObservableState<Int>): ObservableState<ShadeScrimShape?> =
        combine(stackAppearanceInteractor.qsPanelShapeInWindow, viewLeft) { shapeInWindow, left ->
            shapeInWindow?.copy(bounds = shapeInWindow.bounds.minus(leftOffset = left))
        }

    /** Flow of the scrim clipping radius. */
    val scrimClippingRadius: Flow<Int> =
        shadeModeInteractor.shadeMode
            .flatMapLatest { shadeMode ->
                configuration.getDimensionPixelOffset(
                    if (shadeMode is ShadeMode.Dual) {
                        R.dimen.overlay_shade_panel_shape_radius
                    } else {
                        R.dimen.notification_scrim_corner_radius
                    }
                )
            }
            .distinctUntilChanged()

    /** Y coordinate for the top of the notification stack, including the scroll offset. */
    val stackScrollTop: ObservableState<Float> = placeholderStateStorage.stackScrollTop

    /** Vertical bounds for the user visible area of the notification stack. */
    val stackBounds: ObservableState<YSpace> = placeholderStateStorage.stackBounds

    /** Vertical bounds of the top HUN. */
    val headsUpBounds: ObservableState<YSpace> = placeholderStateStorage.hunBounds

    /** Alpha requested by the StackPlaceholder STL element. */
    val stackPlaceholderAlpha: ObservableState<Float> = placeholderStateStorage.stackAlpha

    /**
     * Max alpha to apply directly to the view based on the compose placeholder.
     *
     * TODO(b/338590620): Migrate alphas from [SharedNotificationContainerViewModel] into this flow
     */
    val maxAlpha: Flow<Float> =
        stackAppearanceInteractor.alphaForBrightnessMirror.dumpValue("maxAlpha")

    /** Scroll state of the notification shade. */
    val shadeScrollState: Flow<ShadeScrollState> = stackAppearanceInteractor.shadeScrollState

    /** Receives the amount (px) that the stack should scroll due to internal expansion. */
    val syntheticScrollConsumer: (Float) -> Unit = stackAppearanceInteractor::setSyntheticScroll

    /** Receives an event to scroll the stack up or down. */
    val accessibilityScrollEventConsumer: (AccessibilityScrollEvent) -> Unit =
        stackAppearanceInteractor::sendAccessibilityScrollEvent

    /** Receives whether the current touch gesture is has already been consumed by the stack. */
    val currentGestureExpandingNotifConsumer: (Boolean) -> Unit =
        stackAppearanceInteractor::setCurrentGestureExpandingNotif

    /** Receives whether the current touch gesture is inside any open guts. */
    val currentGestureInGutsConsumer: (Boolean) -> Unit =
        stackAppearanceInteractor::setCurrentGestureInGuts

    /** Receives the bottom bound of the currently focused remote input notification row. */
    val remoteInputRowBottomBoundConsumer: (Float?) -> Unit =
        remoteInputInteractor::setRemoteInputRowBottomBound

    /** Whether the notification stack is scrollable or not. */
    val isScrollable: Flow<Boolean> =
        combine(sceneInteractor.currentScene, sceneInteractor.currentOverlays) {
                currentScene,
                currentOverlays ->
                currentScene.showsScrollableStack() ||
                    currentOverlays.any { it.showsScrollableStack() }
            }
            .dumpWhileCollecting("isScrollable")

    /** Whether the notification stack is displayed in doze mode. */
    val isDozing: Flow<Boolean> by lazy {
        if (SceneContainerFlag.isUnexpectedlyInLegacyMode()) {
            flowOf(false)
        } else {
            keyguardInteractor.get().isDozing.dumpWhileCollecting("isDozing")
        }
    }

    /** Whether the notification stack is displayed in pulsing mode. */
    val isPulsing: Flow<Boolean> by lazy {
        if (SceneContainerFlag.isUnexpectedlyInLegacyMode()) {
            flowOf(false)
        } else {
            keyguardInteractor.get().isPulsing.dumpWhileCollecting("isPulsing")
        }
    }

    val shouldAnimatePulse: StateFlow<Boolean> by lazy {
        if (SceneContainerFlag.isUnexpectedlyInLegacyMode()) {
            MutableStateFlow(false)
        } else {
            keyguardInteractor.get().isAodAvailable
        }
    }

    private fun ContentKey.showsScrollableStack(): Boolean {
        return when (this) {
            Overlays.NotificationsShade,
            Scenes.Shade -> true

            else -> false
        }
    }

    private fun SceneKey.showsNotifications(): Boolean {
        return when (this) {
            Scenes.Lockscreen,
            Scenes.Shade,
            Scenes.QuickSettings -> true

            else -> false
        }
    }

    @AssistedFactory
    interface Factory {
        fun create(): NotificationScrollViewModel
    }
}
