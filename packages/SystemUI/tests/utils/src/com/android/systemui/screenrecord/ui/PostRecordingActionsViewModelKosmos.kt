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

import android.content.applicationContext
import android.net.Uri
import com.android.internal.logging.uiEventLogger
import com.android.systemui.broadcast.broadcastSender
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.plugins.activityStarter
import com.android.systemui.screencapture.common.ui.viewmodel.drawableLoaderViewModel
import com.android.systemui.screencapture.domain.interactor.screenCaptureUiInteractor
import com.android.systemui.screencapture.record.largescreen.data.repository.parentUriRepositoryKosmos
import com.android.systemui.screencapture.record.smallscreen.ui.viewmodel.PostRecordingActionsViewModel
import com.android.systemui.settings.userTracker

val Kosmos.postRecordingActionsViewModelFactory by
    Kosmos.Fixture {
        object : PostRecordingActionsViewModel.Factory {
            override fun create(videoUri: Uri, displayId: Int): PostRecordingActionsViewModel {
                return PostRecordingActionsViewModel(
                    videoUri = videoUri,
                    displayId = displayId,
                    context = applicationContext,
                    broadcastSender = broadcastSender,
                    userTracker = userTracker,
                    drawableLoaderViewModel = drawableLoaderViewModel,
                    screenCaptureUiInteractor = screenCaptureUiInteractor,
                    parentUriRepository = parentUriRepositoryKosmos,
                    uiEventLogger = uiEventLogger,
                    activityStarter = activityStarter,
                )
            }
        }
    }
