/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.systemui.screencapture.data.repository

import android.os.Trace
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.screencapture.common.shared.model.ScreenCaptureUiState
import com.android.systemui.screencapture.common.shared.model.methodName
import com.android.systemui.screencapture.common.shared.model.tracingCookie
import javax.inject.Inject

interface ScreenCaptureTracingRepository {

    fun beginVisibilityChangeSection(state: ScreenCaptureUiState)

    fun endVisibilityChangeSection(state: ScreenCaptureUiState)
}

@SysUISingleton
class ScreenCaptureTracingRepositoryImpl @Inject constructor() : ScreenCaptureTracingRepository {

    override fun beginVisibilityChangeSection(state: ScreenCaptureUiState) {
        with(state) { Trace.beginAsyncSection(methodName, tracingCookie) }
    }

    override fun endVisibilityChangeSection(state: ScreenCaptureUiState) {
        with(state) { Trace.endAsyncSection(methodName, tracingCookie) }
    }
}
