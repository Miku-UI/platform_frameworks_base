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

import android.content.ComponentName
import android.graphics.Bitmap
import androidx.core.graphics.createBitmap
import kotlinx.coroutines.CompletableDeferred

class FakeScreenCaptureIconRepository : ScreenCaptureIconRepository {

    private var loadIconDeferred = CompletableDeferred(Unit)

    var fakeIcon: Result<Bitmap> = Result.success(createBitmap(100, 100))
    val loadAppIconCalls = mutableListOf<Triple<ComponentName, Int, Boolean>>()

    override suspend fun loadIcon(
        component: ComponentName,
        userId: Int,
        badged: Boolean,
    ): Result<Bitmap> {
        loadAppIconCalls.add(Triple(component, userId, badged))
        loadIconDeferred.await()
        return fakeIcon
    }

    fun setLoadIconSuspends(suspends: Boolean) {
        loadIconDeferred =
            if (suspends) {
                CompletableDeferred()
            } else {
                CompletableDeferred(Unit)
            }
    }

    fun completeLoadIcon() {
        loadIconDeferred.complete(Unit)
    }
}
