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

import android.view.SurfaceControl
import android.window.WindowContainerTransaction
import com.android.wm.shell.hierarchy.containers.Container
import com.android.wm.shell.hierarchy.properties.DisplayContainerProperties
import com.android.wm.shell.hierarchy.updates.HierarchySnapshot
import com.android.wm.shell.transition.AnimationPlan
import com.android.wm.shell.transition.DetachResult
import com.android.wm.shell.transition.ITransitionAnimation
import java.io.PrintWriter

/**
 * Encapsulates logic for a particular container in the hierarchy.
 */
interface Mode {

    //
    // Hierarchy Updates (to handle changes to the hierarchy that have already occurred)
    //

    /**
     * A hook into new displays being added, to create any static containers for this mode (if
     * necessary).
     *
     * This is always called globally for all modes BEFORE modes are notified of any containers
     * entering the mode.
     *
     * FUTURE: This would be a good place to hook into to save/restore per-display & mode state
     */
    fun prepareForDisplay(updateContext: UpdateContext, display: Container) {}

    /**
     * A hook into displays being removed, to clean up any static containers previously added in
     * `prepareForDisplay()`.
     *
     * This is always called for all modes AFTER modes are notified that containers on this display
     * are leaving the mode.
     */
    fun cleanupForDisplay(updateContext: UpdateContext, display: Container) {}

    /**
     * A hook into new containers entering this mode, existing containers changing, or a global
     * state changing.
     *
     * The mode's responsibility is to:
     * - Perform any local setup for all `enteringContainers`
     * - Handle updates to the mode's state (or root tasks)
     * - Handle updates to the dependent "global" containers (ie. root or display containers)
     * - Create animations for added or changed containers if desired by calling
     *   `AnimationPlan.setAnimation()`.
     *
     * A mode can use the snapshot to query for which global states have changed, ie:
     * ```kotlin
     * if (snapshot.getChanges(hierarchy.root)[CHANGED_FOCUS]) {
     *     val focusedTask = hierarchy.root.rootProps().focusState.globallyFocusedTaskId
     *     ...
     * }
     * ```
     *
     * This is always called AFTER `containersRemoved()` for the containers passed into this method.
     *
     * @param updateContext Additional information related to the change.
     * @param enteringContainers The set of containers that are newly added to this mode. These
     *                           containers may belong to disjoint parts of the hierarchy.
     * @param changedContainers The set of containers that were pre-existing in this mode that have
     *                          changes. These containers may belong to disjoint parts of the
     *                          hierarchy.
     * @param globalStateChanged Whether the global state outside the mode has changed (ie. changes
     *                           in the root or display container properties)
     * @param snapshot The snapshot of containers in the hierarchy prior to this update
     * @param animationPlan The animation plan for an ongoing Mixpatcher transition, if this update
     *                      is the result of a transition.
     */
    fun containersChanged(
        updateContext: UpdateContext,
        enteringContainers: List<Container>,
        changedContainers: List<Container>,
        globalStateChanged: Boolean,
        snapshot: HierarchySnapshot,
        animationPlan: AnimationPlan?,
    ) {}

    /**
     * A hook into containers leaving this mode.
     *
     * The mode's responsibility is to:
     * - Perform any local cleanup for all `leavingContainers`
     * - Prepare leaving containers for animation (if there are any surface overrides that are
     *   important wrt. preparing a container for an animation) by calling `AnimationPlan.detach()`
     *   on those containers to hand-off animation state to the next animator
     * - Create animations for `removedContainers` if desired (otherwise it is animated by the
     *   new mode for leaving containers) by calling `AnimationPlan.setAnimation()`.
     *
     * This is always called BEFORE `containersChanged()` to ensure an ordering of calls between
     * the last & current mode managing a container.
     *
     * @param updateContext Additional information related to the change.
     * @param leavingContainers The set of containers that are leaving this mode (either to move
     *                          to a different mode, or removed from the hierarchy entirely. These
     *                          containers may belong to disjoint parts of the hierarchy.
     * @param removedContainers The subset of `leavingContainers` containers that were removed from
     *                          the hierarchy entirely.
     * @param snapshot The snapshot of containers in the hierarchy prior to this update.
     * @param animationPlan The animation plan for an ongoing Mixpatcher transition, if this update
     *                      is the result of a transition.
     */
    fun containersRemoved(
        updateContext: UpdateContext,
        leavingContainers: List<Container>,
        removedContainers: List<Container>,
        snapshot: HierarchySnapshot,
        animationPlan: AnimationPlan?,
    ) {}

    //
    // Requests (to compose changes that will manipulate containers in the hierarchy)
    //

    /**
     * Requests this mode to update the given {@param wct} to start managing the given
     * {@param task}. For example, this can mean reparenting the task into one of the root
     * containers that is associated with this mode.
     *
     * A mode implementing this method is not expected to request an update to the hierarchy, only
     * to update the given WindowContainerTransaction (ie. calling this method should have NO
     * side-effects, and subsequent handling of the request should be equivalent to a Core-initiated
     * change of the same type).
     *
     * The caller is expected to apply the transaction.
     *
     * If the mode supports a more specific request type, then it must validate the request given.
     */
    fun requestEnterMode(
        task: Container,
        request: EnterRequestContext,
        wct: WindowContainerTransaction
    ): Boolean {
        throw IllegalStateException("Not supported")
    }

    /**
     * Requests this mode to update the given {@param wct} to layout the containers under this mode
     * in the new display configuration. This happens prior to the display change being applied
     * to the hierarchy.
     *
     * The mode is NOT expected to apply the transaction.
     *
     * FUTURE: Revisit to see if we need intermediate containers to be updated as well (ie. if there
     *         are roots who are built within parent containers, updating the display may require
     *         the parents to be updated before the roots themselves can be properly/easily updated.
     */
    fun requestUpdateForDisplayChange(
        directlyAssignedContainer: Container,
        curDisplayProps: DisplayContainerProperties,
        newDisplayProps: DisplayContainerProperties,
        wct: WindowContainerTransaction
    ) {}

    //
    // Misc
    //

    /**
     * Returns an identifier that can be used to resolve the mode directly if needed (ie. from
     * shell commands)
     */
    open fun getId(): String {
        return this::class.simpleName!!
    }

    /**
     * Handles a shell command. This is primarily for debugging purposes and should not be used
     * in tests since it's not really "api".
     */
    fun onShellCommand(displayId: Int, args: MutableList<String>, pw: PrintWriter) {}

    //
    // Contexts
    //

    /**
     * Additional context for updates, including specific transition information that isn't
     * persisted in the updated hierarchy.
     *
     * @see prepareForDisplay
     * @see cleanupForDisplay
     * @see containersChanged
     * @see containersRemoved
     */
    class UpdateContext(
        var reason: String = "No reason given",
        // The surface transactions that are to be applied if there is an associated transition
        // with the update, if there isn't then these will be null, and the logic doing the update
        // must apply and surface updates itself.
        var preTransitionTx: SurfaceControl.Transaction? = null,
        // The specific container to dump (otherwise the full hierarchy will be dumped)
        var dumpOnlyContainer: Container? = null,
    )

    /**
     * Additional data which is necessary when resolving how to move another task to be associated
     * with a mode. This can be extended by having per-mode types with additional info, but the
     * mode must validate the request type before using it.
     *
     * @see requestEnterMode
     */
    class EnterRequestContext(
        val displayId: Int
    )
}