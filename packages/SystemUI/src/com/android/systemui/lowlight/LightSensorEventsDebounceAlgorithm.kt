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

package com.android.systemui.lowlight

import android.util.Log
import com.android.internal.annotations.GuardedBy
import com.android.internal.util.RingBuffer
import com.android.systemui.Dumpable
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Main
import com.android.systemui.dump.DumpManager
import com.android.systemui.lowlight.dagger.LowLightModule
import com.android.systemui.lowlightclock.LowLightLogger
import com.android.systemui.util.concurrency.DelayableExecutor
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList
import javax.inject.Inject
import javax.inject.Named

/**
 * An algorithm that receives light sensor events, debounces the signals, and transforms into an
 * ambient light mode: light, dark, or undecided.
 *
 * More about the algorithm at go/titan-light-sensor-debouncer.
 */
@SysUISingleton
class LightSensorEventsDebounceAlgorithm
@Inject
constructor(
    @Main private val executor: DelayableExecutor,
    dumpManager: DumpManager,
    private val logger: LowLightLogger,
    @Named(LowLightModule.LIGHT_MODE_THRESHOLD) private val lightModeThreshold: Float,
    @Named(LowLightModule.DARK_MODE_THRESHOLD) private val darkModeThreshold: Float,
    @Named(LowLightModule.LIGHT_MODE_SAMPLING_SPAN) private val lightSamplingSpanMillis: Int,
    @Named(LowLightModule.DARK_MODE_SAMPLING_SPAN) private val darkSamplingSpanMillis: Int,
    @Named(LowLightModule.LIGHT_MODE_SAMPLING_FREQUENCY)
    private val lightSamplingFrequencyMillis: Int,
    @Named(LowLightModule.DARK_MODE_SAMPLING_FREQUENCY) private val darkSamplingFrequencyMillis: Int,
) : Dumpable, AmbientLightModeMonitor.DebounceAlgorithm {
    companion object {
        private const val TAG = "LightDebounceAlgorithm"
        private val DEBUG = Log.isLoggable(TAG, Log.DEBUG)
        private const val LIGHT_SENSOR_EVENTS_LOG_COUNT = 100
    }

    // Registered callback, which gets triggered when the ambient light mode changes.
    private var callback: AmbientLightModeMonitor.Callback? = null

    // Queue of bundles used for calculating [isLightMode], ordered from oldest to latest.
    val bundlesQueueLightMode = CopyOnWriteArrayList<ArrayList<Float>>()

    // Queue of bundles used for calculating [isDarkMode], ordered from oldest to latest
    val bundlesQueueDarkMode = CopyOnWriteArrayList<ArrayList<Float>>()

    private val lock = Object()
    private var lastRecordedLightSensorState: LightSensorStateRecord? = null

    @GuardedBy("lock")
    private val lightSensorStateRecords =
        RingBuffer(LightSensorStateRecord::class.java, LIGHT_SENSOR_EVENTS_LOG_COUNT)

    // Whether the algorithm uses incoming light sensor values to immediately compute an ambient
    // light mode. Default to true to quickly get a result when the algorithm first starts.
    private var immediateResult = true

    // The current ambient light mode.
    @AmbientLightModeMonitor.AmbientLightMode
    var mode = AmbientLightModeMonitor.AMBIENT_LIGHT_MODE_UNDECIDED
        set(value) {
            if (field == value) return
            field = value

            if (immediateResult && value != AmbientLightModeMonitor.AMBIENT_LIGHT_MODE_UNDECIDED) {
                logger.d(
                    TAG,
                    "stopping immediate result because ambient light mode " + "has been determined",
                )
                immediateResult = false
            }

            logger.d(TAG, "Light mode changed: ${createLightSensorStateRecord().toString(null)}")

            callback?.onChange(value)
        }

    // The latest claim of whether it should be light mode.
    var isLightMode = false
        set(value) {
            if (field == value) return
            field = value

            if (DEBUG) Log.d(TAG, "isLightMode: $value")
            updateMode()
        }

    // The latest claim of whether it should be dark mode.
    var isDarkMode = false
        set(value) {
            if (field == value) return
            field = value

            if (DEBUG) Log.d(TAG, "isDarkMode: $value")
            updateMode()
        }

    private fun updateMode() {
        val newMode =
            when (mode) {
                AmbientLightModeMonitor.AMBIENT_LIGHT_MODE_DARK ->
                    if (isLightMode && !isDarkMode) AmbientLightModeMonitor.AMBIENT_LIGHT_MODE_LIGHT
                    else mode
                AmbientLightModeMonitor.AMBIENT_LIGHT_MODE_LIGHT ->
                    if (isDarkMode && !isLightMode) AmbientLightModeMonitor.AMBIENT_LIGHT_MODE_DARK
                    else mode
                else -> // AMBIENT_LIGHT_MODE_UNDECIDED
                when {
                        isDarkMode && !isLightMode ->
                            AmbientLightModeMonitor.AMBIENT_LIGHT_MODE_DARK
                        isLightMode && !isDarkMode ->
                            AmbientLightModeMonitor.AMBIENT_LIGHT_MODE_LIGHT
                        else -> mode
                    }
            }
        if (newMode != mode) {
            mode = newMode
        }
    }

    // The latest average of the light mode bundle.
    var bundleAverageLightMode = 0.0
        set(value) {
            field = value

            if (DEBUG) Log.d(TAG, "light mode average: $value")

            isLightMode = value > lightModeThreshold
        }

    // The latest average of the dark mode bundle.
    var bundleAverageDarkMode = 0.0
        set(value) {
            field = value

            if (DEBUG) Log.d(TAG, "dark mode average: $value")

            isDarkMode = value < darkModeThreshold
        }

    // The latest bundle for calculating light mode claim.
    var bundleLightMode = ArrayList<Float>()
        set(value) {
            field = value

            val average = value.average()

            if (!average.isNaN()) {
                bundleAverageLightMode = average
            }
        }

    // The latest bundle for calculating dark mode claim.
    var bundleDarkMode = ArrayList<Float>()
        set(value) {
            field = value

            val average = value.average()

            if (!average.isNaN()) {
                bundleAverageDarkMode = average
            }
        }

    // The latest light level from light sensor event updates.
    var lightSensorLevel = 0.0f
        set(value) {
            field = value

            if (immediateResult) {
                if (DEBUG) Log.d(TAG, "using light sensor value to compute an immediate result")
                bundleAverageLightMode = value.toDouble()
                bundleAverageDarkMode = value.toDouble()
            }

            bundlesQueueLightMode.forEach { bundle -> bundle.add(value) }
            bundlesQueueDarkMode.forEach { bundle -> bundle.add(value) }

            synchronized(lock) {
                val newRecord = createLightSensorStateRecord()
                if (newRecord != lastRecordedLightSensorState) {
                    lastRecordedLightSensorState = newRecord
                    lightSensorStateRecords.append(lastRecordedLightSensorState)
                }
            }
        }

    // Creates a new bundle that collects light sensor events for calculating the light mode claim,
    // and adds it to the end of the queue. It schedules a call to dequeue this bundle after
    // [LIGHT_SAMPLING_SPAN_MILLIS]. Once started, it also repeatedly calls itself at
    // [LIGHT_SAMPLING_FREQUENCY_MILLIS].
    val enqueueLightModeBundle: Runnable =
        object : Runnable {
            override fun run() {
                if (callback == null) return

                if (DEBUG) Log.d(TAG, "enqueueing a light mode bundle")

                bundlesQueueLightMode.add(ArrayList())

                executor.executeDelayed(dequeueLightModeBundle, lightSamplingSpanMillis.toLong())
                executor.executeDelayed(this, lightSamplingFrequencyMillis.toLong())
            }
        }

    // Creates a new bundle that collects light sensor events for calculating the dark mode claim,
    // and adds it to the end of the queue. It schedules a call to dequeue this bundle after
    // [DARK_SAMPLING_SPAN_MILLIS]. Once started, it also repeatedly calls itself at
    // [DARK_SAMPLING_FREQUENCY_MILLIS].
    val enqueueDarkModeBundle: Runnable =
        object : Runnable {
            override fun run() {
                if (callback == null) return

                if (DEBUG) Log.d(TAG, "enqueueing a dark mode bundle")

                bundlesQueueDarkMode.add(ArrayList())

                executor.executeDelayed(dequeueDarkModeBundle, darkSamplingSpanMillis.toLong())
                executor.executeDelayed(this, darkSamplingFrequencyMillis.toLong())
            }
        }

    // Collects the oldest bundle from the light mode bundles queue, and as a result triggering a
    // calculation of the light mode claim.
    val dequeueLightModeBundle: Runnable =
        object : Runnable {
            override fun run() {
                if (callback == null) return

                val bundle = try {
                    bundlesQueueLightMode.removeAt(0)
                } catch (e: IndexOutOfBoundsException) {
                    return
                }

                bundleLightMode = bundle

                if (DEBUG)
                    Log.d(TAG, "dequeued a light mode bundle of size ${bundleLightMode.size}")
            }
        }

    // Collects the oldest bundle from the dark mode bundles queue, and as a result triggering a
    // calculation of the dark mode claim.
    val dequeueDarkModeBundle: Runnable =
        object : Runnable {
            override fun run() {
                if (callback == null) return

                val bundle = try {
                    bundlesQueueDarkMode.removeAt(0)
                } catch (e: IndexOutOfBoundsException) {
                    return
                }

                bundleDarkMode = bundle

                if (DEBUG) Log.d(TAG, "dequeued a dark mode bundle of size ${bundleDarkMode.size}")
            }
        }

    val isStarted: Boolean
        get() = callback != null

    init {
        dumpManager.registerNormalDumpable(TAG, this)
    }

    /**
     * Start the algorithm.
     *
     * @param callback callback that gets triggered when the ambient light mode changes.
     */
    override fun start(callback: AmbientLightModeMonitor.Callback?) {
        logger.d(TAG, "Starting light sensor events debounce algorithm.")

        if (callback == null) {
            if (DEBUG) Log.w(TAG, "callback is null")
            return
        }

        if (this.callback != null) {
            if (DEBUG) Log.w(TAG, "already started")
            return
        }

        this.callback = callback

        executor.execute(enqueueLightModeBundle)
        executor.execute(enqueueDarkModeBundle)
    }

    /** Stop the algorithm. */
    override fun stop() {
        if (callback == null) {
            logger.d(
                TAG,
                "Skip stopping light sensor events debounce algorithm because " +
                    "it has not been started.",
            )
            return
        }

        logger.d(TAG, "Stopping light sensor events debounce algorithm.")

        callback = null

        reset()
    }

    /** Resets algorithm data. */
    private fun reset() {
        bundlesQueueLightMode.clear()
        bundlesQueueDarkMode.clear()

        lightSensorLevel = 0.0f

        bundleLightMode.clear()
        bundleDarkMode.clear()

        bundleAverageLightMode = 0.0
        bundleAverageDarkMode = 0.0

        isLightMode = false
        isDarkMode = false

        mode = AmbientLightModeMonitor.AMBIENT_LIGHT_MODE_UNDECIDED

        immediateResult = true

        synchronized(lock) {
            lightSensorStateRecords.clear()
        }
    }

    /**
     * Update the light sensor event value.
     *
     * @param value light sensor update value.
     */
    override fun onUpdateLightSensorEvent(value: Float) {
        if (callback == null) {
            if (DEBUG) Log.w(TAG, "ignore light sensor event because algorithm not started")
            return
        }

        lightSensorLevel = value
    }

    override fun dump(pw: PrintWriter, args: Array<out String>) {
        pw.println("started: $isStarted")
        pw.println("lightModeThreshold: $lightModeThreshold")
        pw.println("darkModeThreshold: $darkModeThreshold")

        synchronized(lock) {
            if (!lightSensorStateRecords.isEmpty) {
                pw.println()
                pw.println("Last ${lightSensorStateRecords.size()} events:")
                val events = lightSensorStateRecords.toArray()
                val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
                for (event in events) {
                    pw.println("  ${event.toString(formatter)}")
                }
            }
        }
    }

    private fun createLightSensorStateRecord(): LightSensorStateRecord {
        return LightSensorStateRecord(
            mode,
            lightSensorLevel,
            bundleAverageLightMode,
            bundlesQueueLightMode.size,
            bundleAverageDarkMode,
            bundlesQueueDarkMode.size,
        )
    }

    /**
     * Class used to record one light sensor event for the purposes of providing a history of sensor
     * changes in a dumpsys or logcat.
     */
    data class LightSensorStateRecord(
        val mode: Int,
        val lightSensorLevel: Float,
        val bundleAverageLightMode: Double,
        val bundlesQueueLightModeSize: Int,
        val bundleAverageDarkMode: Double,
        val bundlesQueueDarkModeSize: Int,
    ) {
        private val timestamp: Long = System.currentTimeMillis()

        fun toString(timestampFormatter: SimpleDateFormat?): String {
            val sb = StringBuilder()
            if (timestampFormatter != null) {
                sb.append("time=${timestampFormatter.format(Date(timestamp))}, ")
            }
            return sb.append("mode=${modeString(mode)}, ")
                .append("lsl=$lightSensorLevel, ")
                .append("balm=$bundleAverageLightMode, ")
                .append("bqlms=$bundlesQueueLightModeSize, ")
                .append("badm=$bundleAverageDarkMode, ")
                .append("bqdms=$bundlesQueueDarkModeSize")
                .toString()
        }

        private fun modeString(mode: Int): String {
            return when (mode) {
                AmbientLightModeMonitor.AMBIENT_LIGHT_MODE_LIGHT -> "AMBIENT_LIGHT_MODE_LIGHT"
                AmbientLightModeMonitor.AMBIENT_LIGHT_MODE_DARK -> "AMBIENT_LIGHT_MODE_DARK"
                else -> "AMBIENT_LIGHT_MODE_UNDECIDED"
            }
        }
    }
}
