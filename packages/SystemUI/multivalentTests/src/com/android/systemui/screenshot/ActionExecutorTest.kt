/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.systemui.screenshot

import android.app.ActivityOptions
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.UserHandle
import android.platform.test.annotations.EnableFlags
import android.view.Window
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.Flags.FLAG_SCREENSHOT_MULTIDISPLAY_FOCUS_CHANGE
import com.android.systemui.SysuiTestCase
import com.android.systemui.util.mockito.argumentCaptor
import com.android.systemui.util.mockito.mock
import com.google.common.truth.Truth.assertThat
import kotlin.test.Test
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.any
import org.mockito.kotlin.capture
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyBlocking
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
@SmallTest
class ActionExecutorTest : SysuiTestCase() {
    private val scheduler = TestCoroutineScheduler()
    private val mainDispatcher = StandardTestDispatcher(scheduler)
    private val testScope = TestScope(mainDispatcher)

    private val intentExecutor = mock<ActionIntentExecutor>()
    private val window = mock<Window>()
    private val viewProxy = mock<ScreenshotShelfViewProxy>()
    private val onDismiss = mock<(() -> Unit)>()
    private val pendingIntent = mock<PendingIntent>()
    private val fakeContext = mock<Context>()

    private lateinit var actionExecutor: ActionExecutor

    @Test
    @EnableFlags(FLAG_SCREENSHOT_MULTIDISPLAY_FOCUS_CHANGE)
    fun startSharedTransition_callsLaunchIntent() = runTest {
        actionExecutor = createActionExecutor()
        whenever(fakeContext.displayId).thenReturn(17)
        whenever(window.context).thenReturn(fakeContext)

        actionExecutor.startSharedTransition(Intent(Intent.ACTION_EDIT), UserHandle.CURRENT, true)
        scheduler.advanceUntilIdle()

        val intentCaptor = argumentCaptor<Intent>()
        val activityOptionsCaptor = argumentCaptor<ActivityOptions>()
        verifyBlocking(intentExecutor) {
            launchIntent(
                capture(intentCaptor),
                eq(UserHandle.CURRENT),
                eq(true),
                capture(activityOptionsCaptor),
                any(),
            )
        }
        assertThat(intentCaptor.value.action).isEqualTo(Intent.ACTION_EDIT)
        assertThat(activityOptionsCaptor.value.launchDisplayId).isEqualTo(17)
    }

    @Test
    fun sendPendingIntent_requestsDismissal() = runTest {
        actionExecutor = createActionExecutor()

        actionExecutor.sendPendingIntent(pendingIntent)

        verify(pendingIntent).send(any(Bundle::class.java))
        verify(viewProxy).requestDismissal(null)
    }

    private fun createActionExecutor(): ActionExecutor {
        return ActionExecutor(intentExecutor, testScope, window, viewProxy, onDismiss)
    }
}
