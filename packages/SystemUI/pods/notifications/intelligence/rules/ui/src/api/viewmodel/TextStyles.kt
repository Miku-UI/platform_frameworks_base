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

package com.android.systemui.notifications.intelligence.rules.ui.viewmodel

import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle

/** A set of styles to use for the notification rules pages. */
public data class TextStyles(
    /** Used for the filler, non-editable text in a rule. */
    val defaultStyle: TextStyle,
    /** Span styles that will be applied for editable values that are fully defined. */
    val specifiedValueSpanStyle: SpanStyle,
    /** Span styles that will be applied for editable values that are still ambiguous. */
    val ambiguousValueSpanStyle: SpanStyle,
)
