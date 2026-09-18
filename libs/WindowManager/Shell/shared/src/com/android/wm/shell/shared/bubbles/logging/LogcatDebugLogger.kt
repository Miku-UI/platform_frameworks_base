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

package com.android.wm.shell.shared.bubbles.logging

import android.text.TextUtils
import android.util.Log

/** [DebugLogger] that logs to the device logcat. */
class LogcatDebugLogger : DebugLogger {

    override fun d(message: String, vararg parameters: Any?, eventData: String?) {
        Log.d(TAG, TextUtils.formatSimple(message, *parameters))
    }

    override fun i(message: String, vararg parameters: Any?, eventData: String?) {
        Log.i(TAG, TextUtils.formatSimple(message, *parameters))
    }

    override fun v(message: String, vararg parameters: Any?, eventData: String?) {
        Log.v(TAG, TextUtils.formatSimple(message, *parameters))
    }

    override fun w(message: String, vararg parameters: Any?, eventData: String?) {
        Log.w(TAG, TextUtils.formatSimple(message, *parameters))
    }

    override fun e(message: String, vararg parameters: Any?, eventData: String?) {
        Log.e(TAG, TextUtils.formatSimple(message, *parameters))
    }

    companion object {
        const val TAG = "Bubbles"
    }
}
