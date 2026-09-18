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
package com.android.wm.shell.hierarchy

import android.window.WindowContainerToken
import com.android.wm.shell.hierarchy.containers.Container
import com.android.wm.shell.hierarchy.properties.RootContainerProperties
import com.android.wm.shell.hierarchy.updates.HierarchyUpdateRequester
import com.android.wm.shell.hierarchy.utils.HierarchyUtils

/**
 * The hierarchy of containers being managed by Shell. This hierarchy should only be modified from
 * requests on the HierarchyUpdateRequester.
 */
class ContainerHierarchy {
    /**
     * The ordering of the hierarchy closely matches the WindowManager & SurfaceFlinger hierarchy,
     * children are listed in z-order from bottom to top.
     */
    var root: Container =
        Container(WindowContainerToken.createProxy(":root"), RootContainerProperties())

    /**
     * The requester to request updates to this hierarchy.
     */
    var update: HierarchyUpdateRequester = NoOpUpdateRequester()

    //
    // Hierarchy convenience methods
    //

    /**
     * Returns a container with the given token.
     */
    fun getContainer(token: WindowContainerToken): Container? =
        HierarchyUtils.getContainer(root, token)

    /**
     * Returns the display container with the given displayId.
     */
    fun getDisplay(displayId: Int): Container? = HierarchyUtils.getDisplay(root, displayId)

    /**
     * Returns the display area container with the given displayId.
     */
    fun getTaskDisplayArea(displayId: Int): Container? =
        HierarchyUtils.getTaskDisplayArea(root, displayId)

    /**
     * Returns the task container with the given taskId.
     */
    fun getTask(taskId: Int): Container? = HierarchyUtils.getTask(root, taskId)

    /**
     * Returns a list of containers in top-down breadth-first traversal order.
     */
    fun toContainerList(): List<Container> = HierarchyUtils.toContainersList(root)
}

/**
 * A no-op implementation of a requester.
 */
private class NoOpUpdateRequester : HierarchyUpdateRequester {}