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

@file:Suppress("DEPRECATION")

package com.android.settingslib.graph

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.fragment.app.Fragment
import androidx.preference.Preference
import androidx.preference.PreferenceGroup
import androidx.preference.PreferenceScreen
import androidx.preference.TwoStatePreference
import com.android.settingslib.graph.PreferenceGetterFlags.forceIncludeAllScreens
import com.android.settingslib.graph.PreferenceGetterFlags.includeMetadata
import com.android.settingslib.graph.PreferenceGetterFlags.includeValue
import com.android.settingslib.graph.PreferenceGetterFlags.includeValueDescriptor
import com.android.settingslib.graph.proto.KeyParametersSchemaProto
import com.android.settingslib.graph.proto.ParameterDefinitionProto
import com.android.settingslib.graph.proto.PreconditionStability as PreconditionStabilityProto
import com.android.settingslib.graph.proto.PreconditionStatusProto
import com.android.settingslib.graph.proto.PreferenceGraphProto
import com.android.settingslib.graph.proto.PreferenceGroupProto
import com.android.settingslib.graph.proto.PreferenceProto
import com.android.settingslib.graph.proto.PreferenceProto.ActionTarget
import com.android.settingslib.graph.proto.PreferenceScreenProto
import com.android.settingslib.graph.proto.PreferenceValueDescriptorProto
import com.android.settingslib.metadata.CatalystFlagProviderFactory
import com.android.settingslib.metadata.DiscreteIntValue
import com.android.settingslib.metadata.EXTRA_BINDING_SCREEN_ARGS
import com.android.settingslib.metadata.IntRangeValuePreference
import com.android.settingslib.metadata.KeyParametersSchema
import com.android.settingslib.metadata.PersistentPreference
import com.android.settingslib.metadata.PreferenceAvailabilityProvider
import com.android.settingslib.metadata.PreferenceHierarchy
import com.android.settingslib.metadata.PreferenceMetadata
import com.android.settingslib.metadata.PreferenceRestrictionProvider
import com.android.settingslib.metadata.PreferenceScreenBindingKeyProvider
import com.android.settingslib.metadata.PreferenceScreenCoordinate
import com.android.settingslib.metadata.PreferenceScreenMetadata
import com.android.settingslib.metadata.PreferenceScreenMetadataFactory
import com.android.settingslib.metadata.PreferenceScreenMetadataParameterizedFactory
import com.android.settingslib.metadata.PreferenceScreenRegistry
import com.android.settingslib.metadata.PreferenceScreenRegistry.createScreenInstanceForMetadata
import com.android.settingslib.metadata.ReadWritePermit
import com.android.settingslib.metadata.SensitivityLevel.Companion.DEEP_LINK_ONLY
import com.android.settingslib.metadata.SensitivityLevel.Companion.DO_NOT_EXPOSE
import com.android.settingslib.metadata.SensitivityLevel.Companion.REQUIRES_CONFIRMATION
import com.android.settingslib.metadata.ValidatedKeyParameters
import com.android.settingslib.metadata.ValueDescriptor
import com.android.settingslib.metadata.getTrampolinedLaunchIntent
import com.android.settingslib.metadata.isExposable
import com.android.settingslib.metadata.preferencesapi.ApiPreference
import com.android.settingslib.metadata.preferencesapi.PreferencesApiScreen
import com.android.settingslib.metadata.preferencesapi.extractSafety
import com.android.settingslib.metadata.preferencesapi.preconditions.Allowed
import com.android.settingslib.metadata.preferencesapi.preconditions.ApiPreconditions
import com.android.settingslib.metadata.preferencesapi.preconditions.Custom
import com.android.settingslib.metadata.preferencesapi.preconditions.Disallowed
import com.android.settingslib.metadata.preferencesapi.preconditions.PreconditionStability
import com.android.settingslib.metadata.preferencesapi.types.ApiType
import com.android.settingslib.metadata.preferencesapi.types.FiniteOptionsType
import com.android.settingslib.metadata.preferencesapi.types.IntInRange
import com.android.settingslib.preference.PreferenceScreenCreator
import com.android.settingslib.preference.PreferenceScreenFactory
import com.android.settingslib.preference.PreferenceScreenProvider
import com.android.settingslib.utils.applications.AppUtils
import com.android.settingslib.utils.runSafely
import com.android.settingslib.utils.runSafelyAsync
import com.google.errorprone.annotations.CanIgnoreReturnValue
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

private const val TAG = "PreferenceGraphBuilder"

/** Builder of preference graph. */
class PreferenceGraphBuilder
private constructor(
    private val context: Context,
    private val callingPid: Int,
    private val callingUid: Int,
    private val request: GetPreferenceGraphRequest,
    private val coroutineScope: CoroutineScope,
) {
    private val preferenceScreenFactory by lazy {
        PreferenceScreenFactory(context.ofLocale(request.locale))
    }
    private val builder by lazy { PreferenceGraphProto.newBuilder() }
    private val valueDescriptors = mutableMapOf<String, PreferenceValueDescriptorProto>()
    private val visitedScreens = request.visitedScreens.toMutableSet()
    private val screens = mutableMapOf<String, PreferenceScreenProto.Builder>()
    private val forceIncludeAllScreens = request.flags.forceIncludeAllScreens()
    private val includeParameters = (request.flags and PreferenceGetterFlags.PARAMETERS) != 0
    private val includeHierarchy = (request.flags and PreferenceGetterFlags.EXCLUDE_HIERARCHY) == 0
    private val shrinkHierarchy = (request.flags and PreferenceGetterFlags.SHRINK_HIERARCHY) != 0

    private suspend fun init() {
        val factories = PreferenceScreenRegistry.preferenceScreenMetadataFactories
        for (screen in request.screens) {
            val screenKey = screen.screenKey
            val factory = factories[screenKey] ?: continue
            val hasParameters =
                if (CatalystFlagProviderFactory.catalystUseKeyParameters()) {
                    screen.keyParameters != null
                } else {
                    screen.args != null
                }
            if (!hasParameters && factory is PreferenceScreenMetadataParameterizedFactory) {
                addPreferenceScreen(screenKey, factory)
            } else {
                PreferenceScreenRegistry.create(context, screen)?.let { addPreferenceScreen(it) }
            }
        }
    }

    fun build(): PreferenceGraphProto {
        for ((key, screenBuilder) in screens) builder.putScreens(key, screenBuilder.build())
        builder.putAllValueDescriptors(valueDescriptors)
        return builder.build()
    }

    /**
     * Adds an activity to the graph.
     *
     * Reflection is used to create the instance. To avoid security vulnerability, the code ensures
     * given [activityClassName] must be declared as an <activity> entry in AndroidManifest.xml.
     */
    suspend fun add(activityClassName: String) {
        try {
            val intent = Intent()
            intent.setClassName(context, activityClassName)
            if (
                context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY) ==
                    null
            ) {
                Log.e(TAG, "$activityClassName is not activity")
                return
            }
            val activityClass = context.classLoader.loadClass(activityClassName)
            if (addPreferenceScreenKeyProvider(activityClass)) return
            if (PreferenceScreenProvider::class.java.isAssignableFrom(activityClass)) {
                addPreferenceScreenProvider(activityClass)
            } else {
                Log.w(TAG, "$activityClass does not implement PreferenceScreenProvider")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Fail to add $activityClassName", e)
        }
    }

    private suspend fun addPreferenceScreenKeyProvider(activityClass: Class<*>): Boolean {
        if (!PreferenceScreenBindingKeyProvider::class.java.isAssignableFrom(activityClass)) {
            return false
        }
        val key = getPreferenceScreenKey { activityClass.newInstance() } ?: return false
        if (addPreferenceScreenFromRegistry(key)) {
            builder.addRoots(key)
            return true
        }
        return false
    }

    private suspend fun getPreferenceScreenKey(newInstance: () -> Any): String? =
        withContext(Dispatchers.Main) {
            try {
                val instance = newInstance()
                if (instance is PreferenceScreenBindingKeyProvider) {
                    return@withContext instance.getPreferenceScreenBindingKey(context)
                } else {
                    Log.w(TAG, "$instance is not PreferenceScreenKeyProvider")
                }
            } catch (e: Exception) {
                Log.e(TAG, "getPreferenceScreenKey failed", e)
            }
            null
        }

    private suspend fun addPreferenceScreenFromRegistry(key: String): Boolean {
        val factory =
            PreferenceScreenRegistry.preferenceScreenMetadataFactories[key] ?: return false
        return addPreferenceScreen(key, factory)
    }

    suspend fun addPreferenceScreenProvider(activityClass: Class<*>) {
        Log.d(TAG, "add $activityClass")
        createPreferenceScreen { activityClass.newInstance() }
            ?.let {
                addPreferenceScreen(Intent(context, activityClass), it)
                builder.addRoots(it.key)
            }
    }

    /**
     * Creates [PreferenceScreen].
     *
     * Androidx Activity/Fragment instance must be created in main thread, otherwise an exception is
     * raised.
     */
    private suspend fun createPreferenceScreen(newInstance: () -> Any): PreferenceScreen? =
        withContext(Dispatchers.Main) {
            try {
                val instance = newInstance()
                Log.d(TAG, "createPreferenceScreen $instance")
                if (instance is PreferenceScreenProvider) {
                    return@withContext instance.createPreferenceScreen(
                        preferenceScreenFactory,
                        coroutineScope,
                    )
                } else {
                    Log.w(TAG, "$instance is not PreferenceScreenProvider")
                }
            } catch (e: Exception) {
                Log.e(TAG, "createPreferenceScreen failed", e)
            }
            return@withContext null
        }

    private suspend fun addPreferenceScreen(intent: Intent, preferenceScreen: PreferenceScreen?) {
        val key = preferenceScreen?.key
        if (key.isNullOrEmpty()) {
            Log.e(TAG, "\"$preferenceScreen\" has no key")
            return
        }

        val args = preferenceScreen.peekExtras()?.getBundle(EXTRA_BINDING_SCREEN_ARGS)
        if (CatalystFlagProviderFactory.catalystUseKeyParameters()) {
            val parametersSchema = PreferenceScreenRegistry.getScreenParametersSchema(key)
            val keyParameters = args?.let { parametersSchema?.prepare(it) }

            addPreferenceScreenWithKeyParameters(key, keyParameters) {
                this.intent = intent.toProto()
                root = preferenceScreen.toProto()
            }
        } else {
            @Suppress("CheckReturnValue")
            addPreferenceScreen(key, args) {
                this.intent = intent.toProto()
                root = preferenceScreen.toProto()
            }
        }
    }

    @CanIgnoreReturnValue
    suspend fun addPreferenceScreen(
        screenKey: String,
        factory: PreferenceScreenMetadataFactory,
    ): Boolean {
        val screenMetadata = createScreenInstanceForMetadata(context, factory)
        val isScreenExposable = screenMetadata?.isExposable(context) ?: false
        if (!isScreenExposable) return false
        if (factory !is PreferenceScreenMetadataParameterizedFactory) {
            return addPreferenceScreen(factory.create(context))
        }
        if (visitedScreens.add(PreferenceScreenCoordinate(screenKey))) {
            val screen = screens.getOrPut(screenKey) { PreferenceScreenProto.newBuilder() }
            screen.root = preferenceGroupProto { preference = preferenceProto { key = screenKey } }
            screen.parameterized = true
            if (CatalystFlagProviderFactory.catalystUseKeyParameters()) {
                screen.parametersSchema =
                    factory.parametersSchema.toProto(context, valueDescriptors)
            }
            if (includeParameters) {
                if (CatalystFlagProviderFactory.catalystUseKeyParameters()) {
                    factory.keyParameters(context).collect { screen.addKeyParameters(it.toProto()) }
                } else {
                    factory.parameters(context).collect { screen.addParameters(it.toProto()) }
                }
            }
            if (includeHierarchy) {
                var flagEnabled: Boolean? = null
                if (CatalystFlagProviderFactory.catalystUseKeyParameters()) {
                    // We need to instantiate without parameters to add an empty default entry
                    // if there are no valid parameter sets
                    val parameters = factory.keyParameters(context).toList()
                    if (parameters.isEmpty()) {
                        runSafelyAsync(TAG, "create screen with empty key parameters") {
                            addPreferenceScreen(
                                factory.create(context),
                                PreferenceScreenCoordinate("$screenKey:empty"),
                            )
                        }
                    } else {
                        parameters.forEach {
                            runSafelyAsync(TAG, "create screen with params") {
                                if (flagEnabled == false) return@runSafelyAsync
                                val screenMetadata = factory.createWithKeyParameters(context, it)
                                if (flagEnabled == null)
                                    flagEnabled = checkScreenFlag(screenMetadata)
                                if (flagEnabled) addPreferenceScreen(screenMetadata)
                            }
                        }
                    }
                } else {
                    factory.parameters(context).collect {
                        if (flagEnabled == false) return@collect
                        val screenMetadata = factory.create(context, it)
                        if (flagEnabled == null) flagEnabled = checkScreenFlag(screenMetadata)
                        if (flagEnabled) addPreferenceScreen(screenMetadata)
                    }
                }
            }
        }
        return true
    }

    @CanIgnoreReturnValue
    private suspend fun addPreferenceScreen(
        metadata: PreferenceScreenMetadata,
        coordinate: PreferenceScreenCoordinate =
            PreferenceScreenCoordinate(metadata.key, metadata.keyParameters),
    ): Boolean {
        if (!checkScreenFlag(metadata)) return false
        if (!metadata.isExposable(context)) return false

        return if (CatalystFlagProviderFactory.catalystUseKeyParameters()) {
            addPreferenceScreenWithKeyParameters(metadata.key, metadata.keyParameters, coordinate) {
                completeHierarchy = metadata.hasCompleteHierarchy()
                root =
                    if (includeHierarchy) {
                        metadata
                            .getPreferenceHierarchy(context, coroutineScope)
                            .toProto(metadata, true)
                    } else {
                        preferenceGroupProto { preference = toProto(metadata, metadata, true) }
                    }
            }
        } else {
            addPreferenceScreen(metadata.key, metadata.arguments) {
                completeHierarchy = metadata.hasCompleteHierarchy()
                root =
                    if (includeHierarchy) {
                        metadata
                            .getPreferenceHierarchy(context, coroutineScope)
                            .toProto(metadata, true)
                    } else {
                        preferenceGroupProto { preference = toProto(metadata, metadata, true) }
                    }
            }
        }
    }

    private fun checkScreenFlag(metadata: PreferenceScreenMetadata): Boolean {
        val isFlagDisabled =
            when (metadata) {
                is PreferenceScreenCreator,
                is PreferencesApiScreen -> {
                    runSafely(TAG, "isFlagEnabled", true) { !metadata.isFlagEnabled(context) }
                }

                else -> {
                    false
                }
            }

        if (!forceIncludeAllScreens && isFlagDisabled) {
            Log.w(TAG, "Ignore ${metadata.key} as the flag is disabled")
            return false
        }
        return true
    }

    @CanIgnoreReturnValue
    private suspend fun addPreferenceScreen(
        key: String,
        args: Bundle?,
        init: suspend PreferenceScreenProto.Builder.() -> Unit,
    ): Boolean {
        if (!visitedScreens.add(PreferenceScreenCoordinate(key, args))) return false
        fun newParameterizedScreenBuilder() =
            PreferenceScreenProto.newBuilder().also { it.parameterized = true }
        if (args == null) { // normal screen
            screens[key] = PreferenceScreenProto.newBuilder().also { init(it) }
        } else if (args.isEmpty) { // parameterized screen with backward compatibility
            val builder = screens.getOrPut(key) { newParameterizedScreenBuilder() }
            init(builder)
            val parameterizedScreen = parameterizedPreferenceScreenProto {
                setArgs(args.toProto())
                setScreen(newParameterizedScreenBuilder().also { init(it) })
            }
            builder.addParameterizedScreens(parameterizedScreen)
        } else { // parameterized screen with non-empty arguments
            val builder = screens.getOrPut(key) { newParameterizedScreenBuilder() }
            val parameterizedScreen = parameterizedPreferenceScreenProto {
                setArgs(args.toProto())
                setScreen(newParameterizedScreenBuilder().also { init(it) })
            }
            builder.addParameterizedScreens(parameterizedScreen)
        }
        return true
    }

    @CanIgnoreReturnValue
    private suspend fun addPreferenceScreenWithKeyParameters(
        key: String,
        keyParameters: ValidatedKeyParameters?,
        coordinate: PreferenceScreenCoordinate = PreferenceScreenCoordinate(key, keyParameters),
        init: suspend PreferenceScreenProto.Builder.() -> Unit,
    ): Boolean {
        if (!visitedScreens.add(coordinate)) return false

        fun newParameterizedScreenBuilder() =
            PreferenceScreenProto.newBuilder().also {
                it.parameterized = true
                PreferenceScreenRegistry.getScreenParametersSchema(key)?.let { schema ->
                    it.parametersSchema = schema.toProto(context, valueDescriptors)
                }
            }

        if (keyParameters == null) { // normal screen
            screens[key] = PreferenceScreenProto.newBuilder().also { init(it) }
        } else if (keyParameters.isEmpty) { // parameterized screen with backward compatibility
            val builder = screens.getOrPut(key) { newParameterizedScreenBuilder() }
            init(builder)
            val parameterizedScreen = parameterizedPreferenceScreenProto {
                setKeyParameters(keyParameters.toProto())
                setScreen(newParameterizedScreenBuilder().also { init(it) })
            }
            builder.addParameterizedScreens(parameterizedScreen)
        } else { // parameterized screen with non-empty arguments
            val builder = screens.getOrPut(key) { newParameterizedScreenBuilder() }
            val parameterizedScreen = parameterizedPreferenceScreenProto {
                setKeyParameters(keyParameters.toProto())
                setScreen(newParameterizedScreenBuilder().also { init(it) })
            }
            builder.addParameterizedScreens(parameterizedScreen)
        }
        return true
    }

    private suspend fun PreferenceGroup.toProto(): PreferenceGroupProto = preferenceGroupProto {
        preference = (this@toProto as Preference).toProto()
        for (index in 0 until preferenceCount) {
            val child = getPreference(index)
            addPreferences(
                preferenceOrGroupProto {
                    if (child is PreferenceGroup) {
                        group = child.toProto()
                    } else {
                        preference = child.toProto()
                    }
                }
            )
        }
    }

    private suspend fun Preference.toProto(): PreferenceProto = preferenceProto {
        this@toProto.key?.let { key = it }
        this@toProto.title?.let { title = textProto { string = it.toString() } }
        this@toProto.summary?.let { summary = textProto { string = it.toString() } }
        val preferenceExtras = peekExtras()
        preferenceExtras?.let { extras = it.toProto() }
        enabled = isEnabled
        available = isVisible
        persistent = isPersistent
        if (request.flags.includeValue() && isPersistent && this@toProto is TwoStatePreference) {
            if (!isEnabled) {
                value = preferenceValueProto {
                    error = preferenceErrorProto { error = "enabled not set" }
                }
            } else if (!isVisible) {
                value = preferenceValueProto {
                    error = preferenceErrorProto { error = "availability not set" }
                }
            } else {
                value = preferenceValueProto { booleanValue = this@toProto.isChecked }
            }
        }
        this@toProto.fragment.toActionTarget(preferenceExtras)?.let {
            actionTarget = it
            return@preferenceProto
        }
        this@toProto.intent?.let { actionTarget = it.toActionTarget() }
    }

    private suspend fun PreferenceHierarchy.toProto(
        screenMetadata: PreferenceScreenMetadata,
        isRoot: Boolean,
    ): PreferenceGroupProto = preferenceGroupProto {
        if (this@toProto.metadata.isExposable(context)) {
            preference = toProto(screenMetadata, this@toProto.metadata, isRoot)
        }
        forEachAsync {
            runSafelyAsync(TAG, "process hierarchy node") {
                if (it !is PreferenceHierarchy && !it.metadata.isExposable(context))
                    return@runSafelyAsync
                if (it.metadata is PreferenceScreenMetadata) return@runSafelyAsync

                addPreferences(
                    preferenceOrGroupProto {
                        if (it is PreferenceHierarchy) {
                            group = it.toProto(screenMetadata, false)
                        } else {
                            preference = toProto(screenMetadata, it.metadata, false)
                        }
                    }
                )
            }
        }
    }

    private suspend fun toProto(
        screenMetadata: PreferenceScreenMetadata,
        metadata: PreferenceMetadata,
        isRoot: Boolean,
    ) =
        runSafelyAsync(TAG, "conversion for $screenMetadata $metadata", null) {
            metadata
                .toProto(
                    context,
                    callingPid,
                    callingUid,
                    screenMetadata,
                    isRoot,
                    request.flags,
                    valueDescriptors,
                )
                .also {
                    if (!isRoot && shrinkHierarchy) return@also
                    if (metadata is PreferenceScreenMetadata) {
                        @Suppress("CheckReturnValue") addPreferenceScreen(metadata)
                    }
                    metadata.intent(context)?.resolveActivity(context.packageManager)?.let {
                        if (it.packageName == context.packageName) {
                            add(it.className)
                        }
                    }
                }
        }

    private suspend fun String?.toActionTarget(extras: Bundle?): ActionTarget? {
        if (this.isNullOrEmpty()) return null

        return runSafelyAsync(TAG, "loadClass $this", fallback = null) {
            val fragmentClass = context.classLoader.loadClass(this)

            if (Fragment::class.java.isAssignableFrom(fragmentClass)) {
                @Suppress("UNCHECKED_CAST")
                (fragmentClass as Class<out Fragment>).toActionTarget(extras)
            } else {
                null
            }
        }
    }

    private suspend fun Class<out Fragment>.toActionTarget(extras: Bundle?): ActionTarget? {
        if (
            !PreferenceScreenProvider::class.java.isAssignableFrom(this) &&
                !PreferenceScreenBindingKeyProvider::class.java.isAssignableFrom(this)
        ) {
            return null
        }
        val fragment =
            runSafelyAsync(TAG, "instantiate fragment ${this@toActionTarget}", fallback = null) {
                withContext(Dispatchers.Main) { newInstance().apply { arguments = extras } }
            }
        if (fragment is PreferenceScreenBindingKeyProvider) {
            val screenKey = fragment.getPreferenceScreenBindingKey(context)
            if (screenKey != null && addPreferenceScreenFromRegistry(screenKey)) {
                return actionTargetProto { key = screenKey }
            }
        }
        if (fragment is PreferenceScreenProvider) {
            try {
                val screen =
                    fragment.createPreferenceScreen(preferenceScreenFactory, coroutineScope)
                val screenKey = screen?.key
                if (!screenKey.isNullOrEmpty()) {
                    if (CatalystFlagProviderFactory.catalystUseKeyParameters()) {
                        addPreferenceScreenWithKeyParameters(screenKey, null) {
                            root = screen.toProto()
                        }
                    } else {
                        @Suppress("CheckReturnValue")
                        addPreferenceScreen(screenKey, null) { root = screen.toProto() }
                    }
                    return actionTargetProto { key = screenKey }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Fail to createPreferenceScreen for $fragment", e)
            }
        }
        return null
    }

    private suspend fun Intent.toActionTarget() =
        toActionTarget(context).also {
            resolveActivity(context.packageManager)?.let {
                if (it.packageName == context.packageName) {
                    add(it.className)
                }
            }
        }

    companion object {
        suspend fun of(
            context: Context,
            callingPid: Int,
            callingUid: Int,
            request: GetPreferenceGraphRequest,
            coroutineScope: CoroutineScope,
        ) =
            PreferenceGraphBuilder(context, callingPid, callingUid, request, coroutineScope).also {
                it.init()
            }
    }
}

private fun PreconditionStability.toProto(): PreconditionStabilityProto =
    when (this) {
        PreconditionStability.STABLE_UNTIL_APK_UPDATE ->
            PreconditionStabilityProto.STABLE_UNTIL_APK_UPDATE

        PreconditionStability.UNSTABLE -> PreconditionStabilityProto.UNSTABLE
    }

private fun PreferenceProto.Builder.addPreconditionStatus(
    context: Context,
    preconditionDescription: String?,
    includeValue: Boolean,
    isGet: Boolean,
    evaluate: (suspend () -> ApiPreconditions)? = null,
) {
    if (preconditionDescription == null) return
    val statusBuilder = PreconditionStatusProto.newBuilder()
    statusBuilder.precondition = preconditionDescription
    if (includeValue) {
        val result =
            if (evaluate != null) {
                runBlocking { evaluate() }
            } else {
                null
            }
        if (result is Allowed) {
            statusBuilder.satisfied = true
        } else if (result is Disallowed) {
            statusBuilder.satisfied = false
            statusBuilder.failure = result.getReason(context)
            statusBuilder.stability = result.stability.toProto()
        }
    }
    if (isGet) {
        addGetPreconditionsStatus(statusBuilder)
    } else {
        addSetPreconditionsStatus(statusBuilder)
    }
}

fun PreferenceMetadata.toProto(
    context: Context,
    callingPid: Int,
    callingUid: Int,
    screenMetadata: PreferenceScreenMetadata,
    isRoot: Boolean,
    flags: Int,
    valueDescriptors: MutableMap<String, PreferenceValueDescriptorProto>? = null,
) = preferenceProto {
    val metadata = this@toProto
    key = metadata.key
    runSafely(TAG, "toProto for $key") {
        if (flags.includeMetadata()) {
            writable =
                if (metadata is ApiPreference<*, *>) {
                    metadata.set != null
                } else if (metadata is PersistentPreference<*>) {
                    metadata.supportsWrite
                } else {
                    false
                }

            val preferenceExtras = metadata.extras(context)
            preferenceExtras?.let { extras = it.toProto() }
            enabled = runSafely(TAG, "isEnabled", false) { metadata.isEnabled(context) }
            if (metadata is PreferenceAvailabilityProvider) {
                available = runSafely(TAG, "isAvailable", false) { metadata.isAvailable(context) }
            } else {
                available = true
            }
            if (metadata is PreferenceRestrictionProvider) {
                restricted =
                    runSafely(TAG, "isRestricted", false) { metadata.isRestricted(context) }
            }

            if (CatalystFlagProviderFactory.catalystUseKeyParameters()) {
                if (metadata is PreferenceScreenMetadata) {
                    metadata.keyParametersSchema?.let {
                        parametersSchema = it.toProto(context, valueDescriptors)
                    }
                    metadata.keyParameters?.let { keyParameters = it.toProto() }
                } else if (metadata is ApiPreference<*, *>) {
                    metadata.getParametersSchema()?.let {
                        parametersSchema = it.toProto(context, valueDescriptors)
                    }
                    metadata.getParameters()?.let { keyParameters = it.toProto() }
                } else {
                    // We don't automatically add key parameters onto the
                    // preferences in catalyst v1 so we can add them here.
                    screenMetadata.keyParametersSchema?.let {
                        parametersSchema = it.toProto(context, valueDescriptors)
                    }
                    screenMetadata.keyParameters?.let { keyParameters = it.toProto() }
                }
            }

            screenMetadata.getTrampolinedLaunchIntent(metadata).let { launchIntent = it.toProto() }

            for (tag in metadata.tags(context)) addTags(tag)
        }
        purpose = metadata.purpose
        val includeValue = flags.includeValue()
        if (metadata is ApiPreference<*, *>) {
            addPreconditionStatus(
                context,
                metadata.screenPreconditions?.getDescription(context),
                includeValue,
                isGet = true,
                evaluate = { metadata.evaluatePreconditions(context, metadata.screenPreconditions) },
            )
            addPreconditionStatus(
                context,
                metadata.preconditions?.getDescription(context),
                includeValue,
                isGet = true,
                evaluate = { metadata.evaluatePreconditions(context, metadata.preconditions) },
            )
            addPreconditionStatus(
                context,
                metadata.get.preconditions?.getDescription(context),
                includeValue,
                isGet = true,
                evaluate = { metadata.evaluatePreconditions(context, metadata.get.preconditions) },
            )
            addPreconditionStatus(
                context,
                metadata.set?.preconditions?.getDescription(context),
                includeValue,
                isGet = false,
                evaluate = { metadata.evaluatePreconditions(context, metadata.set?.preconditions) },
            )
            addPreconditionStatus(
                context,
                metadata.set?.valuePreconditions?.getDescription(context),
                includeValue,
                isGet = false,
                // TODO: should this be null?
                evaluate = { metadata.evaluatePreconditions(context, null) },
            )
            metadata.set?.warning?.let { warningConfig ->
                setWarning = setWarningProto {
                    warning = warningConfig.getWarning(context)
                    val preconditionsDescription =
                        when {
                            warningConfig.preconditions != null -> {
                                warningConfig.preconditions!!.getDescription(context)
                            }

                            warningConfig.valuePreconditions != null -> {
                                warningConfig.valuePreconditions!!.getDescription(context)
                            }

                            else -> null
                        }
                    preconditionsDescription?.let { addPreconditions(it) }
                }
            }
        } else if (metadata is PreferencesApiScreen) {
            addPreconditionStatus(
                context,
                metadata.screenPreconditions?.getDescription(context),
                includeValue,
                isGet = true,
                evaluate = { metadata.evaluatePreconditions(context) },
            )
        } else if (metadata is PreferenceAvailabilityProvider) {
            addPreconditionStatus(
                context,
                metadata.availabilityDescription,
                includeValue,
                isGet = true,
                evaluate = {
                    if (metadata.isAvailable(context)) Allowed
                    else
                        Custom(
                            metadata.availabilityDescription,
                            metadata.getAvailabilityStability(),
                        )
                },
            )
        }
        // We treat enabled as "get" for screens as it appears that's the way they've been used.
        metadata.getEnabledDescription()?.let {
            addPreconditionStatus(
                context,
                it,
                includeValue,
                isGet = (metadata is PreferenceScreenMetadata),
                evaluate = {
                    if (metadata.isEnabled(context)) Allowed
                    else
                        Custom(
                            it,
                            metadata.getEnabledStability()
                                ?: PreconditionStability.STABLE_UNTIL_APK_UPDATE,
                        )
                },
            )
        }
        // always true for preferences
        persistent = metadata.isPersistent(context)
        sensitivityLevel = metadata.sensitivityLevel
        if (metadata !is PersistentPreference<*> || metadata is PreferenceScreenMetadata) {
            if (metadata is PreferencesApiScreen && flags.includeValue()) {
                val preconditions = runBlocking { metadata.evaluatePreconditions(context) }
                if (preconditions is Disallowed) {
                    value = preferenceValueProto {
                        error = preferenceErrorProto {
                            if (
                                preconditions.stability ==
                                    PreconditionStability.STABLE_UNTIL_APK_UPDATE
                            ) {
                                error =
                                    "read precondition not met: ${preconditions.getReason(context)} (stable)"
                            } else {
                                error =
                                    "read precondition not met: ${preconditions.getReason(context)}"
                            }
                        }
                    }
                }
            }
            return@preferenceProto
        }
        metadata.getReadPermissions(context)?.let {
            if (it.size > 0) readPermissions = it.toProto()
        }
        metadata.getWritePermissions(context)?.let {
            if (it.size > 0) writePermissions = it.toProto()
        }
        val readPermit = metadata.evalReadPermit(context, callingPid, callingUid)
        val writePermit =
            metadata.evalWritePermit(context, callingPid, callingUid) ?: ReadWritePermit.ALLOW
        readWritePermit = ReadWritePermit.make(readPermit, writePermit)
        if (flags.includeValue()) {
            val errorString =
                if (hasAvailable() && !available) {
                    if (metadata is PreferenceAvailabilityProvider) {
                        if (
                            metadata.getAvailabilityStability() ==
                                PreconditionStability.STABLE_UNTIL_APK_UPDATE
                        ) {
                            "read precondition not met: ${metadata.availabilityDescription} (stable)"
                        } else {
                            "read precondition not met: ${metadata.availabilityDescription}"
                        }
                    } else {
                        "read precondition not met: missing available with unknown reason"
                    }
                } else if (readPermit == ReadWritePermit.REQUIRE_APP_PERMISSION) {
                    "read precondition not met: must hold all specified permissions"
                } else if (readPermit != ReadWritePermit.ALLOW) {
                    if (metadata is ApiPreference<*, *>) {
                        val failure = runBlocking {
                            metadata.evaluatePreconditions(context, metadata.get.preconditions)
                        }
                        if (failure is Disallowed) {
                            failure.getReason(context)
                        } else {
                            "read precondition not met: missing readPermit with unknown reason - ${failure} - ${readPermit}"
                        }
                    } else {
                        "read precondition not met: missing readPermit with unknown reason - ${readPermit}"
                    }
                } else {
                    null
                }

            if (errorString != null) {
                value = preferenceValueProto {
                    error = preferenceErrorProto { error = errorString }
                }
            } else {
                val storage = metadata.storage(context)
                try {
                    value = preferenceValueProto {
                        val key = metadata.key
                        when (metadata.valueType) {
                            Int::class.java,
                            Int::class.javaObjectType -> storage.getInt(key)?.let { intValue = it }

                            Boolean::class.java,
                            Boolean::class.javaObjectType ->
                                storage.getBoolean(key)?.let { booleanValue = it }

                            Float::class.java,
                            Float::class.javaObjectType ->
                                storage.getFloat(key)?.let { floatValue = it }

                            Long::class.java,
                            Long::class.javaObjectType ->
                                storage.getLong(key)?.let { longValue = it }

                            CharSequence::class.java,
                            CharSequence::class.javaObjectType,
                            String::class.java,
                            String::class.javaObjectType ->
                                storage.getString(key)?.let { stringValue = it.toString() }

                            else -> {
                                Log.e(
                                    "PreferenceGraphBuilder",
                                    "Unsupported type ${metadata.valueType}",
                                )
                                error = preferenceErrorProto {
                                    error = "Error: Unsupported type ${metadata.valueType}"
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    value = preferenceValueProto {
                        error = preferenceErrorProto {
                            error = "Error: ${e.message ?: e.toString()}"
                        }
                    }
                }
            }
        }
        if (flags.includeValueDescriptor()) {
            if (metadata is ApiPreference<*, *>) {
                valueDescriptor = metadata.type.toProto(context, valueDescriptors)
            } else {
                valueDescriptor = preferenceValueDescriptorProto {
                    if (metadata is IntRangeValuePreference) {
                        rangeValue = rangeValueProto {
                            min = metadata.getMinValue(context)
                            max = metadata.getMaxValue(context)
                            step = metadata.getIncrementStep(context)
                        }
                    }
                    if (metadata is DiscreteIntValue) {
                        val values = context.resources.getIntArray(metadata.values)
                        val descriptions =
                            context.resources.getTextArray(metadata.valuesDescription)
                        values.zip(descriptions).forEach { (value, desc) ->
                            addPossibleValues(
                                possibleValueProto {
                                    this.value = preferenceValueProto { intValue = value }
                                    description = desc.toString()
                                }
                            )
                        }
                    }
                    if (metadata is com.android.settingslib.metadata.DiscreteTextValue) {
                        val values = context.resources.getTextArray(metadata.values)
                        val descriptions =
                            context.resources.getTextArray(metadata.valuesDescription)
                        values.zip(descriptions).forEach { (value, desc) ->
                            addPossibleValues(
                                possibleValueProto {
                                    this.value = preferenceValueProto {
                                        stringValue = value.toString()
                                    }
                                    description = desc.toString()
                                }
                            )
                        }
                    }
                    if (metadata is com.android.settingslib.metadata.DiscreteStringValue) {
                        val values = context.resources.getStringArray(metadata.values)
                        val descriptions =
                            context.resources.getTextArray(metadata.valuesDescription)
                        values.zip(descriptions).forEach { (value, desc) ->
                            addPossibleValues(
                                possibleValueProto {
                                    this.value = preferenceValueProto { stringValue = value }
                                    description = desc.toString()
                                }
                            )
                        }
                    }
                    when (metadata.valueType) {
                        Int::class.java,
                        Int::class.javaObjectType -> {
                            if (!hasRangeValue()) {
                                rangeValue = rangeValueProto {}
                            }
                        }

                        Boolean::class.java,
                        Boolean::class.javaObjectType -> booleanType = true

                        Float::class.java,
                        Float::class.javaObjectType -> floatType = true

                        Long::class.java,
                        Long::class.javaObjectType -> longType = true

                        CharSequence::class.java,
                        CharSequence::class.javaObjectType,
                        String::class.java,
                        String::class.javaObjectType -> stringType = true

                        else -> error("Error: Unsupported type ${metadata.valueType}")
                    }
                    (metadata as? ValueDescriptor)?.getUnitOfMeasurement()?.let {
                        parameters = keyParametersProto { putValues("unit", it) }
                    }
                }
            }
        }
    }
}

/** Evaluates the read permit of a persistent preference. */
fun <T> PersistentPreference<T>.evalReadPermit(
    context: Context,
    callingPid: Int,
    callingUid: Int,
): Int =
    when {
        !isExposable(context) -> ReadWritePermit.DISALLOW
        getReadPermissions(context)?.check(context, callingPid, callingUid) == false ->
            ReadWritePermit.REQUIRE_APP_PERMISSION

        else ->
            runSafely(TAG, "getReadPermit for $key", ReadWritePermit.DISALLOW) {
                getReadPermit(context, callingPid, callingUid)
            }
    }

/** Evaluates the write permit of a persistent preference. */
fun <T> PersistentPreference<T>.evalWritePermit(
    context: Context,
    callingPid: Int,
    callingUid: Int,
): Int? {
    if (!supportsWrite) {
        return ReadWritePermit.DISALLOW
    }

    val isDebuggable = AppUtils.isDebuggable()

    // Use the global setting as a gate for debug environments
    val hasUnknownSensitivitySettings =
        Settings.Global.getInt(
            context.contentResolver,
            "com.android.settings.UNKNOWN_SENSITIVITY_IS_AVAILABLE",
            0,
        ) == 1

    return when {
        // High sensitivity is strictly disallowed.
        sensitivityLevel == DEEP_LINK_ONLY -> ReadWritePermit.DISALLOW
        sensitivityLevel == REQUIRES_CONFIRMATION -> ReadWritePermit.DISALLOW

        // Unknown sensitivity is disallowed, unless we are on a debuggable build
        // and the caller holds the WRITE_SECURE_SETTINGS permission.
        sensitivityLevel == DO_NOT_EXPOSE && !(isDebuggable && hasUnknownSensitivitySettings) ->
            ReadWritePermit.DISALLOW

        // If the app lacks the required permissions, require them.
        getWritePermissions(context)?.check(context, callingPid, callingUid) == false ->
            ReadWritePermit.REQUIRE_APP_PERMISSION

        // Otherwise, delegate to the specific permit logic.
        else ->
            runSafely(TAG, "getWritePermit for $key", ReadWritePermit.DISALLOW) {
                getWritePermit(context, callingPid, callingUid)
            }
    }
}

private fun Intent.toActionTarget(context: Context): ActionTarget {
    if (component?.packageName == "") {
        setClassName(context, component!!.className)
    }
    return actionTargetProto { intent = toProto() }
}

private fun KeyParametersSchema.toProto(
    context: Context,
    valueDescriptors: MutableMap<String, PreferenceValueDescriptorProto>? = null,
): KeyParametersSchemaProto {
    val builder = KeyParametersSchemaProto.newBuilder()
    getParameters().forEach { (name, definition) ->
        val schemaMap = definition.toParameterSchemaMap(context)
        val purpose = schemaMap[KeyParametersSchema.ParameterDefinition.PURPOSE_KEY] as? String
        val required =
            schemaMap[KeyParametersSchema.ParameterDefinition.REQUIRED_KEY] as? Boolean ?: false
        val paramProto = ParameterDefinitionProto.newBuilder().setRequired(required)
        purpose?.let { paramProto.setPurpose(it) }

        paramProto.setValueDescriptor(definition.type.toProto(context, valueDescriptors))

        builder.putParameters(name, paramProto.build())
    }
    return builder.build()
}

private fun ApiType<*, *>.toProto(
    context: Context,
    valueDescriptors: MutableMap<String, PreferenceValueDescriptorProto>?,
): PreferenceValueDescriptorProto {
    val descriptorKey = getKey()

    fun PreferenceValueDescriptorProto.Builder.setType() {
        if (this@toProto is IntInRange) {
            rangeValue = rangeValueProto {
                this@toProto.min?.let { min = it }
                this@toProto.max?.let { max = it }
                this@toProto.step.let { step = it }
            }
        }
        when (val valueType = this@toProto.getType()) {
            Int::class.java,
            Int::class.javaObjectType -> {
                if (!hasRangeValue()) {
                    rangeValue = rangeValueProto {}
                }
            }

            Boolean::class.java,
            Boolean::class.javaObjectType -> booleanType = true

            Float::class.java,
            Float::class.javaObjectType -> floatType = true

            Long::class.java,
            Long::class.javaObjectType -> longType = true

            String::class.java,
            String::class.javaObjectType -> stringType = true

            else -> error("Error: Unsupported type $valueType")
        }
    }

    fun createFullDescriptor() = preferenceValueDescriptorProto {
        valueDescriptorKey = descriptorKey
        description = this@toProto.getDescription(context)
        this@toProto.getParametersSchema()?.let {
            parametersSchema = it.toProto(context, valueDescriptors)
        }
        this@toProto.getParameters()?.let { parameters = it.toProto() }

        setType()

        if (this@toProto is FiniteOptionsType<*, *>) {
            runBlocking {
                this@toProto.getCachedOptions(context).forEach {
                    addPossibleValues(
                        possibleValueProto {
                            value = preferenceValueProto {
                                when (this@toProto.getType()) {
                                    Int::class.java,
                                    Int::class.javaObjectType ->
                                        intValue = extractSafety(it.first, markup = false) as Int

                                    Boolean::class.java,
                                    Boolean::class.javaObjectType ->
                                        booleanValue =
                                            extractSafety(it.first, markup = false) as Boolean

                                    Float::class.java,
                                    Float::class.javaObjectType ->
                                        floatValue =
                                            extractSafety(it.first, markup = false) as Float

                                    Long::class.java,
                                    Long::class.javaObjectType ->
                                        longValue = extractSafety(it.first, markup = false) as Long

                                    String::class.java,
                                    String::class.javaObjectType ->
                                        stringValue =
                                            extractSafety(it.first, markup = false) as String

                                    else ->
                                        error("Error: Unsupported type ${this@toProto.getType()}")
                                }
                            }
                            description = extractSafety(it.second, markup = false) as String
                        }
                    )
                }
            }
        }
    }

    if (valueDescriptors != null) {
        valueDescriptors.getOrPut(descriptorKey) { createFullDescriptor() }
        return preferenceValueDescriptorProto {
            valueDescriptorKey = descriptorKey
            setType()
        }
    } else {
        return createFullDescriptor()
    }
}

@SuppressLint("AppBundleLocaleChanges")
internal fun Context.ofLocale(locale: Locale?): Context {
    if (locale == null) return this
    val baseConfig: Configuration = resources.configuration
    val baseLocale =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            baseConfig.locales[0]
        } else {
            baseConfig.locale
        }
    if (locale == baseLocale) {
        return this
    }
    val newConfig = Configuration(baseConfig)
    newConfig.setLocale(locale)
    return createConfigurationContext(newConfig)
}
