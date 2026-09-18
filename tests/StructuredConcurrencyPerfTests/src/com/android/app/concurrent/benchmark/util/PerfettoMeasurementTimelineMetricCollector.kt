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
package com.android.app.concurrent.benchmark.util

import android.device.collectors.BaseCollectionListener
import android.device.collectors.annotations.OptionClass
import android.os.Looper
import android.os.Trace
import android.util.Log
import com.android.helpers.ICollectorHelper

@OptionClass(alias = "perfetto-mt-collector")
class PerfettoMeasurementTimelineMetricCollector : BaseCollectionListener<Double>() {
    companion object Helper : ICollectorHelper<Double> {
        private const val TAG = "PerfettoMt"
        var metricStore = mutableMapOf<String, Double>()

        override fun startCollecting(): Boolean {
            Log.i(TAG, "startCollecting")
            metricStore = mutableMapOf<String, Double>()
            return true
        }

        fun putMetric(name: String, value: Double) {
            Log.i(TAG, "stopCollecting")
            metricStore[name] = value
        }

        override fun getMetrics(): Map<String, Double> {
            Log.i(TAG, "getMetrics")
            return metricStore
        }

        override fun stopCollecting(): Boolean {
            Log.i(TAG, "stopCollecting")
            return true
        }
    }

    init {
        Log.i(TAG, "init")
        if (DEBUG) {
            Looper.myLooper()!!.setTraceTag(Trace.TRACE_TAG_APP)
        }
        createHelperInstance(Helper)
    }
}
