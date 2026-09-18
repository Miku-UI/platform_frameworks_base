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

import android.media.projection.IAppContentProjectionCallback
import android.media.projection.MediaProjectionAppContent
import android.os.UserHandle
import java.lang.ref.WeakReference
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

class FakeScreenCaptureAppContentRepository : ScreenCaptureAppContentRepository {

    private val appContentChannels =
        mutableMapOf<Pair<String, UserHandle>, Channel<Result<RawAppContent>>>()

    val appContentsForCalls = mutableListOf<AppContentsForCall>()

    override fun appContentsFor(
        packageName: String,
        user: UserHandle,
        thumbnailWidthPx: Int,
        thumbnailHeightPx: Int,
        iconSizePx: Int,
    ): Flow<Result<RawAppContent>> {
        appContentsForCalls.add(
            AppContentsForCall(packageName, user, thumbnailWidthPx, thumbnailHeightPx, iconSizePx)
        )
        return channelFor(packageName, user).receiveAsFlow()
    }

    fun setAppContent(packageName: String, user: UserHandle, appContent: Result<RawAppContent>) {
        channelFor(packageName, user).trySend(appContent)
    }

    fun setAppContentSuccess(
        packageName: String,
        user: UserHandle,
        appContent: List<MediaProjectionAppContent>,
        callback: WeakReference<IAppContentProjectionCallback>,
    ) {
        setAppContent(
            packageName,
            user,
            Result.success(RawAppContent(contents = appContent, projectionCallback = callback)),
        )
    }

    fun setAppContentSuccess(
        packageName: String,
        user: UserHandle,
        callback: WeakReference<IAppContentProjectionCallback>,
        vararg appContent: MediaProjectionAppContent,
    ) {
        setAppContentSuccess(packageName, user, appContent.toList(), callback)
    }

    fun setAppContentFailure(packageName: String, user: UserHandle, throwable: Throwable) {
        setAppContent(packageName, user, Result.failure(throwable))
    }

    private fun channelFor(packageName: String, user: UserHandle): Channel<Result<RawAppContent>> =
        appContentChannels.computeIfAbsent(packageName to user) { Channel(Channel.CONFLATED) }

    data class AppContentsForCall(
        val packageName: String,
        val user: UserHandle,
        val thumbnailWidthPx: Int,
        val thumbnailHeightPx: Int,
        val iconSizePx: Int,
    )
}
