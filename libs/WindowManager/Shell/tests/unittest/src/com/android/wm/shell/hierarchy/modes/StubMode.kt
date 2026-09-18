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
package com.android.wm.shell.hierarchy.modes

import android.window.WindowContainerTransaction
import com.android.wm.shell.hierarchy.containers.Container
import com.android.wm.shell.hierarchy.properties.DisplayContainerProperties
import com.android.wm.shell.hierarchy.updates.HierarchySnapshot
import com.android.wm.shell.transition.AnimationPlan

/**
 * A test Mode for a container in the ContainerHierarchy.
 */
open class StubMode(
    private val name: String=""
) : Mode {
    val enteringContainers = mutableListOf<Container>()
    val leavingContainers = mutableListOf<Container>()
    val removedContainers = mutableListOf<Container>()
    val changedContainers = mutableListOf<Container>()
    val requestedDisplayChanges = mutableListOf<DisplayContainerProperties>()
    var globalStateSnapshot: HierarchySnapshot? = null

    override fun getId(): String {
        return "TestMode_$name"
    }

    override fun containersChanged(
        updateContext: Mode.UpdateContext,
        enteringContainers: List<Container>,
        changedContainers: List<Container>,
        globalStateChanged: Boolean,
        snapshot: HierarchySnapshot,
        animationPlan: AnimationPlan?
    ) {
        this.enteringContainers.addAll(enteringContainers)
        this.changedContainers.addAll(changedContainers)
        if (globalStateChanged) {
            this.globalStateSnapshot = snapshot
        }
    }

    override fun containersRemoved(
        updateContext: Mode.UpdateContext,
        leavingContainers: List<Container>,
        removedContainers: List<Container>,
        snapshot: HierarchySnapshot,
        animationPlan: AnimationPlan?
    ) {
        this.leavingContainers.addAll(leavingContainers)
        this.removedContainers.addAll(removedContainers)
    }

    override fun requestUpdateForDisplayChange(
        directlyAssignedContainer: Container,
        curProps: DisplayContainerProperties,
        newProps: DisplayContainerProperties,
        wct: WindowContainerTransaction
    ) {
        requestedDisplayChanges.add(newProps)
    }
}