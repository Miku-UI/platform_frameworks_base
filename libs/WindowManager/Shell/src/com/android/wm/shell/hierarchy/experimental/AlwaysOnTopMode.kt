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
import android.graphics.Rect
import android.view.WindowManager.TRANSIT_CHANGE
import android.widget.FrameLayout
import android.window.TaskCreationParams
import android.window.WindowContainerToken
import android.window.WindowContainerTransaction
import com.android.wm.shell.hierarchy.ContainerHierarchy
import com.android.wm.shell.hierarchy.containers.Container
import com.android.wm.shell.hierarchy.containers.ViewOverlayContainer
import com.android.wm.shell.hierarchy.modes.Mode
import com.android.wm.shell.hierarchy.properties.DisplayContainerProperties
import com.android.wm.shell.hierarchy.properties.RootContainerProperties
import com.android.wm.shell.hierarchy.utils.HierarchyUtils
import java.io.PrintWriter
import kotlin.collections.set

import com.android.wm.shell.hierarchy.modes.handheld.HandheldModeRequester

/**
 * An example mode implementation for a non-trivial windowing feature.
 *
 * This windowing mode is always on top, and is not fullscreen.
 */
class AlwaysOnTopMode(
    private val appContext: Context,
    private val hierarchy: ContainerHierarchy,
    private val modeRequester: HandheldModeRequester,
) : Mode {
    private val rootsPerDisplay = mutableMapOf<Int, Container>()
    private val overlays = mutableMapOf<WindowContainerToken, ViewOverlayContainer>()

    /** @see Mode.prepareForDisplay */
    override fun prepareForDisplay(updateContext: Mode.UpdateContext, display: Container) {
        // Create a new root task
        val displayId = display.props<DisplayContainerProperties>().displayId
        val params = TaskCreationParams.Builder()
            .setName("AlwaysOnTopMode_${displayId}")
            .setDisplayId(displayId)
            .setWindowingMode(WindowConfiguration.WINDOWING_MODE_MULTI_WINDOW)
            .build()
        val root = hierarchy.update.createTask(params, this)

        // Set the initial state of the root task
        val wct = WindowContainerTransaction()
        wct.setBounds(root.token, Rect(500, 500, 1000, 1000))
        wct.setAlwaysOnTop(root.token, true)
        hierarchy.update.wm("Setup AlwaysOnTopMode root", TRANSIT_CHANGE, wct)
        rootsPerDisplay[displayId] = root

        // Create an overlay that can launch tasks into this root
        val overlay = ViewOverlayContainer(
            WindowContainerToken.createProxy(":always_on_top_mode_overlay"),
            { context, _ ->
                FrameLayout(context)
            },
            overrideWidth = 200,
            overrideHeight = 200,
        )
        overlay.parent = root
        overlay.initialize(display.displayProps().createWindowContext(appContext))
        val tx = updateContext.preTransitionTx!!
        overlay.surface
            .setLayer(tx, Integer.MAX_VALUE)
            .show(tx)
        overlays[root.token] = overlay
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

    /** @see Mode.requestEnterMode */
    override fun requestEnterMode(
        task: Container,
        request: Mode.EnterRequestContext,
        wct: WindowContainerTransaction
    ): Boolean {
        val root = rootsPerDisplay[request.displayId]!!
        wct.reparent(task.token, root.token, true)
        return true
    }

    /** @see Mode.onShellCommand */
    override fun onShellCommand(displayId: Int, args: MutableList<String>, pw: PrintWriter) {
        val action = args.removeFirst()
        when (action.lowercase()) {
            "launch_new_activity" -> {
                // Launch an activity into the specified root
                val opts = ActivityOptions.makeBasic().apply {
                    setLaunchRootTask(rootsPerDisplay[displayId]!!.token)
                }

                val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_BROWSER)
                intent.setFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
                appContext.startActivity(intent, opts.toBundle())
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