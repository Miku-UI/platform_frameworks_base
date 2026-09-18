/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.systemui.screencapture

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import com.android.systemui.screencapture.common.shared.model.ScreenCaptureUiParameters
import com.android.systemui.screencapture.common.shared.model.ScreenCaptureUiSource
import com.android.systemui.screencapture.domain.interactor.ScreenCaptureUiInteractor
import javax.inject.Inject

private const val TAG = "ScreenCaptureUiReceiver"
private const val SOURCE_EXTRA = "com.android.systemui.screencapture.source"
private const val PARAMS_EXTRA = "com.android.systemui.screencapture.params"
private const val SHOW_SCREEN_CAPTURE_ACTION =
    "com.android.systemui.screencapture.show_screen_capture"

class ScreenCaptureUiReceiver
@Inject
constructor(private val interactor: ScreenCaptureUiInteractor) : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action == SHOW_SCREEN_CAPTURE_ACTION) {
            interactor.show(
                parameters =
                    intent.getParcelableExtra(PARAMS_EXTRA, ScreenCaptureUiParameters::class.java)
                        ?: run {
                            Log.e(TAG, "Failed to extract params from the intent")
                            return
                        },
                source = intent.getParcelableExtra(SOURCE_EXTRA, ScreenCaptureUiSource::class.java),
            )
        }
    }

    companion object {

        fun intentFilter(): IntentFilter = IntentFilter(SHOW_SCREEN_CAPTURE_ACTION)

        fun showScreenCapture(
            params: ScreenCaptureUiParameters,
            source: ScreenCaptureUiSource? = null,
        ): Intent {
            return Intent(SHOW_SCREEN_CAPTURE_ACTION)
                .putExtra(PARAMS_EXTRA, params)
                .putExtra(SOURCE_EXTRA, source)
        }
    }
}
