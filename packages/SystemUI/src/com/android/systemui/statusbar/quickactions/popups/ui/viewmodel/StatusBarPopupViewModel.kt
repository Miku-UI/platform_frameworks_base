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

package com.android.systemui.statusbar.quickactions.popups.ui.viewmodel

import com.android.systemui.statusbar.quickactions.av.ui.viewmodel.AvControlsPopupViewModel
import com.android.systemui.statusbar.quickactions.media.ui.viewmodel.MediaControlPopupViewModel
import com.android.systemui.statusbar.quickactions.screenrecord.ui.viewmodel.LargeScreenStopRecordingPopupViewModel2
import com.android.systemui.statusbar.quickactions.sharescreen.ui.viewmodel.ShareScreenPrivacyIndicatorPopupViewModel

interface StatusBarPopupViewModel {
    /** A factory for creating a [StatusBarPopupViewModel]. */
    sealed interface Factory {
        fun create(): StatusBarPopupViewModel

        interface MediaControl : Factory {
            override fun create(): MediaControlPopupViewModel
        }

        interface AvControls : Factory {
            override fun create(): AvControlsPopupViewModel
        }

        interface ShareScreen : Factory {
            override fun create(): ShareScreenPrivacyIndicatorPopupViewModel
        }

        interface ScreenRecording : Factory {
            override fun create(): LargeScreenStopRecordingPopupViewModel2
        }
    }
}
