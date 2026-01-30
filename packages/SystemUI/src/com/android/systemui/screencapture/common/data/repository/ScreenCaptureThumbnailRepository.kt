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

package com.android.systemui.screencapture.common.data.repository

import android.graphics.Bitmap
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.screencapture.common.ScreenCaptureUiScope
import com.android.systemui.shared.system.ActivityManagerWrapper
import javax.inject.Inject
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.withContext

/** Repository for fetching app thumbnails. */
interface ScreenCaptureThumbnailRepository {
    /** Fetch app thumbnail on background dispatcher. */
    suspend fun loadThumbnail(taskId: Int): Result<Bitmap>
}

/**
 * Default implementation of [ScreenCaptureThumbnailRepository].
 *
 * Captures new thumbnail on request, falls back to cached thumbnail if capture fails.
 */
@ScreenCaptureUiScope
class ScreenCaptureThumbnailRepositoryImpl
@Inject
constructor(
    @Background private val bgContext: CoroutineContext,
    private val activityManager: ActivityManagerWrapper,
) : ScreenCaptureThumbnailRepository {

    override suspend fun loadThumbnail(taskId: Int): Result<Bitmap> =
        withContext(bgContext) {
            getLatestThumbnail(taskId)?.let { Result.success(it) }
                ?: Result.failure(IllegalStateException("Could not get thumbnail for task $taskId"))
        }

    private fun getLatestThumbnail(taskId: Int): Bitmap? =
        activityManager.takeTaskThumbnail(taskId).thumbnail
            ?: activityManager.getTaskThumbnail(taskId, false).thumbnail
}
