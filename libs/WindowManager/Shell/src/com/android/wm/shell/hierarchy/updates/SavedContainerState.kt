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

import com.android.wm.shell.hierarchy.containers.Container
import com.android.wm.shell.hierarchy.updates.HierarchySnapshot.Companion.CHANGED_CHILDREN
import com.android.wm.shell.hierarchy.updates.HierarchySnapshot.Companion.CHANGED_MODE
import com.android.wm.shell.hierarchy.updates.HierarchySnapshot.Companion.CHANGED_PARENT
import com.android.wm.shell.hierarchy.utils.HierarchyUtils

/**
 * Holds information about the prior state of a container during a single update session by making
 * copies of the state.
 */
class SavedContainerState(
    container: Container
) {
    val modeContainer = HierarchyUtils.getModeContainer(container)
    val mode = modeContainer?.mode
    val props = container.props.copy()
    val parentToken = container.parent?.token
    val children = container.children.toList()

    /**
     * Compares the current state and the saved state and returns a diff of the changes.
     */
    fun diff(container: Container, chgs: HierarchyChangeFlags) {
        props.diff(container.props, chgs)
        chgs.compareAndSet(mode, HierarchyUtils.getMode(container), CHANGED_MODE)
        chgs.compareAndSet(parentToken, container.parent?.token, CHANGED_PARENT)
        chgs.compareAndSet(children, container.children, CHANGED_CHILDREN)
    }
}