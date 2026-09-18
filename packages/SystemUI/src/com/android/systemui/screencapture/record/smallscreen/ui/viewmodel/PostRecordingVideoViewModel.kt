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

package com.android.systemui.screencapture.record.smallscreen.ui.viewmodel

import android.net.Uri
import androidx.compose.runtime.getValue
import com.android.systemui.lifecycle.HydratedActivatable
import com.android.systemui.screenrecord.domain.interactor.ScreenRecordingServiceInteractor
import com.android.systemui.screenrecord.shared.model.ScreenRecording
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.filter

interface PostRecordingVideoViewModel {

    val recording: ScreenRecording
    val notificationId: Int
}

class PostRecordingImmediateVideoViewModel
@AssistedInject
constructor(@Assisted videoUri: Uri, @Assisted override val notificationId: Int) :
    HydratedActivatable(), PostRecordingVideoViewModel {

    override val recording: ScreenRecording.Saved =
        ScreenRecording.Saved(videoUri, null, notificationId)

    @AssistedFactory
    interface Factory {

        fun create(videoUri: Uri, notificationId: Int): PostRecordingImmediateVideoViewModel
    }
}

class PostRecordingWaitingVideoViewModel
@AssistedInject
constructor(
    @Assisted private val videoUri: Uri,
    @Assisted override val notificationId: Int,
    screenRecordingServiceInteractor: ScreenRecordingServiceInteractor,
) : HydratedActivatable(), PostRecordingVideoViewModel {

    override val recording: ScreenRecording by
        screenRecordingServiceInteractor.screenRecordings
            .filter { it.uri == videoUri }
            .hydratedStateOf(
                "PostRecordingWaitingVideoViewModel#recording",
                ScreenRecording.Saving(videoUri, notificationId),
            )

    @AssistedFactory
    interface Factory {

        fun create(videoUri: Uri, notificationId: Int): PostRecordingWaitingVideoViewModel
    }
}
