/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.systemui.media.dialog.ui.model

/** Data defining UI of the stop button in the Output Switcher. */
sealed class StopActionState {
    /** State when the stop action is hidden and hence doesn't have any other extra data. */
    object HiddenStopAction : StopActionState()

    /** State when the stop action is visible along with the required label and click listener. */
    data class VisibleStopAction(val title: String, val onClick: () -> Unit) : StopActionState()
}
