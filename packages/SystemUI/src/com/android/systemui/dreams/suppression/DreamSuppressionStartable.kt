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

package com.android.systemui.dreams.suppression

import android.Manifest.permission.WRITE_DREAM_STATE
import android.os.PowerManager
import androidx.annotation.RequiresPermission
import com.android.systemui.CoreStartable
import com.android.systemui.Flags.dreamSuppression
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.dreams.suppression.data.repository.ActivityRecognitionRepository
import com.android.systemui.dreams.suppression.shared.model.DreamSuppression
import com.android.systemui.log.LogBuffer
import com.android.systemui.log.core.Logger
import com.android.systemui.log.dagger.DreamLog
import com.android.systemui.statusbar.pipeline.battery.domain.interactor.BatteryInteractor
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.dropWhile
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/** Manages the suppression of dreams. */
@SysUISingleton
class DreamSuppressionStartable
@Inject
constructor(
    @Background private val bgScope: CoroutineScope,
    private val activityRecognitionRepository: ActivityRecognitionRepository,
    private val batteryInteractor: BatteryInteractor,
    private val powerManager: PowerManager,
    @DreamLog private val logBuffer: LogBuffer,
) : CoreStartable {

    private val logger = Logger(logBuffer, TAG)
    private var inVehicleJob: Job? = null

    @RequiresPermission(WRITE_DREAM_STATE)
    override fun start() {
        if (!dreamSuppression()) {
            return
        }

        batteryInteractor.isCharging
            .onEach { isCharging ->
                if (isCharging) {
                    startInVehicleDetection()
                } else {
                    stopInVehicleDetection()
                }
            }
            .launchIn(bgScope)
    }

    @RequiresPermission(WRITE_DREAM_STATE)
    private fun startInVehicleDetection() {
        if (inVehicleJob != null) {
            return
        }

        logger.i("Starting in-vehicle detection")
        inVehicleJob =
            bgScope.launch {
                activityRecognitionRepository.inVehicle
                    .map { inVehicle ->
                        if (inVehicle) {
                            DreamSuppression.InVehicle
                        } else {
                            DreamSuppression.None
                        }
                    }
                    .distinctUntilChanged()
                    // No-op until dream suppression becomes true
                    .dropWhile { !it.isSuppressed() }
                    .onEach { dreamSuppression ->
                        logger.i({ "Dream suppression updated: suppressed=$bool1 reason=$str1" }) {
                            bool1 = dreamSuppression.isSuppressed()
                            str1 = dreamSuppression.token
                        }

                        if (dreamSuppression.isSuppressed()) {
                            powerManager.suppressAmbientDisplay(
                                dreamSuppression.token,
                                PowerManager.FLAG_AMBIENT_SUPPRESSION_DREAM,
                            )
                        } else {
                            powerManager.suppressAmbientDisplay(
                                DreamSuppression.InVehicle.token,
                                /*suppress=*/ false,
                            )
                        }
                    }
                    .launchIn(this)
            }
    }

    @RequiresPermission(WRITE_DREAM_STATE)
    private fun stopInVehicleDetection() {
        if (inVehicleJob == null) {
            return
        }

        logger.i("Stopping in-vehicle detection")
        inVehicleJob?.cancel()
        inVehicleJob = null
        powerManager.suppressAmbientDisplay(DreamSuppression.InVehicle.token, /* suppress= */ false)
    }

    private companion object {
        const val TAG = "DreamSuppressionStartable"
    }
}
