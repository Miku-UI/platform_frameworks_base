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

import android.annotation.Px
import android.content.Context
import android.content.res.Resources
import android.graphics.Bitmap
import android.net.Uri
import com.android.systemui.lifecycle.Activatable
import com.android.systemui.notifications.intelligence.rules.shared.model.RuleModel

/** A view model for the notification rules screen. */
public interface NotificationRulesScreenViewModel : Activatable {
    /** The list of current saved rules for the user. */
    public val rules: List<RuleModel>

    /**
     * The back stack of screens viewed within the activity. The last screen in the list is the
     * current one.
     */
    public val backStack: List<RulesScreenViewState>

    /** The screen currently being displayed. */
    public val currentScreen: RulesScreenViewState

    /**
     * Transforms [rule] into a readable string. Because this is a read-only view, individual fields
     * are more visually prominent but not clickable.
     */
    public fun buildRuleText(rule: RuleModel, resources: Resources): RuleDisplayModel

    /**
     * Loads the photo thumbnail for a contact from the given [uri].
     *
     * @param userContext a context specific to the user that owns the notification rule.
     */
    suspend fun loadContactBitmapFromUri(uri: Uri, userContext: Context, @Px sizePx: Int): Bitmap?

    public interface Factory {
        public fun create(backStack: List<RulesScreenViewState>): NotificationRulesScreenViewModel
    }
}
