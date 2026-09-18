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

package com.android.systemui.statusbar.quickactions.av.ui.viewmodel

import android.graphics.drawable.Drawable
import androidx.compose.runtime.getValue
import com.android.systemui.display.dagger.SystemUIDisplaySubcomponent.DisplayAware
import com.android.systemui.lifecycle.HydratedActivatable
import com.android.systemui.res.R
import com.android.systemui.statusbar.quickactions.av.domain.interactor.AvControlsChipInteractor
import com.android.systemui.statusbar.quickactions.av.shared.model.Sensor
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.map

data class AppDetailUiState(
    val icon: Drawable?,
    val appName: String,
    val packageName: String,
    val sensorUsages: List<Sensor>,
)

sealed interface SensorAccessSummary {
    data class Simple(val text: String) : SensorAccessSummary

    data class WithCount(val prefix: String, val suffixResId: Int, val suffixArg: Int) :
        SensorAccessSummary
}

class SensorActivityViewModel
@AssistedInject
constructor(
    @DisplayAware private val avControlsChipInteractor: AvControlsChipInteractor,
    @Assisted private val setCurrentPage: (PageType) -> Unit,
) : HydratedActivatable() {

    fun returnToMainPage() {
        setCurrentPage(PageType.MAIN)
    }

    fun enterDedicatedPage() {
        setCurrentPage(PageType.SENSOR_ACTIVITY)
    }

    private val sensorAccessList = avControlsChipInteractor.model.map { it.sensorAccessList }

    val showSensorAccessSection by
        sensorAccessList.map { it.isNotEmpty() }.hydratedStateOf(initialValue = false)

    val activeAppsSensorSectionSummary: SensorAccessSummary? by
        sensorAccessList
            .map {
                val apps = it.map { it.appName }.distinct()
                when (apps.size) {
                    0 -> null
                    1 -> SensorAccessSummary.Simple(apps.first())
                    2 -> SensorAccessSummary.Simple("${apps.first()}, ${apps[1]}")
                    else ->
                        SensorAccessSummary.WithCount(
                            prefix = apps.first(),
                            suffixResId = R.string.privacy_chip_apps_using_sensor_suffix,
                            suffixArg = apps.size - 1,
                        )
                }
            }
            .hydratedStateOf(initialValue = null)

    val activeAppsSensorSectionSupportText: Int? by
        sensorAccessList
            .map {
                val sensors = it.map { it.sensor }.distinct()
                when (sensors.size) {
                    0 -> null
                    1 ->
                        when (sensors.first()) {
                            Sensor.CAMERA -> R.string.privacy_chip_camera_in_use
                            Sensor.MICROPHONE -> R.string.privacy_chip_mic_in_use
                        }
                    else -> R.string.privacy_chip_camera_mic_in_use
                }
            }
            .hydratedStateOf(initialValue = null)

    val activeAppsIconDrawable by
        sensorAccessList
            .map {
                val icons = it.map { it.icon }.distinct()
                when (icons.size) {
                    0 -> null
                    1 -> icons.first()
                    else -> null
                }
            }
            .hydratedStateOf(initialValue = null)

    val appDetails by
        sensorAccessList
            .map { accessList ->
                val usagesPerApp = accessList.groupBy { it.packageName }.entries.toList()
                usagesPerApp.map { entry ->
                    val packageName = entry.key
                    val usages = entry.value
                    AppDetailUiState(
                        icon = usages.first().icon,
                        appName = usages.first().appName,
                        packageName = packageName,
                        sensorUsages = usages.map { it.sensor }.distinct(),
                    )
                }
            }
            .hydratedStateOf(initialValue = emptyList())

    fun closeApp(packageName: String) {
        avControlsChipInteractor.closeApp(packageName)
    }

    fun manageApp(packageName: String) {
        avControlsChipInteractor.manageApp(packageName)
    }

    fun openPrivacyDashboard() {
        avControlsChipInteractor.openPrivacyDashboard()
    }

    /** A factory to be used to create view model instances. */
    @AssistedFactory
    interface Factory {
        fun create(setCurrentPage: (PageType) -> Unit): SensorActivityViewModel
    }
}
