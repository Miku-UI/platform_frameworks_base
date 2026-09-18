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

package com.android.systemui.screencapture.record.smallscreen.shared.model

import android.view.Display
import com.android.systemui.common.shared.model.Text
import com.android.systemui.res.R
import com.android.systemui.screencapture.common.domain.model.ScreenCaptureRecentTask
import com.android.systemui.screencapture.common.shared.model.ScreenCaptureTarget

sealed interface RecordDetailsTargetModel {

    val label: Text
    val isSelectable: Boolean
    val screenCaptureTarget: ScreenCaptureTarget?
    val canShowTouches: Boolean
    val canUseMarkup: Boolean
    val canUseCamera: Boolean
    val shouldShowAppSelector: Boolean
    val warningMessageRes: Int

    data class EntireScreen(
        override val screenCaptureTarget: ScreenCaptureTarget,
        override val label: Text,
    ) : RecordDetailsTargetModel {

        constructor(
            display: Display,
            label: Text,
        ) : this(ScreenCaptureTarget.Fullscreen(display.displayId), label)

        override val isSelectable: Boolean = true
        override val canShowTouches: Boolean = true
        override val canUseMarkup: Boolean = true
        override val canUseCamera: Boolean = true
        override val shouldShowAppSelector: Boolean = false
        override val warningMessageRes: Int =
            R.string.screenrecord_permission_dialog_warning_entire_screen
    }

    data class SingleApp(val task: ScreenCaptureRecentTask, val appLabel: CharSequence?) :
        RecordDetailsTargetModel {

        override val screenCaptureTarget: ScreenCaptureTarget =
            ScreenCaptureTarget.App(displayId = task.displayId, taskId = task.taskId)

        override val label: Text = Text.Resource(R.string.screen_record_single_app)
        override val isSelectable: Boolean = true
        override val canShowTouches: Boolean = false
        override val canUseMarkup: Boolean = false
        override val canUseCamera: Boolean = false
        override val shouldShowAppSelector: Boolean = true
        override val warningMessageRes: Int =
            R.string.screenrecord_permission_dialog_warning_single_app
    }

    data object SingleAppNoRecents : RecordDetailsTargetModel {

        override val label: Text = Text.Resource(R.string.screen_record_single_app_no_recents)
        override val isSelectable: Boolean = false
        override val screenCaptureTarget: ScreenCaptureTarget? = null
        override val canShowTouches: Boolean = false
        override val canUseMarkup: Boolean = false
        override val canUseCamera: Boolean = false
        override val shouldShowAppSelector: Boolean = false
        override val warningMessageRes: Int =
            R.string.screenrecord_permission_dialog_warning_single_app
    }
}
