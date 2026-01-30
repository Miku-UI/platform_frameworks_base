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

package com.android.systemui.notifications

import com.android.internal.logging.UiEvent
import com.android.internal.logging.UiEventLogger

/** The notification animated chip UiEvent enums. */
enum class NotificationAnimatedChipEvent(private val _id: Int) : UiEventLogger.UiEventEnum {
    @UiEvent(doc = "The animated reply chip was shown")
    NOTIFICATION_ANIMATED_REPLY_CHIP_VISIBLE(2395),
    @UiEvent(doc = "The animated action chip was shown")
    NOTIFICATION_ANIMATED_ACTION_CHIP_VISIBLE(2396);

    override fun getId() = _id
}
