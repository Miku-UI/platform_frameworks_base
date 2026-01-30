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

package com.android.settingslib.spa.flow

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.android.settingslib.spa.coroutines.SpaDispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn

private const val TAG = "BroadcastReceiverFlow"

/**
 * A [BroadcastReceiver] flow for the given [intentFilter].
 *
 * Note: This version does not set receiver flags, so only works for system broadcasts.
 */
@RequiresApi(Build.VERSION_CODES.O)
fun Context.broadcastReceiverFlow(intentFilter: IntentFilter): Flow<Intent> =
    broadcastReceiverFlow(intentFilter = intentFilter, flags = 0)

/** A [BroadcastReceiver] flow for the given [intentFilter]. */
@RequiresApi(Build.VERSION_CODES.O)
fun Context.broadcastReceiverFlow(intentFilter: IntentFilter, flags: Int): Flow<Intent> =
    callbackFlow {
            val broadcastReceiver =
                object : BroadcastReceiver() {
                    override fun onReceive(context: Context, intent: Intent) {
                        Log.d(TAG, "onReceive: $intent")
                        val unused = trySend(intent)
                    }
                }
            registerReceiver(broadcastReceiver, intentFilter, flags)

            awaitClose { unregisterReceiver(broadcastReceiver) }
        }
        .catch { e -> Log.e(TAG, "Error while broadcastReceiverFlow", e) }
        .conflate()
        .flowOn(SpaDispatchers.Default)
