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

package com.android.systemui.accessibility.hearingaid

import com.android.systemui.dagger.SysUISingleton
import javax.inject.Inject

/** Controller to manage the dismissal state of hearing device status notifications. */
@SysUISingleton
class HearingDeviceNotificationDismissController @Inject constructor() {
    private val thresholds =
        listOf(BATTERY_LEVEL_CRITICAL, BATTERY_LEVEL_VERY_LOW, BATTERY_LEVEL_LOW).sorted()
    private val dismissedDevices = mutableSetOf<String>()
    // Tracks the lowest threshold warned for each device
    private val lastThresholdMap = mutableMapOf<String, Int>()

    fun dismissNotification(address: String) {
        dismissedDevices.add(address)
    }

    /**
     * Updates the internal threshold state for the device and determines if the notification should
     * be displayed.
     *
     * This method evaluates the [batteryLevel] against pre-defined thresholds and updates internal
     * latches [lastThresholdMap] to manage re-triggering notifications by removing the device from
     * [dismissedDevices] if a new, lower threshold is met.
     *
     * @param address The Bluetooth address of the device.
     * @param batteryLevel The current battery level of the device.
     * @return true if the notification should be shown, false otherwise.
     */
    fun updateAndCheckNotification(address: String, batteryLevel: Int): Boolean {
        val currentThreshold = thresholds.firstOrNull { batteryLevel <= it }
        if (currentThreshold == null) {
            lastThresholdMap.remove(address)
        } else {
            val lastThreshold = lastThresholdMap[address]
            // If it reaches a new or lower threshold, reset the dismissal
            if (lastThreshold == null || currentThreshold < lastThreshold) {
                dismissedDevices.remove(address)
            }
            lastThresholdMap[address] = currentThreshold
        }
        return address !in dismissedDevices
    }

    fun removeDevice(address: String) {
        dismissedDevices.remove(address)
        lastThresholdMap.remove(address)
    }

    fun reset() {
        dismissedDevices.clear()
        lastThresholdMap.clear()
    }

    companion object {
        private const val BATTERY_LEVEL_LOW = 20
        private const val BATTERY_LEVEL_VERY_LOW = 10
        private const val BATTERY_LEVEL_CRITICAL = 2
    }
}
