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

import android.media.projection.MediaProjectionAppContent
import android.os.UserHandle
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

class FakeScreenCaptureAppContentRepository : ScreenCaptureAppContentRepository {

    private val appContentChannels =
        mutableMapOf<Pair<String, UserHandle>, Channel<Result<List<MediaProjectionAppContent>>>>()

    val appContentsForCalls = mutableListOf<AppContentsForCall>()

    override fun appContentsFor(
        packageName: String,
        user: UserHandle,
        thumbnailWidthPx: Int,
        thumbnailHeightPx: Int,
    ): Flow<Result<List<MediaProjectionAppContent>>> {
        appContentsForCalls.add(
            AppContentsForCall(packageName, user, thumbnailWidthPx, thumbnailHeightPx)
        )
        return channelFor(packageName, user).receiveAsFlow()
    }

    fun setAppContent(
        packageName: String,
        user: UserHandle,
        appContent: Result<List<MediaProjectionAppContent>>,
    ) {
        channelFor(packageName, user).trySend(appContent)
    }

    fun setAppContentSuccess(
        packageName: String,
        user: UserHandle,
        appContent: List<MediaProjectionAppContent>,
    ) {
        setAppContent(packageName, user, Result.success(appContent))
    }

    fun setAppContentSuccess(
        packageName: String,
        user: UserHandle,
        vararg appContent: MediaProjectionAppContent,
    ) {
        setAppContentSuccess(packageName, user, appContent.toList())
    }

    fun setAppContentFailure(packageName: String, user: UserHandle, throwable: Throwable) {
        setAppContent(packageName, user, Result.failure(throwable))
    }

    private fun channelFor(
        packageName: String,
        user: UserHandle,
    ): Channel<Result<List<MediaProjectionAppContent>>> =
        appContentChannels.computeIfAbsent(packageName to user) { Channel(Channel.CONFLATED) }

    data class AppContentsForCall(
        val packageName: String,
        val user: UserHandle,
        val thumbnailWidthPx: Int,
        val thumbnailHeightPx: Int,
    )
}
