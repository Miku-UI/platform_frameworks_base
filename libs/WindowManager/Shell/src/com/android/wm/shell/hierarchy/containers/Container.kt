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
package com.android.wm.shell.hierarchy.containers

import android.content.res.Configuration
import android.graphics.RectF
import android.view.SurfaceControl
import android.window.TransitionInfo
import android.window.WindowContainerToken
import com.android.wm.shell.hierarchy.modes.Mode
import com.android.wm.shell.hierarchy.properties.ActivityContainerProperties
import com.android.wm.shell.hierarchy.properties.ContainerProperties
import com.android.wm.shell.hierarchy.properties.DisplayAreaContainerProperties
import com.android.wm.shell.hierarchy.properties.DisplayContainerProperties
import com.android.wm.shell.hierarchy.properties.RootContainerProperties
import com.android.wm.shell.hierarchy.properties.SurfaceProperties
import com.android.wm.shell.hierarchy.properties.TaskContainerProperties
import com.android.wm.shell.hierarchy.properties.WallpaperContainerProperties
import com.android.wm.shell.hierarchy.utils.HierarchyDebugUtils.Companion.tokenToString
import com.android.wm.shell.hierarchy.utils.HierarchyUtils

val NULL_SURFACE = SurfaceControl()

/**
 * A container in the ContainerHierarchy.
 */
open class Container(
    // A stable, unique identifier for this container in the hierarchy
    val token: WindowContainerToken = WindowContainerToken.createProxy(":container"),
    // Properties associated with this container (can be overridden for specific container types)
    val props: ContainerProperties = ContainerProperties(),
    // Overridden debug name for this container
    name: String? = null
) {
    // A debug name for this container
    var name = "${name ?: props.getTypeName()} ${tokenToString(token)}"

    // The actual SurfaceControl for this container. For containers that have corresponding WM
    // containers, then this leash is provided by WM via transitions. Otherwise the creator of
    // the shell-only container is responsible for managing this surface.
    var leash = NULL_SURFACE
        get() {
            if (field === NULL_SURFACE) {
                throw UninitializedPropertyAccessException("Expected leash to be set on $token")
            }
            return field
        }

    // Properties which affect the SurfaceControl (leash) for this container
    val surface: SurfaceProperties = SurfaceProperties(this)

    // An associated mode for this container
    var mode: Mode? = null
        set(value) {
            if (field == value) {
                // Same mode, no need to update
                return
            }
            field = value
        }

    // The parent of this container in the hierarchy
    var parent: Container? = null
        set(value) {
            if (field == value) {
                // Same parent, don't reorder
                return
            }
            // Remove this container from the old parent
            field?.children?.remove(this)
            field = value
            // Add this container to the new parent
            field?.children?.add(this)
        }

    // The children of this container in the hierarchy
    val children = mutableListOf<Container>()

    // Describes a container that exists during a transition, but is not organized and will be
    // removed from the hierarchy once the transition is completed. Modes associated with
    // containers in the hierarchy are not notified of temporary containers when they are added
    // and removed from the hierarchy (but can query the hierarchy as necessary)
    var isTemporaryAnimatingContainer = false

    /**
     * Reorders a child of the container to the top or bottom of the list.
     */
    fun reorderChild(child: Container, toTop: Boolean) {
        if (child.parent != this) {
            throw IllegalArgumentException("Reorder failed, not a child={$child.token}")
        }
        children.remove(child)
        if (toTop) {
            children.add(child)
        } else {
            children.add(0, child)
        }
    }

    /**
     * Updates internal properties given a Transition Change. The container may not yet have been
     * added to the hierarchy, so the root of the hierarchy is provided to help resolve the
     * container's parent.
     *
     * This is always called from top-down hierarchical order (ie. for a transition including
     * changes for both parents and children, the parents will be updated before the children).
     */
    fun updateFromWindowChange(
        root: Container,
        change: TransitionInfo.Change,
    ) {
        props.updateFromWindowChange(change)
        // Only update the parent after the other properties have been updated since those
        // properties affect how the parent is resolved
        parent =
            HierarchyUtils.resolveParentForContainer(root, this, change.parent, change.endDisplayId)
        updateSurfaceFromPropertyChanges()
        // Update the leash
        leash = change.leash

        // Update shell-only container's configurations as necessary
        for (child in children) {
            child.updateConfigurationFromParentIfNeeded(props.config)
        }
    }

    /**
     * This should be called after the properties have been updated, and is only needed if a
     * container is updating properties outside of a transition.
     */
    protected fun updateSurfaceFromPropertyChanges() {
        val parentBounds = parent?.props?.bounds ?: RectF()
        // Update the surface with any changes from the properties
        val relBounds = RectF(props.bounds)
        relBounds.offset(-parentBounds.left, -parentBounds.top)
        surface.updateReferenceFrame(relBounds)
    }

    /**
     * Called when the parent's properties change. This is primarily for shell-only containers that
     * are dependent on the configuration of other WM containers in the hierarchy.
     */
    protected open fun updateConfigurationFromParentIfNeeded(parentConfig: Configuration) {
        // No-op
    }

    override fun toString(): String {
        return name
    }

    //
    // Convenience methods
    //

    /**
     * Convenience method to return a typed subclass of the container's properties.
     */
    inline fun <reified T : ContainerProperties> props(): T {
        return props as T
    }

    /** Returns whether this is a root container. */
    fun isRoot() = props is RootContainerProperties
    /** Returns root props. */
    fun rootProps() = props as RootContainerProperties
    /** Returns whether this is a display container. */
    fun isDisplay() = props is DisplayContainerProperties
    /** Returns display props. */
    fun displayProps() = props as DisplayContainerProperties
    /** Returns whether this is a display area container. */
    fun isDisplayArea() = props is DisplayAreaContainerProperties
    /** Returns display area props. */
    fun displayAreaProps() = props as DisplayAreaContainerProperties
    /** Returns whether this is a task container. */
    fun isTask() = props is TaskContainerProperties
    /** Returns task props. */
    fun taskProps() = props as TaskContainerProperties
    /** Returns whether this is an activity container. */
    fun isActivity() = props is ActivityContainerProperties
    /** Returns activity props. */
    fun activityProps() = props as ActivityContainerProperties
    /** Returns whether this is a wallpaper container. */
    fun isWallpaper() = props is WallpaperContainerProperties
    /** Returns wallpaper props. */
    fun wallpaperProps() = props as WallpaperContainerProperties
    /** Returns whether this is a view overlay container. */
    fun isViewOverlay() = this is ViewOverlayContainer
}