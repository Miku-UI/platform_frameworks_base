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

package com.android.systemui.screencapture.common.domain.interactor

import android.graphics.Bitmap
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.screencapture.common.ScreenCaptureUiScope
import com.android.systemui.screencapture.common.data.repository.ScreenCaptureThumbnailRepository
import com.android.systemui.screencapture.common.domain.model.ScreenCaptureDisplay
import com.android.systemui.screencapture.common.domain.model.ScreenCaptureRecentTask
import com.android.systemui.screenshot.ImageCapture
import javax.inject.Inject
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.withContext

/** Interactor for fetching recent task thumbnails. */
@ScreenCaptureUiScope
class ScreenCaptureThumbnailInteractor
@Inject
constructor(
    @Background private val bgContext: CoroutineContext,
    private val repository: ScreenCaptureThumbnailRepository,
    private val imageCapture: ImageCapture,
) {
    /** Capture thumbnail for the given [task]. */
    suspend fun loadThumbnail(task: ScreenCaptureRecentTask): Result<Bitmap> =
        repository.loadThumbnail(task.taskId)

    /** Capture thumbnail for the given [display]. */
    suspend fun loadThumbnail(display: ScreenCaptureDisplay): Result<Bitmap> =
        withContext(bgContext) {
            imageCapture.captureDisplay(displayId = display.displayId, crop = null)?.let {
                Result.success(it)
            }
                ?: Result.failure(
                    IllegalStateException(
                        "Couldn't capture thumbnail for display ${display.displayId}"
                    )
                )
        }
}
