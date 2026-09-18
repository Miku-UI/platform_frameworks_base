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

package com.android.compose.animation

import android.content.ComponentName
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroupOverlay
import android.view.ViewRootImpl
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.android.internal.jank.InteractionJankMonitor
import com.android.systemui.animation.ActivityTransitionAnimator
import com.android.systemui.animation.DialogCuj
import com.android.systemui.animation.DialogTransitionAnimator
import com.android.systemui.animation.TransitionAnimator
import com.android.systemui.animation.TransitionSource
import kotlin.math.roundToInt

/** Holds the actual implementation details and business logic for the transitions. */
class TransitionDelegate(
    // Immutable Data dependencies
    private val shape: Shape,
    private val composeViewRoot: View,
    private val density: Density,
    private val layoutDirection: LayoutDirection,
    private val isComposed: () -> Boolean,

    // Mutable State access (via delegation/lambda to the Impl's backing field)
    private val mutableTransitionState: MutableTransitionState,
) {

    // Helper interface to access and manipulate the mutable state
    interface MutableTransitionState {
        var animatorState: TransitionAnimator.State?
        var isDialogShowing: Boolean
        var overlay: ViewGroupOverlay?
        var currentComposeViewInOverlay: View?
        var boundsInComposeViewRoot: Rect
        var activityControllerForDisposal: ActivityTransitionAnimator.Controller?
        var currentNodeInOverlay: DrawModifierNode?
    }

    private val transitionSource: TransitionSource by lazy {
        object : TransitionSource {

            override var isSourceVisible: Boolean = true

            override var onSourceVisibilityChanged: (Boolean) -> Unit = {}

            override fun activityTransitionController(
                launchCujType: Int?,
                cookie: ActivityTransitionAnimator.TransitionCookie?,
                component: ComponentName?,
                returnCujType: Int?,
                isEphemeral: Boolean,
            ): ActivityTransitionAnimator.Controller? {
                if (!isComposed()) return null

                val controller = activityController(launchCujType, cookie, component, returnCujType)
                if (isEphemeral) {
                    mutableTransitionState.activityControllerForDisposal?.onDispose()
                    mutableTransitionState.activityControllerForDisposal = controller
                }
                return controller
            }

            override fun dialogTransitionController(
                cuj: DialogCuj?
            ): DialogTransitionAnimator.Controller? {
                if (!isComposed()) return null
                return dialogController(cuj)
            }
        }
    }

    // Public accessor for the Impl class
    internal val source: TransitionSource
        get() = transitionSource

    fun disposeController() {
        mutableTransitionState.activityControllerForDisposal?.onDispose()
        mutableTransitionState.activityControllerForDisposal = null
    }

    fun activityController(
        launchCujType: Int?,
        cookie: ActivityTransitionAnimator.TransitionCookie?,
        component: ComponentName?,
        returnCujType: Int?,
    ): ActivityTransitionAnimator.Controller {
        val delegate = transitionController()
        return object :
            ActivityTransitionAnimator.Controller, TransitionAnimator.Controller by delegate {
            private val cujType: Int?
                get() = if (isLaunching) launchCujType else returnCujType

            override val transitionCookie = cookie
            override val component = component

            override fun onTransitionAnimationStart(isExpandingFullyAbove: Boolean) {
                delegate.onTransitionAnimationStart(isExpandingFullyAbove)
                mutableTransitionState.overlay = transitionContainer.overlay as ViewGroupOverlay
                cujType?.let { InteractionJankMonitor.getInstance().begin(composeViewRoot, it) }
            }

            override fun onTransitionAnimationEnd(isExpandingFullyAbove: Boolean) {
                cujType?.let { InteractionJankMonitor.getInstance().end(it) }
                delegate.onTransitionAnimationEnd(isExpandingFullyAbove)
                mutableTransitionState.overlay = null
            }
        }
    }

    fun dialogController(cuj: DialogCuj?): DialogTransitionAnimator.Controller {
        return object : DialogTransitionAnimator.Controller {
            override val viewRoot: ViewRootImpl? = composeViewRoot.viewRootImpl
            override val dialogIdentity: Any = this@TransitionDelegate
            override val cuj: DialogCuj? = cuj

            override fun startDrawingInOverlayOf(viewGroup: ViewGroup) {
                val newOverlay = viewGroup.overlay as ViewGroupOverlay
                if (newOverlay != mutableTransitionState.overlay) {
                    mutableTransitionState.overlay = newOverlay
                }
            }

            override fun stopDrawingInOverlay() {
                if (mutableTransitionState.overlay != null) {
                    mutableTransitionState.overlay = null
                }
            }

            override fun createTransitionController(): TransitionAnimator.Controller {
                val delegate = transitionController()
                return object : TransitionAnimator.Controller by delegate {
                    override fun onTransitionAnimationEnd(isExpandingFullyAbove: Boolean) {
                        delegate.onTransitionAnimationEnd(isExpandingFullyAbove)
                        mutableTransitionState.isDialogShowing = true
                    }
                }
            }

            override fun createExitController(): TransitionAnimator.Controller {
                val delegate = transitionController()
                return object : TransitionAnimator.Controller by delegate {
                    override fun onTransitionAnimationEnd(isExpandingFullyAbove: Boolean) {
                        delegate.onTransitionAnimationEnd(isExpandingFullyAbove)
                        mutableTransitionState.isDialogShowing = false
                    }
                }
            }

            override fun shouldAnimateExit(): Boolean {
                return isComposed() && composeViewRoot.isAttachedToWindow && composeViewRoot.isShown
            }

            override fun onExitAnimationCancelled() {
                mutableTransitionState.isDialogShowing = false
            }

            override fun jankConfigurationBuilder(): InteractionJankMonitor.Configuration.Builder? {
                val type = cuj?.cujType ?: return null
                return InteractionJankMonitor.Configuration.Builder.withView(type, composeViewRoot)
            }
        }
    }

    /** Private helper containing the common animator state logic. */
    private fun transitionController(): TransitionAnimator.Controller {
        return object : TransitionAnimator.Controller {
            private val rootLocationOnScreen = intArrayOf(0, 0)
            override var transitionContainer: ViewGroup = composeViewRoot.rootView as ViewGroup
            override val isLaunching: Boolean = true

            override fun onTransitionAnimationEnd(isExpandingFullyAbove: Boolean) {
                mutableTransitionState.animatorState = null
                mutableTransitionState.currentNodeInOverlay?.invalidateDraw()
            }

            override fun onTransitionAnimationProgress(
                state: TransitionAnimator.State,
                progress: Float,
                linearProgress: Float,
            ) {
                mutableTransitionState.animatorState =
                    TransitionAnimator.State(
                            state.top,
                            state.bottom,
                            state.left,
                            state.right,
                            state.topCornerRadius,
                            state.bottomCornerRadius,
                        )
                        .apply { visible = state.visible }

                mutableTransitionState.currentComposeViewInOverlay?.let {
                    measureAndLayoutComposeViewInOverlay(it, state)
                }
                mutableTransitionState.currentNodeInOverlay?.invalidateDraw()
            }

            override fun createAnimatorState(): TransitionAnimator.State {
                val boundsInRoot = mutableTransitionState.boundsInComposeViewRoot
                val outline =
                    shape.createOutline(
                        Size(boundsInRoot.width, boundsInRoot.height),
                        layoutDirection,
                        density,
                    )

                val (topCornerRadius, bottomCornerRadius) =
                    when (outline) {
                        is Outline.Rectangle -> 0f to 0f
                        is Outline.Rounded -> {
                            val roundRect = outline.roundRect

                            // TODO(b/230830644): Add better support different corner radii.
                            val topCornerRadius =
                                maxOf(
                                    roundRect.topLeftCornerRadius.x,
                                    roundRect.topLeftCornerRadius.y,
                                    roundRect.topRightCornerRadius.x,
                                    roundRect.topRightCornerRadius.y,
                                )
                            val bottomCornerRadius =
                                maxOf(
                                    roundRect.bottomLeftCornerRadius.x,
                                    roundRect.bottomLeftCornerRadius.y,
                                    roundRect.bottomRightCornerRadius.x,
                                    roundRect.bottomRightCornerRadius.y,
                                )

                            topCornerRadius to bottomCornerRadius
                        }
                        else ->
                            error(
                                "ExpandableState only supports (rounded) rectangles at the " +
                                    "moment."
                            )
                    }

                val rootLocation = rootLocationOnScreen()
                return TransitionAnimator.State(
                    top = rootLocation.y.roundToInt(),
                    bottom = (rootLocation.y + boundsInRoot.height).roundToInt(),
                    left = rootLocation.x.roundToInt(),
                    right = (rootLocation.x + boundsInRoot.width).roundToInt(),
                    topCornerRadius = topCornerRadius,
                    bottomCornerRadius = bottomCornerRadius,
                )
            }

            private fun rootLocationOnScreen(): Offset {
                composeViewRoot.getLocationOnScreen(rootLocationOnScreen)
                val boundsInRoot = mutableTransitionState.boundsInComposeViewRoot
                val x = rootLocationOnScreen[0] + boundsInRoot.left
                val y = rootLocationOnScreen[1] + boundsInRoot.top
                return Offset(x, y)
            }
        }
    }
}
