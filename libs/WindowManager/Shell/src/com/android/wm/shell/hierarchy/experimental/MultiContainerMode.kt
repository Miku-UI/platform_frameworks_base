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

package com.android.wm.shell.hierarchy.experimental

import android.app.ActivityOptions
import android.app.WindowConfiguration
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Rect
import android.graphics.RectF
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.WindowManager.TRANSIT_CHANGE
import android.widget.FrameLayout
import android.window.TaskCreationParams
import android.window.WindowContainerToken
import android.window.WindowContainerTransaction
import androidx.core.graphics.toRect
import com.android.wm.shell.hierarchy.ContainerHierarchy
import com.android.wm.shell.hierarchy.containers.Container
import com.android.wm.shell.hierarchy.containers.ViewOverlayContainer
import com.android.wm.shell.hierarchy.modes.Mode
import com.android.wm.shell.hierarchy.properties.DisplayContainerProperties
import com.android.wm.shell.hierarchy.properties.RootContainerProperties
import com.android.wm.shell.hierarchy.utils.HierarchyUtils
import java.io.PrintWriter

import com.android.wm.shell.hierarchy.modes.handheld.HandheldModeRequester

/**
 * An example mode implementation for a non-trivial windowing feature.
 *
 * This windowing mode lays out tasks in a horizontal list which can be scrolled, like in Overview,
 * which can be reordered.
 */
class MultiContainerMode(
    private val appContext: Context,
    private val hierarchy: ContainerHierarchy,
    private val modeRequester: HandheldModeRequester,
) : Mode {
    private val rootsPerDisplay = mutableMapOf<Int, Container>()
    private val rootChildren = mutableMapOf<Container, MutableList<Container>>()
    private val overlays = mutableMapOf<WindowContainerToken, ViewOverlayContainer>()

    /** @see Mode.prepareForDisplay */
    override fun prepareForDisplay(updateContext: Mode.UpdateContext, display: Container) {
        val displayId = display.props<DisplayContainerProperties>().displayId
        // Create a root task
        val params = TaskCreationParams.Builder()
            .setName("MultiContainerMode_${displayId}")
            .setDisplayId(displayId)
            .setWindowingMode(WindowConfiguration.WINDOWING_MODE_FULLSCREEN)
            .build()
        val root = hierarchy.update.createTask(params, this)

        rootsPerDisplay[displayId] = root
        rootChildren[root] = mutableListOf()

        // Create an overlay that can launch tasks into this root
        val overlay = ViewOverlayContainer(
            WindowContainerToken.createProxy(":multi_container_mode_overlay"),
            { context, _ ->
                val rootView = FrameLayout(context)

                // Create two buttons in the root view that will add and remove sub containers
                val button1 = FrameLayout(context)
                button1.setLayoutParams(FrameLayout.LayoutParams(100, MATCH_PARENT))
                button1.setBackgroundColor(Color.argb(128, 0, 255, 0))
                button1.setOnClickListener {
                    launchExampleAppIntoContainer(addContainer(displayId))
                }
                val button2 = FrameLayout(context)
                button2.setBackgroundColor(Color.argb(128, 255, 0, 0))
                button2.setLayoutParams(FrameLayout.LayoutParams(100, MATCH_PARENT).apply {
                    leftMargin = 100
                })
                button2.setOnClickListener {
                    removeLastContainer(displayId)
                }

                rootView.addView(button1)
                rootView.addView(button2)
                rootView
            },
            overrideWidth = 400,
            overrideHeight = 200,
        )
        overlay.parent = root
        overlay.initialize(display.displayProps().createWindowContext(appContext))

        val newBounds = RectF(overlay.props.bounds)
        newBounds.offset(100f, 100f)

        val tx = updateContext.preTransitionTx!!
        overlay.surface
            .setBounds(tx, newBounds)
            .setLayer(tx, Integer.MAX_VALUE)
            .show(tx)
        overlays[root.token] = overlay
    }

    private fun addContainer(displayId: Int): WindowContainerToken {
        val root = rootsPerDisplay.getValue(displayId)

        // Create a child root task
        val params = TaskCreationParams.Builder()
            .setName("MultiContainerMode_Child_${displayId}")
            .setDisplayId(displayId)
            .setWindowingMode(WindowConfiguration.WINDOWING_MODE_MULTI_WINDOW)
            .build()
        val childRoot = hierarchy.update.createTask(params)
        val childRoots = rootChildren.getValue(root)
        childRoots.add(childRoot)

        // Not sure why setParentContainer() is not working but just reparent it for now
        val wct = WindowContainerTransaction()
        wct.reparent(childRoot.token, root.token, true)
        layoutChildren(root.props.bounds, childRoots, wct)
        hierarchy.update.wm("add child container", TRANSIT_CHANGE, wct)
        return childRoot.token
    }

    private fun launchExampleAppIntoContainer(token: WindowContainerToken) {
        val opts = ActivityOptions.makeBasic().apply {
            setLaunchRootTask(token)
        }
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_BROWSER)
        intent.setFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
        appContext.startActivity(intent, opts.toBundle())
    }

    private fun layoutChildren(
        rootBounds: RectF,
        childRoots: List<Container>,
        wct: WindowContainerTransaction
    ) {
        println("${childRoots} ${childRoots.size}")
        println("${rootBounds}")
        // Layout all the children
        val childBounds = Array(childRoots.size) {
            Rect()
        }
        rootBounds.toRect().splitVertically(*childBounds)
        for ((i, container) in childRoots.withIndex()) {
            println("Child bounds: ${childBounds[i]}")
            wct.setBounds(container.token, childBounds[i])
        }
    }

    private fun removeLastContainer(displayId: Int) {
        val root = rootsPerDisplay.getValue(displayId)
        val childRoots = rootChildren.getValue(root)
        if (childRoots.isEmpty()) {
            println("No roots to remove")
            return
        }

        // Remove the last child root
        val lastChildRoot = childRoots.removeLast()
        hierarchy.update.removeTask(lastChildRoot)

        // Layout the children again
        val wct = WindowContainerTransaction()
        if (childRoots.isEmpty()) {
            wct.reorder(root.token, false, true)
        } else {
            layoutChildren(root.props.bounds, childRoots, wct)
        }
        hierarchy.update.wm("remove child container", TRANSIT_CHANGE, wct)
    }

    /** @see Mode.cleanupForDisplay */
    override fun cleanupForDisplay(updateContext: Mode.UpdateContext, display: Container) {
        val displayId = display.props<DisplayContainerProperties>().displayId
        val root = rootsPerDisplay.remove(displayId)
        hierarchy.update.removeTask(root!!)

        val overlay = overlays.remove(root.token)
        if (overlay != null) {
            val tx = updateContext.preTransitionTx!!
            overlay.release(tx)
            overlay.parent = null
        }
    }

    /** @see Mode.displayChanging */
    override fun requestUpdateForDisplayChange(
        directlyAssignedContainer: Container,
        curDisplayProps: DisplayContainerProperties,
        newDisplayProps: DisplayContainerProperties,
        wct: WindowContainerTransaction
    ) {
        val root = rootsPerDisplay.getValue(curDisplayProps.displayId)
        val childRoots = rootChildren.getValue(root)
        if (childRoots.isEmpty()) {
            println("No roots to remove")
            return
        }
        layoutChildren(newDisplayProps.bounds, childRoots, wct)
    }

    /** @see Mode.requestEnterMode */
    override fun requestEnterMode(
        task: Container,
        request: Mode.EnterRequestContext,
        wct: WindowContainerTransaction
    ): Boolean {
        val childRootToken = addContainer(request.displayId)
        wct.reparent(task.token, childRootToken, true)
        wct.reorder(childRootToken, true, true)
        return true
    }

    /** @see Mode.requestEnterMode */
    override fun onShellCommand(displayId: Int, args: MutableList<String>, pw: PrintWriter) {
        val action = args.removeFirst()
        when (action.lowercase()) {
            "launch_new_activity" -> {
                launchExampleAppIntoContainer(addContainer(displayId))
            }

            "move_focused_task" -> {
                val rootProps = hierarchy.root.props<RootContainerProperties>()
                val taskId = rootProps.focusState.globallyFocusedTaskId
                val task = hierarchy.getTask(taskId)
                if (task == null) {
                    pw.println("No task with id=$taskId")
                    return
                }
                if (HierarchyUtils.getMode(task) == this) {
                    pw.println("Task id=$taskId is already associated with ${getId()}")
                    return
                }

                val wct = WindowContainerTransaction()
                requestEnterMode(task, Mode.EnterRequestContext(displayId), wct)
                hierarchy.update.wm(
                    "Moving focused task $taskId into ${getId()}",
                    TRANSIT_CHANGE,
                    wct
                )
            }

            else -> {
                pw.println("Unknown action: $action")
            }
        }
    }
}