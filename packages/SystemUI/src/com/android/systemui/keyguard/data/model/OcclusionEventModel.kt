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

package com.android.systemui.keyguard.data.model

import android.app.ActivityManager.RunningTaskInfo
import com.android.internal.policy.IKeyguardService
import com.android.systemui.keyguard.WindowManagerOcclusionManager
import com.android.systemui.keyguard.shared.format

/** Events that drive the occlusion state machine. */
sealed interface OcclusionEventModel {
    /** The boolean occlusion signal from [IKeyguardService.setOccluded]. */
    data class OccludedFromWm(val isOccluded: Boolean) : OcclusionEventModel

    /** The pre-signal from WmShell containing the occluding task info. */
    data class OccludingTaskChanged(val taskInfo: RunningTaskInfo?) : OcclusionEventModel {
        override fun toString(): String {
            return "OccludingTaskChanged(taskInfo=${taskInfo.format()})"
        }
    }

    /**
     * The remote animation for occlusion/unocclusion has started in
     * [WindowManagerOcclusionManager].
     */
    data class StartedRemoteAnimation(val onTop: Boolean, val taskInfo: RunningTaskInfo?) :
        OcclusionEventModel {
        override fun toString(): String {
            return "StartedRemoteAnimation(onTop=$onTop, taskInfo=${taskInfo.format()})"
        }
    }
}
