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

package com.android.systemui.dreams.ui.metrics

import com.android.internal.logging.UiEvent
import com.android.internal.logging.UiEventLogger

enum class DreamSetupUiEvent(private val id: Int) : UiEventLogger.UiEventEnum {
    @UiEvent(doc = "The dream setup flow was triggered.") DREAM_SETUP_TRIGGERED(2630),
    @UiEvent(doc = "The dream setup flow was completed.") DREAM_SETUP_COMPLETED(2631),
    @UiEvent(doc = "The dream setup flow was snoozed.") DREAM_SETUP_SNOOZED(2632),
    @UiEvent(doc = "The dream setup flow failed permanently.") DREAM_SETUP_FAILED_PERMANENTLY(2633),
    @UiEvent(doc = "The dream setup flow failed due to security check.")
    DREAM_SETUP_SECURITY_CHECK_FAILED(2634),
    @UiEvent(doc = "The dream setup flow was dismissed.") DREAM_SETUP_DISMISSED(2635);

    override fun getId() = id
}
