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

package com.android.systemui.media.dialog

import com.android.settingslib.media.MediaDevice
import com.android.systemui.kairos.awaitClose
import com.android.systemui.qs.flags.QsDetailedView
import com.android.systemui.util.kotlin.getOrNull
import com.android.systemui.utils.coroutines.flow.conflatedCallbackFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onStart

val MediaSwitchingController.currentInputDevice: Flow<MediaDevice?>
    get() =
        conflatedCallbackFlow {
                if (!QsDetailedView.isEnabled) {
                    return@conflatedCallbackFlow
                }

                val callback =
                    object : MediaSwitchingController.Callback {
                        override fun onMediaChanged() {}

                        override fun onMediaStoppedOrPaused() {}

                        override fun onRouteChanged() {}

                        override fun onDeviceListChanged() {
                            trySend(mCurrentInputDevice.getOrNull())
                        }

                        override fun dismissDialog() {}

                        override fun onQuickAccessButtonsChanged() {}
                    }

                start(callback)
                awaitClose { stop() }
            }
            .onStart { emit(null) }
