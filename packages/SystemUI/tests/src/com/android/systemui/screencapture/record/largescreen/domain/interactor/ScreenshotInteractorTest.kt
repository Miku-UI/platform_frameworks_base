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
import android.net.Uri
import android.view.Display.DEFAULT_DISPLAY
import android.view.WindowManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.internal.logging.uiEventLoggerFake
import com.android.internal.util.ScreenshotRequest
import com.android.internal.util.mockScreenshotHelper
import com.android.systemui.SysuiTestCase
import com.android.systemui.kosmos.runTest
import com.android.systemui.screencapture.ScreenCaptureEvent
import com.android.systemui.screenshot.mockImageCapture
import com.android.systemui.shade.data.repository.fakeFocusedDisplayRepository
import com.android.systemui.testKosmosNew
import com.android.systemui.user.data.repository.fakeUserRepository
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.isNull
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@SmallTest
@RunWith(AndroidJUnit4::class)
class ScreenshotInteractorTest : SysuiTestCase() {
    private val kosmos = testKosmosNew()

    @Mock private lateinit var mockBitmap: Bitmap

    private val interactor: ScreenshotInteractor by lazy { kosmos.screenshotInteractor }

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
    }

    @Test
    fun requestFullscreenScreenshot_callsScreenshotHelper_withCorrectRequest() {
        kosmos.runTest {
            val displayId = 3
            interactor.requestFullscreenScreenshot(displayId, null)

            val screenshotRequestCaptor = argumentCaptor<ScreenshotRequest>()
            verify(mockScreenshotHelper)
                .takeScreenshot(screenshotRequestCaptor.capture(), any(), isNull())

            val capturedRequest = screenshotRequestCaptor.lastValue
            assertThat(capturedRequest.type).isEqualTo(WindowManager.TAKE_SCREENSHOT_FULLSCREEN)
            assertThat(capturedRequest.source)
                .isEqualTo(WindowManager.ScreenshotSource.SCREENSHOT_SCREEN_CAPTURE_UI)
            assertThat(capturedRequest.displayId).isEqualTo(displayId)
        }
    }

    @Test
    fun requestFullscreenScreenshot_withoutDisplayIdArg_callsScreenshotHelper_withFocusedDisplayId() {
        kosmos.runTest {
            val focusedDisplayId = 123
            fakeFocusedDisplayRepository.setDisplayId(focusedDisplayId)

            interactor.requestFullscreenScreenshot()

            val screenshotRequestCaptor = argumentCaptor<ScreenshotRequest>()
            verify(mockScreenshotHelper)
                .takeScreenshot(screenshotRequestCaptor.capture(), any(), isNull())

            val capturedRequest = screenshotRequestCaptor.lastValue
            assertThat(capturedRequest.displayId).isEqualTo(focusedDisplayId)
        }
    }

    @Test
    fun requestFullscreenScreenshot_callsScreenshotHelper_usesCustomUriInRequest() {
        kosmos.runTest {
            val displayId = 3
            val customUri =
                Uri.parse("content://com.android.externalstorage.documents/tree/primary%3ATest")
            interactor.requestFullscreenScreenshot(displayId, customUri)

            val screenshotRequestCaptor = argumentCaptor<ScreenshotRequest>()
            verify(mockScreenshotHelper)
                .takeScreenshot(screenshotRequestCaptor.capture(), any(), isNull())

            val capturedRequest = screenshotRequestCaptor.lastValue
            assertThat(capturedRequest.customSaveUri).isEqualTo(customUri)
        }
    }

    @Test
    fun requestFullscreenScreenshot_logsEvent() =
        kosmos.runTest {
            val displayId = 3

            interactor.requestFullscreenScreenshot(displayId, null)

            assertThat(uiEventLoggerFake.numLogs()).isEqualTo(1)
            assertThat(uiEventLoggerFake.eventId(0))
                .isEqualTo(
                    ScreenCaptureEvent.SCREEN_CAPTURE_LARGE_SCREEN_FULLSCREEN_SCREENSHOT_REQUESTED
                        .id
                )
        }

    @Test
    fun requestPartialScreenshot_callsScreenshotHelper_withCorrectRequest() {
        kosmos.runTest {
            val bounds = Rect(0, 0, 100, 100)
            val displayId = 3
            whenever(mockImageCapture.captureDisplay(eq(displayId), eq(bounds)))
                .thenReturn(mockBitmap)

            val mainUser = UserInfo(0, "primary user", UserInfo.FLAG_MAIN)
            val secondaryUser = UserInfo(1, "secondary user", 0)
            fakeUserRepository.setUserInfos(listOf(mainUser, secondaryUser))
            fakeUserRepository.setSelectedUserInfo(secondaryUser)

            interactor.requestPartialScreenshot(bounds, displayId, null)

            val screenshotRequestCaptor = argumentCaptor<ScreenshotRequest>()
            verify(mockImageCapture).captureDisplay(any(), eq(bounds))
            verify(mockScreenshotHelper)
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

    @Test
    fun requestPartialScreenshot_callsScreenshotHelper_usesCustomUriInRequest() {
        kosmos.runTest {
            val bounds = Rect(0, 0, 100, 100)
            val displayId = 3
            val customUri =
                Uri.parse("content://com.android.externalstorage.documents/tree/primary%3ATest")
            whenever(mockImageCapture.captureDisplay(eq(displayId), eq(bounds)))
                .thenReturn(mockBitmap)

            interactor.requestPartialScreenshot(bounds, displayId, customUri)

            val screenshotRequestCaptor = argumentCaptor<ScreenshotRequest>()
            verify(mockScreenshotHelper)
                .takeScreenshot(screenshotRequestCaptor.capture(), any(), isNull())

            val capturedRequest = screenshotRequestCaptor.lastValue
            assertThat(capturedRequest.customSaveUri).isEqualTo(customUri)
        }
    }

    @Test
    fun requestPartialScreenshot_logsEvent() =
        kosmos.runTest {
            val bounds = Rect(0, 0, 100, 100)
            val displayId = 3
            whenever(mockImageCapture.captureDisplay(eq(displayId), eq(bounds)))
                .thenReturn(mockBitmap)

            interactor.requestPartialScreenshot(bounds, displayId, null)

            assertThat(uiEventLoggerFake.numLogs()).isEqualTo(1)
            assertThat(uiEventLoggerFake.eventId(0))
                .isEqualTo(
                    ScreenCaptureEvent.SCREEN_CAPTURE_LARGE_SCREEN_PARTIAL_SCREENSHOT_REQUESTED.id
                )
        }

    @Test
    fun requestAppWindowScreenshot_callsScreenshotHelper_withCorrectRequest() {
        kosmos.runTest {
            val taskId = 1
            val displayId = 3
            whenever(mockImageCapture.captureTask(eq(taskId))).thenReturn(mockBitmap)

            val mainUser = UserInfo(0, "primary user", UserInfo.FLAG_MAIN)
            val secondaryUser = UserInfo(1, "secondary user", 0)
            fakeUserRepository.setUserInfos(listOf(mainUser, secondaryUser))
            fakeUserRepository.setSelectedUserInfo(secondaryUser)

            interactor.requestAppWindowScreenshot(taskId, displayId)

            val screenshotRequestCaptor = argumentCaptor<ScreenshotRequest>()
            verify(mockImageCapture).captureTask(eq(taskId))
            verify(mockScreenshotHelper)
                .takeScreenshot(screenshotRequestCaptor.capture(), any(), isNull())

            val capturedRequest = screenshotRequestCaptor.lastValue
            assertThat(capturedRequest.type).isEqualTo(WindowManager.TAKE_SCREENSHOT_PROVIDED_IMAGE)
            assertThat(capturedRequest.source)
                .isEqualTo(WindowManager.ScreenshotSource.SCREENSHOT_SCREEN_CAPTURE_UI)
            assertThat(capturedRequest.bitmap).isEqualTo(mockBitmap)
            assertThat(capturedRequest.displayId).isEqualTo(displayId)
            assertThat(capturedRequest.userId).isEqualTo(secondaryUser.id)
        }
    }

    @Test
    fun requestAppWindowScreenshot_logsEvent() =
        kosmos.runTest {
            val taskId = 1
            val displayId = 3
            whenever(mockImageCapture.captureTask(eq(taskId))).thenReturn(mockBitmap)

            interactor.requestAppWindowScreenshot(taskId, displayId)

            assertThat(uiEventLoggerFake.numLogs()).isEqualTo(1)
            assertThat(uiEventLoggerFake.eventId(0))
                .isEqualTo(
                    ScreenCaptureEvent.SCREEN_CAPTURE_LARGE_SCREEN_APP_WINDOW_SCREENSHOT_REQUESTED
                        .id
                )
        }
}
