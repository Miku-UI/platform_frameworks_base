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

package com.android.systemui.screencapture.common.shared.model

import android.os.IBinder
import android.os.ResultReceiver
import android.os.UserHandle
import com.android.systemui.screencapture.record.largescreen.shared.model.ScreenCaptureRegion as LargeScreenCaptureRegion
import com.android.systemui.screencapture.record.largescreen.shared.model.ScreenCaptureType as LargeScreenCaptureType

data class LargeScreenCaptureUiParameters(
    val defaultCaptureType: LargeScreenCaptureType? = null,
    val defaultCaptureRegion: LargeScreenCaptureRegion? = null,
)

data class ScreenCaptureUiParameters
@JvmOverloads
constructor(
    val screenCaptureType: ScreenCaptureType,
    val isUserConsentRequired: Boolean = false,
    val resultReceiver: ResultReceiver? = null,
    val mediaProjection: IBinder? = null,
    val hostAppUserHandle: UserHandle = UserHandle.CURRENT,
    val hostAppUid: Int = 0,
    val largeScreenParameters: LargeScreenCaptureUiParameters? = null,
)
