/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.systemui.statusbar.pipeline.airplane.data.repository.impl

import android.net.ConnectivityManager
import android.os.Handler
import android.provider.Settings.Global
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.log.table.TableLogBuffer
import com.android.systemui.log.table.logDiffsForTable
import com.android.systemui.statusbar.pipeline.airplane.data.repository.AirplaneModeRepository
import com.android.systemui.statusbar.pipeline.airplane.shared.AirplaneTableLog
import com.android.systemui.util.settings.GlobalSettings
import com.android.systemui.util.settings.SettingObserver
import com.android.systemui.utils.coroutines.flow.conflatedCallbackFlow
import javax.inject.Inject
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext

@SysUISingleton
@Suppress("EXPERIMENTAL_IS_NOT_ENABLED")
public class AirplaneModeRepositoryImpl
@Inject
constructor(
    private val connectivityManager: ConnectivityManager,
    @Background private val bgHandler: Handler?,
    @Background private val backgroundContext: CoroutineContext,
    private val globalSettings: GlobalSettings,
    @AirplaneTableLog logger: TableLogBuffer,
    @Background scope: CoroutineScope,
) : AirplaneModeRepository {
    // TODO(b/254848912): Replace this with a generic SettingObserver coroutine once we have it.
    public override val isAirplaneMode: StateFlow<Boolean> =
        conflatedCallbackFlow {
                val observer =
                    object : SettingObserver(globalSettings, bgHandler, Global.AIRPLANE_MODE_ON) {
                        override fun handleValueChanged(value: Int, observedChange: Boolean) {
                            trySend(value == 1)
                        }
                    }

                observer.isListening = true
                trySend(observer.value == 1)
                awaitClose { observer.isListening = false }
            }
            .distinctUntilChanged()
            .logDiffsForTable(logger, columnName = "isAirplaneMode", initialValue = false)
            .stateIn(
                scope,
                started = SharingStarted.WhileSubscribed(),
                // When the observer starts listening, the flow will emit the current value so the
                // initialValue here is irrelevant.
                initialValue = false,
            )

    public override suspend fun setIsAirplaneMode(isEnabled: Boolean) {
        withContext(backgroundContext) { connectivityManager.setAirplaneMode(isEnabled) }
    }
}
