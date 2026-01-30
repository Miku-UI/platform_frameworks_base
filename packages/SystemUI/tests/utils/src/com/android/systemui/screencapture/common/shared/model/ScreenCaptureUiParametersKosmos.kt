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

import android.os.UserHandle
import com.android.systemui.kosmos.Kosmos

val Kosmos.recordScreenCaptureUiParameters: ScreenCaptureUiParameters by
    Kosmos.Fixture {
        ScreenCaptureUiParameters(
            screenCaptureType = ScreenCaptureType.RECORD,
            isUserConsentRequired = false,
            resultReceiver = null,
            mediaProjection = null,
            hostAppUserHandle = UserHandle.CURRENT,
            hostAppUid = 0,
            largeScreenParameters = largeScreenCaptureUiParameters,
        )
    }

val Kosmos.castScreenCaptureUiParameters: ScreenCaptureUiParameters by
    Kosmos.Fixture {
        ScreenCaptureUiParameters(
            screenCaptureType = ScreenCaptureType.CAST,
            isUserConsentRequired = false,
            resultReceiver = null,
            mediaProjection = null,
            hostAppUserHandle = UserHandle.CURRENT,
            hostAppUid = 0,
        )
    }

val Kosmos.shareScreenCaptureUiParameters: ScreenCaptureUiParameters by
    Kosmos.Fixture {
        ScreenCaptureUiParameters(
            screenCaptureType = ScreenCaptureType.SHARE_SCREEN,
            isUserConsentRequired = false,
            resultReceiver = null,
            mediaProjection = null,
            hostAppUserHandle = UserHandle.CURRENT,
            hostAppUid = 0,
        )
    }

/** Modifiable to set the default capture type and capture region */
var Kosmos.largeScreenCaptureUiParameters: LargeScreenCaptureUiParameters by
    Kosmos.Fixture { LargeScreenCaptureUiParameters() }
