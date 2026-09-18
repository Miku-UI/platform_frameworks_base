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

package com.android.systemui.screencapture.common.repository

import android.Manifest
import android.annotation.EnforcePermission
import android.content.Context
import android.media.projection.IAppContentProjectionCallback
import android.media.projection.IAppContentProjectionSession
import android.os.PermissionEnforcer
import android.os.RemoteCallback
import android.os.RemoteException

class FakeAppContentProjectionCallback(context: Context) :
    IAppContentProjectionCallback.Stub(PermissionEnforcer(context)) {
    data class ContentRequestCall(
        val newContentConsumer: RemoteCallback,
        val thumbnailWidth: Int,
        val thumbnailHeight: Int,
        val iconWidth: Int,
        val iconHeight: Int,
    )

    val onContentRequestCalls = mutableListOf<ContentRequestCall>()

    @EnforcePermission(allOf = [Manifest.permission.MANAGE_MEDIA_PROJECTION])
    @Throws(RemoteException::class)
    override fun onContentRequest(
        newContentConsumer: RemoteCallback,
        thumbnailWidth: Int,
        thumbnailHeight: Int,
        iconWidth: Int,
        iconHeight: Int,
    ) {
        onContentRequest_enforcePermission()
        onContentRequestCalls.add(
            ContentRequestCall(
                newContentConsumer,
                thumbnailWidth,
                thumbnailHeight,
                iconWidth,
                iconHeight,
            )
        )
    }

    @EnforcePermission(allOf = [Manifest.permission.MANAGE_MEDIA_PROJECTION])
    @Throws(RemoteException::class)
    override fun onLoopbackProjectionStarted(
        session: IAppContentProjectionSession?,
        contentId: Int,
        isAudioRequested: Boolean,
    ) {
        onLoopbackProjectionStarted_enforcePermission()
    }

    var onSessionStoppedCallCount: Int = 0

    @EnforcePermission(allOf = [Manifest.permission.MANAGE_MEDIA_PROJECTION])
    @Throws(RemoteException::class)
    override fun onSessionStopped() {
        onSessionStopped_enforcePermission()
        onSessionStoppedCallCount++
    }

    @EnforcePermission(allOf = [Manifest.permission.MANAGE_MEDIA_PROJECTION])
    @Throws(RemoteException::class)
    override fun onContentRequestCanceled() {
        onContentRequestCanceled_enforcePermission()
    }
}
