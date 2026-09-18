/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.settingslib.metadata.preferencesapi

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.annotation.StringRes
import androidx.fragment.app.Fragment
import com.android.settingslib.datastore.Permissions
import com.android.settingslib.datastore.and
import com.android.settingslib.datastore.or
import com.android.settingslib.metadata.KeyParametersSchema
import com.android.settingslib.metadata.PreferenceHierarchy
import com.android.settingslib.metadata.PreferenceMetadata
import com.android.settingslib.metadata.PreferenceScreenMetadata
import com.android.settingslib.metadata.SensitivityLevel
import com.android.settingslib.metadata.ValidatedKeyParameters
import com.android.settingslib.metadata.preferenceHierarchy
import com.android.settingslib.metadata.preferencesapi.Utils.EXCEPTION_MESSAGE_NO_PARAMETER_DEFINED
import com.android.settingslib.metadata.preferencesapi.Utils.getExceptionMessageMultipleDefines
import com.android.settingslib.metadata.preferencesapi.Utils.getExceptionMessageMultipleParametersDefined
import com.android.settingslib.metadata.preferencesapi.Utils.getExceptionMessageWrongOrder
import com.android.settingslib.metadata.preferencesapi.category.Category
import com.android.settingslib.metadata.preferencesapi.multiusers.ManagementScope
import com.android.settingslib.metadata.preferencesapi.multiusers.ManagementScope.OWN_USER
import com.android.settingslib.metadata.preferencesapi.multiusers.PreferenceTarget
import com.android.settingslib.metadata.preferencesapi.multiusers.PreferenceTarget.USER
import com.android.settingslib.metadata.preferencesapi.preconditions.Allowed
import com.android.settingslib.metadata.preferencesapi.preconditions.ApiPreconditions
import com.android.settingslib.metadata.preferencesapi.preconditions.Disallowed
import com.android.settingslib.metadata.preferencesapi.types.ApiType
import com.android.settingslib.metadata.preferencesapi.types.FiniteOptionsType
import kotlin.collections.mutableListOf
import kotlin.reflect.KClass
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.runBlocking
import com.android.settingslib.metadata.preferencesapi.safe
import com.android.settingslib.metadata.preferencesapi.unsafe

/** Interface for preference screens that provide parameters in a non-static method. */
interface ProvidesParametersNonStatically {
    suspend fun getAllPossibleParameters(context: Context): Flow<ValidatedKeyParameters>

    /**
     * Synchronous version of [getAllPossibleParameters] for Java.
     *
     * This should go away soon once we support suspending calls throughout.
     */
    // TODO(469317113): Remove this once suspending calls are supported.
    fun getAllPossibleParametersSync(context: Context) = runBlocking {
        getAllPossibleParameters(context)
    }
}

/** Scope for parameterization-related declarations. */
class ParameterizationConfig {
    internal class ApiParameterDefinition(
        val name: String,
        @StringRes val purpose: Int,
        val required: Boolean,
        val type: FiniteOptionsType<*, *>,
    )

    internal val parameters = mutableMapOf<String, ApiParameterDefinition>()
    internal var prepareScreenExtras: ((ValidatedKeyParameters, Bundle) -> Unit)? = null
    internal var prepareSpaRoute: ((ValidatedKeyParameters) -> String)? = null

    /**
     * Defines a parameter and adds it to the schema.
     *
     * @param name The unique name for the parameter.
     * @param purpose A human-readable purpose of the parameter.
     * @param required Whether this parameter must be provided. Defaults to `false`.
     * @param type The type of the parameter, used to generate its possible values.
     * @throws IllegalArgumentException if a parameter with the same name is already defined.
     */
    fun parameter(
        name: String,
        @StringRes purpose: Int,
        required: Boolean = false,
        type: FiniteOptionsType<*, *>,
    ) {
        if (parameters.containsKey(name)) {
            throw IllegalArgumentException("Parameter '$name' is already defined.")
        }
        parameters[name] = ApiParameterDefinition(name, purpose, required, type)
    }

    /**
     * Declares how to convert the API-First parameters into the `Bundle` of extras required to
     * launch the fragment for this screen.
     */
    fun prepareScreenExtras(lambda: (ValidatedKeyParameters, Bundle) -> Unit) {
        if (prepareScreenExtras != null) {
            throw IllegalStateException(getExceptionMessageMultipleDefines("prepareScreenExtras"))
        }
        prepareScreenExtras = lambda
    }

    /** Declares how to generate the SPA route from the API-First parameters. */
    fun prepareSpaRoute(lambda: (ValidatedKeyParameters) -> String) {
        if (prepareSpaRoute != null) {
            throw IllegalStateException(getExceptionMessageMultipleDefines("prepareSpaRoute"))
        }
        prepareSpaRoute = lambda
    }

    /** Builds and returns the final [KeyParametersSchema] instance. For internal use by the DSL. */
    internal fun buildSchema(): KeyParametersSchema = KeyParametersSchema {
        parameters.values.map {
            parameter(name = it.name, purpose = it.purpose, required = it.required, type = it.type)
        }
    }
}

/**
 * Container for all information and preferences on a Settings screen which is intended to be
 * exposed via API using 2026 "Lightweight" way.
 */
abstract class PreferencesApiScreen
private constructor(
    override val key: String,
    val topLevelSettingsCategory: Category,
    val fragment: KClass<out Fragment>?,
    override val purpose: Int,
    val alreadyPartiallyMigrated: KClass<*>? = null,
    val canManage: ManagementScope = OWN_USER,
    /**
     * The route prefix for screens implemented using the Settings Platform Architecture (SPA). This
     * is only relevant if this screen's UI is implemented using SPA.
     */
    val spaRoutePrefix: String?,
) : PreferenceScreenMetadata, ProvidesParametersNonStatically {
    init {
        if (alreadyPartiallyMigrated != null) {
            require(key.startsWith(PARTIALLY_MIGRATED_PREFIX)) {
                "The key '$key' must start with '$PARTIALLY_MIGRATED_PREFIX' because it has an already migrated class."
            }
        }
    }

    /** Constructor for screens implemented using a traditional Android [Fragment]. */
    constructor(
        key: String,
        topLevelSettingsCategory: Category,
        fragment: KClass<out Fragment>,
        purpose: Int,
        alreadyPartiallyMigrated: KClass<*>? = null,
        canManage: ManagementScope = OWN_USER,
    ) : this(
        key,
        topLevelSettingsCategory,
        fragment,
        purpose,
        alreadyPartiallyMigrated,
        canManage,
        null,
    )

    /**
     * Constructor for screens implemented using the Settings Platform Architecture (SPA) with a
     * static route.
     */
    constructor(
        key: String,
        topLevelSettingsCategory: Category,
        spaRoutePrefix: String,
        purpose: Int,
        alreadyPartiallyMigrated: KClass<*>? = null,
        canManage: ManagementScope = OWN_USER,
    ) : this(
        key,
        topLevelSettingsCategory,
        null,
        purpose,
        alreadyPartiallyMigrated,
        canManage,
        spaRoutePrefix,
    )

    /**
     * Constructor for screens implemented using the Settings Platform Architecture (SPA) with a
     * dynamic route generated from parameters.
     */
    constructor(
        key: String,
        topLevelSettingsCategory: Category,
        purpose: Int,
        alreadyPartiallyMigrated: KClass<*>? = null,
        canManage: ManagementScope = OWN_USER,
    ) : this(
        key,
        topLevelSettingsCategory,
        null,
        purpose,
        alreadyPartiallyMigrated,
        canManage,
        null,
    )

    override fun fragmentClass(): Class<out Fragment>? {
        // If it's a valid fragment screen, return the class
        if (fragment != null) return fragment.java

        // If it's a valid SPA screen (static or dynamic), it's correct to return null.
        if (spaRoutePrefix != null || prepareSpaRoute != null) return null

        // Otherwise, the developer used the dynamic SPA constructor but didn't define parameters.
        throw IllegalStateException(
            "A screen must have a destination. It must either be a Fragment screen, or define a `spaRoutePrefix` for a static SPA screen, or use the `parameters` block to define a `prepareSpaRoute` for a dynamic SPA screen."
        )
    }

    override fun isFlagEnabled(context: Context): Boolean =
        flag?.check(FlagContext(context)) ?: super.isFlagEnabled(context)

    override fun getPreferenceHierarchy(
        context: Context,
        coroutineScope: CoroutineScope,
    ): PreferenceHierarchy =
        preferenceHierarchy(context) {
            for (preference in preferences) {
                if (preference.isFlagEnabled(context)) {
                    +preference
                }
            }
        }

    /**
     * Evaluates preconditions in order: screen-level.
     * Returns the first precondition that is not [Allowed], or [Allowed] if all preconditions
     * are met.
     */
    suspend fun evaluatePreconditions(context: Context): ApiPreconditions {
        val opContext =
            ApiOperationContext(
                context = context,
                parameters = keyParameters ?: ValidatedKeyParameters.EMPTY,
            )

        screenPreconditions?.check(opContext)?.let {
            if (it is Disallowed) {
                Log.d(
                    TAG,
                    "Screen precondition failed: ${it.getReason(context)}",
                )
            }
            if (it != Allowed) return it
        }
        return Allowed
    }

    override fun getLaunchIntent(context: Context, metadata: PreferenceMetadata?): Intent? {
        val checkScreenPreconditions =
            // TODO(b/469317113): This should run asynchronously
            runBlocking { evaluatePreconditions(context) }

        if (checkScreenPreconditions != Allowed) {
            return null
        }

        return super.getLaunchIntent(context, metadata)
    }

    var flag: FlagConfig? = null
    var parametersSchema: KeyParametersSchema? = null
    var screenPermissions: Permissions? = null
    var screenPreconditions: PreconditionsConfig? = null
    var screenTags: List<String>? = null
    var screenSensitivityLevel: @SensitivityLevel Int? = null

    override val keyParametersSchema: KeyParametersSchema?
        get() = parametersSchema

    override val keyParameters: ValidatedKeyParameters?
        get() = if (::screenParameters.isInitialized) screenParameters else super.keyParameters

    private var cachedLaunchScreenExtra: Bundle? = null

    override val launchScreenExtra: Bundle
        get() {
            // Return cached version if available
            cachedLaunchScreenExtra?.let {
                return it
            }

            val bundle = super.launchScreenExtra ?: Bundle()
            val keyParams = keyParameters ?: return bundle

            prepareScreenExtras?.invoke(keyParams, bundle)

            // Cache the result
            cachedLaunchScreenExtra = bundle
            return bundle
        }

    override val sensitivityLevel
        get() = screenSensitivityLevel ?: SensitivityLevel.NO_SENSITIVITY

    private lateinit var screenParameters: ValidatedKeyParameters
    private var prepareScreenExtras: ((ValidatedKeyParameters, Bundle) -> Unit)? = null
    private var prepareSpaRoute: ((ValidatedKeyParameters) -> String)? = null
    private var allPossibleParameters: suspend ((Context) -> Collection<ValidatedKeyParameters>) = {
        emptyList()
    }

    val preferencesPermissions = mutableListOf<String>()

    val preferences = mutableListOf<ApiPreference<*, *>>()

    /**
     * A factory function to create an instance of [ApiPreference]. This is a convenient way to
     * instantiate a preference without creating a new concrete class.
     *
     * ```
     * preference(
     *     key = "PREFERENCE_KEY",
     *     purpose = R.string.my_preference_purpose,
     *     type = AnyString,
     *     appliesTo = DEVICE
     * ) {
     *     flag { Flags.FooBarFlag() }
     *     permissions(Manifest.permission.PERMISSION)
     *     preconditions("My precondition description") { context ->
     *         if (conditionFoo(context)) {
     *             Allowed
     *         } else {
     *             HardwareUnsupported("Hardware Foo not connected")
     *         }
     *     }
     *
     *     get {
     *         permissions(Manifest.permission.PERMISSION_GET)
     *         preconditions("My get precondition description") { context ->
     *             if (conditionBar(context)) {
     *                 Allowed
     *             } else {
     *                 EnterpriseRestriction("Admin Bar restriction")
     *             }
     *         }
     *
     *         execute { context ->
     *             // Get the value
     *             Foo(context)
     *         }
     *     }
     *
     *     set {
     *         permissions(Manifest.permission.PERMISSION_SET)
     *         preconditions("My set precondition description") { context ->
     *             if (conditionFooBar(context)) {
     *                 Allowed
     *             } else {
     *                 Custom("condition FooBar not met")
     *             }
     *         }
     *         valuePreconditions("My value precondition description") { context, value ->
     *             if (conditionBarFoo(context, value)) {
     *                 Allowed
     *             } else {
     *                 Custom("Value not allowed to be set")
     *             }
     *         }
     *
     *         execute { context, value ->
     *             // Set the value
     *             Bar(context, value)
     *         }
     *     }
     * }
     * ```
     */
    protected fun <InternalType : Any, ExternalType : Any> preference(
        key: String,
        purpose: Int,
        type: ApiType<InternalType, ExternalType>,
        appliesTo: PreferenceTarget = USER(canManage = OWN_USER),
        lambda: ApiPreferenceConfigBuilder<InternalType, ExternalType>.() -> Unit,
    ) {
        val builder =
            ApiPreferenceConfigBuilder(
                key,
                purpose,
                type,
                appliesTo,
                screenPermissions,
                screenPreconditions,
                { keyParametersSchema },
                { keyParameters },
            )
        try {
            builder.lambda()
            preferences.add(builder.build())
        } catch (e: Exception) {
            Log.e(TAG, "Failed to build preference: $key for screen: $key", e)
        }
    }

    /** Initializes the [ValidatedKeyParameters] if this preference screen is parameterized. */
    fun initializeParameters(keyParameters: ValidatedKeyParameters) {
        screenParameters = keyParameters
        // Ensure the cache is cleared if parameters are re-initialized
        cachedLaunchScreenExtra = null
    }

    /**
     * Returns a [Flow] of all possible [ValidatedKeyParameters] parameters if this preference
     * screen is parameterized, otherwise returns an empty flow.
     *
     * This method provides a stream of all valid combinations of parameters that can be used to
     * instantiate this preference screen.
     *
     * @param context The application context.
     * @return A [Flow] emitting all possible [ValidatedKeyParameters].
     */
    override suspend fun getAllPossibleParameters(context: Context) =
        allPossibleParameters(context).asFlow()

    /**
     * Returns the SPA route for this screen, generating it dynamically if parameters are present.
     */
    fun getSpaRoute(): String? {
        val keyParams = keyParameters ?: return spaRoutePrefix
        return prepareSpaRoute?.invoke(keyParams) ?: spaRoutePrefix
    }

    override fun tags(context: Context): Array<String> {
        val tags = (screenTags ?: emptyList()).toMutableList()
        tags.add("api-first")

        if (tags.none { it in deviceStateTags }) {
            tags.add(APP_FUNCTION_UNCATEGORIZED)
        }
        return tags.toTypedArray()
    }

    protected fun flag(lambda: () -> Boolean) {
        if (flag != null) {
            error(getExceptionMessageMultipleDefines("flag"))
        }

        if (
            parametersSchema != null ||
                preferences.isNotEmpty() ||
                screenPermissions != null ||
                screenPreconditions != null ||
                screenTags != null ||
                screenSensitivityLevel != null
        ) {
            error(getExceptionMessageWrongOrder("flag"))
        }

        flag = FlagConfig {
            if (shouldSkipFlagCheck(context)) {
                return@FlagConfig true
            }
            lambda()
        }
    }

    /**
     * Declares the parameterization for this preference screen.
     *
     * Example:
     * ```
     * parameters {
     *     parameter(
     *         name = "package",
     *         purpose = "The app package",
     *         required = true,
     *         type = GeneratedParameterType(...)
     *     )
     *
     *     prepareScreenExtras { parameters, extras ->
     *         // Convert from the specified parameters into the extras required
     *         // to launch the screen. Look at the fragment to see what extras
     *         // it depends on to figure out how to do this
     *         extras.putString("package", parameters["package"])
     *     }
     *
     * }
     * ```
     */
    protected fun parameters(lambda: ParameterizationConfig.() -> Unit) {
        if (parametersSchema != null) {
            throw IllegalStateException(getExceptionMessageMultipleDefines("parameters"))
        }

        if (preferences.isEmpty() && screenPermissions == null && screenPreconditions == null) {
            val scope = ParameterizationConfig()
            scope.lambda()
            parametersSchema = scope.buildSchema()
            prepareScreenExtras = scope.prepareScreenExtras
            prepareSpaRoute = scope.prepareSpaRoute

            val parametersSize = scope.parameters.size
            if (parametersSize == 0) {
                throw IllegalStateException(EXCEPTION_MESSAGE_NO_PARAMETER_DEFINED)
            }
            if (parametersSize > 1) {
                throw IllegalStateException(
                    getExceptionMessageMultipleParametersDefined(parametersSize)
                )
            }

            if (spaRoutePrefix != null && prepareSpaRoute != null) {
                throw IllegalStateException(
                    "A screen cannot have both a static `spaRoutePrefix` and a dynamic `prepareSpaRoute`."
                )
            }

            // A screen with a dynamic SPA route must define how to prepare it.
            if (fragment == null && spaRoutePrefix == null && prepareSpaRoute == null) {
                throw IllegalStateException(
                    "A screen with a dynamic SPA route must define a `prepareSpaRoute`."
                )
            }

            val parameterToUse = scope.parameters.values.first()

            this@PreferencesApiScreen.allPossibleParameters = { context ->
                parameterToUse.type.getCachedOptions(context).mapNotNull { parameterOption ->
                    parameterOption.first?.let {
                        this@PreferencesApiScreen.parametersSchema!!.prepare(
                            parameterToUse.name.safe() to it
                        )
                    }
                }
            }
        } else {
            throw IllegalStateException(getExceptionMessageWrongOrder("parameters"))
        }
    }

    /**
     * Configure this screen's sensitivity level.
     *
     * The default is SensitivityLevel.NO_SENSITIVITY. The screen sensitivity will impact whether
     * this screen and its preferences will be exposed or not in the api.
     */
    protected fun sensitivityLevel(sensitivityLevel: @SensitivityLevel Int) {
        if (screenSensitivityLevel != null) {
            error(getExceptionMessageMultipleDefines("sensitivityLevel"))
        }
        if (preferences.isNotEmpty()) {
            error(getExceptionMessageWrongOrder("sensitivityLevel"))
        }
        screenSensitivityLevel = sensitivityLevel
    }

    /**
     * Configure arbitrary tags related to this screen.
     *
     * These tags are visible in the API surface for clients to identify groups of screens and
     * preferences.
     */
    protected fun tags(vararg tags: String) {
        if (screenTags != null) {
            error(getExceptionMessageMultipleDefines("tags"))
        }

        if (preferences.isNotEmpty()) {
            error(getExceptionMessageWrongOrder("tags"))
        }

        screenTags = tags.toList()
    }

    /** Declares the permissions for this preference screen. */
    protected fun permissions(permissions: Permissions) {
        if (screenPermissions != null) {
            error(getExceptionMessageMultipleDefines("permissions"))
        }

        if (preferences.isNotEmpty() || screenPreconditions != null) {
            error(getExceptionMessageWrongOrder("permissions"))
        }

        screenPermissions = permissions
    }

    /** Declares the permissions for this preference screen. */
    protected fun permissions(permission: String) {
        permissions(Permissions.allOf(permission))
    }

    /** Create a [Permissions] which requires two permissions. */
    infix fun String.and(other: String): Permissions = Permissions.allOf(this, other)

    /** Create a [Permissions] which requires either of two permissions. */
    infix fun String.or(other: String): Permissions = Permissions.anyOf(this, other)

    /** Create a [Permissions] which requires two permissions. */
    infix fun String.and(other: Permissions): Permissions = Permissions.allOf(this) and other

    /** Create a [Permissions] which requires either of two permissions. */
    infix fun String.or(other: Permissions): Permissions = Permissions.anyOf(this) or other

    protected fun preconditions(
        @StringRes description: Int,
        lambda: suspend ApiOperationContext.() -> ApiPreconditions,
    ) {
        setPreconditions(PreconditionsConfig(description, lambda))
    }

    protected fun preconditions(
        description: String,
        lambda: suspend ApiOperationContext.() -> ApiPreconditions,
    ) {
        setPreconditions(PreconditionsConfig(description, lambda))
    }

    private fun setPreconditions(config: PreconditionsConfig) {
        if (screenPreconditions != null) {
            error(getExceptionMessageMultipleDefines("preconditions"))
        }

        if (preferences.isNotEmpty()) {
            error(getExceptionMessageWrongOrder("preconditions"))
        }

        screenPreconditions = config
    }

    companion object {
        private const val TAG = "PreferencesApiScreen"

        const val PARTIALLY_MIGRATED_PREFIX = "api_"

        // Matches DeviceStateConfig.DeviceStateAppFunctionType
        const val APP_FUNCTION_UNCATEGORIZED = "getUncategorizedDeviceState"
        const val APP_FUNCTION_STORAGE = "getStorageDeviceState"
        const val APP_FUNCTION_BATTERY = "getBatteryDeviceState"
        const val APP_FUNCTION_MOBILE_DATA = "getMobileDataUsageDeviceState"
        const val APP_FUNCTION_NOTIFICATIONS = "getNotificationsDeviceState"
        const val APP_FUNCTION_APPS = "getAppsDeviceState"
        const val APP_FUNCTION_NONE = "excludedFromAppFunctions"

        private val deviceStateTags =
            setOf(
                APP_FUNCTION_UNCATEGORIZED,
                APP_FUNCTION_STORAGE,
                APP_FUNCTION_BATTERY,
                APP_FUNCTION_MOBILE_DATA,
                APP_FUNCTION_NOTIFICATIONS,
                APP_FUNCTION_APPS,
            )
    }
}
