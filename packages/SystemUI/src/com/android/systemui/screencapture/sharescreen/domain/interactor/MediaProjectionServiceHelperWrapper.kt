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
import android.media.projection.ReviewGrantedConsentResult
import com.android.systemui.mediaprojection.MediaProjectionServiceHelper
import javax.inject.Inject

/** Wrapper for static methods in [MediaProjectionServiceHelper] to allow for fakes in tests. */
interface MediaProjectionServiceHelperWrapper {
    fun createOrReuseProjection(
        uid: Int,
        packageName: String,
        reviewGrantedConsentRequired: Boolean,
        displayId: Int,
        mediaProjectionType: Int,
    ): IMediaProjection

    fun setReviewedConsentIfNeeded(
        @ReviewGrantedConsentResult result: Int,
        reviewGrantedConsentRequired: Boolean,
        projection: IMediaProjection?,
    )
}

/** Default implementation of [MediaProjectionServiceHelperWrapper]. */
class MediaProjectionServiceHelperWrapperImpl @Inject constructor() :
    MediaProjectionServiceHelperWrapper {
    override fun createOrReuseProjection(
        uid: Int,
        packageName: String,
        reviewGrantedConsentRequired: Boolean,
        displayId: Int,
        mediaProjectionType: Int,
    ): IMediaProjection {
        return MediaProjectionServiceHelper.createOrReuseProjection(
            uid,
            packageName,
            reviewGrantedConsentRequired,
            displayId,
            mediaProjectionType,
        )
    }

    override fun setReviewedConsentIfNeeded(
        result: Int,
        reviewGrantedConsentRequired: Boolean,
        projection: IMediaProjection?,
    ) {
        MediaProjectionServiceHelper.setReviewedConsentIfNeeded(
            result,
            reviewGrantedConsentRequired,
            projection,
        )
    }
}
