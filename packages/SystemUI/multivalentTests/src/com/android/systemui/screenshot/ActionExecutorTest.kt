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
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.pm.UserInfo
import android.net.Uri
import android.os.Bundle
import android.os.UserHandle
import android.os.UserManager
import android.view.Window
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.clipboardoverlay.ClipboardListener.EXTRA_SUPPRESS_OVERLAY
import com.android.systemui.user.data.repository.FakeUserRepository
import com.android.systemui.user.data.repository.UserIconProvider
import com.android.systemui.user.utils.UserScopedService
import com.google.common.truth.Truth.assertThat
import kotlin.test.Test
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
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
    private val userManager = mock<UserManager>()
    private lateinit var userRepository: FakeUserRepository
    private val clipboardManagerService = mock<UserScopedService<ClipboardManager>>()
    private val clipboardManager = mock<ClipboardManager>()
    private val window = mock<Window>()
    private val viewProxy = mock<ScreenshotShelfViewProxy>()
    private val onDismiss = mock<(() -> Unit)>()
    private val pendingIntent = mock<PendingIntent>()
    private val fakeContext = mock<Context>()
    private val contentResolver = mock<ContentResolver>()

    private lateinit var actionExecutor: ActionExecutor

    @Before
    fun setUp() {
        userRepository = FakeUserRepository(UserIconProvider(context, userManager, mainDispatcher))
    }

    @Test
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
                intentCaptor.capture(),
                eq(UserHandle.CURRENT),
                eq(true),
                activityOptionsCaptor.capture(),
                any(),
            )
        }
        assertThat(intentCaptor.firstValue.action).isEqualTo(Intent.ACTION_EDIT)
        assertThat(activityOptionsCaptor.firstValue.launchDisplayId).isEqualTo(17)
    }

    @Test
    fun sendPendingIntent_requestsDismissal() = runTest {
        actionExecutor = createActionExecutor()

        actionExecutor.sendPendingIntent(pendingIntent)

        verify(pendingIntent).send(any(Bundle::class.java))
        verify(viewProxy).requestDismissal(null)
    }

    @Test
    fun copyScreenshotToClipboard_copiesUriToClipboard() = runTest {
        val currentUser =
            UserInfo(/* id= */ UserHandle.USER_SYSTEM, /* name= */ "primary user", /* flags= */ 0)
        userRepository.setUserInfos(listOf(currentUser))
        whenever(fakeContext.contentResolver).thenReturn(contentResolver)
        whenever(clipboardManagerService.forUser(anyOrNull())).thenReturn(clipboardManager)
        val uri = Uri.parse("content://media/external/images/media/123")
        actionExecutor = createActionExecutor()

        actionExecutor.copyScreenshotToClipboard(uri)

        val clipDataArg = argumentCaptor<ClipData>()
        verify(clipboardManagerService).forUser(currentUser.userHandle)
        verify(clipboardManager).setPrimaryClip(clipDataArg.capture())
        verify(viewProxy).requestDismissal(null)
        assertThat(clipDataArg.firstValue.getItemAt(0).uri).isEqualTo(uri)
        assertThat(clipDataArg.firstValue.description.extras?.getBoolean(EXTRA_SUPPRESS_OVERLAY))
            .isTrue()
    }

    private fun createActionExecutor(): ActionExecutor {
        return ActionExecutor(
            fakeContext,
            intentExecutor,
            userRepository,
            clipboardManagerService,
            testScope,
            window,
            viewProxy,
            onDismiss,
        )
    }
}
