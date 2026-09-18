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
import android.os.IBinder
import android.view.Display.INVALID_DISPLAY
import android.view.Surface
import android.view.SurfaceControl
import android.view.WindowManager
import android.window.ActivityTransitionInfo
import android.window.TransitionInfo
import android.window.WindowContainerToken
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

/**
 * Utility for creating/editing synthetic [TransitionInfo.Change] for tests.
 */
class ChangeBuilder {
    private val change: TransitionInfo.Change

    constructor(container: WindowContainerToken, @WindowManager.TransitionType mode: Int) {
        change = TransitionInfo.Change(container, createMockSurface())
        change.mode = mode
    }

    constructor(
        taskInfo: ActivityManager.RunningTaskInfo,
        @WindowManager.TransitionType mode: Int,
    ) {
        change = TransitionInfo.Change(taskInfo.token, createMockSurface())
        change.taskInfo = taskInfo
        change.mode = mode
    }

    constructor(@WindowManager.TransitionType mode: Int) {
        change =
            TransitionInfo.Change(
                if (com.android.window.flags.Flags.transitMixpatcherBase())
                    WindowContainerToken.createProxy("Change")
                else null,
                createMockSurface(),
            )
        change.mode = mode
    }

    constructor(@WindowManager.TransitionType mode: Int, leash: SurfaceControl) {
        change =
            TransitionInfo.Change(
                if (com.android.window.flags.Flags.transitMixpatcherBase())
                    WindowContainerToken.createProxy("Change")
                else null,
                leash,
            )
        change.mode = mode
    }

    fun setFlags(@TransitionInfo.ChangeFlags flags: Int): ChangeBuilder {
        change.flags = flags
        return this
    }

    fun setRotate(anim: Int): ChangeBuilder {
        return setRotate(Surface.ROTATION_90, anim)
    }

    fun setRotate(): ChangeBuilder {
        return setRotate(WindowManager.LayoutParams.ROTATION_ANIMATION_UNSPECIFIED)
    }

    fun setRotate(@Surface.Rotation target: Int, anim: Int): ChangeBuilder {
        change.setRotation(Surface.ROTATION_0, target)
        change.rotationAnimation = anim
        return this
    }

    fun setActivityTransitionInfo(activityTransitionInfo: ActivityTransitionInfo?): ChangeBuilder {
        change.activityTransitionInfo = activityTransitionInfo
        return this
    }

    fun setTaskFragmentToken(taskFragmentToken: IBinder?): ChangeBuilder {
        change.taskFragmentToken = taskFragmentToken
        return this
    }

    fun setDisplayId(id: Int): ChangeBuilder {
        change.setDisplayId(id, id)
        return this
    }

    fun moveToDisplay(startId: Int, endId: Int): ChangeBuilder {
        change.setDisplayId(startId, endId)
        return this
    }

    /** @return the constructed [TransitionInfo.Change]. */
    fun build(): TransitionInfo.Change {
        if (change.startDisplayId == INVALID_DISPLAY) {
            change.setDisplayId(DEFAULT_DISPLAY_ID, DEFAULT_DISPLAY_ID)
        }
        return change
    }

    companion object {
        const val DEFAULT_DISPLAY_ID = 0

        private fun createMockSurface(valid: Boolean = true) =
            mock<SurfaceControl> {
                on { isValid } doReturn valid
                on { toString() } doReturn "TestSurface"
            }
    }
}
