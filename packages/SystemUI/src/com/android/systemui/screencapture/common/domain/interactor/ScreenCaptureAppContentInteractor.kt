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

import android.media.projection.IAppContentProjectionCallback
import com.android.systemui.screencapture.common.ScreenCapture
import com.android.systemui.screencapture.common.ScreenCaptureUiScope
import com.android.systemui.screencapture.common.data.repository.ScreenCaptureAppContentRepository
import com.android.systemui.screencapture.common.domain.model.ScreenCaptureAppContent
import com.android.systemui.screencapture.common.shared.model.ScreenCaptureUiParameters
import java.lang.ref.WeakReference
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** The app content available to be shared for a single package. */
data class SingleAppContent(
    /** The list of the app content that can be shared. */
    val contents: List<ScreenCaptureAppContent>,
    /**
     * The projection callback that can start the session. This is a [WeakReference] because the
     * callback is a Binder object tied to a service connection. Using a weak reference prevents
     * consumers of this data class from accidentally causing a memory leak by holding a strong
     * reference to the Binder proxy after the connection has been torn down.
     */
    val projectionCallback: WeakReference<IAppContentProjectionCallback>,
)

/** Interactor for fetching app content info. */
@ScreenCaptureUiScope
class ScreenCaptureAppContentInteractor
@Inject
constructor(
    private val repository: ScreenCaptureAppContentRepository,
    @ScreenCapture private val parameters: ScreenCaptureUiParameters,
) {
    /**
     * Fetch app content info for the given [packageName].
     *
     * Thumbnails will be fetched at the given [thumbnailWidthPx] and [thumbnailHeightPx]. Icons
     * will be fetched at the given [iconSizePx].
     */
    fun appContentsFor(
        packageName: String,
        thumbnailWidthPx: Int,
        thumbnailHeightPx: Int,
        iconSizePx: Int,
    ): Flow<Result<SingleAppContent>> =
        repository
            .appContentsFor(
                packageName = packageName,
                user = (parameters as ScreenCaptureUiParameters.ShareScreen).hostAppUserHandle,
                thumbnailWidthPx = thumbnailWidthPx,
                thumbnailHeightPx = thumbnailHeightPx,
                iconSizePx = iconSizePx,
            )
            .map { result ->
                result.map { appContentResult ->
                    SingleAppContent(
                        contents =
                            appContentResult.contents.map {
                                ScreenCaptureAppContent(packageName, it)
                            },
                        projectionCallback = appContentResult.projectionCallback,
                    )
                }
            }
}
