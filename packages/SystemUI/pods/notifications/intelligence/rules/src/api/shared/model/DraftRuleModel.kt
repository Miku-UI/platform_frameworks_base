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

package com.android.systemui.notifications.intelligence.rules.shared.model

/**
 * The equivalent of [FilterModel] for draft rules.
 *
 * Because the rule is a draft, some values may be underspecified. Any underspecified values will
 * use [RuleValue.Ambiguous], and the user will have to specify the value in the UI before the rule
 * is saved.
 */
data class DraftFilterModel(
    /**
     * The people that this rule applies to. Null if people are not part of the rule filter. See
     * [android.app.NotificationRule.Filter.getContacts] and
     * [android.app.NotificationRule.Filter.getShortcutIds].
     */
    val people: RuleValue<PeopleModel>? = null,
    /**
     * The apps that this rule applies to. Null if included apps are not part of the rule filter.
     * [android.app.NotificationRule.Filter.getIncludedPackageUids].
     */
    val includedApps: RuleValue<IncludedAppsModel>? = null,
    /**
     * The keywords that the notification must contain in order for the rule to apply. Null if
     * keywords are not part of the rule filter. See
     * [android.app.NotificationRule.Filter.getKeywords].
     *
     * Note: Keywords can never be ambiguous since they're just strings, so this field doesn't use
     * [RuleValue].
     */
    val keywords: KeywordsModel? = null,
) {
    /** True if any parts of the filter are still ambiguous. */
    val hasAmbiguousValues: Boolean
        get() {
            return people is RuleValue.Ambiguous || includedApps is RuleValue.Ambiguous
        }
}

/**
 * Represents a **draft** of a notification rule. Notification rules control when and how
 * notifications are presented.
 *
 * This rule draft is being edited by a user. This is mostly an internal equivalent of
 * [android.app.NotificationRule], but with ambiguous value support as well.
 *
 * See also: [RuleModel] for rules that are already saved.
 */
sealed interface DraftRuleModel {
    /** The action to apply to the notification. See [android.app.NotificationRule.getAction]. */
    val action: ActionModel
    /** The filter on the rule. See [android.app.NotificationRule.Filter]. */
    val filter: DraftFilterModel

    /** True if any parts of the rule are still ambiguous. */
    val hasAmbiguousValues: Boolean
        get() = filter.hasAmbiguousValues

    /** This represents a new rule being created. */
    data class New(override val action: ActionModel, override val filter: DraftFilterModel) :
        DraftRuleModel

    /** This represents a pre-existing rule being edited. */
    data class PreExisting(
        val id: Int,
        override val action: ActionModel,
        override val filter: DraftFilterModel,
    ) : DraftRuleModel

    /** Copies this draft, changing the given values. */
    fun copyDraft(
        action: ActionModel = this.action,
        filter: DraftFilterModel = this.filter,
    ): DraftRuleModel {
        return when (this) {
            is New -> this.copy(action = action, filter = filter)
            is PreExisting -> this.copy(action = action, filter = filter)
        }
    }

    companion object {
        /** Converts a rule to a draft version so it can be edited. */
        fun RuleModel.toDraft(): DraftRuleModel {
            return PreExisting(id = id, action = action, filter = filter.toDraft())
        }

        private fun FilterModel?.toDraft(): DraftFilterModel {
            return DraftFilterModel(
                people = this?.people.toDraft(),
                includedApps = this?.includedApps.toDraft(),
                keywords = this?.keywords,
            )
        }

        /** Converts a given type to a [RuleValue.Specified] version of that type. */
        private fun <T> T?.toDraft(): RuleValue<T>? {
            return if (this == null) {
                null
            } else {
                RuleValue.Specified(this)
            }
        }

        /**
         * Converts a new draft rule to a fully fledged rule.
         *
         * @throws IllegalStateException if any of the values in [filter] are [RuleValue.Ambiguous].
         */
        fun New.toFullRule(id: Int): RuleModel {
            return RuleModel(id = id, action = this.action, filter = this.filter.toFullFilter())
        }

        /**
         * Converts a pre-existing draft rule to a fully fledged rule.
         *
         * @throws IllegalStateException if any of the values in [filter] are [RuleValue.Ambiguous].
         */
        fun PreExisting.toFullRule(): RuleModel {
            return RuleModel(
                id = this.id,
                action = this.action,
                filter = this.filter.toFullFilter(),
            )
        }

        private fun DraftFilterModel.toFullFilter(): FilterModel? {
            if (this.people != null || this.includedApps != null || this.keywords != null) {
                return FilterModel(
                    people = this.people.toFullValue(),
                    includedApps = this.includedApps.toFullValue(),
                    keywords = this.keywords,
                )
            }

            return null
        }

        private fun <T> RuleValue<T>?.toFullValue(): T? {
            return when (this) {
                is RuleValue.Specified<T> -> this.value
                is RuleValue.Ambiguous<T> -> {
                    throw IllegalArgumentException(
                        "All values must be specified before a rule can be created"
                    )
                }
                null -> null
            }
        }
    }
}

/** Represents various actions that a rule can apply to a notification. */
sealed interface ActionModel {
    /** See [android.app.NotificationRule.Action.PRIMARY_ACTION_HIGHLIGHT_AND_ALERT]. */
    data object HighlightAndAlert : ActionModel

    /** See [android.app.NotificationRule.Action.PRIMARY_ACTION_HIGHLIGHT]. */
    data object Highlight : ActionModel

    /** See [android.app.NotificationRule.Action.PRIMARY_ACTION_LOW]. */
    data object Silence : ActionModel

    /** See [android.app.NotificationRule.Action.PRIMARY_ACTION_BUNDLE]. */
    data class Bundle(
        /** See [android.app.NotificationRule.Action.getDynamicBundleName]. */
        val name: String?,
        /** See [android.app.NotificationRule.Action.getDynamicBundleEmojiIcon]. */
        val emojiIcon: String?,
    ) : ActionModel

    /** See [android.app.NotificationRule.Action.PRIMARY_ACTION_BLOCK]. */
    data object Block : ActionModel
}

/** Represents a single aspect of a notification rule. */
public sealed interface RuleValue<T> {
    /** This aspect is fully specified and has the value [value]. */
    public data class Specified<T>(val value: T) : RuleValue<T>

    /** This aspect is underspecified and must be clarified by the user in the edit UI. */
    public data class Ambiguous<T>(
        /** The text to show in the UI before the user clarifies the value. */
        val placeholderText: String
    ) : RuleValue<T>
}
