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
import android.content.ContentResolver
import android.content.Context
import android.content.res.Resources
import android.graphics.Bitmap
import android.net.Uri
import com.android.systemui.notifications.intelligence.rules.shared.model.AppModel
import com.android.systemui.notifications.intelligence.rules.shared.model.DraftRuleModel
import com.android.systemui.notifications.intelligence.rules.shared.model.PersonModel

/** A view model for editing a specific notification rule. Work-in-progress. */
public interface NotificationRuleEditViewModel {
    /** The rule being edited or created. */
    public var rule: DraftRuleModel

    /** True if there was an error in saving the new/updated rule. */
    val isErrorVisible: Boolean

    /**
     * Creates display text for the [rule]. This text is also interactable: Users can tap individual
     * fields to edit the contents.
     *
     * @param onEnterEditField invoked when the user starts editing a particular field of the rule.
     * @param onExitEditField invoked when the user finishes editing a particular field of the rule.
     */
    public fun buildRuleText(
        onEnterEditField: (RulesScreenViewState.EditField) -> Unit,
        onExitEditField: () -> Unit,
        resources: Resources,
    ): RuleDisplayModel

    /** Saves a new set of apps to the draft rule. */
    public fun onAppsSaved(newApps: List<AppModel>, onExitEditField: () -> Unit)

    /** Saves a new set of people to the draft rule. */
    fun onPeopleSaved(newPeople: List<PersonModel>, onExitEditField: () -> Unit)

    /** Saves a new set of keywords to the draft rule. */
    fun onKeywordsSaved(newKeywords: List<String>, onExitEditField: () -> Unit)

    /**
     * Fetches all people whose name matches [searchQuery].
     *
     * @param contentResolver the content resolver for the current user.
     */
    suspend fun fetchPeople(
        searchQuery: String,
        contentResolver: ContentResolver,
    ): List<PersonModel>

    /**
     * Loads the photo thumbnail for a contact from the given [uri].
     *
     * @param userContext a context specific to the user that owns the notification rule.
     */
    public suspend fun loadContactBitmapFromUri(
        uri: Uri,
        userContext: Context,
        @Px sizePx: Int,
    ): Bitmap?

    /** Fetches all apps installed on the device. */
    public suspend fun fetchInstalledApps(context: Context): List<AppModel>

    /**
     * Saves the current version of [rule]. [isErrorVisible] will update based on whether the rule
     * was saved successfully or not.
     */
    fun saveRule()

    /** Cleans up any temporary values. Should be invoked when the user leaves the edit page. */
    fun cleanUp()

    interface Factory {
        fun create(
            rule: DraftRuleModel,
            onNavigateToCurrentRulesScreen: () -> Unit,
        ): NotificationRuleEditViewModel
    }
}
