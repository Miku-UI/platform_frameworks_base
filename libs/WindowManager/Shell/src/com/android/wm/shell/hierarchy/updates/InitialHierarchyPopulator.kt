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

package com.android.wm.shell.hierarchy.updates

import android.app.ActivityManager
import android.util.Pair
import android.view.SurfaceControl
import android.window.DisplayAreaInfo
import androidx.annotation.VisibleForTesting
import com.android.wm.shell.Flags
import com.android.wm.shell.RootDisplayAreaOrganizer
import com.android.wm.shell.RootTaskDisplayAreaOrganizer
import com.android.wm.shell.ShellTaskOrganizer
import com.android.wm.shell.hierarchy.ContainerHierarchy
import com.android.wm.shell.hierarchy.containers.Container
import com.android.wm.shell.hierarchy.modes.Mode
import com.android.wm.shell.hierarchy.properties.DisplayAreaContainerProperties
import com.android.wm.shell.hierarchy.properties.DisplayContainerProperties
import com.android.wm.shell.hierarchy.properties.TaskContainerProperties
import com.android.wm.shell.hierarchy.utils.HierarchyUtils
import com.android.wm.shell.sysui.ShellInit

/**
 * Wrapper class to populate the initial state of the hierarchy.
 * NOTE: This entire class can be removed once b/463244413 is fixed
 */
class InitialHierarchyPopulator(
    private val shellTaskOrganizer: ShellTaskOrganizer,
    private val rootDisplayAreaOrganizer: RootDisplayAreaOrganizer,
    private val taskDisplayAreaOrganizer: RootTaskDisplayAreaOrganizer,
    private val hierarchy: ContainerHierarchy,
    private val updater: HierarchyUpdater,
    shellInit: ShellInit,
) {
    init {
        if (Flags.enableShellModes()) {
            shellInit.addInitCallback(this::onInit, this)
        }
    }

    @VisibleForTesting
    fun onInit() {
        val snapshot = HierarchySnapshot(hierarchy.toContainerList())

        // Populate the initial hierarchy in order
        addInitialRootDisplayAreas(rootDisplayAreaOrganizer.getInitialRootDisplayAreas())
        addInitialTaskDisplayAreas(taskDisplayAreaOrganizer.getInitialTaskDisplayAreas())
        addInitialTasks(shellTaskOrganizer.getInitialTasks())

        updater.notifyModes(Mode.UpdateContext(reason = "Initial population"), snapshot)
    }

    @VisibleForTesting
    fun addInitialRootDisplayAreas(displayAreas: List<Pair<DisplayAreaInfo, SurfaceControl>>) {
        for (da in displayAreas) {
            val daInfo = da.first
            val properties = DisplayContainerProperties(daInfo.displayId)
            properties.config = daInfo.configuration
            val container = Container(
                token = daInfo.token,
                props = properties,
            )
            container.leash = da.second
            container.parent = hierarchy.root
        }
    }

    @VisibleForTesting
    fun addInitialTaskDisplayAreas(displayAreas: List<Pair<DisplayAreaInfo, SurfaceControl>>) {
        for (da in displayAreas) {
            val daInfo = da.first
            val properties = DisplayAreaContainerProperties(daInfo.featureId)
            properties.config = daInfo.configuration
            val container = Container(
                token = daInfo.token,
                props = properties,
            )
            container.leash = da.second
            container.parent = hierarchy.getDisplay(daInfo.displayId)
        }
    }

    @VisibleForTesting
    fun addInitialTasks(rawTasks: List<Pair<ActivityManager.RunningTaskInfo, SurfaceControl>>) {
        // Create all the tasks
        val tasks = mutableListOf<Container>()
        for (task in rawTasks) {
            val taskInfo = task.first
            val properties = TaskContainerProperties(taskInfo)
            properties.config = taskInfo.configuration
            val container = Container(
                token = taskInfo.token,
                props = properties,
            )
            container.leash = task.second
            tasks.add(container)
        }

        // Sort the list by increasing parent task id (with no parents (-1) first)
        tasks.sortWith { a, b ->
            a.taskProps().taskInfo.parentTaskId
            -b.taskProps().taskInfo.parentTaskId
        }

        for (container in tasks) {
            val displayId = container.taskProps().taskInfo.displayId
            container.parent =
                HierarchyUtils.resolveParentForContainer(hierarchy.root, container, null, displayId)
        }
    }
}