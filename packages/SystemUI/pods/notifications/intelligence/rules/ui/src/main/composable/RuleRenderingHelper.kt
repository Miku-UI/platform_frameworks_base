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

package com.android.systemui.notifications.intelligence.rules.ui.composable

import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import com.android.systemui.notifications.intelligence.rules.shared.model.AppModel
import com.android.systemui.notifications.intelligence.rules.shared.model.PersonModel
import com.android.systemui.notifications.intelligence.rules.ui.viewmodel.TextChunk
import com.android.systemui.notifications.intelligence.rules.ui.viewmodel.TextStyles

/** Transforms a series of text chunks into a single annotated string that can be rendered. */
internal fun buildAnnotatedString(
    textChunks: List<TextChunk>,
    textStyles: TextStyles,
): AnnotatedString {
    return buildAnnotatedString { textChunks.forEach { appendTextChunk(it, textStyles) } }
}

/** Transforms a series of text chunks into the inline text content that should be rendered. */
internal fun buildInlineContentMap(
    textChunks: List<TextChunk>,
    appIcon: @Composable (AppModel) -> Unit,
    personIcon: @Composable (PersonModel) -> Unit,
    textSize: TextUnit,
): Map<String, InlineTextContent> {
    return textChunks.filterIsInstance<TextChunk.Icon<*>>().associate { iconChunk ->
        val iconContent =
            InlineTextContent(
                placeholder =
                    Placeholder(
                        width = textSize,
                        height = textSize,
                        placeholderVerticalAlign = PlaceholderVerticalAlign.Center,
                    )
            ) {
                when (val model = iconChunk.model) {
                    is AppModel -> appIcon(model)
                    is PersonModel -> personIcon(model)
                    else -> {
                        throw IllegalStateException("Unsupported model type for inline icon")
                    }
                }
            }
        iconChunk.iconId to iconContent
    }
}

private fun AnnotatedString.Builder.appendTextChunk(chunk: TextChunk, textStyles: TextStyles) {
    when (chunk) {
        is TextChunk.BasicText -> {
            append(chunk.text)
        }
        is TextChunk.FieldValueText -> {
            withStyle(style = textStyles.specifiedValueSpanStyle) { append(chunk.text) }
        }
        is TextChunk.ClickableText -> {
            appendClickableRegion(
                text = chunk.text,
                onClick = chunk.onClick,
                isAmbiguous = chunk.isAmbiguous,
                textStyles = textStyles,
            )
        }
        is TextChunk.Icon<*> -> {
            appendInlineContent(chunk.iconId)
        }
    }
}

/** Appends a piece of clickable text. */
private fun AnnotatedString.Builder.appendClickableRegion(
    text: String,
    onClick: () -> Unit,
    isAmbiguous: Boolean,
    textStyles: TextStyles,
) {
    withLink(
        LinkAnnotation.Clickable(
            tag = text,
            styles =
                TextLinkStyles(
                    style =
                        if (isAmbiguous) textStyles.ambiguousValueSpanStyle
                        else textStyles.specifiedValueSpanStyle
                ),
            linkInteractionListener = { onClick.invoke() },
        )
    ) {
        append(text)
    }
}
