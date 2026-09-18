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

package com.android.systemui.keyguard.data.repository

import android.app.ActivityManager.RunningTaskInfo
import com.android.systemui.keyguard.data.model.ShowWhenLockedActivityInfoModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Fake implementation of [KeyguardOcclusionRepository] for tests. */
class FakeKeyguardOcclusionRepository : KeyguardOcclusionRepository {
    private val _showWhenLockedActivityInfo =
        MutableStateFlow(ShowWhenLockedActivityInfoModel(isOnTop = false))
    override val showWhenLockedActivityInfo: StateFlow<ShowWhenLockedActivityInfoModel> =
        _showWhenLockedActivityInfo.asStateFlow()

    private var isOccluded = false
    private var bufferedTaskInfo: RunningTaskInfo? = null

    override fun setOccludedFromWm(isOccluded: Boolean) {
        this.isOccluded = isOccluded
        if (isOccluded) {
            // Apply the buffered task info when we become occluded
            _showWhenLockedActivityInfo.value =
                ShowWhenLockedActivityInfoModel(isOnTop = true, taskInfo = bufferedTaskInfo)
        } else {
            // Clear everything when no longer occluded
            _showWhenLockedActivityInfo.value =
                ShowWhenLockedActivityInfoModel(isOnTop = false, taskInfo = null)
        }
    }

    override fun setOccludedFromRemoteAnimation(onTop: Boolean, taskInfo: RunningTaskInfo?) {
        this.isOccluded = onTop
        this.bufferedTaskInfo = taskInfo
        _showWhenLockedActivityInfo.value =
            ShowWhenLockedActivityInfoModel(isOnTop = onTop, taskInfo = taskInfo)
    }

    /** Fakes an event from WmShell. */
    fun setOccludingTaskFromWmShell(taskInfo: RunningTaskInfo?) {
        if (taskInfo != null) {
            bufferedTaskInfo = taskInfo
        }

        if (isOccluded) {
            if (taskInfo != null) {
                // Update the active task info
                _showWhenLockedActivityInfo.value =
                    ShowWhenLockedActivityInfoModel(isOnTop = true, taskInfo = taskInfo)
            } else {
                // Ignore null task info while occluded to prevent intermittent drops
            }
        }
    }
}

val KeyguardOcclusionRepository.fake: FakeKeyguardOcclusionRepository
    get() = this as FakeKeyguardOcclusionRepository
