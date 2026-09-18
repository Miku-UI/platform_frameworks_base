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

import android.content.res.Resources
import androidx.annotation.PluralsRes
import com.android.systemui.notifications.intelligence.rules.shared.model.AppModel
import com.android.systemui.notifications.intelligence.rules.shared.model.PersonModel
import com.android.systemui.res.R

/** Creates a model of a full rules text string using the given text values. */
internal fun buildRuleText(
    appsText: TextModel.SingleFieldTextModel<AppModel>?,
    peopleText: TextModel.SingleFieldTextModel<PersonModel>?,
    keywordsText: TextModel.SingleFieldTextModel<String>?,
    bundleText: TextModel.BundleTextModel?,
    resources: Resources,
): RuleDisplayModel {
    // Each field text requires annotations like underlining, click-ability, etc. And, we can't put
    // annotated strings directly into a string resource. So, this method has a few steps:

    // Step 1: Fill out the full rule template with all the fields, but has no styles, clickability,
    // etc. Just a plain ol' string.
    val simpleString: String =
        resources.getString(
            R.string.notification_rules_full_text,
            // TODO: b/478225883 - Is "" an okay value for other languages?
            appsText?.text ?: "",
            peopleText?.text ?: "",
            keywordsText?.text ?: "",
            bundleText?.text ?: "",
        )

    // Step 2: Re-find the fields within the string and annotate each chunk with style etc.
    // TODO: b/478225883 - It's possible the fields have different orders in different languages.
    val fields: List<TextModel> = listOfNotNull(appsText, peopleText, keywordsText, bundleText)
    val textChunks: List<TextChunk> =
        buildList {
                var startIndex = 0
                fields.forEach { field ->
                    val fieldStartIndex = simpleString.indexOf(field.text, startIndex)
                    check(fieldStartIndex >= 0)

                    // Append any part of the string that isn't part of this field
                    add(
                        TextChunk.BasicText(
                            simpleString.substring(startIndex until fieldStartIndex)
                        )
                    )
                    // Append this field
                    addAll(field.toTextChunks())
                    // Continue with the rest of the string
                    startIndex = fieldStartIndex + field.text.length
                }

                // Append any part of the string that's leftover at the end
                add(TextChunk.BasicText(simpleString.substring(startIndex)))
            }
            .filterNot {
                // Get rid of any empty strings from all the concatenation shenanigans
                it == TextChunk.BasicText("")
            }

    return RuleDisplayModel(textChunks)
}

private fun TextModel.toTextChunks(): List<TextChunk> {
    return when (this) {
        is TextModel.SingleFieldTextModel<*> -> this.toTextChunks()
        is TextModel.BundleTextModel -> this.toTextChunks()
    }
}

/** Transforms a single field (like "from Photos +3 more") into a list of individual text chunks. */
private fun TextModel.SingleFieldTextModel<*>.toTextChunks(): List<TextChunk> {
    if (valueFieldRange == null) {
        return listOf(TextChunk.BasicText(text))
    }

    return buildList {
            add(TextChunk.BasicText(text.substring(0 until valueFieldRange.first)))

            // Always add the inline icon right before the value field.
            if (firstItem != null && firstItemIconId != null) {
                add(TextChunk.Icon(firstItem, firstItemIconId))
            }
            if (onClick != null) {
                add(
                    TextChunk.ClickableText(
                        text = text.substring(valueFieldRange),
                        onClick = onClick,
                        isAmbiguous = isAmbiguous,
                    )
                )
            } else {
                add(TextChunk.FieldValueText(text = text.substring(valueFieldRange)))
            }

            add(TextChunk.BasicText(text.substring(valueFieldRange.last + 1)))
        }
        .filterNot {
            // Get rid of any empty strings, which will happen if the onClick range is at the
            // beginning or end of the string
            it == TextChunk.BasicText("")
        }
}

private fun TextModel.BundleTextModel.toTextChunks(): List<TextChunk> {
    if (nameRange == null && emojiRange == null) {
        return listOf(TextChunk.BasicText(text))
    }

    // Only name range
    if (nameRange != null && emojiRange == null) {
        return buildList {
            add(TextChunk.BasicText(text.substring(0 until nameRange.first)))
            add(TextChunk.FieldValueText(text = text.substring(nameRange)))
            add(TextChunk.BasicText(text.substring(nameRange.last + 1)))
        }
    }

    // Only emoji range
    if (emojiRange != null && nameRange == null) {
        return buildList {
            add(TextChunk.BasicText(text.substring(0 until emojiRange.first)))
            add(TextChunk.FieldValueText(text = text.substring(emojiRange)))
            add(TextChunk.BasicText(text.substring(emojiRange.last + 1)))
        }
    }

    // Both name & emoji
    val rangesInOrder: List<IntRange> =
        if (nameRange!!.first < emojiRange!!.first) {
            check(nameRange.last < emojiRange.first)
            listOf(nameRange, emojiRange)
        } else {
            check(emojiRange.last < nameRange.first)
            listOf(emojiRange, nameRange)
        }
    return buildList {
        add(TextChunk.BasicText(text.substring(0 until rangesInOrder[0].first)))
        add(TextChunk.FieldValueText(text = text.substring(rangesInOrder[0])))
        add(
            TextChunk.BasicText(
                text.substring(rangesInOrder[0].last + 1 until rangesInOrder[1].first)
            )
        )
        add(TextChunk.FieldValueText(text = text.substring(rangesInOrder[1])))
        add(TextChunk.BasicText(text.substring(rangesInOrder[1].last + 1)))
    }
}

object ItemTextStringConstants {
    @PluralsRes val includedAppString = R.plurals.notification_rules_from_items
    @PluralsRes val personString = R.plurals.notification_rules_from_items
    @PluralsRes val keywordString = R.plurals.notification_rules_keyword_items
}
