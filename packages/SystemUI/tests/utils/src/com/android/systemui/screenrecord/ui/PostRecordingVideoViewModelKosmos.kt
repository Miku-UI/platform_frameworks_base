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

package com.android.systemui.screenrecord.ui

import android.net.Uri
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.screencapture.record.smallscreen.ui.viewmodel.PostRecordingImmediateVideoViewModel
import com.android.systemui.screencapture.record.smallscreen.ui.viewmodel.PostRecordingWaitingVideoViewModel
import com.android.systemui.screenrecord.domain.interactor.screenRecordingServiceInteractor

val Kosmos.postRecordingImmediateVideoViewModelFactory by
    Kosmos.Fixture {
        object : PostRecordingImmediateVideoViewModel.Factory {
            override fun create(
                videoUri: Uri,
                notificationId: Int,
            ): PostRecordingImmediateVideoViewModel {
                return PostRecordingImmediateVideoViewModel(videoUri, notificationId)
            }
        }
    }

val Kosmos.postRecordingWaitingVideoViewModelFactory by
    Kosmos.Fixture {
        object : PostRecordingWaitingVideoViewModel.Factory {
            override fun create(
                videoUri: Uri,
                notificationId: Int,
            ): PostRecordingWaitingVideoViewModel {
                return PostRecordingWaitingVideoViewModel(
                    videoUri = videoUri,
                    notificationId = notificationId,
                    screenRecordingServiceInteractor = screenRecordingServiceInteractor,
                )
            }
        }
    }
