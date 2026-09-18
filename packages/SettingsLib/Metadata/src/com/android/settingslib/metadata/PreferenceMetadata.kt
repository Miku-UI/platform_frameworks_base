/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.settingslib.metadata

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.provider.Settings
import androidx.annotation.AnyThread
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.android.settingslib.metadata.preferencesapi.ApiPreference
import com.android.settingslib.metadata.preferencesapi.PreferencesApiScreen
import com.android.settingslib.metadata.preferencesapi.preconditions.Allowed
import com.android.settingslib.metadata.preferencesapi.preconditions.Disallowed
import com.android.settingslib.metadata.preferencesapi.preconditions.PreconditionStability
import com.android.settingslib.utils.applications.AppUtils

/** Indicates how sensitive of the data. */
@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.TYPE)
annotation class SensitivityLevel {
    companion object {
        const val DO_NOT_EXPOSE = 0
        const val NO_SENSITIVITY = 1
        const val MUST_PROVIDE_UNDO = 2
        const val REQUIRES_CONFIRMATION = 3
        const val DEEP_LINK_ONLY = 4
    }
}

/**
 * Interface provides preference metadata (title, summary, icon, etc.).
 *
 * Besides the existing APIs, subclass could integrate with following interface to provide more
 * information:
 * - [PreferenceTitleProvider]: provide dynamic title content
 * - [PreferenceSummaryProvider]: provide dynamic summary content (e.g. based on preference value)
 * - [PreferenceIconProvider]: provide dynamic icon content (e.g. based on flag)
 * - [PreferenceIndexableProvider]: provide if it is indexable dynamically
 * - [PreferenceAvailabilityProvider]: provide preference availability (e.g. based on flag)
 * - [PreferenceLifecycleProvider]: provide the lifecycle callbacks and notify state change
 *
 * Notes:
 * - UI framework support:
 *     - This class does not involve any UI logic, it is the data layer.
 *     - Subclass could integrate with datastore and UI widget to provide UI layer. For instance,
 *       `PreferenceBinding` supports Jetpack Preference binding.
 * - Datastore:
 *     - Subclass should implement the [PersistentPreference] to note that current preference is
 *       persistent in datastore.
 *     - It is always recommended to support back up preference value changed by user. Typically,
 *       the back up and restore happen within datastore, the [allowBackup] API is to mark if
 *       current preference value should be backed up (backup allowed by default).
 * - Preference indexing for search:
 *     - Override [isIndexable] API to mark if preference is indexable (enabled by default).
 *     - If [isIndexable] returns true, preference title and summary will be indexed with cache.
 *       More indexing data could be provided through [keywords].
 *     - Settings search will cache the preference title/summary/keywords for indexing. The cache is
 *       invalidated when system locale changed, app upgraded, etc.
 *     - Dynamic content is not suitable to be cached for indexing. Subclass that implements
 *       [PreferenceTitleProvider] / [PreferenceSummaryProvider] will not have its title / summary
 *       indexed.
 */
@AnyThread
interface PreferenceMetadata {

    /** Preference key. */
    val key: String

    /**
     * The purpose of the preference. This string should be understandable in English without
     * additional context beyond the rest of the preference definition. It should not just repeat
     * the name of the preference. For example, if the preference name is "enable airplane mode" the
     * purpose may be "Controls airplane mode. When enabled this will turn off all wireless
     * communications on the device.".
     *
     * When this preference is parameterised, the purpose must be understandable regardless of
     * parameters. For example, if this is a preference for enabling picture-in-picture, the purpose
     * may be "Controls whether a specific app is allowed to enter picture-in-picture mode". We can
     * assume clients will look at the parameters to understand how to specify the app.
     */
    @get:StringRes val purpose: Int

    /** Preference key when attached to preference hierarchy. */
    val bindingKey: String
        get() = key

    /**
     * Preference title resource id.
     *
     * Implement [PreferenceTitleProvider] if title is generated dynamically.
     */
    val title: Int
        @StringRes get() = 0

    /** The sensitivity level of the preference. */
    val sensitivityLevel: @SensitivityLevel Int
        get() = SensitivityLevel.DO_NOT_EXPOSE

    /**
     * Preference summary resource id.
     *
     * Implement [PreferenceSummaryProvider] if summary is generated dynamically (e.g. summary is
     * provided per preference value)
     */
    val summary: Int
        @StringRes get() = 0

    /** Icon of the preference. */
    val icon: Int
        @DrawableRes get() = 0

    /**
     * Returns if preference is indexable for settings search.
     *
     * The override should return constant value `true` / `false` only, and implement
     * [PreferenceIndexableProvider] if the result is determined dynamically.
     *
     * Note: If [indexable] of a [PreferenceScreenMetadata] returns `false`, all the preferences on
     * the screen are not indexable.
     */
    val indexable: Boolean
        get() = true

    /** Additional keywords for indexing. */
    val keywords: Int
        @StringRes get() = 0

    /**
     * Return the extras Bundle object associated with this preference.
     *
     * It is used to provide more *internal* information for metadata. External app is not expected
     * to use this information as it could be changed in future. Consider [tags] for external usage.
     */
    fun extras(context: Context): Bundle? = null

    /**
     * Returns the tags associated with this preference.
     *
     * Unlike [extras], tags are exposed for external usage. The returned tag list must be constants
     * and **append only**. Do not edit/delete existing tag strings as it can cause backward
     * compatibility issue.
     *
     * Use cases:
     * - identify a specific preference
     * - identify a group of preferences related to network settings
     */
    fun tags(context: Context): Array<String> = arrayOf()

    /**
     * Returns if the preference is available on condition, which indicates its availability could
     * be changed at runtime and should not be cached (e.g. for indexing).
     *
     * [PreferenceAvailabilityProvider] subclass returns `true` by default. For [PreferenceMetadata]
     * that are generated programmatically should also return `true` even it does not implement
     * [PreferenceAvailabilityProvider].
     */
    val isAvailableOnCondition: Boolean
        get() = this is PreferenceAvailabilityProvider

    /**
     * Returns if preference is enabled.
     *
     * UI framework normally does not allow user to interact with the preference widget when it is
     * disabled.
     *
     * If [PreferenceScreenMetadata.isEnabled] is override and `false` value is returned
     * potentially, [PreferenceIndexableProvider] should be implemented to indicate that the screen
     * might not be accessible and thus no indexable.
     */
    fun isEnabled(context: Context): Boolean = true

    /**
     * Returns a human readable description of the enabled state of the preference.
     *
     * This should describe any preconditions that must be met for the preference to be enabled.
     *
     * It does not need to be set if [isEnabled] always returns `true`.
     */
    fun getEnabledDescription(): String? = null

    /**
     * Returns the stability of the enabled state of the preference.
     *
     * This should describe whether the enabled state can be cached.
     *
     * It does not need to be set if [isEnabled] always returns `true`.
     */
    fun getEnabledStability() : PreconditionStability? = PreconditionStability.STABLE_UNTIL_APK_UPDATE

    /**
     * Returns the keys of depended preferences.
     *
     * Keep in mind that the dependency is effective only on the same screen. For cross screen
     * dependency, especially for preference screen entry point, add observer (e.g. on the depended
     * preference's data store) explicitly to update the preference with
     * [PreferenceLifecycleProvider].
     */
    fun dependencies(context: Context): Array<String> = arrayOf()

    /** Returns if the preference is persistent in datastore. */
    fun isPersistent(context: Context): Boolean = false

    /**
     * Returns if preference value backup is allowed (by default returns `true` if preference is
     * persistent).
     */
    fun allowBackup(context: Context): Boolean = isPersistent(context)

    /** Returns preference intent. */
    fun intent(context: Context): Intent? = null
}

/**
 * If this metadata can be exposed to the user
 */
fun PreferenceMetadata.isExposable(context: Context) : Boolean {
    val showUiOnlyPreferences=
        AppUtils.isDebuggable() && (Settings.Global.getInt(
            context.contentResolver,
            "com.android.settings.EXCLUDE_UI_ONLY_PREFERENCES",
            1,
        ) == 0)
    val showDoNotExposePreferences =
        AppUtils.isDebuggable() && (Settings.Global.getInt(
            context.contentResolver,
            "com.android.settings.UNKNOWN_SENSITIVITY_IS_AVAILABLE",
            0,
        ) == 1)
    return (this.isExposureAllowed() || showDoNotExposePreferences)
            && (!this.isUiOnlyPreference(context) || showUiOnlyPreferences)
}

/**
 * If this metadata object has a sensitivity which allows exposure
 */
fun PreferenceMetadata.isExposureAllowed() : Boolean = listOf(
    SensitivityLevel.NO_SENSITIVITY,
    SensitivityLevel.MUST_PROVIDE_UNDO,
    SensitivityLevel.REQUIRES_CONFIRMATION,
    SensitivityLevel.DEEP_LINK_ONLY
).contains(sensitivityLevel)

/**
 * If this metadata is allowed to have a get value
 */
fun PreferenceMetadata.isGetAllowed() : Boolean = listOf(
    SensitivityLevel.NO_SENSITIVITY,
    SensitivityLevel.MUST_PROVIDE_UNDO,
    SensitivityLevel.REQUIRES_CONFIRMATION,
    SensitivityLevel.DEEP_LINK_ONLY
).contains(sensitivityLevel)

/**
 * If this metadata is allowed to have a set value
 */
fun PreferenceMetadata.isSetAllowed() : Boolean = listOf(
    SensitivityLevel.NO_SENSITIVITY,
    SensitivityLevel.MUST_PROVIDE_UNDO
).contains(sensitivityLevel)

/** Metadata of preference group. */
@AnyThread
interface PreferenceGroup : PreferenceMetadata {

    override val indexable
        get() = title != 0
}

/** Metadata of preference category. */
@AnyThread
open class PreferenceCategory(
    override val key: String,
    override val purpose: Int,
    override val title: Int,
) : PreferenceGroup {
    override fun tags(context: Context) = arrayOf(UI_ONLY_PREFERENCE)
}

/** Tag representing a preference that is ui only */
const val UI_ONLY_PREFERENCE = "ui_only_preference"

/**
 * Tag representing a pure metadata object in a UI screen.
 *
 * Marking a preference with this tag will prevent it from being bound to a UI object, thus allowing
 * for adding pure metadata objects in a fully migrated to the UI screen.
 */
const val METADATA_IN_UI="metadata_in_ui"

/** Tag representing a preference that is considered `hero` and must be gettable*/
const val HERO = "hero"

/** Tag representing a preference that is considered `hero` and must be gettable and settable*/
const val HERO_SET = "hero_set"

/** Tag representing a preference that is considered `must pass` and must be gettable*/
const val MUSTPASS = "mustpass"

/** Tag representing a preference that is considered `must pass` and must be gettable and settable*/
const val MUSTPASS_SET = "mustpass_set"

/** Returns a string describing the preconditions for accessing the preference. */
fun PreferenceMetadata.accessPreconditionsAsString(context: Context): String? {
    val preconditions =
        if (this is ApiPreference<*, *>) {
            listOfNotNull(
                    screenPreconditions?.getDescription(context),
                    preconditions?.getDescription(context),
                )
                .joinToString(", ")
        } else if (this is PreferencesApiScreen) {
            screenPreconditions?.getDescription(context) ?: ""
        } else if (this is PreferenceScreenMetadata && getEnabledDescription() != null) {
            getEnabledDescription() ?: ""
        } else if (this is PreferenceAvailabilityProvider) {
            availabilityDescription
        } else {
            ""
        }

    return if (preconditions.isEmpty()) null else "Preconditions to accessing: $preconditions."
}

/** Returns a string describing the preconditions for accessing the preference as well as the status of them. */
suspend fun PreferenceMetadata.resolvedAccessAndGetPreconditionsAsString(context: Context): String? {
    return if (this is ApiPreference<*, *>) {
            val operationContext = getApiOperationContext(context)

            val screenPreconditionsResult = screenPreconditions?.check(operationContext)
            val preconditionsResult = preconditions?.check(operationContext)
            val getPreconditionsResult = get?.preconditions?.check(operationContext)

            val preconditionFailures = listOfNotNull(
                if (screenPreconditionsResult is Disallowed) screenPreconditionsResult.getReason(context) else null,
                if (preconditionsResult is Disallowed) preconditionsResult.getReason(context) else null,
                if (getPreconditionsResult is Disallowed) getPreconditionsResult.getReason(context) else null,
            )

            val preconditionPasses = listOfNotNull(
                if (screenPreconditionsResult is Allowed) screenPreconditions?.getDescription(context) else null,
                if (preconditionsResult is Allowed) preconditions?.getDescription(context) else null,
                if (getPreconditionsResult is Allowed) get?.preconditions?.getDescription(context) else null,
            )

            val preconditionFailuresString = if (preconditionFailures.isEmpty()) {
                null
            } else {
                "Failing get preconditions: ${preconditionFailures.joinToString(", ")}"
            }

            val preconditionPassesString = if (preconditionPasses.isEmpty()) {
                null
            } else {
                "Passing get preconditions: ${preconditionPasses.joinToString(", ")}"
            }

            listOfNotNull(preconditionFailuresString, preconditionPassesString).joinToString(", ").takeIf { it.isNotEmpty() }
        } else if (this is PreferencesApiScreen && screenPreconditions != null) {
            val screenPreconditionsResult = evaluatePreconditions(context)

            if (screenPreconditionsResult is Disallowed) "Failing screen access preconditions: ${screenPreconditionsResult.getReason(context)}"
            else "Passing screen access preconditions: ${screenPreconditions?.getDescription(context)}"
        } else if (this is PreferenceScreenMetadata && getEnabledDescription() != null) {
            // For screens, enabled means accessible
            if (isEnabled(context)) {
                "Passing get preconditions: ${getEnabledDescription()}"
            } else {
                "Failing get preconditions: ${getEnabledDescription()}"
            }
        } else if (this is PreferenceAvailabilityProvider) {
            if (isAvailable(context)) {
                "Passing get preconditions: ${availabilityDescription}"
            } else {
                "Failing get preconditions: ${availabilityDescription}"
            }
        } else {
            null
        }
}

/** Returns a string describing the preconditions for setting the preference as well as the status of them. */
suspend fun PreferenceMetadata.resolvedSetPreconditionsAsString(context: Context): String? {
    return if (this is ApiPreference<*, *>) {
            val operationContext = getApiOperationContext(context)
            val setPreconditionsResult = set?.preconditions?.check(operationContext)
            if (setPreconditionsResult is Disallowed) "Failing set preconditions: ${setPreconditionsResult.getReason(context)}"
            else if (setPreconditionsResult is Allowed) "Passing set preconditions: ${set?.preconditions?.getDescription(context)}"
            else null
        } else if (getEnabledDescription() != null && this !is PreferenceScreenMetadata) {
            // Screens are not settable so no need to communicate set preconditions.
            if (isEnabled(context)) {
                "Passing set preconditions: ${getEnabledDescription()}"
            } else {
                "Failing set preconditions: ${getEnabledDescription()}"
            }
        } else {
            null
        }
}

suspend fun PreferenceMetadata.stableAccessPreconditionFailuresAsString(context: Context): String? {
    val preconditions =
        if (this is ApiPreference<*, *>) {
            val failure = this.evaluatePreconditions(context, this.get.preconditions)
            if (failure is Disallowed && failure.stability == PreconditionStability.STABLE_UNTIL_APK_UPDATE) {
                failure.getReason(context)
            } else ""
        } else if (this is PreferencesApiScreen) {
            val failure = this.evaluatePreconditions(context)
            if (failure is Disallowed && failure.stability == PreconditionStability.STABLE_UNTIL_APK_UPDATE) {
                failure.getReason(context)
            } else ""
        } else if (this is PreferenceScreenMetadata && getEnabledDescription() != null && !isEnabled(context) && getEnabledStability() == PreconditionStability.STABLE_UNTIL_APK_UPDATE) {
            "Failed precondition: ${getEnabledDescription()}"
        } else if (this is PreferenceAvailabilityProvider && getAvailabilityStability() == PreconditionStability.STABLE_UNTIL_APK_UPDATE && !isAvailable(context)) {
                "Failed precondition: ${availabilityDescription}"
        } else {
            ""
        }
    return if (preconditions.isEmpty()) null else "This is permanently unavailable on this device due to: $preconditions."
}

/** Returns a string describing the preconditions for reading the preference. */
fun PreferenceMetadata.getPreconditionsAsString(context: Context): String? {
    return (this as? ApiPreference<*, *>)?.get?.preconditions?.getDescription(context)?.let {
        "Preconditions to getting: $it."
    }
}

/** Returns a string describing the preconditions for writing the preference. */
fun PreferenceMetadata.setPreconditionsAsString(context: Context): String? {
    if (this is ApiPreference<*, *>) {
        val preconditions = listOfNotNull(
                set?.preconditions?.getDescription(context),
                set?.valuePreconditions?.getDescription(context),
            )
            .joinToString(", ")
        return if (preconditions.isEmpty()) null else "Preconditions to setting: $preconditions."
    } else {
        if (this !is PreferenceScreenMetadata && getEnabledDescription() != null) {
            // Screens are not settable so no need to communicate set preconditions.
            return "Preconditions to setting: ${getEnabledDescription()}."
        }
    }
    return null
}

suspend fun PreferenceMetadata.stableSetPreconditionFailuresAsString(context: Context): String? {
    val preconditions =
        if (this is ApiPreference<*, *>) {
            val failure = this.evaluatePreconditions(context, this.set?.preconditions)
            if (failure is Disallowed && failure.stability == PreconditionStability.STABLE_UNTIL_APK_UPDATE) {
                failure.getReason(context)
            } else ""
        } else if (this !is PreferenceScreenMetadata && getEnabledDescription() != null) {
            // Screens are not settable so no need to communicate set preconditions.
            if (getEnabledStability() == PreconditionStability.STABLE_UNTIL_APK_UPDATE && !isEnabled(context)) {
                "Failed precondition: ${getEnabledDescription()}"
            } else ""
        } else {
            ""
        }
    return if (preconditions.isEmpty()) null else "Setting this preference is permanently unavailable on this device due to: $preconditions."
}

/** Returns a string describing the warning for writing the preference. */
fun PreferenceMetadata.setWarningAsString(context: Context): String? {
    val warningInfo = when (this) {
        is ApiPreference<*, *> -> {
            this.set?.warning?.let { warningConfig ->
                val warningMessage = warningConfig.getWarning(context)

                val preconditionsDescription = when {
                    warningConfig.preconditions != null ->
                        warningConfig.preconditions.getDescription(context)

                    warningConfig.valuePreconditions != null ->
                        warningConfig.valuePreconditions.getDescription(context)

                    else -> null
                }

                WarningInfo(preconditionsDescription, warningMessage)
            }
        }

        is PreferenceSetWarningProvider -> {
            this.setWarning
        }

        else -> {
            null
        }
    }

    return warningInfo?.let { warning ->
        // Compute the set warning as a string message
        val conditionalText =
            warning.preconditionsDescription?.takeIf { it.isNotBlank() }?.let { description ->
                " (if preconditions are met: $description)"
            } ?: ""

        "[Must show to user]: ${warning.warningMessage}$conditionalText."
    }
}
