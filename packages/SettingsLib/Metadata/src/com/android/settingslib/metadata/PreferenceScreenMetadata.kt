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
import androidx.annotation.AnyThread
import androidx.fragment.app.Fragment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow

/**
 * Metadata of preference screen.
 *
 * [PreferenceScreenMetadata] class is reused for both screen container and entry points to maintain
 * the states (availability, enable, restriction, title, etc.) consistently. Different instances are
 * created for screen container and entry points respectively. In case the implementation would like
 * to perform action for container (or entry point) only, [isContainer] and [isEntryPoint] could be
 * leveraged to distinguish current screen metadata instance is acting as container or entry point.
 *
 * For parameterized preference screen that relies on additional information (e.g. package name,
 * language code) to build its content, the subclass must:
 * - override [arguments] in constructor (Deprecated: Will be removed once the catalyst framework
 *   stops passing the arguments as a bundle. Instead: override [ValidatedKeyParameters])
 * - override [bindingKey] to distinguish the preferences on the preference hierarchy
 * - add a companion object that inherits from [ParameterizedPreferenceScreenArgumentsFactory] and
 *   provide the parameter schema and implement the `fun parameters(context: Context): Flow<Bundle>`
 *   (context is optional) to provide all possible arguments
 */
@AnyThread
interface PreferenceScreenMetadata : PreferenceGroup {
    /** Arguments to build the screen content. */
    @Deprecated(
        "This property will be removed once the catalyst framework stops passing the arguments as a bundle. Use the keyParameters instead."
    )
    val arguments: Bundle?
        get() = null

    val keyParametersSchema: KeyParametersSchema?
        get() = null

    val keyParameters: ValidatedKeyParameters?
        get() = null

    /**
     * Returns additional extras to be included in the intent used to launch this screen.
     *
     * These extras are typically used to pass specific data required by the destination
     * fragment for its initialization.
     *
     * Note: The fragment will not receive this Bundle as a single nested extra. Instead,
     * all values from this bundle will be added independently to the resulting launch
     * intent's extras.
     */
    val launchScreenExtra: Bundle?
        get() = null

    /** The default sensitivity level of the screen. */
    override val sensitivityLevel: @SensitivityLevel Int
        get() = SensitivityLevel.NO_SENSITIVITY

    /**
     * The screen title resource, which precedes [getScreenTitle] if provided.
     *
     * By default, screen title is same with [title].
     */
    val screenTitle: Int
        get() = title

    /** Returns if the flag (e.g. for rollout) is enabled on current screen. */
    fun isFlagEnabled(context: Context): Boolean = true

    /** Returns dynamic screen title, use [screenTitle] whenever possible. */
    fun getScreenTitle(context: Context): CharSequence? = null

    /** Returns if current screen metadata instance is acting as container. */
    fun isContainer(context: PreferenceLifecycleContext): Boolean =
        bindingKey == context.preferenceScreenKey

    /** Returns if current screen metadata instance is acting as entry point. */
    fun isEntryPoint(context: PreferenceLifecycleContext): Boolean =
        bindingKey != context.preferenceScreenKey

    /** Returns the fragment class to show the preference screen. */
    fun fragmentClass(): Class<out Fragment>?

    /**
     * Indicates if [getPreferenceHierarchy] returns a complete hierarchy of the preference screen.
     *
     * If `true`, the result of [getPreferenceHierarchy] will be used to inflate preference screen.
     * Otherwise, it is an intermediate state called hybrid mode, preference hierarchy is
     * represented by other ways (e.g. XML resource) and [PreferenceMetadata]s in
     * [getPreferenceHierarchy] will only be used to bind UI widgets.
     */
    fun hasCompleteHierarchy(): Boolean = true

    /**
     * Returns the hierarchy of preference screen.
     *
     * The implementation should include all preferences into the hierarchy but pay attention to the
     * flag guard when [hasCompleteHierarchy] is false.
     *
     * If the screen has different [PreferenceHierarchy] based on additional information (e.g. app
     * filter, profile), implements [PreferenceHierarchyGenerator]. The UI framework will support
     * switching [PreferenceHierarchy] on current screen with given type.
     *
     * Notes:
     * - Do not assume the [context] is UI context.
     * - Do not run heavy operation with the [coroutineScope], which will cause ANR.
     * - Always launch new coroutine as child of given [coroutineScope] (structured concurrency), so
     *   that the task will be cancelled automatically when the given [coroutineScope] is cancelled.
     *   This mitigates potential memory leaks.
     *
     * @param context Context to build the hierarchy, please DO NOT assume it is UI context. This
     *   could be activity context when it is to display UI, or application context for background
     *   service to retrieve preference metadata.
     * @param coroutineScope CoroutineScope to create async preference metadata elements. This could
     *   be main thread scoped when display UI or background thread scoped for external request via
     *   Android Service. Never run heavy operation inside the [coroutineScope] to avoid ANR.
     */
    fun getPreferenceHierarchy(
        context: Context,
        coroutineScope: CoroutineScope,
    ): PreferenceHierarchy

    /**
     * Returns the [Intent] to show current preference screen.
     *
     * NOTE: Always provide action for the returned intent. Otherwise, SettingsIntelligence starts
     * intent with com.android.settings.SEARCH_RESULT_TRAMPOLINE action instead of given activity.
     *
     * @param metadata the preference to locate when show the screen
     */
    fun getLaunchIntent(context: Context, metadata: PreferenceMetadata?): Intent? {
        val highlightKey = metadata?.key
        return when {
            CatalystFlagProviderFactory.catalystUseKeyParameters() && keyParameters != null -> {
                makeLaunchpadIntent(context, key, keyParameters!!, highlightKey)
            }

            arguments != null -> {
                makeLaunchpadIntent(context, key, arguments!!, highlightKey)
            }

            else -> {
                makeLaunchpadIntent(context, key, highlightKey)
            }
        }
    }

    private fun makeLaunchpadIntent(context: Context, screenKey: String, key: String?): Intent =
        Intent(LAUNCH_SETTINGS_PAGES_ACTION).apply {
            setPackage("com.android.settings")
            putExtra(EXTRA_SCREEN_KEY, screenKey)
            if (key != null) {
                putExtra(EXTRA_FRAGMENT_ARG_KEY, key)
            }
        }

    private fun makeLaunchpadIntent(
        context: Context,
        screenKey: String,
        keyParameters: ValidatedKeyParameters,
        key: String?,
    ): Intent =
        Intent(LAUNCH_SETTINGS_PAGES_ACTION).apply {
            setPackage("com.android.settings")
            launchScreenExtra?.let { putExtra(EXTRA_LAUNCH_SCREEN, it) }
            putExtra(EXTRA_SCREEN_KEY, screenKey)
            putExtra(EXTRA_SCREEN_ARGS, keyParameters.toBundle())
            if (key != null) {
                putExtra(EXTRA_FRAGMENT_ARG_KEY, key)
            }
        }

    private fun makeLaunchpadIntent(
        context: Context,
        screenKey: String,
        arguments: Bundle,
        key: String?,
    ): Intent =
        Intent(LAUNCH_SETTINGS_PAGES_ACTION).apply {
            setPackage("com.android.settings")
            putExtra(EXTRA_SCREEN_KEY, screenKey)
            putExtra(EXTRA_SCREEN_ARGS, arguments)
            if (key != null) {
                putExtra(EXTRA_FRAGMENT_ARG_KEY, key)
            }
        }

    companion object {
        const val LAUNCH_SETTINGS_PAGES_ACTION = "com.android.settings.action.LAUNCH_SETTINGS_PAGES"
        const val EXTRA_SCREEN_KEY = "screen_key"
        const val EXTRA_SCREEN_ARGS = "screen_args"
        internal const val EXTRA_FRAGMENT_ARG_KEY = ":settings:fragment_args_key"

        /** Key for a Bundle of extras to be added to the launch intent. See [launchScreenExtra]. */
        const val EXTRA_LAUNCH_SCREEN = "launch_screen_extra"
        /** Extra key for an itemization value to be mapped to the first screen parameter. */
        const val EXTRA_ITEMIZATION = "itemization"
    }
}

/**
 * Generator of [PreferenceHierarchy] based on given type.
 *
 * This interface should be used together with [PreferenceScreenMetadata] and
 * [PreferenceScreenMetadata.getPreferenceHierarchy] should return [generatePreferenceHierarchy]
 * with default preference hierarchy type.
 *
 * The UI framework could leverage [PreferenceLifecycleContext.switchPreferenceHierarchy] to switch
 * preference hierarchy with given type.
 */
interface PreferenceHierarchyGenerator<T> {

    /** Generates [PreferenceHierarchy] with given type. */
    fun generatePreferenceHierarchy(
        context: Context,
        coroutineScope: CoroutineScope,
        hierarchyType: T,
    ): PreferenceHierarchy
}

/**
 * Factory of [PreferenceScreenMetadata].
 *
 * Annotation processor generates implementation of this interface based on
 * [ProvidePreferenceScreen] when [ProvidePreferenceScreen.parameterized] is `false`.
 */
fun interface PreferenceScreenMetadataFactory {

    /**
     * Creates a new [PreferenceScreenMetadata].
     *
     * @param context application context to create the PreferenceScreenMetadata
     */
    fun create(context: Context): PreferenceScreenMetadata
}

/**
 * Parameterized factory of [PreferenceScreenMetadata].
 *
 * Annotation processor generates implementation of this interface based on
 * [ProvidePreferenceScreen] when [ProvidePreferenceScreen.parameterized] is `true`.
 */
interface PreferenceScreenMetadataParameterizedFactory : PreferenceScreenMetadataFactory {
    override fun create(context: Context) =
        if (CatalystFlagProviderFactory.catalystUseKeyParameters()) {
            createWithKeyParameters(context, parametersSchema.prepareEmpty())
        } else {
            create(context, Bundle.EMPTY)
        }

    /**
     * Creates a new [PreferenceScreenMetadata] with given arguments.
     *
     * @param context application context to create the PreferenceScreenMetadata
     * @param args arguments to create the screen metadata, [Bundle.EMPTY] is reserved for the
     *   default case when screen is migrated from normal to parameterized
     */
    fun create(context: Context, args: Bundle): PreferenceScreenMetadata

    /**
     * Returns all possible arguments to create [PreferenceScreenMetadata].
     *
     * Note that [Bundle.EMPTY] is a special arguments reserved for backward compatibility when a
     * preference screen was a normal screen but migrated to parameterized screen later:
     * 1. Set [ProvidePreferenceScreen.parameterizedMigration] to `true`, so that the generated
     *    [acceptEmptyArguments] will be `true`.
     * 1. In the original [parameters] implementation, produce a [Bundle.EMPTY] for the default
     *    case.
     *
     * Do not use [Bundle.EMPTY] for other purpose.
     */
    fun parameters(context: Context): Flow<Bundle>

    /**
     * Returns true when the parameterized screen was a normal screen.
     *
     * The [PreferenceScreenMetadata] is expected to accept an empty arguments ([Bundle.EMPTY]) and
     * take care of backward compatibility.
     */
    fun acceptEmptyArguments(): Boolean = false

    /**
     * Creates a new [PreferenceScreenMetadata] with given key-parameters.
     *
     * @param context application context to create the PreferenceScreenMetadata
     * @param keyParameters parameters to create the screen metadata
     */
    fun createWithKeyParameters(
        context: Context,
        keyParameters: ValidatedKeyParameters,
    ): PreferenceScreenMetadata

    /**
     * Returns all possible key-parameters to create [PreferenceScreenMetadata].
     *
     * Note that an empty key-parameters is used for backward compatibility when a preference screen
     * transitions from being non-parameterized to parameterized. In such migration scenarios the
     * parameterized screen is expected to gracefully accept and handle empty key-parameters to
     * ensure compatibility with older configurations or entry points.
     *
     * To mark a preference screen as transitioning from non-parameterized to parameterized:
     * 1. Set [ProvidePreferenceScreen.parameterizedMigration] to `true`, so that the generated
     *    [acceptEmptyArguments] will be `true`.
     * 2. In the [keyParameters] implementation, produce a parametersSchema.prepareEmpty() for the
     *    default case.
     */
    fun keyParameters(context: Context): Flow<ValidatedKeyParameters>

    /**
     * Defines the schema for the parameters in order to create an instance of the parameterized
     * screen.
     */
    val parametersSchema: KeyParametersSchema
}

interface ParameterizedPreferenceScreenArgumentsFactory {
    /**
     * Defines the schema for the parameters in order to create an instance of the parameterized
     * screen.
     */
    val parametersSchema: KeyParametersSchema

    /** Returns all possible parameters to create a [PreferenceScreenMetadata]. */
    fun keyParameters(context: Context): Flow<ValidatedKeyParameters>
}
