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

package com.android.systemui.accessibility

import android.content.Context
import android.content.Intent

/** Helper class to share methods between [SystemActions] and [SystemActionsRepository]. */
object SystemActionsHelper {
    const val INTENT_ACTION_ACCESSIBILITY_DISMISS_NOTIFICATION_SHADE =
        "SYSTEM_ACTION_ACCESSIBILITY_DISMISS_NOTIFICATION_SHADE"

    @JvmStatic
    fun createIntent(context: Context, intentAction: String): Intent {
        return Intent(intentAction).apply {
            setPackage(context.packageName)
            addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
        }
    }
}
