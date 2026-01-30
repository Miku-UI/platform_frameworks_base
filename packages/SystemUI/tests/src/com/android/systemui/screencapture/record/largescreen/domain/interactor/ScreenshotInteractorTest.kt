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

package com.android.systemui.screencapture.record.largescreen.domain.interactor

import android.content.pm.UserInfo
import android.graphics.Bitmap
import android.graphics.Rect
import android.view.WindowManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.internal.util.ScreenshotRequest
import com.android.internal.util.mockScreenshotHelper
import com.android.systemui.SysuiTestCase
import com.android.systemui.kosmos.testScope
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.screenshot.mockImageCapture
import com.android.systemui.testKosmos
import com.android.systemui.user.data.repository.fakeUserRepository
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.isNull
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@SmallTest
@RunWith(AndroidJUnit4::class)
class ScreenshotInteractorTest : SysuiTestCase() {
    private val kosmos = testKosmos().useUnconfinedTestDispatcher()
    private val testScope = kosmos.testScope

    @Mock private lateinit var mockBitmap: Bitmap

    private val interactor: ScreenshotInteractor by lazy { kosmos.screenshotInteractor }

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
    }

    @Test
    fun requestFullscreenScreenshot_callsScreenshotHelper_withCorrectRequest() {
        testScope.runTest {
            val displayId = 3
            interactor.requestFullscreenScreenshot(displayId)

            val screenshotRequestCaptor = argumentCaptor<ScreenshotRequest>()
            verify(kosmos.mockScreenshotHelper, times(1))
                .takeScreenshot(screenshotRequestCaptor.capture(), any(), isNull())

            val capturedRequest = screenshotRequestCaptor.lastValue
            assertThat(capturedRequest.type).isEqualTo(WindowManager.TAKE_SCREENSHOT_FULLSCREEN)
            assertThat(capturedRequest.source)
                .isEqualTo(WindowManager.ScreenshotSource.SCREENSHOT_SCREEN_CAPTURE_UI)
            assertThat(capturedRequest.displayId).isEqualTo(displayId)
        }
    }

    @Test
    fun requestPartialScreenshot_callsScreenshotHelper_withCorrectRequest() {
        testScope.runTest {
            val bounds = Rect(0, 0, 100, 100)
            val displayId = 3
            whenever(kosmos.mockImageCapture.captureDisplay(eq(displayId), eq(bounds)))
                .thenReturn(mockBitmap)

            val mainUser = UserInfo(0, "primary user", UserInfo.FLAG_MAIN)
            val secondaryUser = UserInfo(1, "secondary user", 0)
            kosmos.fakeUserRepository.setUserInfos(listOf(mainUser, secondaryUser))
            kosmos.fakeUserRepository.setSelectedUserInfo(secondaryUser)

            interactor.requestPartialScreenshot(bounds, displayId)

            val screenshotRequestCaptor = argumentCaptor<ScreenshotRequest>()
            verify(kosmos.mockImageCapture, times(1)).captureDisplay(any(), eq(bounds))
            verify(kosmos.mockScreenshotHelper, times(1))
                .takeScreenshot(screenshotRequestCaptor.capture(), any(), isNull())

            val capturedRequest = screenshotRequestCaptor.lastValue
            assertThat(capturedRequest.type).isEqualTo(WindowManager.TAKE_SCREENSHOT_PROVIDED_IMAGE)
            assertThat(capturedRequest.source)
                .isEqualTo(WindowManager.ScreenshotSource.SCREENSHOT_SCREEN_CAPTURE_UI)
            assertThat(capturedRequest.bitmap).isEqualTo(mockBitmap)
            assertThat(capturedRequest.boundsInScreen).isEqualTo(bounds)
            assertThat(capturedRequest.displayId).isEqualTo(displayId)
            assertThat(capturedRequest.userId).isEqualTo(secondaryUser.id)
        }
    }
}
