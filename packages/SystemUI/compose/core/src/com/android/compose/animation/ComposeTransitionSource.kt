package com.android.compose.animation

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

import android.content.ComponentName
import android.view.View
import android.view.ViewGroupOverlay
import androidx.compose.foundation.BorderStroke
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.android.systemui.animation.ActivityTransitionAnimator
import com.android.systemui.animation.ComposableControllerFactory
import com.android.systemui.animation.DialogCuj
import com.android.systemui.animation.DialogTransitionAnimator
import com.android.systemui.animation.TransitionAnimator
import com.android.systemui.animation.TransitionSource

class ComposeTransitionSource(
    override val color: () -> Color,
    override val contentColor: Color,
    override val shape: Shape,
    override val borderStroke: BorderStroke?,
    override val composeViewRoot: View,
    override val density: Density,
    override val transitionControllerFactory: ComposableControllerFactory?,
    private val layoutDirection: LayoutDirection,
    private val isComposed: () -> Boolean,
) : TransitionSource, ExpandableController {

    override var isSourceVisible: Boolean = true
        set(value) {
            this@ComposeTransitionSource.isDialogShowing = !value
            field = value
        }

    override var onSourceVisibilityChanged: (Boolean) -> Unit = {}

    override var animatorState by mutableStateOf<TransitionAnimator.State?>(null)

    override var isDialogShowing by mutableStateOf(!isSourceVisible)

    override var overlay by mutableStateOf<ViewGroupOverlay?>(null)

    override var currentComposeViewInOverlay by mutableStateOf<View?>(null)

    override var boundsInComposeViewRoot by mutableStateOf(Rect.Zero)

    private var activityControllerForDisposal: ActivityTransitionAnimator.Controller? = null

    override var currentNodeInOverlay: DrawModifierNode? = null

    private val mutableTransitionState =
        object : TransitionDelegate.MutableTransitionState {
            override var animatorState: TransitionAnimator.State?
                get() = this@ComposeTransitionSource.animatorState
                set(value) {
                    this@ComposeTransitionSource.animatorState = value
                }

            override var isDialogShowing: Boolean
                get() = this@ComposeTransitionSource.isDialogShowing
                set(value) {
                    this@ComposeTransitionSource.isDialogShowing = value
                    onSourceVisibilityChanged(!value)
                }

            override var overlay: ViewGroupOverlay?
                get() = this@ComposeTransitionSource.overlay
                set(value) {
                    this@ComposeTransitionSource.overlay = value
                }

            override var currentComposeViewInOverlay: View?
                get() = this@ComposeTransitionSource.currentComposeViewInOverlay
                set(value) {
                    this@ComposeTransitionSource.currentComposeViewInOverlay = value
                }

            override var boundsInComposeViewRoot: Rect
                get() = this@ComposeTransitionSource.boundsInComposeViewRoot
                set(value) {
                    this@ComposeTransitionSource.boundsInComposeViewRoot = value
                }

            override var activityControllerForDisposal: ActivityTransitionAnimator.Controller?
                get() = this@ComposeTransitionSource.activityControllerForDisposal
                set(value) {
                    this@ComposeTransitionSource.activityControllerForDisposal = value
                }

            override var currentNodeInOverlay: DrawModifierNode?
                get() = this@ComposeTransitionSource.currentNodeInOverlay
                set(value) {
                    this@ComposeTransitionSource.currentNodeInOverlay = value
                }
        }

    private val transitionDelegate: TransitionDelegate =
        TransitionDelegate(
            shape,
            composeViewRoot,
            density,
            layoutDirection,
            isComposed,
            mutableTransitionState,
        )

    override val transitionSource: TransitionSource
        get() = this

    override val isAnimating: Boolean by derivedStateOf { animatorState != null && overlay != null }

    override fun onDispose() {
        transitionDelegate.disposeController()
    }

    override fun dialogTransitionController(cuj: DialogCuj?): DialogTransitionAnimator.Controller {
        return transitionDelegate.dialogController(cuj)
    }

    override fun activityTransitionController(
        launchCujType: Int?,
        cookie: ActivityTransitionAnimator.TransitionCookie?,
        component: ComponentName?,
        returnCujType: Int?,
        isEphemeral: Boolean,
    ): ActivityTransitionAnimator.Controller {
        return transitionDelegate.activityController(
            launchCujType,
            cookie,
            component,
            returnCujType,
        )
    }
}
