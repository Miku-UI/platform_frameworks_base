/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.compose.animation

import android.view.View
import android.view.ViewGroupOverlay
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.android.systemui.animation.ActivityTransitionAnimator
import com.android.systemui.animation.ComposableControllerFactory
import com.android.systemui.animation.Expandable
import com.android.systemui.animation.TransitionAnimator
import com.android.systemui.animation.TransitionSource

/** A controller that can control animated launches from an [Expandable]. */
@Stable
interface ExpandableController {
    /** The [TransitionSource] controlled by this controller. */
    val transitionSource: TransitionSource

    /** Whether this controller is currently animating a launch. */
    val isAnimating: Boolean

    /** Called when the [Expandable] stop being included in the composition. */
    fun onDispose()

    /** The current animation state, if we are currently animating a dialog or activity. */
    var animatorState: TransitionAnimator.State?

    /** Whether a dialog controlled by this ExpandableController is currently showing. */
    var isDialogShowing: Boolean

    /** The overlay in which we should animate the launch. */
    var overlay: ViewGroupOverlay?

    /** The current [ComposeView] being animated in the [overlay], if any. */
    var currentComposeViewInOverlay: View?

    /** The bounds in [composeViewRoot] of the expandable controlled by this controller. */
    var boundsInComposeViewRoot: Rect
    /**
     * The current [DrawModifierNode] in the overlay, drawing the expandable during a transition.
     */
    var currentNodeInOverlay: DrawModifierNode?

    /** Provides the background color of the Expandable composable. */
    val color: () -> Color

    /** The content color (for text/icons) used inside the Expandable composable. */
    val contentColor: Color

    /** The shape used to clip and draw the background of the Expandable composable. */
    val shape: Shape

    /** The optional border stroke applied to the Expandable composable. */
    val borderStroke: BorderStroke?

    /** The root Android [View] that hosts the Compose hierarchy. */
    val composeViewRoot: View

    /** The current screen density, used for converting Dp to Pixels. */
    val density: Density

    /** The factory used to create new composable controllers for transitions, if applicable. */
    val transitionControllerFactory: ComposableControllerFactory?
}

/**
 * Create an [ExpandableController] to control an [Expandable]. This is useful if you need to create
 * the controller before the [Expandable], for instance to handle clicks outside of the Expandable
 * that would still trigger a dialog/activity launch animation.
 */
@Composable
fun rememberExpandableController(
    color: Color,
    shape: Shape,
    contentColor: Color = contentColorFor(color),
    borderStroke: BorderStroke? = null,
    transitionControllerFactory: ComposableControllerFactory? = null,
): ExpandableController {
    return rememberExpandableController(
        color = { color },
        shape = shape,
        contentColor = contentColor,
        borderStroke = borderStroke,
        transitionControllerFactory = transitionControllerFactory,
    )
}

/** Create an [ExpandableController] to control an [Expandable]. */
@Composable
fun rememberExpandableController(
    color: () -> Color,
    shape: Shape,
    contentColor: Color = Color.Unspecified,
    borderStroke: BorderStroke? = null,
    transitionControllerFactory: ComposableControllerFactory? = null,
): ExpandableController {
    val composeViewRoot = LocalView.current
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current

    // Whether this composable is still composed. We only do the dialog exit animation if this is
    // true.
    var isComposed by remember { mutableStateOf(true) }

    val controller =
        remember(
            color,
            contentColor,
            shape,
            borderStroke,
            composeViewRoot,
            density,
            layoutDirection,
            transitionControllerFactory,
        ) {
            if (TransitionAnimator.dynamicTargetResolutionEnabled()) {
                ComposeTransitionSource(
                    color,
                    contentColor,
                    shape,
                    borderStroke,
                    composeViewRoot,
                    density,
                    transitionControllerFactory,
                    layoutDirection,
                    { isComposed },
                )
            } else {
                ExpandableControllerImpl(
                    color,
                    contentColor,
                    shape,
                    borderStroke,
                    composeViewRoot,
                    density,
                    transitionControllerFactory,
                    layoutDirection,
                    { isComposed },
                )
            }
        }

    DisposableEffect(Unit) {
        onDispose {
            isComposed = false
            controller.onDispose()
        }
    }

    return controller
}

internal class ExpandableControllerImpl(
    override val color: () -> Color,
    override val contentColor: Color,
    override val shape: Shape,
    override val borderStroke: BorderStroke?,
    override val composeViewRoot: View,
    override val density: Density,
    override val transitionControllerFactory: ComposableControllerFactory?,
    private val layoutDirection: LayoutDirection,
    private val isComposed: () -> Boolean,
) : ExpandableController {

    override var animatorState by mutableStateOf<TransitionAnimator.State?>(null)

    override var isDialogShowing by mutableStateOf(false)

    override var overlay by mutableStateOf<ViewGroupOverlay?>(null)

    override var currentComposeViewInOverlay by mutableStateOf<View?>(null)

    override var boundsInComposeViewRoot by mutableStateOf(Rect.Zero)

    private var activityControllerForDisposal: ActivityTransitionAnimator.Controller? = null

    /**
     * The current [DrawModifierNode] in the overlay, drawing the expandable during a transition.
     */
    override var currentNodeInOverlay: DrawModifierNode? = null

    override val isAnimating: Boolean by derivedStateOf { animatorState != null && overlay != null }

    private val mMutableTransitionState =
        object : TransitionDelegate.MutableTransitionState {
            override var animatorState: TransitionAnimator.State?
                get() = this@ExpandableControllerImpl.animatorState
                set(value) {
                    this@ExpandableControllerImpl.animatorState = value
                }

            override var isDialogShowing: Boolean
                get() = this@ExpandableControllerImpl.isDialogShowing
                set(value) {
                    this@ExpandableControllerImpl.isDialogShowing = value
                }

            override var overlay: ViewGroupOverlay?
                get() = this@ExpandableControllerImpl.overlay
                set(value) {
                    this@ExpandableControllerImpl.overlay = value
                }

            override var currentComposeViewInOverlay: View?
                get() = this@ExpandableControllerImpl.currentComposeViewInOverlay
                set(value) {
                    this@ExpandableControllerImpl.currentComposeViewInOverlay = value
                }

            override var boundsInComposeViewRoot: Rect
                get() = this@ExpandableControllerImpl.boundsInComposeViewRoot
                set(value) {
                    this@ExpandableControllerImpl.boundsInComposeViewRoot = value
                }

            override var activityControllerForDisposal: ActivityTransitionAnimator.Controller?
                get() = this@ExpandableControllerImpl.activityControllerForDisposal
                set(value) {
                    this@ExpandableControllerImpl.activityControllerForDisposal = value
                }

            override var currentNodeInOverlay: DrawModifierNode?
                get() = this@ExpandableControllerImpl.currentNodeInOverlay
                set(value) {
                    this@ExpandableControllerImpl.currentNodeInOverlay = value
                }
        }

    private val transitionDelegate: TransitionDelegate =
        TransitionDelegate(
            shape,
            composeViewRoot,
            density,
            layoutDirection,
            isComposed,
            mMutableTransitionState,
        )

    override val transitionSource: TransitionSource
        get() = transitionDelegate.source

    override fun onDispose() {
        transitionDelegate.disposeController()
    }
}
