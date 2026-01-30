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
import kotlinx.coroutines.CompletableDeferred

class FakeScreenCaptureLabelRepository : ScreenCaptureLabelRepository {

    var fakeLabel: Result<CharSequence> = Result.success("FakeLabel")
    val loadLabelCalls = mutableListOf<Triple<ComponentName, Int, Boolean>>()
    private var loadLabelDeferred = CompletableDeferred(Unit)

    override suspend fun loadLabel(
        component: ComponentName,
        userId: Int,
        badged: Boolean,
    ): Result<CharSequence> {
        loadLabelCalls.add(Triple(component, userId, badged))
        loadLabelDeferred.await()
        return fakeLabel
    }

    fun setLoadLabelSuspends(suspends: Boolean) {
        loadLabelDeferred =
            if (suspends) {
                CompletableDeferred()
            } else {
                CompletableDeferred(Unit)
            }
    }

    fun completeLoadLabel() {
        loadLabelDeferred.complete(Unit)
    }
}
