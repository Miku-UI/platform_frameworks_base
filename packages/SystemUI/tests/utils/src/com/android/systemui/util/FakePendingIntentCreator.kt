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
import com.android.systemui.util.mockito.mock

/** A PendingIntentCreator that returns mock PendingIntents and records calls. */
class FakePendingIntentCreator : PendingIntentCreator {

    val getBroadcastCalls = mutableListOf<GetBroadcastCall>()

    override fun getBroadcast(
        context: Context,
        requestCode: Int,
        intent: Intent,
        @PendingIntent.Flags flags: Int,
    ): PendingIntent {
        val ret = mock<PendingIntent>()

        getBroadcastCalls.add(GetBroadcastCall(context, requestCode, intent, flags, ret))

        return ret
    }

    data class GetBroadcastCall(
        val context: Context,
        val requestCode: Int,
        val intent: Intent,
        val flags: Int,
        val returnedPendingIntent: PendingIntent,
    )
}
