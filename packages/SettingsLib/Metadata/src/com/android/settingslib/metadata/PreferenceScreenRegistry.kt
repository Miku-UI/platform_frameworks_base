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
import android.os.Bundle
import android.util.Log
import com.android.settingslib.datastore.KeyValueStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.runBlocking

/** Registry of all available preference screens in the app. */
object PreferenceScreenRegistry {
    private const val TAG = "ScreenRegistry"

    /** Provider of key-value store. */
    private lateinit var keyValueStoreProvider: KeyValueStoreProvider

    /** The default permit for external application to read preference values. */
    var defaultReadPermit: @ReadWritePermit Int = ReadWritePermit.ALLOW

    /** The default permit for external application to write preference values. */
    var defaultWritePermit: @ReadWritePermit Int = ReadWritePermit.DISALLOW

    /**
     * Factories of all available [PreferenceScreenMetadata]s.
     *
     * The map key is preference screen key.
     */
    var preferenceScreenMetadataFactories = FixedArrayMap<String, PreferenceScreenMetadataFactory>()

    /** Metrics logger for preference actions triggered by user interaction. */
    var preferenceUiActionMetricsLogger: PreferenceUiActionMetricsLogger? = null

    /** Sets the [KeyValueStoreProvider]. */
    fun setKeyValueStoreProvider(keyValueStoreProvider: KeyValueStoreProvider) {
        this.keyValueStoreProvider = keyValueStoreProvider
    }

    /**
     * Returns the key-value store for given preference.
     *
     * Must call [setKeyValueStoreProvider] before invoking this method, otherwise
     * [NullPointerException] is raised.
     */
    fun getKeyValueStore(context: Context, preference: PreferenceMetadata): KeyValueStore? =
        keyValueStoreProvider.getKeyValueStore(context, preference)

    /**
     * True if the screen requires parameters to be constructed.
     */
    fun isParameterized(context: Context, screenKey: String): Boolean {
        val factory = preferenceScreenMetadataFactories[screenKey] ?: return false
        return factory is PreferenceScreenMetadataParameterizedFactory
    }

    /**
     * Returns a flow of parameters for the screen.
     *
     * If the screen is unparameterized, or there are no valid parameters, an empty flow is
     * returned.
     */
    fun getParameters(context: Context, screenKey: String): Flow<Bundle> {
        return (preferenceScreenMetadataFactories[screenKey] as? PreferenceScreenMetadataParameterizedFactory)?.parameters(context) ?: emptyFlow()
    }

    fun getKeyParameters(context: Context, screenKey: String): Flow<ValidatedKeyParameters> {
        return (preferenceScreenMetadataFactories[screenKey] as? PreferenceScreenMetadataParameterizedFactory)?.keyParameters(context) ?: emptyFlow()
    }

    fun getScreenParametersSchema(screenKey: String): KeyParametersSchema? {
        return (preferenceScreenMetadataFactories[screenKey] as? PreferenceScreenMetadataParameterizedFactory)?.parametersSchema
    }

    /** Creates [PreferenceScreenMetadata] of particular screen. */
    fun create(context: Context, screenCoordinate: PreferenceScreenCoordinate) =
        if (CatalystFlagProviderFactory.catalystUseKeyParameters()) {
            val validatedKeyParameters = screenCoordinate.keyParameters?.let {
                getScreenParametersSchema(screenCoordinate.screenKey)?.prepare(it)
            }
            createWithKeyParameters(context, screenCoordinate.screenKey, validatedKeyParameters)
        } else {
            create(context, screenCoordinate.screenKey, screenCoordinate.args)
        }

    /** Creates [PreferenceScreenMetadata] of particular screen key with given arguments. */
    @Deprecated("This method will be removed once the catalyst framework stops passing the arguments as a bundle. Use createWithKeyParameters instead.")
    fun create(context: Context, screenKey: String?, args: Bundle?): PreferenceScreenMetadata? {
        if (screenKey == null) return null
        val factory = preferenceScreenMetadataFactories[screenKey] ?: return null
        val appContext = context.applicationContext
        if (factory is PreferenceScreenMetadataParameterizedFactory) {
            if (args != null) return factory.create(appContext, args)
            // In case the parameterized screen was a normal screen, it is expected to accept
            // Bundle.EMPTY arguments and take care of backward compatibility.
            if (factory.acceptEmptyArguments()) return factory.create(appContext)
            Log.e(TAG, "screen $screenKey is parameterized but args is not provided")
            return null
        } else {
            if (args == null) return factory.create(appContext)
            Log.e(TAG, "screen $screenKey is not parameterized but args is provided")
            return null
        }
    }

    /** Creates [PreferenceScreenMetadata] of particular screen key with given arguments. */
    fun createWithKeyParameters(context: Context, screenKey: String?, keyParameters: ValidatedKeyParameters?): PreferenceScreenMetadata? {
        if (screenKey == null) return null
        val factory = preferenceScreenMetadataFactories[screenKey] ?: return null
        val appContext = context.applicationContext
        if (factory is PreferenceScreenMetadataParameterizedFactory) {
            if (keyParameters != null) return factory.createWithKeyParameters(appContext, keyParameters)
            // In case the parameterized screen was a normal screen, it is expected to accept
            // Bundle.EMPTY arguments and take care of backward compatibility.
            if (factory.acceptEmptyArguments()) return factory.create(appContext)
            Log.e(TAG, "screen $screenKey is parameterized but keyParameters is not provided")
            return null
        } else {
            if (keyParameters == null) return factory.create(appContext)
            Log.e(TAG, "screen $screenKey is not parameterized but keyParameters is provided")
            return null
        }
    }

    /**
     * Create an instance of a specific screen based on key, regardless of parameterization.
     * This method is used solely for getting the metadata of a screen.
     */
    fun createScreenInstanceForMetadata(context: Context, screenKey:String?) : PreferenceScreenMetadata? {
        val screenFactory = if(screenKey != null) preferenceScreenMetadataFactories[screenKey] else null
        if(screenFactory == null)
            return null
        return createScreenInstanceForMetadata(context, screenFactory)
    }

    /**
     * Create an instance of a specific screen based on key, regardless of parameterization.
     * This method is used solely for getting the metadata of a screen.
     */
    fun createScreenInstanceForMetadata(context: Context, factory: PreferenceScreenMetadataFactory): PreferenceScreenMetadata? {
        return if (factory !is PreferenceScreenMetadataParameterizedFactory)
            factory.create(context)
        else {
            if (CatalystFlagProviderFactory.catalystUseKeyParameters()) {
                val firstKeyParameter = runBlocking {
                    factory.keyParameters(context).firstOrNull()
                } ?: ValidatedKeyParameters(factory.parametersSchema, emptyMap())
                factory.createWithKeyParameters(context, firstKeyParameter)
            } else {
                val firstParameter = runBlocking {
                    factory.parameters(context).firstOrNull()
                }
                if (firstParameter == null)
                    null
                else factory.create(context, firstParameter)
            }
        }
    }
}

/** Provider of [KeyValueStore]. */
fun interface KeyValueStoreProvider {

    /**
     * Returns the key-value store for given preference.
     *
     * Here are some use cases:
     * - provide the default storage for all preferences
     * - determine the storage per preference keys or the interfaces implemented by the preference
     */
    fun getKeyValueStore(context: Context, preference: PreferenceMetadata): KeyValueStore?
}
