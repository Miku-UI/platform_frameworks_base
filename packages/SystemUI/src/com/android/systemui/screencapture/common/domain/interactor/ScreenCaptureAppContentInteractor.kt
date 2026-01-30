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

import com.android.systemui.screencapture.common.ScreenCaptureUi
import com.android.systemui.screencapture.common.ScreenCaptureUiScope
import com.android.systemui.screencapture.common.data.repository.ScreenCaptureAppContentRepository
import com.android.systemui.screencapture.common.domain.model.ScreenCaptureAppContent
import com.android.systemui.screencapture.common.shared.model.ScreenCaptureUiParameters
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/** Interactor for fetching app content info. */
@ScreenCaptureUiScope
class ScreenCaptureAppContentInteractor
@Inject
constructor(
    private val repository: ScreenCaptureAppContentRepository,
    @ScreenCaptureUi private val parameters: ScreenCaptureUiParameters,
) {
    /**
     * Fetch app content info for the given [packageName].
     *
     * Thumbnails will be fetched at the given [thumbnailWidthPx] and [thumbnailHeightPx].
     */
    fun appContentsFor(
        packageName: String,
        thumbnailWidthPx: Int,
        thumbnailHeightPx: Int,
    ): Flow<Result<List<ScreenCaptureAppContent>>> =
        repository
            .appContentsFor(
                packageName = packageName,
                user = parameters.hostAppUserHandle,
                thumbnailWidthPx = thumbnailWidthPx,
                thumbnailHeightPx = thumbnailHeightPx,
            )
            .map { appContent ->
                if (appContent.isFailure) {
                    Result.failure(appContent.exceptionOrNull()!!)
                } else {
                    Result.success(
                        appContent.getOrNull()!!.map { ScreenCaptureAppContent(packageName, it) }
                    )
                }
            }

    /**
     * Fetch app content info for all the given [packageNames].
     *
     * Thumbnails will be fetched at the given [thumbnailWidthPx] and [thumbnailHeightPx]. Only
     * includes entries for packages that have app content that was successfully fetched.
     */
    fun appContentsFor(
        packageNames: List<String>,
        thumbnailWidthPx: Int,
        thumbnailHeightPx: Int,
    ): Flow<List<ScreenCaptureAppContent>> =
        combine(
            packageNames.distinct().map {
                appContentsFor(
                    packageName = it,
                    thumbnailWidthPx = thumbnailWidthPx,
                    thumbnailHeightPx = thumbnailHeightPx,
                )
            }
        ) { appContents ->
            appContents.mapNotNull { it.getOrNull() }.flatMap { it }
        }
}
