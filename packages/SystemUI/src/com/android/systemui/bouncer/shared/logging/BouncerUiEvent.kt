/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.systemui.bouncer.shared.logging

import com.android.internal.logging.UiEvent
import com.android.internal.logging.UiEventLogger

/**
 * Bouncer UI events {@link com.android.keyguard.KeyguardSecurityContainer.BouncerUiEvent}. Only
 * contains that used by metrics.
 */
enum class BouncerUiEvent(private val _id: Int) : UiEventLogger.UiEventEnum {
    @UiEvent(doc = "Bouncer is dismissed using extended security access.")
    BOUNCER_DISMISS_EXTENDED_ACCESS(413),

    // "PASSWORD" for BOUNCER_PASSWORD_SUCCESS includes password, pattern, and pin.
    @Deprecated(
        "Use more specific BOUNCER_SUCCESS_* and BOUNCER_FAILURE_* UiEvents." +
            " This UiEvent is the sum of all password, pin, and pattern successes."
    )
    @UiEvent(doc = "Bouncer is successfully unlocked using password, pin or pattern.")
    BOUNCER_PASSWORD_SUCCESS(418),

    // "PASSWORD" for BOUNCER_PASSWORD_FAILURE includes password, pattern, and pin.
    @Deprecated(
        "Use the more specific BOUNCER_SUCCESS_* and BOUNCER_FAILURE_* UiEvents" +
            " This UiEvent is the sum of all password, pin and pattern failures."
    )
    @UiEvent(doc = "An attempt to unlock bouncer using password, pin or pattern has failed.")
    BOUNCER_PASSWORD_FAILURE(419),
    @UiEvent(doc = "The alternate bouncer was shown.") ALTERNATE_BOUNCER_SHOWN(2577),
    @UiEvent(doc = "Bouncer is successfully unlocked using password.")
    BOUNCER_SUCCESS_PASSWORD(2578),
    @UiEvent(doc = "Bouncer is successfully unlocked using pin.") BOUNCER_SUCCESS_PIN(2579),
    @UiEvent(doc = "Bouncer is successfully unlocked using pattern.") BOUNCER_SUCCESS_PATTERN(2580),
    @UiEvent(doc = "An attempt to unlock bouncer using password has failed.")
    BOUNCER_FAILURE_PASSWORD(2581),
    @UiEvent(doc = "An attempt to unlock bouncer using pin has failed.") BOUNCER_FAILURE_PIN(2582),
    @UiEvent(doc = "An attempt to unlock bouncer using pattern has failed.")
    BOUNCER_FAILURE_PATTERN(2583);

    override fun getId() = _id
}
