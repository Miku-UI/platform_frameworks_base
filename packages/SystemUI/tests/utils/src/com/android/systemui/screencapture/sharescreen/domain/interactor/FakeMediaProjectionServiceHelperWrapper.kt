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

package com.android.systemui.screencapture.sharescreen.domain.interactor

import android.media.projection.IMediaProjection
import org.mockito.kotlin.mock

class FakeMediaProjectionServiceHelperWrapper : MediaProjectionServiceHelperWrapper {
    private var projection: IMediaProjection = mock()

    var createOrReuseProjectionCallCount = 0
        private set

    var setReviewedConsentIfNeededCallCount = 0
        private set

    var lastSetReviewedConsentResult: Int? = null
        private set

    override fun createOrReuseProjection(
        uid: Int,
        packageName: String,
        reviewGrantedConsentRequired: Boolean,
        displayId: Int,
        mediaProjectionType: Int,
    ): IMediaProjection {
        createOrReuseProjectionCallCount++
        return projection
    }

    override fun setReviewedConsentIfNeeded(
        result: Int,
        reviewGrantedConsentRequired: Boolean,
        projection: IMediaProjection?,
    ) {
        setReviewedConsentIfNeededCallCount++
        lastSetReviewedConsentResult = result
    }
}
