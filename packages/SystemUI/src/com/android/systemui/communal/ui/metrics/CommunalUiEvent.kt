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

package com.android.systemui.communal.ui.metrics

import com.android.internal.logging.UiEvent
import com.android.internal.logging.UiEventLogger

/** UI event for communal. */
enum class CommunalUiEvent(val eventId: Int) : UiEventLogger.UiEventEnum {
    @UiEvent(doc = "The user tapped a widget drag handle to show the accessibility resize buttons.")
    COMMUNAL_HUB_WIDGET_SHOW_ACCESSIBILITY_RESIZE_BUTTONS(2573),
    @UiEvent(doc = "The user tapped a widget drag handle to hide the accessibility resize buttons.")
    COMMUNAL_HUB_WIDGET_HIDE_ACCESSIBILITY_RESIZE_BUTTONS(2574),
    @UiEvent(doc = "The user tapped the expand (+) button to resize a widget.")
    COMMUNAL_HUB_WIDGET_EXPAND_BY_ACCESSIBILITY_BUTTON(2575),
    @UiEvent(doc = "The user tapped the shrink (-) button to resize a widget.")
    COMMUNAL_HUB_WIDGET_SHRINK_BY_ACCESSIBILITY_BUTTON(2576);

    override fun getId(): Int = eventId
}
