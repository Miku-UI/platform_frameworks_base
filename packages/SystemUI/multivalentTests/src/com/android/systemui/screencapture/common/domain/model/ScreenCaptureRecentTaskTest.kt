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

package com.android.systemui.screencapture.common.domain.model

import android.content.ComponentName
import android.graphics.Rect
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.mediaprojection.appselector.data.RecentTask
import com.android.wm.shell.shared.split.SplitBounds
import com.android.wm.shell.shared.split.SplitScreenConstants
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class ScreenCaptureRecentTaskTest : SysuiTestCase() {

    @Test
    fun constructor_usesRecentTaskFields() {
        // Arrange
        val fakeTopComponent = ComponentName("FakeTopPackage", "FakeTopClass")
        val fakeBaseComponent = ComponentName("FakeBaseBackage", "FakeBaseClass")
        val fakeSplitBounds =
            SplitBounds(
                /* leftTopBounds= */ Rect(1, 1, 1, 1),
                /* rightBottomBounds= */ Rect(2, 2, 2, 2),
                /* leftTopTaskId= */ 4,
                /* rightBottomTaskId= */ 5,
                /* snapPosition= */ SplitScreenConstants.SNAP_TO_2_33_66,
            )
        val fakeTask =
            RecentTask(
                taskId = 1,
                displayId = 2,
                userId = 3,
                topActivityComponent = fakeTopComponent,
                baseIntentComponent = fakeBaseComponent,
                colorBackground = 0x99123456.toInt(),
                isForegroundTask = true,
                userType = RecentTask.UserType.STANDARD,
                splitBounds = fakeSplitBounds,
            )

        // Act
        val result = ScreenCaptureRecentTask(fakeTask)

        // Assert
        with(result) {
            assertThat(taskId).isEqualTo(1)
            assertThat(displayId).isEqualTo(2)
            assertThat(userId).isEqualTo(3)
            assertThat(component).isEqualTo(fakeBaseComponent)
            assertThat(backgroundColor).isEqualTo(0x99123456.toInt())
            assertThat(splitBounds).isEqualTo(fakeSplitBounds)
        }
    }
}
