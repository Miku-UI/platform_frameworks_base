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
import androidx.core.graphics.createBitmap
import kotlinx.coroutines.CompletableDeferred

class FakeScreenCaptureThumbnailRepository : ScreenCaptureThumbnailRepository {

    var fakeThumbnail: Result<Bitmap> = Result.success(createBitmap(100, 100))
    val loadThumbnailCalls = mutableListOf<Int>()
    private var loadThumbnailDeferred = CompletableDeferred(Unit)

    override suspend fun loadThumbnail(taskId: Int): Result<Bitmap> {
        loadThumbnailCalls.add(taskId)
        loadThumbnailDeferred.await()
        return fakeThumbnail
    }

    fun setLoadThumbnailSuspends(suspends: Boolean) {
        loadThumbnailDeferred =
            if (suspends) {
                CompletableDeferred()
            } else {
                CompletableDeferred(Unit)
            }
    }

    fun completeLoadThumbnail() {
        loadThumbnailDeferred.complete(Unit)
    }
}
