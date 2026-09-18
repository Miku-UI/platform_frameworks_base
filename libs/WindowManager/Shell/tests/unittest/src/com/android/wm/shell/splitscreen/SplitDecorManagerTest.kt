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

package com.android.wm.shell.splitscreen

import android.animation.AnimatorTestRule
import android.app.ActivityManager
import android.content.res.Configuration
import android.graphics.Rect
import android.testing.AndroidTestingRunner
import android.testing.TestableLooper
import android.view.SurfaceControl
import androidx.test.filters.SmallTest
import com.android.launcher3.icons.IconProvider
import com.android.wm.shell.ShellTestCase
import com.android.wm.shell.common.split.SplitDecorManager
import java.util.function.Consumer
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.kotlin.mock

/** Tests for [SplitDecorManager]. */
@SmallTest
@TestableLooper.RunWithLooper
@RunWith(AndroidTestingRunner::class)
class SplitDecorManagerTest : ShellTestCase() {
    @JvmField @Rule val animatorTestRule = AnimatorTestRule(this)

    private lateinit var mSplitDecorManager: SplitDecorManager

    @Before
    fun setup() {
        mSplitDecorManager = SplitDecorManager(Configuration(), mock<IconProvider>())
        mSplitDecorManager.inflate(mContext, mock<SurfaceControl>())
    }

    @Test
    fun testAnimateResized_whileFadeOutRunning_invokesCallbackWithTrue() {
        val taskInfo =
            ActivityManager.RunningTaskInfo().apply {
                taskDescription = ActivityManager.TaskDescription.Builder().build()
            }
        val displayBounds = Rect(0, 0, 1024, 768)
        val newBounds = Rect(0, 0, 512, 768)
        // Show
        mSplitDecorManager.onResizing(
            taskInfo,
            newBounds,
            Rect(),
            displayBounds,
            SurfaceControl.Transaction(),
            0,
            0,
            /* immediately = */ true,
        )
        // Start fade-out
        mSplitDecorManager.fadeOutDecor({}, /* addDelay= */ false)

        val callback = mock<Consumer<Boolean>>()
        mSplitDecorManager.onResized(SurfaceControl.Transaction(), callback)

        verify(callback, never()).accept(anyBoolean())

        animatorTestRule.advanceTimeBy(5000)

        verify(callback).accept(true)
    }
}
