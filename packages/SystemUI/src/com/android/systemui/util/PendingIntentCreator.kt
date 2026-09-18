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

package com.android.systemui.util

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.android.systemui.dagger.SysUISingleton
import javax.inject.Inject

/** Wrapper for PendingIntent static methods that create PendingIntents, for testability. */
interface PendingIntentCreator {
    /** See [PendingIntent#getBroadcast] */
    fun getBroadcast(
        context: Context,
        requestCode: Int,
        intent: Intent,
        @PendingIntent.Flags flags: Int,
    ): PendingIntent
}

/** Wrapper for PendingIntent static methods that create PendingIntents, for testability. */
@SysUISingleton
class PendingIntentCreatorImpl @Inject constructor() : PendingIntentCreator {
    /** See [PendingIntent#getBroadcast] */
    override fun getBroadcast(
        context: Context,
        requestCode: Int,
        intent: Intent,
        @PendingIntent.Flags flags: Int,
    ): PendingIntent {
        return PendingIntent.getBroadcast(context, requestCode, intent, flags)
    }
}
