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

package com.android.server.display

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.input.InputSensorInfo
import android.os.Parcel
import android.os.SystemClock
import android.view.Display
import android.view.DisplayAddress
import java.io.InputStream
import java.io.OutputStream

internal const val TEST_SENSOR_NAME = "test_sensor_name"
internal const val TEST_SENSOR_TYPE_STRING = "test_sensor_type"
internal const val TEST_SENSOR_TYPE = 0
internal const val TEST_SENSOR_MAX_RANGE = 1f


internal fun createInMemoryPersistentDataStore(): PersistentDataStore {
    return PersistentDataStore(object : PersistentDataStore.Injector() {
        override fun openRead(): InputStream = InputStream.nullInputStream()
        override fun startWrite(): OutputStream = OutputStream.nullOutputStream()
        override fun finishWrite(os: OutputStream?, success: Boolean) {}
    })
}

/**
 * Create a custom {@link DisplayAddress} to ensure we're not relying on any specific
 * display-address implementation in our code. Intentionally uses default object (reference)
 * equality rules.
 */
internal fun createTestDisplayAddress(): DisplayAddress = object : DisplayAddress() {
    override fun writeToParcel(p0: Parcel, p1: Int) {}
}

@JvmOverloads
internal fun createSensor(
    type: Int = TEST_SENSOR_TYPE,
    stringType: String = TEST_SENSOR_TYPE_STRING,
    name: String = TEST_SENSOR_NAME,
    maxRange: Float = TEST_SENSOR_MAX_RANGE
): Sensor = Sensor(
    InputSensorInfo(
        name, "vendor", 0, 0, type, maxRange, 1f, 1f, 1, 1, 1, stringType, "", 0, 0, 0
    )
)

@JvmOverloads
internal fun createSensorEvent(
    sensor: Sensor,
    value: Float,
    timestamp: Long = SystemClock.elapsedRealtimeNanos()
): SensorEvent = SensorEvent(
    sensor, 0, timestamp, floatArrayOf(value)
)

@JvmOverloads
fun createDisplayMode(
    id: Int = Display.Mode.INVALID_MODE_ID,
    parentId: Int = Display.Mode.INVALID_MODE_ID,
    flags: Int = 0,
    width: Int = 100,
    height: Int = 200,
    peakRefreshRate: Float = 60f,
    vsyncRate: Float = 60f,
    alternativeRefreshRates: FloatArray = floatArrayOf(),
    supportedHdrTypes: IntArray = intArrayOf()

): Display.Mode = Display.Mode(
    id, parentId, flags, width, height, peakRefreshRate, vsyncRate,
    alternativeRefreshRates, supportedHdrTypes
)
