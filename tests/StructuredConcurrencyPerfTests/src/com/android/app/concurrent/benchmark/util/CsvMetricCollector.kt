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
import android.os.Environment
import android.util.Log
import androidx.benchmark.Outputs
import androidx.test.platform.app.InstrumentationRegistry
import com.android.helpers.ICollectorHelper
import java.io.File
import java.io.IOException

@OptionClass(alias = "csv-metric-collector")
class CsvMetricCollector : BaseCollectionListener<String?>() {

    companion object Helper : ICollectorHelper<String?> {
        private const val TAG = "CsvMetricCollector"

        private const val PARAM_HEADER = "param_1,param_2,param_3,param_4"
        private val MAX_SUPPORTED_PARAMS = PARAM_HEADER.count { it == ',' } + 1
        private const val CSV_HEADER = "test_name,class_name,$PARAM_HEADER,metric_name,metric_value"
        private val csvFileContents = StringBuilder()
        lateinit var currentCsvPrefix: String
        private lateinit var bgThreadName: String

        init {
            clearActiveName()
        }

        override fun startCollecting(): Boolean {
            Log.i(TAG, "startCollecting")
            csvFileContents.clear()
            csvFileContents.append("${CSV_HEADER}\n")
            return true
        }

        private fun randomThreadId(): String {
            val numericalChars = ('0'..'9')
            return String(CharArray(8) { numericalChars.random() })
        }

        fun getCurrentBgThreadName(): String {
            return bgThreadName
        }

        fun setActiveName(className: String, methodName: String) {
            dbg { "setActiveName" }
            bgThreadName =
                if (className.isEmpty() || methodName.isEmpty()) {
                    "${BG_THREAD_NAME_PREFIX}not_set}"
                } else {
                    "${BG_THREAD_NAME_PREFIX}${randomThreadId()}"
                }
            val paramStart = methodName.indexOf('[')
            val paramEnd = methodName.indexOf(']')
            var params = ""
            var testName: CharSequence = methodName
            var extraCommas = MAX_SUPPORTED_PARAMS - 1
            if (0 < paramStart) {
                testName = methodName.subSequence(0, paramStart)
                if (paramStart < paramEnd) {
                    val paramStr = methodName.subSequence(paramStart + 1, paramEnd).toString()
                    val paramArray = paramStr.split(",")
                    extraCommas = MAX_SUPPORTED_PARAMS - paramArray.size
                    if (extraCommas < 0) {
                        error(
                            "Max supported parameters for csv file is $MAX_SUPPORTED_PARAMS, but got $paramStr"
                        )
                    }
                    params = paramArray.joinToString(",") { it }
                }
            }
            params += String(CharArray(extraCommas) { ',' })
            currentCsvPrefix = "$testName,$className,$params"
            Log.i(
                TAG,
                "setActiveName(\"$className\", \"$methodName\"), " +
                    "new metric csv prefix is: $currentCsvPrefix",
            )
        }

        fun clearActiveName() {
            setActiveName("", "")
        }

        fun putMetric(name: String, value: String) {
            Log.i(TAG, "putMetric(\"$name\", \"$value\")")
            csvFileContents.append("$currentCsvPrefix,$name,$value\n")
        }

        override fun getMetrics(): Map<String?, String?>? {
            Log.i(TAG, "getMetrics()")

            val context = InstrumentationRegistry.getInstrumentation().targetContext
            InstrumentationRegistry.getInstrumentation().componentName
            // On Android Q+ we are using the media directory because that is
            // the directory that the shell has access to. Context: b/181601156
            @Suppress("DEPRECATION")
            val dirUsableByAppAndShell =
                context.externalMediaDirs.firstOrNull {
                    Environment.getExternalStorageState(it) == Environment.MEDIA_MOUNTED
                }
            val metricsFile =
                File(dirUsableByAppAndShell, "metric-summary_${Outputs.dateToFileName()}.csv")
            val path = metricsFile.absolutePath
            if (!metricsFile.exists()) {
                metricsFile.parentFile?.mkdirs()
                try {
                    metricsFile.createNewFile()
                } catch (e: IOException) {
                    val msg = "Failed to create file for benchmark report in: ${metricsFile.parent}"
                    Log.e(TAG, msg, e)
                }
                Log.i(TAG, "Writing metrics to file: $path")
            } else {
                Log.w(TAG, "Writing metrics to existing file, overwriting content: $path")
            }

            try {
                metricsFile.writeText(csvFileContents.toString())
            } catch (e: IOException) {
                Log.e(TAG, "Failed to write contents to csv file: $path", e)
            }

            return mapOf("metricSummaryOutputFile" to path)
        }

        override fun stopCollecting(): Boolean {
            Log.i(TAG, "stopCollecting")
            csvFileContents.clear()
            return true
        }
    }

    init {
        Log.i(TAG, "init")
        createHelperInstance(Helper)
    }
}
