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
package com.android.systemui.statusbar.notification.shared

import android.app.Notification
import android.content.Context
import android.text.TextUtils
import com.android.internal.R
import java.time.Duration

/**
 * Represents a single metric item to be displayed in a MetricStyle notification. This is a UI model
 * representation of [Notification.Metric].
 */
sealed interface Metric {
    /** The label describing the metric. */
    val label: CharSequence

    /** Represents a metric that displays a time difference, like a stopwatch or timer. */
    sealed interface TimeDifference : Metric {
        val isTimer: Boolean
        val useAdaptiveFormat: Boolean

        /** Time difference relative to a specific [java.time.Instant]. */
        data class Instant(
            val zeroTime: java.time.Instant,
            override val isTimer: Boolean,
            override val useAdaptiveFormat: Boolean,
            override val label: CharSequence,
        ) : TimeDifference

        /** Time difference relative to an elapsed realtime. */
        data class ElapsedRealtime(
            val zeroElapsedRealtime: Long,
            override val isTimer: Boolean,
            override val useAdaptiveFormat: Boolean,
            override val label: CharSequence,
        ) : TimeDifference

        /** Time difference representing a paused duration. */
        data class Paused(
            val pausedDuration: Duration,
            override val isTimer: Boolean,
            override val useAdaptiveFormat: Boolean,
            override val label: CharSequence,
        ) : TimeDifference
    }

    /**
     * Represents a metric that displays a simple text value. More than one option can be provided
     * for `value` (e.g. for the integer 1200 it can be "1200" or "1.2K"); the view will choose the
     * appropriate one based on the available screen space.
     */
    data class Text(val textVariants: List<CharSequence>, override val label: CharSequence) :
        Metric
}

/** Extracts a list of UI model [Metric]s from the [Notification.MetricStyle]. */
fun Notification.MetricStyle.extractMetrics(systemUiContext: Context): Sequence<Metric> {
    return metrics.asSequence().map { metric ->
        val metricValue = metric.value
        val valueString = metricValue.toValueString(systemUiContext)
        val label =
            if (!TextUtils.isEmpty(valueString.subtext())) {
                systemUiContext.getString(
                    R.string.notification_metric_label_unit,
                    metric.label,
                    valueString.subtext(),
                )
            } else {
                metric.label
            }
        when (metricValue) {
            is Notification.Metric.TimeDifference -> {
                val useAdaptiveFormat =
                    metricValue.format == Notification.Metric.TimeDifference.FORMAT_ADAPTIVE
                val isTimer = metricValue.isTimer
                when {
                    metricValue.zeroTime != null ->
                        Metric.TimeDifference.Instant(
                            zeroTime = checkNotNull(metricValue.zeroTime),
                            isTimer = isTimer,
                            useAdaptiveFormat = useAdaptiveFormat,
                            label = label,
                        )

                    metricValue.zeroElapsedRealtime != null ->
                        Metric.TimeDifference.ElapsedRealtime(
                            zeroElapsedRealtime = checkNotNull(metricValue.zeroElapsedRealtime),
                            isTimer = isTimer,
                            useAdaptiveFormat = useAdaptiveFormat,
                            label = label,
                        )

                    metricValue.pausedDuration != null ->
                        Metric.TimeDifference.Paused(
                            pausedDuration = checkNotNull(metricValue.pausedDuration),
                            isTimer = isTimer,
                            useAdaptiveFormat = useAdaptiveFormat,
                            label = label,
                        )

                    else -> Metric.Text(valueString.textVariants, label)
                }
            }

            else -> Metric.Text(valueString.textVariants, label)
        }
    }
}
