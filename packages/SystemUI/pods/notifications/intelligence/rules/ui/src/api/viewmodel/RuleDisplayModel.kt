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

/** Represents a rule that can be displayed on-screen. */
public data class RuleDisplayModel(
    /** The list of text pieces to display. */
    val textChunks: List<TextChunk>
)

/** Represents a single piece of text that all has the same style and function. */
public sealed interface TextChunk {
    /** Simple filler text. */
    public data class BasicText(val text: String) : TextChunk

    /** Text that represents the value of a particular field but is *not* clickable. */
    public data class FieldValueText(val text: String) : TextChunk

    /** Text that, when clicked on, will trigger [onClick]. */
    public data class ClickableText(
        val text: String,
        val onClick: () -> Unit,
        /** True if this text is ambiguous and requires clarification. See [RuleValue.Ambiguous]. */
        val isAmbiguous: Boolean,
    ) : TextChunk

    /** An icon embedded inline within the text. */
    data class Icon<T>(val model: T, val iconId: String) : TextChunk
}
