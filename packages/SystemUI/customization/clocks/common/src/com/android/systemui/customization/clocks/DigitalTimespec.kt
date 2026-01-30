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

package com.android.systemui.customization.clocks

import com.android.systemui.plugins.keyguard.ui.clocks.ClockViewIds

enum class DigitalTimespec(private val hourViewId: Int, private val minuteViewId: Int) {
    TIME_FULL_FORMAT(ClockViewIds.TIME_FULL_FORMAT, ClockViewIds.TIME_FULL_FORMAT),
    DIGIT_PAIR(ClockViewIds.HOUR_DIGIT_PAIR, ClockViewIds.MINUTE_DIGIT_PAIR),
    FIRST_DIGIT(ClockViewIds.HOUR_FIRST_DIGIT, ClockViewIds.MINUTE_FIRST_DIGIT),
    SECOND_DIGIT(ClockViewIds.HOUR_SECOND_DIGIT, ClockViewIds.MINUTE_SECOND_DIGIT);

    fun getViewId(isHour: Boolean): Int = if (isHour) hourViewId else minuteViewId
}
