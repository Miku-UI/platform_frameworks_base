/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.wm.shell.hierarchy.experimental.testsplit

import android.app.WindowConfiguration
import android.content.Context
import android.graphics.Color
import android.graphics.Rect
import android.graphics.RectF
import android.util.Log
import android.view.Gravity
import android.view.SurfaceControl
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.WindowManager.TRANSIT_CHANGE
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.util.TypedValue
import android.window.TaskCreationParams
import android.window.TransitionInfo
import android.window.WindowAnimationState
import android.window.WindowContainerToken
import android.window.WindowContainerTransaction
import com.android.wm.shell.hierarchy.ContainerHierarchy
import com.android.wm.shell.hierarchy.containers.Container
import com.android.wm.shell.hierarchy.containers.ViewOverlayContainer
import com.android.wm.shell.hierarchy.modes.Mode
import com.android.wm.shell.hierarchy.modes.handheld.HandheldModeRequester
import com.android.wm.shell.hierarchy.properties.DisplayContainerProperties
import com.android.wm.shell.hierarchy.properties.TaskContainerProperties
import com.android.wm.shell.hierarchy.updates.HierarchySnapshot
import com.android.wm.shell.hierarchy.utils.HierarchyUtils
import com.android.wm.shell.transition.AnimationPlan
import com.android.wm.shell.transition.DetachResult
import com.android.wm.shell.transition.ITransitionAnimation
import java.io.PrintWriter

/**
 * A Simple mode to simulate PIP.
 * Draws a small window in bottom right corner with overlay buttons to exit the mode.
 */
class PipMode(
    private val appContext: Context,
    private val hierarchy: ContainerHierarchy,
    private val modeRequester: HandheldModeRequester,
) : Mode {
    private val TAG = PipMode::class.simpleName

    private val rootsPerDisplay = mutableMapOf<Int, Container>()
    private val overlays = mutableMapOf<WindowContainerToken, ViewOverlayContainer>()

    private var activeContainer: Container? = null

    override fun prepareForDisplay(updateContext: Mode.UpdateContext, display: Container) {
        val displayId = display.props<DisplayContainerProperties>().displayId
        Log.d(TAG, "prepareForDisplay: $displayId")

        // Create root task for PIP
        // TaskOrganizer.createTask throws IllegalArgumentException with PINNED and non-activity.
        // Using MULTI_WINDOW instead.
        val params =
            TaskCreationParams.Builder()
                .setName("PipMode_Root_$displayId")
                .setDisplayId(displayId)
                .setWindowingMode(WindowConfiguration.WINDOWING_MODE_MULTI_WINDOW)
                .build()

        val pipRoot = hierarchy.update.createTask(params, this)
        rootsPerDisplay[displayId] = pipRoot

        val windowContext = display.displayProps().createWindowContext(appContext)
        setupOverlay(updateContext, pipRoot, windowContext)
    }

    private fun setupOverlay(updateContext: Mode.UpdateContext, root: Container, windowContext: Context) {
        val density = root.props.config.densityDpi / 160f
        val overlay =
            ViewOverlayContainer(
                WindowContainerToken.createProxy(":pip_mode_overlay"),
                { callbackContext, _ -> inflateUi(callbackContext, root) },
            )

        overlay.parent = root
        overlay.initialize(windowContext)
        overlays[root.token] = overlay

        val tx = updateContext.preTransitionTx!!
        overlay.surface.setLayer(tx, Integer.MAX_VALUE).hide(tx)
    }

    private fun inflateUi(context: Context, root: Container): View {
        val parent = FrameLayout(context)
        val containerLayout =
            LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
            }
            containerLayout.addView(
                createOverlayButton(context, "CLEAR", Color.argb(200, 255, 0, 0)) {
                    exitPipMode(root)
                },
                LinearLayout.LayoutParams(0, MATCH_PARENT).apply { weight = 1f },
            )

            containerLayout.addView(
                createOverlayButton(context, "SplitMode", Color.argb(200, 0, 0, 255)) {
                    val child = activeContainer
                    if (child != null) {
                        val display = HierarchyUtils.getAncestorDisplay(child)
                        val wct = modeRequester.requestEnterSplitMode(child,
                            Mode.EnterRequestContext(display!!.displayProps().displayId)
                        )
                        wct?.let {
                            hierarchy.update.wm("PIP back to split", TRANSIT_CHANGE, it)
                        }
                        activeContainer = null
                    }
                },
                LinearLayout.LayoutParams(0, MATCH_PARENT).apply { weight = 1f },
            )

            parent.addView(containerLayout, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
            return parent
    }

    private fun createOverlayButton(context: Context, label: String, color: Int, onClick: () -> Unit): FrameLayout {
        return FrameLayout(context).apply {
            setBackgroundColor(color)
            addView(
                TextView(context).apply {
                    text = label
                    gravity = Gravity.CENTER
                    setTextColor(Color.WHITE)
                    // TODO figure out why we need to set such a high number.
                    //  is it wrong context?
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 100f)
                }
            )
            setOnClickListener { onClick() }
        }
    }

    private fun exitPipMode(root: Container) {
        val child = activeContainer ?: return
        Log.d(TAG, "Exiting PIP mode for $child")

        val displayId = root.props<TaskContainerProperties>().taskInfo.displayId
        val tda = HierarchyUtils.getTaskDisplayArea(hierarchy.root, displayId)

        val wct = WindowContainerTransaction()
        wct.reparent(child.token, tda?.token, true)
        wct.setWindowingMode(child.token, WindowConfiguration.WINDOWING_MODE_FULLSCREEN)

        val overlay = overlays[root.token]
        if (overlay != null) {
            // TODO: Hide overlay via transaction?
        }

        hierarchy.update.wm("Exit PIP", TRANSIT_CHANGE, wct)
        activeContainer = null
    }

    override fun requestEnterMode(
        task: Container,
        request: Mode.EnterRequestContext,
        wct: WindowContainerTransaction,
    ): Boolean {
        Log.d(TAG, "Entering PIP mode for $task")
        val displayId = request.displayId
        val root = rootsPerDisplay[displayId] ?: return false
        wct.reparent(task.token, root.token, true)
        wct.reorder(root.token, true)

        // Initial bounds
        val displayBounds = root.props.bounds
        val density = appContext.resources.displayMetrics.density
        val sizePx = (200 * density).toInt()
        val marginPx = (100 * density).toInt()
        val right = displayBounds.right - marginPx
        val bottom = displayBounds.bottom - marginPx
        val left = right - sizePx
        val top = bottom - sizePx

        wct.setBounds(task.token, Rect(left.toInt(), top.toInt(), right.toInt(), bottom.toInt()))

        activeContainer = task
        return true
    }

    override fun containersChanged(
        updateContext: Mode.UpdateContext,
        enteringContainers: List<Container>,
        changedContainers: List<Container>,
        globalStateChanged: Boolean,
        snapshot: HierarchySnapshot,
        animationPlan: AnimationPlan?,
    ) {
        val subject = (enteringContainers + changedContainers).firstOrNull() ?: return
        val displayId = subject.props<TaskContainerProperties>().taskInfo.displayId
        val root = rootsPerDisplay[displayId] ?: return

        val overlay = overlays[root.token] ?: return
        val child = activeContainer

        val tx = updateContext.preTransitionTx ?: return

        if (child != null && child.parent == root) {
            val displayBounds = root.props.bounds

            val density = appContext.resources.displayMetrics.density
            val sizePx = (200 * density).toInt()
            val marginPx = (100 * density).toInt()

            val right = displayBounds.right - marginPx
            val bottom = displayBounds.bottom - marginPx
            val left = right - sizePx
            val top = bottom - sizePx
            val overlaySizeH = (80 * density).toInt()

            // Overlay is above the PIP
            val overlayBounds = RectF(left, top - overlaySizeH, right, top)

            overlay.surface.setBounds(tx, overlayBounds).show(tx)
        } else {
            overlay.surface.hide(tx)
        }

        if (animationPlan != null) {
            val changes = snapshot.getChanges(subject)
            val parentChanged = changes.get(HierarchySnapshot.CHANGED_PARENT)
            val modeChanged = changes.get(HierarchySnapshot.CHANGED_MODE)
            val windowingModeChanged = changes.get(HierarchySnapshot.CHANGED_WINDOWING_MODE)

            if (parentChanged || modeChanged || windowingModeChanged) {
                animationPlan.setAnimation(subject.token, PipEnterAnimation(subject, snapshot))
            }
        }
    }

    override fun containersRemoved(
        updateContext: Mode.UpdateContext,
        leavingContainers: List<Container>,
        removedContainers: List<Container>,
        snapshot: HierarchySnapshot,
        animationPlan: AnimationPlan?,
    ) {
        val child = activeContainer
        if (child != null && leavingContainers.contains(child)) {
            Log.d(TAG, "activeContainer leaving PIP, hiding overlay")
            val displayId = child.props<TaskContainerProperties>().taskInfo.displayId
            val root = rootsPerDisplay[displayId]
            val overlay = root?.let { overlays[it.token] }
            val tx = updateContext.preTransitionTx
            if (overlay != null && tx != null) {
                overlay.surface.hide(tx)
            }
            activeContainer = null
        }
    }

    override fun requestUpdateForDisplayChange(
        directlyAssignedContainer: Container,
        curDisplayProps: DisplayContainerProperties,
        newDisplayProps: DisplayContainerProperties,
        wct: WindowContainerTransaction,
    ) {
        val root = rootsPerDisplay[newDisplayProps.displayId] ?: return
        val child = activeContainer ?: return

        if (child.parent == root) {
            val displayBounds = newDisplayProps.bounds
            val density = appContext.resources.displayMetrics.density
            val sizePx = (200 * density).toInt()

            val right = displayBounds.right
            val bottom = displayBounds.bottom
            val left = right - sizePx
            val top = bottom - sizePx

            val bounds = Rect(left.toInt(), top.toInt(), right.toInt(), bottom.toInt())
            wct.setBounds(child.token, bounds)
        }
    }

    override fun onShellCommand(displayId: Int, args: MutableList<String>, pw: PrintWriter) {}

    private inner class PipEnterAnimation(val subject: Container, val snapshot: HierarchySnapshot) :
        ITransitionAnimation {
        override fun getDebugName() = "PipEnterAnimation"

        override fun detach(
            tokens: List<WindowContainerToken>,
            t: SurfaceControl.Transaction,
        ): DetachResult {
            return DetachResult(emptyList())
        }

        override fun start(
            info: TransitionInfo,
            from: List<WindowAnimationState>,
            cb: ITransitionAnimation.IFinishedCallback,
        ) {
            // TODO: Use the `from` WindowAnimationState to seamlessly continue mid-animation
            //  transitions.
            // start bounds from snapshot
            val startState = snapshot.snapshots[subject]
            val startBounds = startState?.props?.bounds
            val endBounds = subject.props.bounds

            Log.d(TAG, "Anim Start: subject=$subject startBounds=$startBounds endBounds=$endBounds")

            val tx = SurfaceControl.Transaction()
            val surf = subject.leash
            val anim = android.animation.ValueAnimator.ofFloat(0f, 1f)
            anim.duration = 700

            // Ensure surface is visible and on top during animation
            // Likely this is redundant but just in case.
            tx.show(surf)
            tx.apply()

            anim.addUpdateListener { a ->
                val f = a.animatedValue as Float
                if (startBounds != null && !startBounds.isEmpty) {
                    val currLeft = startBounds.left + (endBounds.left - startBounds.left) * f
                    val currTop = startBounds.top + (endBounds.top - startBounds.top) * f

                    val currW =
                        startBounds.width() +
                            (endBounds.width().coerceAtLeast(1f) - startBounds.width()) * f
                    val currH =
                        startBounds.height() +
                            (endBounds.height().coerceAtLeast(1f) - startBounds.height()) * f

                    val scaleX = currW / endBounds.width().coerceAtLeast(1f)
                    val scaleY = currH / endBounds.height().coerceAtLeast(1f)

                    Log.d(
                        TAG,
                        "Anim update: f=$f pos=($currLeft, $currTop) scale=($scaleX, $scaleY)",
                    )

                    tx.setPosition(surf, currLeft, currTop)
                    tx.setScale(surf, scaleX, scaleY)
                }
                tx.setAlpha(surf, 1f)
                tx.apply()
            }
            anim.addListener(
                object : android.animation.AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: android.animation.Animator) {
                        Log.d(TAG, "Anim End")
                        // Ensure final state is clean
                        tx.setPosition(surf, endBounds.left, endBounds.top)
                        tx.setScale(surf, 1f, 1f)
                        tx.setAlpha(surf, 1f)
                        tx.apply()
                        tx.close()
                        cb.onFinished(null)
                    }
                }
            )
            anim.start()
        }
    }
}
