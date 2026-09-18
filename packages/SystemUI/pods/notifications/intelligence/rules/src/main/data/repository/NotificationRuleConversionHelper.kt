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

package com.android.systemui.notifications.intelligence.rules.data.repository

import android.app.NotificationRule
import android.app.NotificationRule.Action.PRIMARY_ACTION_BLOCK
import android.app.NotificationRule.Action.PRIMARY_ACTION_BUNDLE
import android.app.NotificationRule.Action.PRIMARY_ACTION_HIGHLIGHT
import android.app.NotificationRule.Action.PRIMARY_ACTION_HIGHLIGHT_AND_ALERT
import android.app.NotificationRule.Action.PRIMARY_ACTION_LOW
import com.android.systemui.notifications.intelligence.rules.shared.model.ActionModel

/**
 * Helper object for converting between the external and internal model representations of a rule.
 */
object NotificationRuleConversionHelper {
    val validPrimaryActionValues =
        listOf(
            PRIMARY_ACTION_HIGHLIGHT_AND_ALERT,
            PRIMARY_ACTION_HIGHLIGHT,
            PRIMARY_ACTION_LOW,
            PRIMARY_ACTION_BUNDLE,
            PRIMARY_ACTION_BLOCK,
        )

    fun ActionModel.toExternalModel(): NotificationRule.Action {
        val primaryAction =
            when (this) {
                ActionModel.HighlightAndAlert -> PRIMARY_ACTION_HIGHLIGHT_AND_ALERT
                ActionModel.Highlight -> PRIMARY_ACTION_HIGHLIGHT
                ActionModel.Silence -> PRIMARY_ACTION_LOW
                is ActionModel.Bundle -> PRIMARY_ACTION_BUNDLE
                ActionModel.Block -> PRIMARY_ACTION_BLOCK
            }
        val bundleName =
            if (this is ActionModel.Bundle) {
                this.name
            } else {
                null
            }
        val bundleEmoji =
            if (this is ActionModel.Bundle) {
                this.emojiIcon
            } else {
                null
            }
        return NotificationRule.Action.Builder(primaryAction)
            .setDynamicBundleName(bundleName)
            .setDynamicBundleEmojiIcon(bundleEmoji)
            .build()
    }

    fun NotificationRule.Action.toInternalModel(): ActionModel {
        return when (primaryAction) {
            PRIMARY_ACTION_HIGHLIGHT_AND_ALERT -> ActionModel.HighlightAndAlert
            PRIMARY_ACTION_HIGHLIGHT -> ActionModel.Highlight
            PRIMARY_ACTION_LOW -> ActionModel.Silence
            PRIMARY_ACTION_BUNDLE ->
                ActionModel.Bundle(
                    name = this.dynamicBundleName,
                    emojiIcon = this.dynamicBundleEmojiIcon,
                )
            PRIMARY_ACTION_BLOCK -> ActionModel.Block
            else ->
                throw IllegalStateException(
                    "Action $primaryAction not present in `validPrimaryActionValues`"
                )
        }
    }
}
