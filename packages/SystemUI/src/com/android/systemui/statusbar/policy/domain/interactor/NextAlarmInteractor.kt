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

package com.android.systemui.statusbar.policy.domain.interactor

import android.app.AlarmManager
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.statusbar.policy.NextAlarmController
import com.android.systemui.utils.coroutines.flow.conflatedCallbackFlow
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn

/** Interactor responsible for determining if the alarm icon should be shown. */
@SysUISingleton
class NextAlarmInteractor
@Inject
constructor(
    @Background private val bgDispatcher: CoroutineDispatcher,
    @Background private val scope: CoroutineScope,
    private val nextAlarmController: NextAlarmController,
) {

    /**
     * A StateFlow that emits `true` if there is an upcoming alarm scheduled, and `false` otherwise.
     * This is driven by callbacks from [NextAlarmController].
     */
    val isAlarmSet: StateFlow<Boolean> =
        conflatedCallbackFlow {
                val callback =
                    NextAlarmController.NextAlarmChangeCallback {
                        nextAlarm: AlarmManager.AlarmClockInfo? ->
                        // nextAlarm is non-null if an alarm is set, null otherwise.
                        trySend(nextAlarm != null)
                    }

                nextAlarmController.addCallback(callback)
                awaitClose { nextAlarmController.removeCallback(callback) }
            }
            .flowOn(bgDispatcher)
            .stateIn(
                initialValue = false,
                scope = scope,
                started = SharingStarted.WhileSubscribed(),
            )
}
