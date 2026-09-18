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
package com.android.wm.shell.hierarchy.properties

import android.app.WindowConfiguration
import android.content.res.Configuration
import android.content.res.Configuration.ORIENTATION_LANDSCAPE
import android.content.res.Configuration.ORIENTATION_PORTRAIT
import android.graphics.RectF
import android.view.Surface
import android.window.TransitionInfo
import androidx.annotation.CallSuper
import com.android.wm.shell.dagger.hierarchy.WmSyncedProperty
import com.android.wm.shell.hierarchy.updates.HierarchyChangeFlags
import com.android.wm.shell.hierarchy.updates.HierarchySnapshot.Companion.CHANGED_BOUNDS
import com.android.wm.shell.hierarchy.updates.HierarchySnapshot.Companion.CHANGED_ROTATION
import com.android.wm.shell.hierarchy.updates.HierarchySnapshot.Companion.CHANGED_VISIBILITY
import com.android.wm.shell.hierarchy.updates.HierarchySnapshot.Companion.CHANGED_WINDOWING_MODE
import com.android.wm.shell.hierarchy.utils.HierarchyDebugUtils
import com.android.wm.shell.hierarchy.utils.HierarchyDebugUtils.Companion.capitalize
import com.android.wm.shell.hierarchy.utils.ImmutableRectF
import com.android.wm.shell.shared.TransitionUtil

val NO_USER = -1

/**
 * Properties that correspond to the container's associated container in the hierarchy.
 * This can be subclassed for different types that have additional information specific to that
 * window container.
 */
open class ContainerProperties(
    val userId: Int = NO_USER
) {
    // The configuration for this container
    @WmSyncedProperty
    var config: Configuration = Configuration()
        set(value) {
            // Always copy the configuration when setting
            field.setTo(value)
        }

    // Whether this container is requested to be visible or not. This does not aggregate the
    // visibility of any ancestors
    var visibleRequested: Boolean = false

    // Convenience property to get the bounds of this container
    // Note: Bounds are in display coordinates
    val bounds: RectF
        // Always make a copy of the bounds (for now) to prevent accidental manipulation
        get() = ImmutableRectF(config.windowConfiguration.bounds)

    // Convenience property
    @Surface.Rotation
    val rotation: Int
        get() = config.windowConfiguration.rotation

    // Convenience property
    @WindowConfiguration.WindowingMode
    val windowingMode: Int
        get() = config.windowConfiguration.windowingMode

    // Convenience property
    @WindowConfiguration.ActivityType
    val activityType: Int
        get() = config.windowConfiguration.activityType

    /**
     * Updates properties given the transition change. This is primarily used for containers that
     * have corresponding containers in the WM hierarchy.
     */
    @CallSuper
    open fun updateFromWindowChange(change: TransitionInfo.Change) {
        // FUTURE: We should have explicit flags defining visibility since it's not always
        // reconcilable by mode
        if (TransitionUtil.isOpeningMode(change.mode)) {
            visibleRequested = true
        } else if (TransitionUtil.isClosingMode(change.mode)) {
            visibleRequested = false
        }
        // In lieu of being able to update the configuration entirely, update the most useful bits
        // from the generic change info (property subclasses that have access to the full
        // configuration will also override this method and set it directly)
        val width = change.endAbsBounds.width()
        val height = change.endAbsBounds.height()
        config.windowConfiguration.setBounds(change.endAbsBounds)
        config.windowConfiguration.setMaxBounds(change.endAbsBounds)
        config.windowConfiguration.rotation = change.endRotation
        config.orientation = if (width >= height) ORIENTATION_LANDSCAPE else ORIENTATION_PORTRAIT
    }

    /**
     * Sets the assignable window properties to the values in the other given properties.
     * This is a deep copy -- changes in the other container should not affect this property's
     * values.
     *
     * This should be overridden by subclasses to copy additional properties.
     */
    @CallSuper
    protected open fun copyFrom(other: ContainerProperties) {
        if (this == other) {
            throw IllegalStateException(
                "You've probably accidentally passed in the same instance into the copy method.")
        }
        config = other.config
        visibleRequested = other.visibleRequested
    }

    /**
     * Returns a copy of the window container properties.
     *
     * This should be overridden by subclasses to create the typed container properties and call
     * `copyFrom()` to ensure all inherited properties are copied.
     */
    open fun copy(): ContainerProperties {
        return ContainerProperties(userId).apply {
            copyFrom(this@ContainerProperties)
        }
    }

    /**
     * Returns the diff of two window container properties (for the saved state).
     * This call should produce no side effects on either this or the passed in properties.
     */
    @CallSuper
    open fun diff(other: ContainerProperties, chgs: HierarchyChangeFlags) {
        chgs.compareAndSet(visibleRequested, other.visibleRequested, CHANGED_VISIBILITY)
        chgs.compareAndSet(bounds, other.bounds, CHANGED_BOUNDS)
        chgs.compareAndSet(rotation, other.rotation, CHANGED_ROTATION)
        chgs.compareAndSet(windowingMode, other.windowingMode, CHANGED_WINDOWING_MODE)
    }

    final override fun toString(): String {
        val props = propsToString()
        return "p {$props}"
    }

    /**
     * Returns a string representation of the properties.
     * To be overridden by subclasses.
     */
    @CallSuper
    protected open fun propsToString(): String {
        val winMode = capitalize(WindowConfiguration.windowingModeToString(windowingMode))
        val actType = capitalize(WindowConfiguration.activityTypeToString(activityType))
        val rotDegress = HierarchyDebugUtils.rotationToDegrees(rotation)
        val rotStr = if (rotation != config.windowConfiguration.displayRotation) " rot=$rotDegress"
                else ""
        return "u$userId vis=$visibleRequested winMode=$winMode actType=$actType " +
                "bounds=${bounds.toShortString()}$rotStr"
    }

    /**
     * A user-friendly type name for debugging purposes.
     */
    open fun getTypeName(): String {
        return "Container"
    }
}