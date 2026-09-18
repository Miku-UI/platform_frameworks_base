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

import android.graphics.Bitmap
import android.graphics.Rect
import android.net.Uri
import android.os.Handler
import android.view.WindowManager
import com.android.internal.logging.UiEventLogger
import com.android.internal.util.ScreenshotHelper
import com.android.internal.util.ScreenshotRequest
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.display.data.repository.FocusedDisplayRepository
import com.android.systemui.screencapture.ScreenCaptureEvent
import com.android.systemui.screenshot.ImageCapture
import com.android.systemui.user.data.repository.UserRepository
import javax.inject.Inject
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.withContext

/** Interactor responsible for employing ScreenshotHelper to take various types of screenshots. */
@SysUISingleton
class ScreenshotInteractor
@Inject
constructor(
    @Background private val backgroundContext: CoroutineContext,
    @Background private val backgroundHandler: Handler,
    private val uiEventLogger: UiEventLogger,
    private val imageCapture: ImageCapture,
    private val screenshotHelper: ScreenshotHelper,
    private val userRepository: UserRepository,
    private val focusedDisplayRepository: FocusedDisplayRepository,
) {
    suspend fun requestFullscreenScreenshot(
        displayId: Int = focusedDisplayRepository.focusedDisplayId.value,
        customSaveUri: Uri? = null,
    ) {
        val request =
            ScreenshotRequest.Builder(
                    WindowManager.TAKE_SCREENSHOT_FULLSCREEN,
                    WindowManager.ScreenshotSource.SCREENSHOT_SCREEN_CAPTURE_UI,
                )
                .setDisplayId(displayId)
                .setCustomSaveUri(customSaveUri)
                .build()

        takeScreenshot(request)

        uiEventLogger.log(
            ScreenCaptureEvent.SCREEN_CAPTURE_LARGE_SCREEN_FULLSCREEN_SCREENSHOT_REQUESTED
        )
    }

    suspend fun requestPartialScreenshot(regionBounds: Rect, displayId: Int, customSaveUri: Uri?) {
        val bitmap =
            withContext(backgroundContext) {
                requireNotNull(imageCapture.captureDisplay(displayId, regionBounds))
            }
        val request =
            ScreenshotRequest.Builder(
                    WindowManager.TAKE_SCREENSHOT_PROVIDED_IMAGE,
                    WindowManager.ScreenshotSource.SCREENSHOT_SCREEN_CAPTURE_UI,
                )
                .setBitmap(bitmap)
                .setBoundsOnScreen(regionBounds)
                .setDisplayId(displayId)
                .setUserId(userRepository.getSelectedUserInfo().id)
                .setCustomSaveUri(customSaveUri)
                .build()

        takeScreenshot(request)

        uiEventLogger.log(
            ScreenCaptureEvent.SCREEN_CAPTURE_LARGE_SCREEN_PARTIAL_SCREENSHOT_REQUESTED
        )
    }

    suspend fun requestAppWindowScreenshot(taskId: Int, displayId: Int) {
        val bitmap =
            withContext(backgroundContext) { requireNotNull(imageCapture.captureTask(taskId)) }
        val request = makeAppWindowRequest(bitmap, displayId)

        takeScreenshot(request)

        uiEventLogger.log(
            ScreenCaptureEvent.SCREEN_CAPTURE_LARGE_SCREEN_APP_WINDOW_SCREENSHOT_REQUESTED
        )
    }

    private fun makeAppWindowRequest(bitmap: Bitmap, displayId: Int): ScreenshotRequest {
        return ScreenshotRequest.Builder(
                WindowManager.TAKE_SCREENSHOT_PROVIDED_IMAGE,
                WindowManager.ScreenshotSource.SCREENSHOT_SCREEN_CAPTURE_UI,
            )
            .setBitmap(bitmap)
            .setDisplayId(displayId)
            .setUserId(userRepository.getSelectedUserInfo().id)
            .build()
    }

    private suspend fun takeScreenshot(request: ScreenshotRequest) {
        withContext(backgroundContext) {
            screenshotHelper.takeScreenshot(request, backgroundHandler, null)
        }
    }
}
