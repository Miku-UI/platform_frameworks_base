/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.settingslib.spaprivileged.framework.common

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import com.android.settingslib.spa.flow.broadcastReceiverFlow as spaBroadcastReceiverFlow
import kotlinx.coroutines.flow.Flow

/** A [BroadcastReceiver] flow for the given [intentFilter]. */
@Deprecated("Please use the base version with the correct flags")
fun Context.broadcastReceiverFlow(intentFilter: IntentFilter): Flow<Intent> =
    spaBroadcastReceiverFlow(intentFilter, flags = Context.RECEIVER_VISIBLE_TO_INSTANT_APPS)
