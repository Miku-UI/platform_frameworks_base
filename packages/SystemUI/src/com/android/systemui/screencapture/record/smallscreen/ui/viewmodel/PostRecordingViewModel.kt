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

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.android.systemui.lifecycle.HydratedActivatable
import com.android.systemui.plugins.ActivityStarter
import com.android.systemui.res.R
import com.android.systemui.screencapture.common.shared.model.ScreenCaptureType
import com.android.systemui.screencapture.common.shared.model.ScreenCaptureUiParameters
import com.android.systemui.screencapture.common.ui.viewmodel.DrawableLoaderViewModel
import com.android.systemui.screencapture.common.ui.viewmodel.DrawableLoaderViewModelImpl
import com.android.systemui.screencapture.domain.interactor.ScreenCaptureUiInteractor
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject

private const val MIME_TYPE = "video/mp4"

class PostRecordingViewModel
@AssistedInject
constructor(
    @Assisted val videoUri: Uri,
    private val context: Context,
    private val activityStarter: ActivityStarter,
    private val drawableLoaderViewModelImpl: DrawableLoaderViewModelImpl,
    private val screenCaptureUiInteractor: ScreenCaptureUiInteractor,
) : HydratedActivatable(), DrawableLoaderViewModel by drawableLoaderViewModelImpl {

    fun retake() {
        screenCaptureUiInteractor.show(
            ScreenCaptureUiParameters(screenCaptureType = ScreenCaptureType.RECORD)
        )
    }

    fun edit() {
        startVideoActivity(
            action = Intent.ACTION_EDIT,
            label = context.getString(R.string.screen_record_edit),
        )
    }

    fun share() {
        startVideoActivity(
            action = Intent.ACTION_SEND,
            label = context.getString(R.string.screenrecord_share_label),
        )
    }

    private fun startVideoActivity(action: String, label: String) {
        val intent =
            Intent(action)
                .setDataAndType(videoUri, MIME_TYPE)
                .putExtra(Intent.EXTRA_STREAM, videoUri)

        activityStarter.startActivity(
            Intent.createChooser(intent, label).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            true,
        )
    }

    @AssistedFactory
    interface Factory {

        fun create(videoUri: Uri): PostRecordingViewModel
    }
}
