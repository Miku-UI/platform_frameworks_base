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
 * limitations under the License
 */

package com.android.systemui.statusbar.notification.row

import android.content.Context
import android.util.AttributeSet
import android.widget.Chronometer
import android.widget.TextView
import androidx.core.view.isVisible
import com.android.systemui.res.R
import com.android.systemui.statusbar.notification.shared.Metric

/**
 * A hybrid notification view for notifications using {@link android.app.Notification.MetricStyle}.
 */
class HybridMetricNotificationView
@JvmOverloads
constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    defStyleRes: Int = 0,
) : HybridNotificationView(context, attrs, defStyleAttr, defStyleRes) {

    private lateinit var _metricLabel: TextView
    val metricLabel: TextView
        get() = _metricLabel

    private lateinit var _metricValue: TextView
    val metricValue: TextView
        get() = _metricValue

    private lateinit var _metricChronometer: Chronometer
    val metricChronometer: Chronometer
        get() = _metricChronometer

    override fun onFinishInflate() {
        super.onFinishInflate()
        _metricLabel = requireViewById(R.id.metric_label_0)
        _metricValue = requireViewById(R.id.metric_value_0)
        _metricChronometer = requireViewById(R.id.metric_chronometer_0)

        applyTextColor(_metricLabel, mTextColor)
        applyTextColor(_metricValue, mTextColor)
        applyTextColor(_metricChronometer, mTextColor)

        mTransformationHelper.addViewTransformingToSimilar(_metricLabel)
        mTransformationHelper.addViewTransformingToSimilar(_metricValue)
        mTransformationHelper.addViewTransformingToSimilar(_metricChronometer)
    }

    fun bind(title: CharSequence?, metric: Metric?) {
        val metric = metric ?: return super.bind(title, null, null)

        val hasTitle = !title.isNullOrBlank()
        // If the notification has no title, the metric's label is used as the main title.
        val notifTitle = if (hasTitle) title else metric.label
        super.bind(notifTitle, null, null)

        _metricLabel.text = if (hasTitle) metric.label else null
        _metricLabel.isVisible = hasTitle

        when (metric) {
            is Metric.Text -> {
                _metricValue.text = metric.textVariants.first()
                _metricValue.isVisible = true
                _metricChronometer.isVisible = false
            }
            is Metric.TimeDifference -> {
                _metricValue.isVisible = false
                _metricChronometer.isVisible = true
                _metricChronometer.isCountDown = metric.isTimer
                _metricChronometer.isUseAdaptiveFormat = metric.useAdaptiveFormat
                _metricChronometer.format = null
                _metricChronometer.setStarted(metric !is Metric.TimeDifference.Paused)
                when (metric) {
                    is Metric.TimeDifference.ElapsedRealtime ->
                        _metricChronometer.setBase(metric.zeroElapsedRealtime)
                    is Metric.TimeDifference.Instant -> _metricChronometer.setBase(metric.zeroTime)
                    is Metric.TimeDifference.Paused ->
                        _metricChronometer.setPausedDuration(metric.pausedDuration)
                }
            }
        }
    }
}
