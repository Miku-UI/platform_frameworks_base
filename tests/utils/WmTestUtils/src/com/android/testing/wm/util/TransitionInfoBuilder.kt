/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.testing.wm.util

import android.app.ActivityManager
import android.app.ActivityTaskManager.INVALID_TASK_ID
import android.content.ComponentName
import android.os.IBinder
import android.view.Display.INVALID_DISPLAY
import android.view.SurfaceControl
import android.view.WindowManager
import android.window.ActivityTransitionInfo
import android.window.TransitionInfo
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

/**
 * Utility for creating/editing synthetic [TransitionInfo] for tests.
 *
 * @param type the type of the transition. See [WindowManager.TransitionType].
 * @param flags the flags for the transition. See [WindowManager.TransitionFlags].
 * @param asNoOp if true, the root leash will not be added.
 * @param displayId the display ID for the root leash and transition changes.
 */
class TransitionInfoBuilder @JvmOverloads constructor(
    @WindowManager.TransitionType type: Int,
    @WindowManager.TransitionFlags flags: Int = 0,
    private val displayId: Int = DEFAULT_DISPLAY_ID,
) {
    // The underlying TransitionInfo object being built.
    private val info: TransitionInfo = TransitionInfo(type, flags)

    /** Adds a change to the [TransitionInfo]. */
    @JvmOverloads
    fun addChange(
        @WindowManager.TransitionType mode: Int,
        @TransitionInfo.ChangeFlags flags: Int = TransitionInfo.FLAG_NONE,
    ) = addChange(ChangeBuilder(mode).setDisplayId(displayId).setFlags(flags).build())

    /** Adds a change to the [TransitionInfo] for task transition with [flags]. */
    fun addChange(
        @WindowManager.TransitionType mode: Int,
        @TransitionInfo.ChangeFlags flags: Int,
        taskInfo: ActivityManager.RunningTaskInfo,
    ) = addChange(ChangeBuilder(taskInfo, mode).setDisplayId(displayId).setFlags(flags).build())

    /** Adds a change to the [TransitionInfo] for task transition with [flags]. */
    fun addChange(
        @WindowManager.TransitionType mode: Int,
        taskInfo: ActivityManager.RunningTaskInfo,
    ) = addChange(ChangeBuilder(taskInfo, mode).setDisplayId(displayId).build())

    /** Adds a change to the [TransitionInfo] for activity transition. */
    fun addChange(
        @WindowManager.TransitionType mode: Int,
        activityTransitionInfo: ActivityTransitionInfo,
    ) = addChange(
        ChangeBuilder(mode).setDisplayId(displayId)
            .setActivityTransitionInfo(activityTransitionInfo).build()
    )

    /** Adds a change to the [TransitionInfo] for activity transition without task id. */
    fun addChange(@WindowManager.TransitionType mode: Int, activityComponent: ComponentName) =
        addChange(
            ChangeBuilder(mode).setDisplayId(displayId)
                .setActivityTransitionInfo(
                    ActivityTransitionInfo(activityComponent, INVALID_TASK_ID)
                ).build()
        )

    /** Add a change to the [TransitionInfo] for task fragment. */
    fun addChange(@WindowManager.TransitionType mode: Int, taskFragmentToken: IBinder) =
        addChange(
            ChangeBuilder(mode).setDisplayId(displayId)
                .setTaskFragmentToken(taskFragmentToken).build()
        )

    /**
     * Adds a pre-configured change to the [TransitionInfo].
     *
     * @param change the TransitionInfo.Change object to add.
     * @param endDisplayId the end display to override the change with
     * @return this TransitionInfoBuilder instance for chaining.
     */
    @JvmOverloads
    fun addChange(change: TransitionInfo.Change): TransitionInfoBuilder {
        if (change.endDisplayId == INVALID_DISPLAY) {
            change.setDisplayId(displayId, displayId)
        }
        if (info.findRootIndex(change.endDisplayId) < 0) {
            info.addRootLeash(
                change.endDisplayId,
                createMockSurface(), /* leash */
                0, /* offsetLeft */
                0, /* offsetTop */
            )
        }
        // Add the change to the internal TransitionInfo object.
        info.addChange(change)
        return this // Return this for fluent builder pattern.
    }

    /**
     * Adds a root to the [TransitionInfo].
     *
     * @param rootLeash the SurfaceControl leash to use for the root.
     * @param displayId the display ID for this root leash. Defaults to the builder's displayId.
     * @return this TransitionInfoBuilder instance for chaining.
     */
    @JvmOverloads
    fun addRoot(
        rootLeash: SurfaceControl,
        displayId: Int = this.displayId
    ): TransitionInfoBuilder {
        info.addRootLeash(displayId, rootLeash, 0, 0)
        return this
    }

    /**
     * Builds and returns the configured [TransitionInfo] object.
     *
     * @return the constructed [TransitionInfo].
     */
    fun build(): TransitionInfo {
        return info
    }

    companion object {
        // Default display ID for root leashes and changes.
        const val DEFAULT_DISPLAY_ID = 0

        // Create a mock SurfaceControl for testing.
        private fun createMockSurface() = mock<SurfaceControl> {
            on { isValid } doReturn true
            on { toString() } doReturn "TestSurface"
        }
    }
}
