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

import androidx.annotation.StringRes
import com.android.systemui.res.R

enum class RecordDetailsPopupType(@StringRes val contentDescriptionRes: Int?) {
    Invisible(null),
    Settings(R.string.screen_record_settings),
    AppSelector(R.string.screen_record_capture_target_choose_app),
    ColorSelector(null),
}
