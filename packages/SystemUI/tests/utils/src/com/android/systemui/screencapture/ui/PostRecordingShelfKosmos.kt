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

package com.android.systemui.screencapture.ui

import android.content.applicationContext
import android.graphics.drawable.Icon
import android.net.Uri
import android.view.Display
import android.view.accessibility.accessibilityManager
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.screencapture.record.smallscreen.ui.postRecordSnackbarDialogs
import com.android.systemui.screenrecord.ui.postRecordingActionsViewModelFactory
import com.android.systemui.screenrecord.ui.postRecordingImmediateVideoViewModelFactory
import com.android.systemui.statusbar.phone.systemUIDialogFactory

var Kosmos.postRecordingShelfFactory: PostRecordingShelf.Factory by
    Kosmos.Fixture {
        object : PostRecordingShelf.Factory {
            override fun create(
                uri: Uri,
                thumbnail: Icon?,
                display: Display,
                notificationId: Int,
            ): PostRecordingShelf =
                PostRecordingShelf(
                    uri = uri,
                    thumbnail = thumbnail,
                    display = display,
                    notificationId = notificationId,
                    context = applicationContext,
                    dialogFactory = systemUIDialogFactory,
                    actionsViewModelFactory = postRecordingActionsViewModelFactory,
                    videoViewModelFactory = postRecordingImmediateVideoViewModelFactory,
                    postRecordSnackbarDialogs = postRecordSnackbarDialogs,
                    accessibilityManager = accessibilityManager,
                )
        }
    }
