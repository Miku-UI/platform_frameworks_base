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
import java.util.BitSet

val EMPTY_SNAPSHOT = HierarchySnapshot()

/**
 * Stores a set of hierarchy change flags.
 */
class HierarchyChangeFlags(vararg flags: Int) : BitSet() {
    init {
        for (f in flags) {
            set(f)
        }
    }

    /**
     * Convenience helper to compare two values and set the given flag in the change set if they are
     * different.
     */
    fun <T> compareAndSet(value: T, other: T, flag: Int) {
        if (value != other) {
            set(flag)
        }
    }

    /**
     * Returns whether the only change is the children.
     */
    fun isOnlyChildrenChange(): Boolean {
        return cardinality() == 1 && this[CHANGED_CHILDREN]
    }
}

/**
 * Tracks changes to the hierarchy within a single update session.
 */
class HierarchySnapshot {

    // Do not use containers in this list for parent relationships, use the parent token in the
    // container snapshot
    val preUpdateContainers: List<Container>
    val snapshots = mutableMapOf<Container, SavedContainerState>()

    /**
     * Constructor for default initialization and testing
     */
    constructor() {
        preUpdateContainers = listOf()
    }

    /**
     * Saves a snapshots of the provided containers.
     */
    constructor(containers: List<Container>) {
        preUpdateContainers = containers
        for (container in containers) {
            snapshots[container] = SavedContainerState(container)
        }
    }

    /**
     * Returns the flag set of changes on the given container compared to the saved state.
     */
    fun getChanges(container: Container): HierarchyChangeFlags {
        val chgs = HierarchyChangeFlags()
        if (container in snapshots) {
            val snapshot = snapshots.getValue(container)
            snapshot.diff(container, chgs)
        }
        return chgs
    }

    /**
     * Returns whether the given container specifically has changed.
     */
    fun hasChanges(container: Container): Boolean {
        return !getChanges(container).isEmpty
    }

    /**
     * Returns a list of any ancestors that have changed in a way that interesting for descendants.
     * The returned list is in no specific order.
     */
    fun getChangedAncestors(modeContainer: Container): List<Container> {
        val ancestors = mutableListOf<Container>()
        var c: Container? = modeContainer.parent
        while (c != null) {
            if (areInterestingChangesToChildren(getChanges(c))) {
                ancestors.add(c)
            }
            c = c.parent
        }
        return ancestors
    }

    /**
     * Returns whether specific changes are interesting to descendant container's modes.
     *
     * ie. When a display container changes display state, generally all modes with containers under
     *     that display will be interested in being notified.
     */
    private fun areInterestingChangesToChildren(changeFlags: HierarchyChangeFlags): Boolean {
        // Descendants should only care about changes to their direct ancestors, so ignore
        // children-only changes
        return changeFlags.cardinality() >= 1 && !changeFlags[CHANGED_CHILDREN]
    }

    companion object {
        // Flags describing changes to a generic container.
        // Changes to the below should also be reflected in `HierarchyDebugUtils.flagsToStr()`.
        const val CHANGED_PARENT = 1
        const val CHANGED_MODE = 2
        const val CHANGED_CHILDREN = 3
        const val CHANGED_BOUNDS = 4
        const val CHANGED_ROTATION = 5
        const val CHANGED_WINDOWING_MODE = 6
        const val CHANGED_VISIBILITY = 7

        // Changes to a root container
        const val ROOT_CONTAINER_OFFSET = 200
        const val CHANGED_FOCUS = ROOT_CONTAINER_OFFSET + 1
        const val CHANGED_USER = ROOT_CONTAINER_OFFSET + 2
        const val CHANGED_USER_PROFILES = ROOT_CONTAINER_OFFSET + 3
        const val CHANGED_KEYGUARD = ROOT_CONTAINER_OFFSET + 4
        const val CHANGED_IS_FOLDED = ROOT_CONTAINER_OFFSET + 5

        // Changes to a display container
        const val DISPLAY_CONTAINER_OFFSET = 300
        const val CHANGED_INSETS = DISPLAY_CONTAINER_OFFSET + 1

        // Changes to a task container
        const val TASK_CONTAINER_OFFSET = 400
        const val CHANGED_TASK_DESCRIPTION = TASK_CONTAINER_OFFSET + 1
        const val CHANGED_PIP_PARAMS = TASK_CONTAINER_OFFSET + 2

        // Can be used by form factors to extend and provide their own set of change flags
        const val CUSTOM_CHANGE_OFFSET = 1000
    }
}