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
import android.text.Annotation
import android.text.Spanned
import android.text.TextUtils
import androidx.annotation.PluralsRes
import com.android.systemui.log.core.Logger
import com.android.systemui.notifications.intelligence.rules.shared.model.ActionModel
import com.android.systemui.res.R

sealed interface TextModel {
    val text: String

    /** Represents a single field as text. */
    data class SingleFieldTextModel<T>(
        override val text: String,
        /** True if this text is ambiguous and requires clarification. See [RuleValue.Ambiguous]. */
        val isAmbiguous: Boolean,
        /**
         * The range of the text that represents the actual field values. For example, for the
         * string "from Phone +3 more", [valueFieldRange] should encompass just the "Phone +3 more"
         * part.
         *
         * [onClick] will be associated with this range.
         */
        val valueFieldRange: IntRange?,
        /** If this field is fully specified, this is the first item in the list. */
        val firstItem: T?,
        val firstItemIconId: String?,
        /** Optional clickable behavior. */
        val onClick: (() -> Unit)?,
    ) : TextModel

    /** Represents a custom bundle as text. */
    data class BundleTextModel(
        override val text: String,
        /** The range of [text] that represents the bundle name. */
        val nameRange: IntRange?,
        /** The range of text that represents the bundle emoji. */
        val emojiRange: IntRange?,
    ) : TextModel
}

/**
 * Creates text that shows 1 or more selected items.
 *
 * @param [iconId] an ID that will be paired with an inline icon in the composable. Use null if this
 *   field doesn't have an associated image.
 */
internal fun <T> createMultiItemText(
    items: List<T>,
    iconId: ((T) -> String)?,
    label: (T) -> String,
    onClick: (() -> Unit)?,
    @PluralsRes itemTextString: Int,
    resources: Resources,
    logger: Logger,
): TextModel.SingleFieldTextModel<T> {
    check(items.isNotEmpty()) { "Items must be non-empty" }
    val first = items[0]

    val firstLabel = label(first)

    val template = resources.getQuantityText(itemTextString, items.size)
    val spanned =
        TextUtils.expandTemplate(template, firstLabel, (items.size - 1).toString()) as Spanned

    return createFieldText(
        spanned,
        firstItem = first,
        firstItemIconId = iconId?.invoke(first),
        onClick = onClick,
        isAmbiguous = false,
        logger = logger,
    )
}

/**
 * Creates text for a rule value that's underspecified. Clicking on the text will let users clarify
 * what items should be selected.
 */
internal fun <T> createAmbiguousText(
    placeholderText: String,
    onClick: () -> Unit,
    @PluralsRes itemTextString: Int,
    resources: Resources,
    logger: Logger,
): TextModel.SingleFieldTextModel<T> {
    val template = resources.getQuantityText(itemTextString, 1)
    val spanned = TextUtils.expandTemplate(template, placeholderText) as Spanned
    return createFieldText(
        spanned,
        firstItem = null,
        firstItemIconId = null,
        onClick = onClick,
        isAmbiguous = true,
        logger = logger,
    )
}

private fun <T> createFieldText(
    spanned: Spanned,
    firstItem: T?,
    firstItemIconId: String?,
    onClick: (() -> Unit)?,
    isAmbiguous: Boolean,
    logger: Logger,
): TextModel.SingleFieldTextModel<T> {
    val annotations: Array<Annotation> = spanned.getSpans(0, spanned.length, Annotation::class.java)

    val valueFieldRange = annotations.findAnnotationRange(key = "valueField", spanned = spanned)
    if (valueFieldRange == null) {
        logger.w({ "No valueField annotation for $str1" }) { str1 = spanned.toString() }
    }

    return TextModel.SingleFieldTextModel(
        text = spanned.toString(),
        valueFieldRange = valueFieldRange,
        onClick = onClick,
        firstItem = firstItem,
        firstItemIconId = firstItemIconId,
        isAmbiguous = isAmbiguous,
    )
}

/**
 * Creates text substring representing the bundling action, or returns null if [action] isn't a
 * bundle action.
 */
// TODO: b/478225883 - Make bundle name & emoji icon clickable.
internal fun createBundleText(
    action: ActionModel,
    resources: Resources,
    logger: Logger,
): TextModel.BundleTextModel? {
    if (action !is ActionModel.Bundle) {
        return null
    }

    val template = resources.getText(R.string.notification_rules_bundle_text_description)

    // TODO: b/478225883 - Use default bundle name & emoji here.
    val spanned =
        TextUtils.expandTemplate(template, action.name ?: "???", action.emojiIcon ?: "?") as Spanned
    val annotations = spanned.getSpans(0, spanned.length, Annotation::class.java)
    val nameRange: IntRange? =
        annotations.findAnnotationRange(key = "bundleName", spanned = spanned)
    val emojiRange: IntRange? =
        annotations.findAnnotationRange(key = "bundleEmoji", spanned = spanned)
    if (nameRange == null) {
        logger.w({ "No bundleName annotation for $str1" }) { str1 = spanned.toString() }
    }
    if (emojiRange == null) {
        logger.w({ "No bundleEmoji annotation for $str1" }) { str1 = spanned.toString() }
    }

    return TextModel.BundleTextModel(
        text = spanned.toString(),
        nameRange = nameRange,
        emojiRange = emojiRange,
    )
}

/**
 * Returns the range within [spanned] that is associated with an annotation of key=[key] and
 * value=true.
 */
private fun Array<Annotation>.findAnnotationRange(key: String, spanned: Spanned): IntRange? {
    return this.firstOrNull { annotation -> annotation.key == key && annotation.value.toBoolean() }
        ?.let { spanned.getSpanStart(it) until spanned.getSpanEnd(it) }
}
