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

package com.android.systemui.statusbar.policy

class FakeIndividualSensorPrivacyController : IndividualSensorPrivacyController {
    override fun init() {}

    var supportsSensorToggleTestValue: Boolean = true

    override fun supportsSensorToggle(sensor: Int): Boolean = supportsSensorToggleTestValue

    override fun isSensorBlocked(sensor: Int) = blocks.getOrDefault(sensor, false)

    var isSensorBlockedByHardwareToggleTestValue: Boolean = false

    override fun isSensorBlockedByHardwareToggle(sensor: Int): Boolean =
        isSensorBlockedByHardwareToggleTestValue

    var isCameraPrivacyEnabledTestValue: Boolean = true

    override fun isCameraPrivacyEnabled(packageName: String?): Boolean =
        isCameraPrivacyEnabledTestValue

    override fun setSensorBlocked(source: Int, sensor: Int, blocked: Boolean) {
        if (isSensorBlocked(sensor) != blocked) {
            listeners.forEach { it.onSensorBlockedChanged(sensor, blocked) }
        }
        blocks[sensor] = blocked
    }

    override fun suppressSensorPrivacyReminders(sensor: Int, suppress: Boolean) {}

    override fun addCallback(listener: IndividualSensorPrivacyController.Callback) {
        listeners.add(listener)
    }

    override fun removeCallback(listener: IndividualSensorPrivacyController.Callback) {
        listeners.remove(listener)
    }

    var requiresAuthentication: Boolean = false

    override fun requiresAuthentication(): Boolean = requiresAuthentication

    private val blocks = mutableMapOf<Int, Boolean>()
    private val listeners = mutableListOf<IndividualSensorPrivacyController.Callback>()
}
