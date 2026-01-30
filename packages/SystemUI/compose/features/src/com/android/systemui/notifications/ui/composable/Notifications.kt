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

package com.android.systemui.notifications.ui.composable

import android.util.Log
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.DecayAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.animation.splineBasedDecay
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imeAnimationTarget
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.overscroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toAndroidRectF
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastMaxOf
import androidx.compose.ui.util.fastMinOf
import androidx.compose.ui.util.lerp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.compose.animation.scene.ContentKey
import com.android.compose.animation.scene.ContentScope
import com.android.compose.animation.scene.ElementKey
import com.android.compose.animation.scene.LowestZIndexContentPicker
import com.android.compose.animation.scene.SceneTransitionLayoutState
import com.android.compose.animation.scene.content.state.TransitionState
import com.android.compose.gesture.effect.OffsetOverscrollEffect
import com.android.compose.gesture.effect.rememberOffsetOverscrollEffect
import com.android.compose.modifiers.thenIf
import com.android.compose.modifiers.width
import com.android.compose.nestedscroll.OnStopScope
import com.android.compose.nestedscroll.PriorityNestedScrollConnection
import com.android.compose.nestedscroll.ScrollController
import com.android.compose.theme.LocalAndroidColorScheme
import com.android.internal.jank.Cuj.CUJ_NOTIFICATION_SHADE_SCROLL_FLING
import com.android.internal.jank.InteractionJankMonitor
import com.android.systemui.common.ui.compose.windowinsets.LocalScreenCornerRadius
import com.android.systemui.res.R
import com.android.systemui.scene.session.ui.composable.SaveableSession
import com.android.systemui.scene.session.ui.composable.sessionCoroutineScope
import com.android.systemui.scene.shared.model.Overlays
import com.android.systemui.scene.shared.model.Scenes
import com.android.systemui.shade.ui.composable.ShadeHeader
import com.android.systemui.statusbar.notification.stack.shared.model.AccessibilityScrollEvent
import com.android.systemui.statusbar.notification.stack.shared.model.ShadeScrimBounds
import com.android.systemui.statusbar.notification.stack.shared.model.ShadeScrimRounding
import com.android.systemui.statusbar.notification.stack.shared.model.ShadeScrollState
import com.android.systemui.statusbar.notification.stack.ui.view.NotificationScrollView
import com.android.systemui.statusbar.notification.stack.ui.viewmodel.NotificationTransitionThresholds.EXPANSION_FOR_MAX_CORNER_RADIUS
import com.android.systemui.statusbar.notification.stack.ui.viewmodel.NotificationTransitionThresholds.EXPANSION_FOR_MAX_SCRIM_ALPHA
import com.android.systemui.statusbar.notification.stack.ui.viewmodel.NotificationsPlaceholderViewModel
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

object Notifications {
    object Elements {
        val NotificationScrim = ElementKey("NotificationScrim")
        val NotificationStackPlaceholder = ElementKey("NotificationStackPlaceholder")
        val HeadsUpNotificationPlaceholder =
            ElementKey("HeadsUpNotificationPlaceholder", contentPicker = LowestZIndexContentPicker)
    }
}

/**
 * Adds the space where heads up notifications can appear in the scene. This should generally be the
 * entire size of the scene.
 */
@Composable
fun ContentScope.HeadsUpNotificationSpace(
    stackScrollView: NotificationScrollView,
    viewModel: NotificationsPlaceholderViewModel,
    useHunBounds: () -> Boolean = { true },
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .element(Notifications.Elements.HeadsUpNotificationPlaceholder)
                .fillMaxWidth()
                .notificationHeadsUpHeight(stackScrollView)
                .debugBackground(viewModel, DEBUG_HUN_COLOR)
                .onGloballyPositioned { coordinates: LayoutCoordinates ->
                    // This element is sometimes opted out of the shared element system, so there
                    // can be multiple instances of it during a transition. Thus we need to
                    // determine which instance should feed its bounds to NSSL to avoid providing
                    // conflicting values.
                    val useBounds = useHunBounds()
                    if (useBounds) {
                        val positionInWindow = coordinates.positionInWindow()
                        val boundsInWindow = coordinates.boundsInWindow()
                        debugLog(viewModel) {
                            "HUNS onGloballyPositioned:" +
                                " size=${coordinates.size}" +
                                " bounds=$boundsInWindow"
                        }
                        // Note: boundsInWindow doesn't scroll off the screen, so use
                        // positionInWindow for top bound, which can scroll off screen while
                        // snoozing.
                        stackScrollView.setHeadsUpTop(positionInWindow.y)
                        stackScrollView.setHeadsUpBottom(boundsInWindow.bottom)
                    }
                }
    )
}

/**
 * A version of [HeadsUpNotificationSpace] that can be swiped up off the top edge of the screen by
 * the user. When swiped up, the heads up notification is snoozed.
 *
 * @param useDrawBounds Whether to communicate drawBounds updated to the [stackScrollView]. This
 *   should be `true` when content rendering the regular stack is not setting draw bounds anymore,
 *   but HUNs can still appear.
 */
@Composable
fun ContentScope.SnoozeableHeadsUpNotificationSpace(
    useDrawBounds: () -> Boolean,
    stackScrollView: NotificationScrollView,
    viewModel: NotificationsPlaceholderViewModel,
    modifier: Modifier = Modifier,
) {
    val isSnoozable by viewModel.isHeadsUpOrAnimatingAway.collectAsStateWithLifecycle(false)

    var scrollOffset by remember { mutableFloatStateOf(0f) }
    val headsUpInset = with(LocalDensity.current) { headsUpTopInset().toPx() }
    val minScrollOffset = -headsUpInset
    val maxScrollOffset = 0f

    val scrollableState = rememberScrollableState { delta ->
        consumeDeltaWithinRange(
            current = scrollOffset,
            setCurrent = { scrollOffset = it },
            min = minScrollOffset,
            max = maxScrollOffset,
            delta,
        )
    }

    val snoozeScrollConnection =
        object : NestedScrollConnection {
            override suspend fun onPreFling(available: Velocity): Velocity {
                if (
                    velocityOrPositionalThresholdReached(scrollOffset, minScrollOffset, available.y)
                ) {
                    scrollableState.animateScrollBy(minScrollOffset, tween())
                } else {
                    scrollableState.animateScrollBy(-minScrollOffset, tween())
                }
                return available
            }
        }

    val horizontalAlignment = viewModel.horizontalAlignment
    val halfScreenWidth = LocalWindowInfo.current.containerSize.width / 2

    LaunchedEffect(isSnoozable) { scrollOffset = 0f }

    LaunchedEffect(scrollableState.isScrollInProgress) {
        if (!scrollableState.isScrollInProgress && scrollOffset <= minScrollOffset) {
            viewModel.setHeadsUpAnimatingAway(false)
            viewModel.snoozeHun()
        }
    }

    // Wait for being Idle on this content, otherwise LaunchedEffect would fire too soon, and
    // another transition could override the NSSL stack bounds.
    val updateDrawBounds = layoutState.transitionState.isIdle() && useDrawBounds()

    LaunchedEffect(updateDrawBounds) {
        if (updateDrawBounds) {
            // Reset the stack bounds to avoid caching these values from the previous Scenes, and
            // not to confuse the StackScrollAlgorithm when it displays a HUN over GONE.
            stackScrollView.apply {
                // use -headsUpInset to allow HUN translation outside bounds for snoozing
                setStackTop(-headsUpInset)
            }
        }
    }

    HeadsUpNotificationSpace(
        stackScrollView = stackScrollView,
        viewModel = viewModel,
        modifier =
            modifier
                // In side-aligned layouts, HUNs are limited to half the screen width.
                .thenIf(horizontalAlignment != Alignment.CenterHorizontally) {
                    Modifier.width { halfScreenWidth }
                }
                .offset {
                    IntOffset(
                        x = if (horizontalAlignment == Alignment.End) halfScreenWidth else 0,
                        y =
                            calculateHeadsUpPlaceholderYOffset(
                                scrollOffset.roundToInt(),
                                minScrollOffset.roundToInt(),
                                stackScrollView.topHeadsUpHeight,
                            ),
                    )
                }
                .onGloballyPositioned {
                    if (updateDrawBounds) {
                        stackScrollView.updateDrawBounds(
                            it.boundsInWindow().toAndroidRectF().apply {
                                // extend bounds to the screen top to avoid cutting off HUN
                                // transitions
                                top = 0f
                                bottom += headsUpInset
                            }
                        )
                    }
                }
                .thenIf(isSnoozable) { Modifier.nestedScroll(snoozeScrollConnection) }
                .scrollable(orientation = Orientation.Vertical, state = scrollableState),
    )
}

/** Y position of the HUNs at rest, when the shade is closed. */
@Composable
fun headsUpTopInset(): Dp =
    WindowInsets.safeDrawing.asPaddingValues().calculateTopPadding() +
        dimensionResource(R.dimen.heads_up_status_bar_padding)

/** Adds the space where notification stack should appear in the scene. */
@Composable
fun ContentScope.ConstrainedNotificationStack(
    stackScrollView: NotificationScrollView,
    viewModel: NotificationsPlaceholderViewModel,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .onSizeChanged { viewModel.onConstrainedAvailableSpaceChanged(it.height) }
                .onGloballyPositioned {
                    if (shouldUseLockscreenStackBounds(layoutState.transitionState)) {
                        stackScrollView.updateDrawBounds(it.rawBoundsInWindow())
                    }
                }
    ) {
        NotificationPlaceholder(
            stackScrollView = stackScrollView,
            viewModel = viewModel,
            useStackBounds = { shouldUseLockscreenStackBounds(layoutState.transitionState) },
            modifier =
                Modifier.fillMaxWidth()
                    .notificationStackHeight(view = stackScrollView, constrainToMaxHeight = true)
                    .onGloballyPositioned { coordinates ->
                        viewModel.onLockScreenStackBottomChanged(
                            coordinates.boundsInWindow().bottom
                        )
                    },
        )
        HeadsUpNotificationSpace(
            stackScrollView = stackScrollView,
            viewModel = viewModel,
            useHunBounds = {
                shouldUseLockscreenHunBounds(
                    layoutState.transitionState,
                    viewModel.quickSettingsShadeContentKey,
                )
            },
            modifier = Modifier.align(Alignment.TopCenter),
        )
    }
}

/**
 * Adds the space where notification stack should appear in the scene, with a scrim and nested
 * scrolling.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ContentScope.NotificationScrollingStack(
    shadeSession: SaveableSession,
    stackScrollView: NotificationScrollView,
    viewModel: NotificationsPlaceholderViewModel,
    jankMonitor: InteractionJankMonitor,
    maxScrimTop: () -> Float,
    shouldPunchHoleBehindScrim: Boolean,
    stackTopPadding: Dp,
    stackBottomPadding: Dp,
    modifier: Modifier = Modifier,
    shouldFillMaxSize: Boolean = true,
    shouldIncludeHeadsUpSpace: Boolean = true,
    shouldShowScrim: Boolean = true,
    supportNestedScrolling: Boolean,
    onEmptySpaceClick: (() -> Unit)? = null,
) {
    if (!isAlwaysComposedContentVisible()) {
        // Some scenes or overlays that use this Composable may be using alwaysCompose=true which
        // will cause them to compose everything but not be visible. Because this Composable has
        // many side effects that push UI state upstream to its view-model, interactors, and
        // repositories and because the repositories are shared across callers of this Composable,
        // the cleanest way to prevent always-composing but invisible scenes/overlays from polluting
        // the shared state with bogus values is to prevent this entire Composable from actually
        // composing at all.
        //
        // Note that this optimization is very wide and is actively contradicting the point of
        // alwaysCompose=true (which attempts to pre-compose as much as it can), the initial use of
        // alwaysCompose=true is to always compose QS content, not notifications.
        //
        // Should a more granular optimization be preferred, we can let this Composable compose but
        // dive deeper into it and make sure that all of the side effects that send state upstream
        // to its view-model are properly taking lifecycle state into account.
        Box(modifier)
        return
    }

    val composeViewRoot = LocalView.current
    val coroutineScope = shadeSession.sessionCoroutineScope(key = "NotificationScrollingStack")
    val density = LocalDensity.current
    val screenCornerRadius = LocalScreenCornerRadius.current
    val scrimCornerRadius = dimensionResource(R.dimen.notification_scrim_corner_radius)
    val surfaceEffect0Color = LocalAndroidColorScheme.current.surfaceEffect0
    val scrollState =
        shadeSession.rememberSaveableSession(saver = ScrollState.Saver, key = "ScrollState") {
            ScrollState(initial = 0)
        }
    val syntheticScroll = viewModel.syntheticScroll.collectAsStateWithLifecycle(0f)
    val expansionFraction by viewModel.expandFraction.collectAsStateWithLifecycle(0f)
    val shadeToQsFraction by viewModel.shadeToQsFraction.collectAsStateWithLifecycle(0f)

    val navBarHeight = WindowInsets.systemBars.asPaddingValues().calculateBottomPadding()
    val screenHeight = with(density) { LocalConfiguration.current.screenHeightDp.dp.toPx() }

    /**
     * The height in px of the contents of notification stack. Depending on the number of
     * notifications, this can exceed the space available on screen to show notifications, at which
     * point the notification stack should become scrollable.
     */
    val stackHeight = shadeSession.rememberSession(key = "StackHeight") { mutableIntStateOf(0) }

    /**
     * Space available for the notification stack on the screen. These bounds don't scroll off the
     * screen, and respect the scrim paddings, scrim clipping.
     */
    val stackBoundsOnScreen = remember { mutableStateOf(Rect.Zero) }

    val scrimRounding =
        viewModel.shadeScrimRounding.collectAsStateWithLifecycle(ShadeScrimRounding())

    // the offset for the notifications scrim. Its upper bound is 0, and its lower bound is
    // calculated in minScrimOffset. The scrim is the same height as the screen minus the
    // height of the Shade Header, and at rest (scrimOffset = 0) its top bound is at maxScrimStartY.
    // When fully expanded (scrimOffset = minScrimOffset), its top bound is at minScrimStartY,
    // which is equal to the height of the Shade Header. Thus, when the scrim is fully expanded, the
    // entire height of the scrim is visible on screen.
    val scrimOffset = shadeSession.rememberSession(key = "ScrimOffset") { Animatable(0f) }

    // set the bounds to null when the scrim disappears
    DisposableEffect(Unit) { onDispose { viewModel.onScrimBoundsChanged(null) } }

    // Top position if the scrim, when it is fully expanded.
    val minScrimTop = ShadeHeader.Dimensions.StatusBarHeight

    // The minimum offset for the scrim. The scrim is considered fully expanded when it
    // is at this offset.
    val minScrimOffset: () -> Float = { with(density) { minScrimTop.toPx() } - maxScrimTop() }

    // The height of the scrim visible on screen when it is in its resting (collapsed) state.
    val minVisibleScrimHeight: () -> Float = {
        screenHeight - maxScrimTop() - with(density) { navBarHeight.toPx() }
    }

    val isRemoteInputActive by viewModel.isRemoteInputActive.collectAsStateWithLifecycle(false)

    // The bottom Y bound of the currently focused remote input notification.
    val remoteInputRowBottom by viewModel.remoteInputRowBottomBound.collectAsStateWithLifecycle(0f)

    // The top y bound of the IME.
    val imeTop = remember { mutableFloatStateOf(0f) }

    val shadeScrollState by
        shadeSession.rememberSession(key = "ShadeScrollState") {
            derivedStateOf {
                ShadeScrollState(
                    // we are not scrolled to the top unless the scroll position is zero,
                    // and the scrim is at its maximum offset
                    isScrolledToTop = scrimOffset.value >= 0f && scrollState.value == 0,
                    scrollPosition = scrollState.value,
                    maxScrollPosition = scrollState.maxValue,
                )
            }
        }

    LaunchedEffect(shadeScrollState) { viewModel.setScrollState(shadeScrollState) }

    // if contentHeight drops below minimum visible scrim height while scrim is
    // expanded and IME is not showing, reset scrim offset.
    LaunchedEffect(stackHeight, scrimOffset, imeTop) {
        snapshotFlow {
                stackHeight.intValue < minVisibleScrimHeight() &&
                    scrimOffset.value < 0f &&
                    imeTop.floatValue <= 0f
            }
            .collect { shouldCollapse -> if (shouldCollapse) scrimOffset.animateTo(0f, tween()) }
    }

    // if we receive scroll delta from NSSL, offset the scrim and placeholder accordingly.
    LaunchedEffect(syntheticScroll, scrimOffset, scrollState) {
        snapshotFlow { syntheticScroll.value }
            .collect { delta ->
                scrollNotificationStack(
                    delta = delta,
                    animate = false,
                    scrimOffset = scrimOffset,
                    minScrimOffset = minScrimOffset,
                    scrollState = scrollState,
                )
            }
    }

    // if remote input state changes, compare the row and IME's overlap and offset the scrim and
    // placeholder accordingly.
    LaunchedEffect(isRemoteInputActive, remoteInputRowBottom, imeTop) {
        imeTop.floatValue = 0f
        snapshotFlow { imeTop.floatValue }
            .collect { imeTopValue ->
                // only scroll the stack if ime value has been populated (ime placeholder has been
                // composed at least once), and our remote input row overlaps with the ime bounds.
                if (isRemoteInputActive && imeTopValue > 0f && remoteInputRowBottom > imeTopValue) {
                    scrollNotificationStack(
                        delta = remoteInputRowBottom - imeTopValue,
                        animate = true,
                        scrimOffset = scrimOffset,
                        minScrimOffset = minScrimOffset,
                        scrollState = scrollState,
                    )
                }
            }
    }

    // TalkBack sends a scroll event, when it wants to navigate to an item that is not displayed in
    // the current viewport.
    LaunchedEffect(viewModel) {
        viewModel.setAccessibilityScrollEventConsumer { event ->
            // scroll up, or down by the height of the visible portion of the notification stack
            val direction =
                when (event) {
                    AccessibilityScrollEvent.SCROLL_UP -> -1
                    AccessibilityScrollEvent.SCROLL_DOWN -> 1
                }
            val viewPortHeight = stackBoundsOnScreen.value.height
            val scrollStep = max(0f, viewPortHeight - stackScrollView.stackBottomInset)
            val scrollPosition = scrollState.value.toFloat()
            val scrollRange = scrollState.maxValue.toFloat()
            val targetScroll = (scrollPosition + direction * scrollStep).coerceIn(0f, scrollRange)
            coroutineScope.launch {
                scrollNotificationStack(
                    delta = targetScroll - scrollPosition,
                    animate = false,
                    scrimOffset = scrimOffset,
                    minScrimOffset = minScrimOffset,
                    scrollState = scrollState,
                )
            }
        }
        try {
            awaitCancellation()
        } finally {
            viewModel.setAccessibilityScrollEventConsumer(null)
        }
    }

    val scrimNestedScrollConnection =
        if (supportNestedScrolling) {
            shadeSession.rememberSession(
                key = "ScrimConnection",
                scrimOffset,
                minScrimTop,
                density,
            ) {
                val flingSpec: DecayAnimationSpec<Float> = splineBasedDecay(density)
                val flingBehavior = NotificationScrimFlingBehavior(flingSpec)
                NotificationScrimNestedScrollConnection(
                    scrimOffset = { scrimOffset.value },
                    snapScrimOffset = { value ->
                        coroutineScope.launch { scrimOffset.snapTo(value) }
                    },
                    animateScrimOffset = { value ->
                        coroutineScope.launch { scrimOffset.animateTo(value) }
                    },
                    minScrimOffset = minScrimOffset,
                    maxScrimOffset = 0f,
                    contentHeight = { stackHeight.intValue.toFloat() },
                    minVisibleScrimHeight = minVisibleScrimHeight,
                    flingBehavior = flingBehavior,
                )
            }
        } else {
            null
        }

    val swipeToExpandNotificationScrollConnection =
        shadeSession.rememberSession(
            key = "SwipeToExpandNotificationScrollConnection",
            scrimOffset,
            minScrimTop,
            density,
            viewModel.isCurrentGestureExpandingNotification,
        ) {
            PriorityNestedScrollConnection(
                orientation = Orientation.Vertical,
                canStartPreScroll = { _, _, _ -> false },
                canStartPostScroll = { _, _, _ -> viewModel.isCurrentGestureExpandingNotification },
                onStart = { firstScroll ->
                    object : ScrollController {
                        override fun onScroll(
                            deltaScroll: Float,
                            source: NestedScrollSource,
                        ): Float {
                            return if (viewModel.isCurrentGestureExpandingNotification) {
                                // consume all the amount, when this swipe is expanding a
                                // notification
                                deltaScroll
                            } else {
                                // don't consume anything, when the expansion is done
                                0f
                            }
                        }

                        override fun onCancel() {
                            // No-op
                        }

                        override fun canStopOnPreFling(): Boolean = false

                        override suspend fun OnStopScope.onStop(initialVelocity: Float): Float = 0f
                    }
                },
            )
        }

    val overScrollEffect: OffsetOverscrollEffect = rememberOffsetOverscrollEffect()
    // whether the stack is moving due to a swipe or fling
    val isScrollInProgress =
        scrollState.isScrollInProgress || overScrollEffect.isInProgress || scrimOffset.isRunning

    LaunchedEffect(isScrollInProgress) {
        if (isScrollInProgress) {
            jankMonitor.begin(composeViewRoot, CUJ_NOTIFICATION_SHADE_SCROLL_FLING)
            debugLog(viewModel) { "STACK scroll begins" }
        } else {
            debugLog(viewModel) { "STACK scroll ends" }
            jankMonitor.end(CUJ_NOTIFICATION_SHADE_SCROLL_FLING)
        }
    }

    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier =
            modifier
                .element(Notifications.Elements.NotificationScrim)
                .overscroll(verticalOverscrollEffect)
                .thenIf(supportNestedScrolling) {
                    Modifier.layout { measurable, constraints ->
                        // Adjust the ScrimOffset during layout transitions.
                        val yOffset =
                            calculateScrimOffset(
                                scrimOffset,
                                viewModel,
                                expansionFraction,
                                shadeToQsFraction,
                            )
                        // Shrink the scrim height by the amount it is translated down, but still
                        // respect the original constraints to support shared element transitions.
                        val constrainedHeight =
                            constraints.constrainHeight(
                                // This change modifies the scrim animation to ensure its height
                                // expands to fill the entire screen by the end of the transition.
                                // TODO(b/438706987) Due to this complexity, the animation may need
                                //  to be revisited later as part of a larger refactor.
                                lerp(
                                    constraints.maxHeight,
                                    constraints.maxHeight + minScrimOffset().roundToInt() - yOffset,
                                    expansionFraction,
                                )
                            )
                        val placeable =
                            measurable.measure(
                                constraints =
                                    constraints.copy(
                                        minHeight = constrainedHeight,
                                        maxHeight = constrainedHeight,
                                    )
                            )
                        layout(width = placeable.width, height = placeable.height) {
                            placeable.place(IntOffset(x = 0, y = yOffset))
                        }
                    }
                }
                .graphicsLayer {
                    shape =
                        calculateCornerRadius(
                                scrimCornerRadius,
                                screenCornerRadius,
                                { expansionFraction },
                                shouldAnimateScrimCornerRadius(
                                    layoutState,
                                    shouldPunchHoleBehindScrim,
                                    viewModel.notificationsShadeContentKey,
                                ),
                            )
                            .let { scrimRounding.value.toRoundedCornerShape(it) }
                    clip = true
                }
                .onGloballyPositioned { coordinates ->
                    val boundsInWindow = coordinates.boundsInWindow()
                    debugLog(viewModel) {
                        "SCRIM onGloballyPositioned:" +
                            " size=${coordinates.size}" +
                            " bounds=$boundsInWindow"
                    }
                    viewModel.onScrimBoundsChanged(
                        ShadeScrimBounds(
                            left = boundsInWindow.left,
                            top = boundsInWindow.top,
                            right = boundsInWindow.right,
                            bottom = boundsInWindow.bottom,
                        )
                    )
                }
                .thenIf(onEmptySpaceClick != null) {
                    Modifier.clickable(
                        interactionSource = interactionSource,
                        indication = null, // Prevent flicker on transition
                        onClick = { onEmptySpaceClick?.invoke() },
                    )
                }
    ) {
        Spacer(
            modifier =
                Modifier.thenIf(shouldFillMaxSize) { Modifier.fillMaxSize() }
                    .drawBehind { drawRect(Color.Black, blendMode = BlendMode.DstOut) }
        )
        Box(
            modifier =
                Modifier.graphicsLayer {
                        alpha = (expansionFraction / EXPANSION_FOR_MAX_SCRIM_ALPHA).coerceAtMost(1f)
                    }
                    .thenIf(shouldShowScrim) { Modifier.background(surfaceEffect0Color) }
                    .thenIf(shouldFillMaxSize) { Modifier.fillMaxSize() }
                    .padding(
                        top = stackTopPadding,
                        bottom =
                            if (supportNestedScrolling) minScrimTop + stackBottomPadding
                            else stackBottomPadding,
                    )
                    .onGloballyPositioned {
                        if (!shouldUseLockscreenStackBounds(layoutState.transitionState)) {
                            stackScrollView.updateDrawBounds(it.rawBoundsInWindow())
                        }
                    }
                    .debugBackground(viewModel, DEBUG_BOX_COLOR)
        ) {
            Column(
                modifier =
                    Modifier.disableSwipesWhenScrolling()
                        .nestedScroll(swipeToExpandNotificationScrollConnection)
                        .thenIf(supportNestedScrolling && scrimNestedScrollConnection != null) {
                            Modifier.nestedScroll(scrimNestedScrollConnection!!)
                        }
                        .verticalScroll(scrollState, overscrollEffect = overScrollEffect)
                        .fillMaxWidth()
                        // Added extra bottom padding for keeping footerView inside parent
                        // Viewbounds during overscroll, refer to b/437347340#comment3
                        .padding(bottom = 4.dp)
                        .onGloballyPositioned { coordinates ->
                            stackBoundsOnScreen.value = coordinates.boundsInWindow()
                        }
            ) {
                NotificationPlaceholder(
                    stackScrollView = stackScrollView,
                    viewModel = viewModel,
                    useStackBounds = {
                        !shouldUseLockscreenStackBounds(layoutState.transitionState)
                    },
                    modifier =
                        Modifier.notificationStackHeight(view = stackScrollView).onSizeChanged {
                            size ->
                            stackHeight.intValue = size.height
                        },
                )
                Spacer(
                    modifier =
                        Modifier.windowInsetsBottomHeight(WindowInsets.imeAnimationTarget)
                            .onGloballyPositioned { coordinates: LayoutCoordinates ->
                                imeTop.floatValue = screenHeight - coordinates.size.height
                            }
                )
            }
        }
        if (shouldIncludeHeadsUpSpace) {
            HeadsUpNotificationSpace(
                stackScrollView = stackScrollView,
                viewModel = viewModel,
                useHunBounds = {
                    !shouldUseLockscreenHunBounds(
                        layoutState.transitionState,
                        viewModel.quickSettingsShadeContentKey,
                    )
                },
                modifier = Modifier.padding(top = stackTopPadding),
            )
        }
    }
}

/**
 * Calculate the correct NotificationScrim offset during layout transitions.
 *
 * If scrim is expanded while transitioning to Gone or QS scene, increase the offset in step with
 * the corresponding transition so that it is 0 when it completes.
 */
private fun ContentScope.calculateScrimOffset(
    scrimOffset: Animatable<Float, AnimationVector1D>,
    viewModel: NotificationsPlaceholderViewModel,
    expansionFraction: Float,
    shadeToQsFraction: Float,
) =
    if (
        scrimOffset.value < 0 &&
            (layoutState.isTransitioning(
                from = viewModel.notificationsShadeContentKey,
                to = Scenes.Gone,
            ) ||
                layoutState.isTransitioning(
                    from = viewModel.notificationsShadeContentKey,
                    to = Scenes.Lockscreen,
                ))
    ) {
        (scrimOffset.value * expansionFraction).roundToInt()
    } else if (
        scrimOffset.value < 0 &&
            layoutState.isTransitioning(from = Scenes.Shade, to = Scenes.QuickSettings)
    ) {
        (scrimOffset.value * (1 - shadeToQsFraction)).roundToInt()
    } else {
        scrimOffset.value.roundToInt()
    }

@Composable
private fun ContentScope.NotificationPlaceholder(
    stackScrollView: NotificationScrollView,
    viewModel: NotificationsPlaceholderViewModel,
    useStackBounds: () -> Boolean,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .element(Notifications.Elements.NotificationStackPlaceholder)
                .debugBackground(viewModel, DEBUG_STACK_COLOR)
                .onSizeChanged { size -> debugLog(viewModel) { "STACK onSizeChanged: size=$size" } }
                .onGloballyPositioned { coordinates: LayoutCoordinates ->
                    // This element is opted out of the shared element system, so there can be
                    // multiple instances of it during a transition. Thus we need to determine which
                    // instance should feed its bounds to NSSL to avoid providing conflicting values
                    val useBounds = useStackBounds()
                    if (useBounds) {
                        // NOTE: positionInWindow.y scrolls off screen, but boundsInWindow.top won't
                        val positionInWindow = coordinates.positionInWindow()
                        debugLog(viewModel) {
                            "STACK onGloballyPositioned:" +
                                " size=${coordinates.size}" +
                                " position=$positionInWindow" +
                                " bounds=${coordinates.boundsInWindow()}"
                        }
                        stackScrollView.setStackTop(positionInWindow.y)
                    }
                }
    )
}

private suspend fun scrollNotificationStack(
    delta: Float,
    animate: Boolean,
    scrimOffset: Animatable<Float, AnimationVector1D>,
    minScrimOffset: () -> Float,
    scrollState: ScrollState,
) {
    val minOffset = minScrimOffset()
    if (scrimOffset.value > minOffset) {
        val remainingDelta =
            (minOffset - (scrimOffset.value - delta)).coerceAtLeast(0f).roundToInt()
        if (remainingDelta > 0) {
            if (animate) {
                // launch a new coroutine for the remainder animation so that it doesn't suspend the
                // scrim animation, allowing both to play simultaneously.
                coroutineScope { launch { scrollState.animateScrollTo(remainingDelta) } }
            } else {
                scrollState.scrollTo(remainingDelta)
            }
        }
        val newScrimOffset = (scrimOffset.value - delta).coerceAtLeast(minOffset)
        if (animate) {
            scrimOffset.animateTo(newScrimOffset)
        } else {
            scrimOffset.snapTo(newScrimOffset)
        }
    } else {
        if (animate) {
            scrollState.animateScrollBy(delta)
        } else {
            scrollState.scrollBy(delta)
        }
    }
}

private fun TransitionState.isOnLockscreen(): Boolean {
    return currentScene == Scenes.Lockscreen && currentOverlays.isEmpty()
}

private fun shouldUseLockscreenStackBounds(state: TransitionState): Boolean {
    return when (state) {
        is TransitionState.Idle -> state.isOnLockscreen()
        is TransitionState.Transition ->
            // Keep using the lockscreen stack bounds when there is no placeholder on the next
            // content
            state.fromContent == Scenes.Lockscreen && state.toContent != Scenes.Shade ||
                state.isTransitioningBetween(content = Scenes.Lockscreen, other = Overlays.Bouncer)
    }
}

private fun shouldUseLockscreenHunBounds(
    state: TransitionState,
    quickSettingsShade: ContentKey,
): Boolean {
    return when (state) {
        is TransitionState.Idle -> state.isOnLockscreen()
        is TransitionState.Transition ->
            state.isTransitioning(from = quickSettingsShade, to = Scenes.Lockscreen)
    }
}

private fun shouldAnimateScrimCornerRadius(
    state: SceneTransitionLayoutState,
    shouldPunchHoleBehindScrim: Boolean,
    notificationsShade: ContentKey,
): Boolean {
    return shouldPunchHoleBehindScrim ||
        state.isTransitioning(from = notificationsShade, to = Scenes.Lockscreen)
}

private fun calculateCornerRadius(
    scrimCornerRadius: Dp,
    screenCornerRadius: Dp,
    expansionFraction: () -> Float,
    transitioning: Boolean,
): Dp {
    return if (transitioning) {
        lerp(
                start = screenCornerRadius.value,
                stop = scrimCornerRadius.value,
                fraction = (expansionFraction() / EXPANSION_FOR_MAX_CORNER_RADIUS).coerceIn(0f, 1f),
            )
            .dp
    } else {
        scrimCornerRadius
    }
}

private fun calculateHeadsUpPlaceholderYOffset(
    scrollOffset: Int,
    minScrollOffset: Int,
    topHeadsUpHeight: Int,
): Int {
    return -minScrollOffset +
        (scrollOffset * (-minScrollOffset + topHeadsUpHeight) / -minScrollOffset)
}

private fun velocityOrPositionalThresholdReached(
    scrollOffset: Float,
    minScrollOffset: Float,
    availableVelocityY: Float,
): Boolean {
    return availableVelocityY < HUN_SNOOZE_VELOCITY_THRESHOLD ||
        (availableVelocityY <= 0f &&
            scrollOffset < minScrollOffset * HUN_SNOOZE_POSITIONAL_THRESHOLD_FRACTION)
}

/**
 * Takes a range, current value, and delta, and updates the current value by the delta, coercing the
 * result within the given range. Returns how much of the delta was consumed.
 */
private fun consumeDeltaWithinRange(
    current: Float,
    setCurrent: (Float) -> Unit,
    min: Float,
    max: Float,
    delta: Float,
): Float {
    return if (delta < 0 && current > min) {
        val remainder = (current + delta - min).coerceAtMost(0f)
        setCurrent((current + delta).coerceAtLeast(min))
        delta - remainder
    } else if (delta > 0 && current < max) {
        val remainder = (current + delta).coerceAtLeast(0f)
        setCurrent((current + delta).coerceAtMost(max))
        delta - remainder
    } else 0f
}

private inline fun debugLog(viewModel: NotificationsPlaceholderViewModel, msg: () -> Any) {
    if (viewModel.isDebugLoggingEnabled) {
        Log.d(TAG, msg().toString())
    }
}

private fun Modifier.debugBackground(
    viewModel: NotificationsPlaceholderViewModel,
    color: Color,
): Modifier =
    if (viewModel.isVisualDebuggingEnabled) {
        background(color)
    } else {
        this
    }

private fun ShadeScrimRounding.toRoundedCornerShape(radius: Dp): RoundedCornerShape {
    val topRadius = if (isTopRounded) radius else 0.dp
    val bottomRadius = if (isBottomRounded) radius else 0.dp
    return RoundedCornerShape(
        topStart = topRadius,
        topEnd = topRadius,
        bottomStart = bottomRadius,
        bottomEnd = bottomRadius,
    )
}

private const val TAG = "FlexiNotifs"
private val DEBUG_STACK_COLOR = Color(1f, 0f, 0f, 0.2f)
private val DEBUG_HUN_COLOR = Color(0f, 0f, 1f, 0.2f)
private val DEBUG_BOX_COLOR = Color(0f, 1f, 0f, 0.2f)
private const val HUN_SNOOZE_POSITIONAL_THRESHOLD_FRACTION = 0.25f
private const val HUN_SNOOZE_VELOCITY_THRESHOLD = -70f

/**
 * The boundaries of this layout relative to the window's origin, without being clipped to the
 * window bounds.
 *
 * This is different from [boundsInWindow], which clips the bounds to the window. Unclipped bounds
 * are needed when a layout is positioned off-screen, for example during a scene transition.
 */
private fun LayoutCoordinates.rawBoundsInWindow(): android.graphics.RectF {
    val root = findRootCoordinates()

    val bounds = root.localBoundingBoxOf(this)
    val boundsLeft = bounds.left
    val boundsTop = bounds.top
    val boundsRight = bounds.right
    val boundsBottom = bounds.bottom

    if (boundsLeft == boundsRight || boundsTop == boundsBottom) {
        return android.graphics.RectF()
    }

    val topLeft = root.localToWindow(Offset(boundsLeft, boundsTop))
    val topRight = root.localToWindow(Offset(boundsRight, boundsTop))
    val bottomRight = root.localToWindow(Offset(boundsRight, boundsBottom))
    val bottomLeft = root.localToWindow(Offset(boundsLeft, boundsBottom))

    val left = fastMinOf(topLeft.x, topRight.x, bottomLeft.x, bottomRight.x)
    val right = fastMaxOf(topLeft.x, topRight.x, bottomLeft.x, bottomRight.x)

    val top = fastMinOf(topLeft.y, topRight.y, bottomLeft.y, bottomRight.y)
    val bottom = fastMaxOf(topLeft.y, topRight.y, bottomLeft.y, bottomRight.y)

    return android.graphics.RectF(left, top, right, bottom)
}
