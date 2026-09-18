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
import android.os.Process
import android.os.UserHandle
import android.provider.Settings
import android.util.Log
import androidx.annotation.StringRes
import com.android.settingslib.metadata.KeyParametersSchema
import com.android.settingslib.datastore.KeyValueStore
import com.android.settingslib.datastore.NoOpKeyedObservable
import com.android.settingslib.datastore.Permissions
import com.android.settingslib.datastore.and
import com.android.settingslib.datastore.or
import com.android.settingslib.metadata.PersistentPreference
import com.android.settingslib.metadata.SensitivityLevel
import com.android.settingslib.metadata.ReadWritePermit
import com.android.settingslib.metadata.ValidatedKeyParameters
import com.android.settingslib.metadata.preferencesapi.Utils.getExceptionMessageAlreadyDefined
import com.android.settingslib.metadata.preferencesapi.Utils.getExceptionMessageMultipleDefines
import com.android.settingslib.metadata.preferencesapi.Utils.getExceptionMessageWrongOrder
import com.android.settingslib.metadata.preferencesapi.multiusers.PreferenceTarget
import com.android.settingslib.metadata.preferencesapi.preconditions.Allowed
import com.android.settingslib.metadata.preferencesapi.preconditions.ApiPreconditions
import com.android.settingslib.metadata.preferencesapi.preconditions.Disallowed
import com.android.settingslib.metadata.preferencesapi.types.ApiType
import com.android.settingslib.metadata.preferencesapi.types.CustomEnum
import com.android.settingslib.metadata.preferencesapi.types.EnumApi
import com.android.settingslib.utils.applications.AppUtils
import kotlinx.coroutines.runBlocking

/**
 * The context for an API-first operation, providing access to the application [Context] and
 * any necessary environmental information for executing preference
 * operations, ensuring that each operation has a consistent and secure context to run in.
 *
 * @property context The application context, used for accessing system services and resources.
 * @property userId The user id of the user to which the preference belongs.
 * @property userHandle The user handle of the user to which the preference belongs.
 * @property parameters A map of validated key-value pairs that can be used to parameterize
 *   the behavior of the preference. These parameters are derived from the preference screen's
 *   configuration and are guaranteed to be valid.
 */
class ApiOperationContext(
    val context: Context,
    val userId: Int = Process.myUserHandle().hashCode(),
    val userHandle: UserHandle = Process.myUserHandle(),
    val parameters: ValidatedKeyParameters
)

/**
 * The context for a flag check, providing access to the application [Context].
 *
 * @property context The application context, used for accessing system services and resources.
 */
class FlagContext(val context: Context)

/** Configuration of the [ApiPreference] flag. */
class FlagConfig(val check: FlagContext.() -> Boolean)

/** Configuration of the [ApiPreference] preconditions. */
class PreconditionsConfig private constructor(
    @StringRes val descriptionRes: Int?,
    val description: String?,
    val check: suspend ApiOperationContext.() -> ApiPreconditions,
) {
    init {
        require(descriptionRes != null || description != null)
    }

    constructor(
        @StringRes description: Int,
        check: suspend ApiOperationContext.() -> ApiPreconditions
    ) : this(descriptionRes = description, description = null, check = check)

    constructor(
        description: String,
        check: suspend ApiOperationContext.() -> ApiPreconditions
    ) : this(descriptionRes = null, description = description, check = check)

    /** Get the description as a string using the provided context. */
    fun getDescription(context: Context): String =
        resolveString(context, descriptionRes, description)
}

/** Configuration of the [ApiPreference] get. */
class GetConfig<V : Any>(
    val permissions: Permissions? = null,
    val preconditions: PreconditionsConfig? = null,
    val execute: suspend ApiOperationContext.() -> V
)

/** Configuration of the [ApiPreference] value preconditions. */
class ValuePreconditionsConfig<V : Any> private constructor(
    @StringRes val descriptionRes: Int?,
    val description: String?,
    val check: suspend (ApiOperationContext.(V) -> ApiPreconditions),
) {
    init {
        require(descriptionRes != null || description != null)
    }

    constructor(
        @StringRes description: Int,
        check: suspend (ApiOperationContext.(V) -> ApiPreconditions),
    ) : this(descriptionRes = description, description = null, check = check)

    constructor(
        description: String,
        check: suspend (ApiOperationContext.(V) -> ApiPreconditions),
    ) : this(descriptionRes = null, description = description, check = check)

    /** Get the description as a string using the provided context. */
    fun getDescription(context: Context): String =
        resolveString(context, descriptionRes, description)
}

/**
 * Configuration of the [ApiPreference] set operation's warning. If preconditions are defined,
 * warning is triggered based on them.
 */
class WarningConfig<V : Any> private constructor(
    @StringRes val warningRes: Int?,
    val warning: String?,
    val preconditions: PreconditionsConfig?,
    val valuePreconditions: ValuePreconditionsConfig<V>?,
) {
    init {
        require(warningRes != null || warning != null)
    }

    constructor(
        @StringRes warning: Int,
        preconditions: PreconditionsConfig? = null,
        valuePreconditions: ValuePreconditionsConfig<V>? = null,
    ) : this(
        warningRes = warning,
        warning = null,
        preconditions = preconditions,
        valuePreconditions = valuePreconditions
    )

    constructor(
        warning: String,
        preconditions: PreconditionsConfig? = null,
        valuePreconditions: ValuePreconditionsConfig<V>? = null,
    ) : this(
        warningRes = null,
        warning = warning,
        preconditions = preconditions,
        valuePreconditions = valuePreconditions
    )

    /** Get the warning message as a string using the provided context. */
    fun getWarning(context: Context): String = resolveString(context, warningRes, warning)
}

/** Configuration of the [ApiPreference] set. */
class SetConfig<V : Any>(
    val permissions: Permissions? = null,
    val preconditions: PreconditionsConfig? = null,
    val valuePreconditions: ValuePreconditionsConfig<V>? = null,
    val warning: WarningConfig<V>? = null,
    val execute: (suspend ApiOperationContext.(V) -> Unit)
)

/**
 * A preference abstraction to describe the ability of getting and (optionally) setting a value
 * specific to that preference, without relying on binding to an actual UI widget. This class is
 * produced when an Engineer in a partner team migrates their preference to Catalyst using the 2026
 * "Lightweight" way. It sets suitable defaults, and converts between the code we are asking
 * partner teams to write and the methods which Catalyst expects.
 *
 * @property flagConfig Flag configuration for the preference.
 * @property appliesTo The [PreferenceTarget] to which the preference applies.
 */
abstract class ApiPreference<InternalType: Any, ExternalType : Any>(
    val flagConfig: FlagConfig?,
    val appliesTo: PreferenceTarget
) : PersistentPreference<ExternalType> {
    companion object {
        private const val TAG = "ApiPreference"

        private const val VALUE_TYPE_MISMATCH_ERROR = "Value type mismatch. Expected %s, got %s"

        private fun buildValueTypeMismatchError(expected: Class<*>, actual: Class<*>) =
            String.format(VALUE_TYPE_MISMATCH_ERROR, expected.name, actual.name)
    }

    /**
     * Returns true if the flag is enabled so the preference should be shown in
     * API outputs and UI.
     */
    fun isFlagEnabled(context: Context) = flagConfig?.check(FlagContext(context)) ?: true

    private val cachedKeyParameters: ValidatedKeyParameters by lazy {
        getScreenParameters.invoke() ?: ValidatedKeyParameters.EMPTY
    }

    /**
     * Builds the final [Permissions] object by combining screen-level, common, and
     * operation-specific permissions.
     */
    private fun buildPermissions(operationPermissions: Permissions?): Permissions {
        var permissions = Permissions.EMPTY
        screenPermissions?.let { permissions = permissions and it }
        this.permissions?.let { permissions = permissions and it }
        operationPermissions?.let { permissions = permissions and it }
        return permissions
    }

    /**
     * Creates and returns an [ApiOperationContext] for the current preference.
     *
     * This method encapsulates the creation of the operation context, ensuring that it is
     * consistently initialized with the necessary application [Context] and validated key
     * parameters from the preference screen. If no screen parameters are available, it defaults
     * to an empty set of parameters.
     *
     * @param context The application context to be used in the operation context.
     * @return An initialized [ApiOperationContext] instance.
     */
    fun getApiOperationContext(context: Context) =
        ApiOperationContext(context = context, parameters = cachedKeyParameters)

    /**
     * Evaluates preconditions in order: screen-level, common, and operation-specific.
     * Returns the first precondition that is not [Allowed], or [Allowed] if all preconditions
     * are met.
     */
    suspend fun evaluatePreconditions(
        context: Context,
        operationPreconditions: PreconditionsConfig?
    ): ApiPreconditions {
        val operationContext: ApiOperationContext by lazy { getApiOperationContext(context) }

        screenPreconditions?.check(operationContext)?.let {
            if (it is Disallowed) {
                Log.d(TAG, "Screen precondition failed: ${it.getReason(context)}")
            }

            if (it != Allowed) return it
        }

        preconditions?.check(operationContext)?.let {
            if (it is Disallowed) {
                Log.d(TAG, "Preference precondition failed: ${it.getReason(context)}")
            }

            if (it != Allowed) return it
        }

        operationPreconditions?.check(operationContext)?.let {
            if (it is Disallowed) {
                Log.d(TAG, "Operation precondition failed: ${it.getReason(context)}")
            }

            if (it != Allowed) return it
        }

        return Allowed
    }

    override fun getReadPermissions(context: Context) = buildPermissions(get.permissions)
    override fun getWritePermissions(context: Context) = buildPermissions(set?.permissions)

    override fun getReadPermit(context: Context, callingPid: Int, callingUid: Int) =
        // TODO(b/469317113): This should run asynchronously
        runBlocking {
            when (evaluatePreconditions(context, get.preconditions)) {
                Allowed -> ReadWritePermit.ALLOW
                else -> ReadWritePermit.DISALLOW
            }
        }

    override fun getWritePermit(context: Context, callingPid: Int, callingUid: Int) =
        // TODO(b/469317113): This should run asynchronously
        runBlocking {
            if (!supportsWrite) {
                return@runBlocking ReadWritePermit.DISALLOW
            }
            when (evaluatePreconditions(context, set?.preconditions)) {
                Allowed -> ReadWritePermit.ALLOW
                else -> ReadWritePermit.DISALLOW
            }
        }

    override fun storage(context: Context): KeyValueStore =
        object : NoOpKeyedObservable<String>(), KeyValueStore {
            private val operationContext: ApiOperationContext by lazy {
                getApiOperationContext(context)
            }

            override fun contains(storeKey: String): Boolean = storeKey == key

            override fun <T : Any> getValue(storeKey: String, valueType: Class<T>): T? =
                // TODO(b/469317113): This should run asynchronously
                runBlocking {
                    when {
                        storeKey == key -> {
                            get.execute(operationContext) as T?
                        }

                        else -> null
                    }
                }

            override fun <T : Any> setValue(storeKey: String, valueType: Class<T>, value: T?) =
                // TODO(b/469317113): This should run asynchronously
                runBlocking {
                    // Catalyst's KeyValueStore is designed to handle arbitrary key/value pairs.
                    // However, the API-first approach dictates that each [ApiPreference] instance
                    // is responsible for a single, specific key. Thus, ignoring calls for other keys.
                    if (storeKey != key) {
                        return@runBlocking
                    }

                    // If value type is not of the preference valueType (V), throw an exception
                    // Normalize expected and actual types to their object wrapper equivalents to
                    // prevent primitive/boxed type mismatches.
                    if (value != null && this@ApiPreference.valueType.kotlin.javaObjectType != value.javaClass.kotlin.javaObjectType) {
                        throw IllegalArgumentException(
                            buildValueTypeMismatchError(
                                this@ApiPreference.valueType,
                                value.javaClass
                            )
                        )
                    }

                    // This cast is safe because we already checked the `value` is of type `V`
                    val valueV = value as ExternalType
                    val valuePreconditionsCheck =
                        set?.valuePreconditions?.check?.invoke(operationContext, valueV) ?: Allowed
                    when (valuePreconditionsCheck) {
                        Allowed -> set?.execute(operationContext, valueV)
                        is Disallowed -> error(
                            valuePreconditionsCheck.getReason(operationContext.context)
                        )
                    }
                }
        }

    /**
     *  A function that returns the validated key-value parameters from the preference screen this
     *  preference belongs to.
     *
     *  Added as a function because initializeParameters runs after the preferences are built
     *  in init block.
     */
    abstract val getScreenParameters: () -> ValidatedKeyParameters?

    /**
     * A function that returns the key parameters schema for the preference.
    */
    abstract val getParametersSchema: () -> KeyParametersSchema?

    /**
     * A function that returns the validated key parameters for the preference.
     */
    abstract val getParameters: () -> ValidatedKeyParameters?

    /** Preference's permission on the screen level. */
    abstract val screenPermissions: Permissions?

    /** Preference's preconditions on the screen level. */
    abstract val screenPreconditions: PreconditionsConfig?

    /** Preference's permission. */
    abstract val permissions: Permissions?

    /** Preference's preconditions. */
    abstract val preconditions: PreconditionsConfig?

    /** Get block with logic for retrieving the preference's value. */
    abstract val get: GetConfig<ExternalType>

    /** Set block with logic for changing a preference's value. */
    abstract val set: SetConfig<ExternalType>?

    /** The type of this preference. This defines both the raw type (e.g. String, Int) and also
     * which options are acceptable, both programmatically and in a human-readable way. */
    abstract val type: ApiType<InternalType, ExternalType>
}

/**
 * Warning configuration builder for an [ApiPreference] set operation. The warning message can be
 * triggered in two ways:
 * * always triggered if using just a warning message;
 * * conditionally triggered by using preconditions or value preconditions before the warning
 * message.
 *
 * ```
 * warning {
 *     warn("Foo warning")
 * }
 * ```
 *
 * or
 *
 * ```
 * warning {
 *     preconditions("Foo description") { ... }
 *     warn("Bar warning")
 * }
 * ```
 *
 * or
 *
 * ```
 * warning {
 *     valuePreconditions("Foo description") { value -> ... }
 *     warn("Bar warning")
 * }
 * ```
 */
@ApiPreferenceDsl
class WarningConfigBuilder<V : Any> {
    private var preconditionsConfig: PreconditionsConfig? = null
    private var valuePreconditionsConfig: ValuePreconditionsConfig<V>? = null
    private var warning: String? = null
    private var warningRes: Int? = null

    /**
     * Defines a precondition check that will trigger the warning in case of [Allowed], with a
     * string resource description.
     */
    fun preconditions(
        @StringRes description: Int,
        lambda: suspend ApiOperationContext.() -> ApiPreconditions
    ) {
        setPreconditions(PreconditionsConfig(description, lambda))
    }

    /**
     * Defines a precondition check that will trigger the warning in case of [Allowed], with a
     * string description.
     */
    fun preconditions(
        description: String,
        lambda: suspend ApiOperationContext.() -> ApiPreconditions
    ) {
        setPreconditions(PreconditionsConfig(description, lambda))
    }

    private fun setPreconditions(config: PreconditionsConfig) {
        if (preconditionsConfig != null || valuePreconditionsConfig != null) {
            error(getExceptionMessageAlreadyDefined("preconditions or valuePreconditions"))
        }

        if (warning != null || warningRes != null) {
            error(getExceptionMessageWrongOrder("preconditions"))
        }

        preconditionsConfig = config
    }

    /**
     * Defines a precondition check that will trigger the warning in case of [Allowed], with a
     * string resource description.
     */
    fun valuePreconditions(
        @StringRes description: Int,
        lambda: suspend ApiOperationContext.(V) -> ApiPreconditions
    ) {
        setValuePreconditions(ValuePreconditionsConfig(description, lambda))
    }

    /**
     * Defines a precondition check that will trigger the warning in case of [Allowed], with a
     * string description.
     */
    fun valuePreconditions(
        description: String,
        lambda: suspend ApiOperationContext.(V) -> ApiPreconditions
    ) {
        setValuePreconditions(ValuePreconditionsConfig(description, lambda))
    }

    private fun setValuePreconditions(config: ValuePreconditionsConfig<V>) {
        if (preconditionsConfig != null || valuePreconditionsConfig != null) {
            error(getExceptionMessageAlreadyDefined("preconditions or valuePreconditions"))
        }

        if (warning != null || warningRes != null) {
            error(getExceptionMessageWrongOrder("valuePreconditions"))
        }

        valuePreconditionsConfig = config
    }

    /**
     * Sets the warning message as a string resource to display when triggered before setting a
     * value.
     */
    fun warn(@StringRes message: Int) {
        if (warning != null || warningRes != null) {
            error(getExceptionMessageMultipleDefines("warn"))
        }
        warningRes = message
    }

    /** Sets the warning message as a string to display when triggered before setting a value. */
    fun warn(message: String) {
        if (warning != null || warningRes != null) {
            error(getExceptionMessageMultipleDefines("warn"))
        }
        warning = message
    }

    internal fun build(): WarningConfig<V> {
        return when {
            warning != null ->
                WarningConfig(warning = warning!!, preconditions = preconditionsConfig, valuePreconditions = valuePreconditionsConfig)

            warningRes != null ->
                WarningConfig(warning = warningRes!!, preconditions = preconditionsConfig, valuePreconditions = valuePreconditionsConfig)

            else -> error("warning 'warn' block is required")
        }
    }
}

@DslMarker
internal annotation class ApiPreferenceDsl

/**
 * Get configuration builder for an [ApiPreference].
 *
 * ```
 * get {
 *     execute { context ->
 *         // Get the value
 *         Foo(context)
 *     }
 * }
 * ```
 */
@ApiPreferenceDsl
class GetConfigBuilder<InternalType: Any, ExternalType : Any>(private val type: ApiType<InternalType, ExternalType>) {
    private var permissionsConfig: Permissions? = null
    private var preconditionsConfig: PreconditionsConfig? = null
    private var executeBlock: (suspend ApiOperationContext.() -> ExternalType)? = null

    /** Sets permissions for the get. */
    fun permissions(permissions: Permissions) {
        if (permissionsConfig != null) {
            error(getExceptionMessageMultipleDefines("permissions"))
        }

        if (preconditionsConfig != null || executeBlock != null) {
            error(getExceptionMessageWrongOrder("permissions"))
        }

        permissionsConfig = permissions
    }

    /** Sets permissions for the get. */
    fun permissions(permission: String) {
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

    /**
     * Defines a precondition check that must pass for the get to be executed, with a string
     * resource description.
     */
    fun preconditions(
        @StringRes description: Int,
        lambda: suspend ApiOperationContext.() -> ApiPreconditions
    ) {
        setPreconditions(PreconditionsConfig(description, lambda))
    }

    /**
     * Defines a precondition check that must pass for the get to be executed, with a string
     * description.
     */
    fun preconditions(
        description: String,
        lambda: suspend ApiOperationContext.() -> ApiPreconditions
    ) {
        setPreconditions(PreconditionsConfig(description, lambda))
    }

    private fun setPreconditions(config: PreconditionsConfig) {
        if (preconditionsConfig != null) {
            error(getExceptionMessageMultipleDefines("preconditions"))
        }

        if (executeBlock != null) {
            error(getExceptionMessageWrongOrder("preconditions"))
        }

        preconditionsConfig = config
    }

    /** Declare the execute block of the get. */
    fun execute(lambda: suspend ApiOperationContext.() -> InternalType) {
        if (executeBlock != null) {
            error(getExceptionMessageMultipleDefines("execute"))
        }

        executeBlock = {
            type.convertInternalToExternal(lambda())
        }
    }

    internal fun build(): GetConfig<ExternalType> {
        return GetConfig(
            permissions = permissionsConfig,
            preconditions = preconditionsConfig,
            execute = executeBlock ?: error("get 'execute' block is required")
        )
    }
}

/**
 * Set configuration builder for an [ApiPreference].
 *
 * ```
 * set {
 *     execute { context, value ->
 *         // Set the value
 *         Bar(context, value)
 *     }
 * }
 * ```
 */
@ApiPreferenceDsl
class SetConfigBuilder<InternalType: Any, ExternalType : Any>(private val type: ApiType<InternalType, ExternalType>) {
    private var permissionsConfig: Permissions? = null
    private var preconditionsConfig: PreconditionsConfig? = null
    private var valuePreconditionsConfig: ValuePreconditionsConfig<ExternalType>? = null
    private var warningConfig: WarningConfig<ExternalType>? = null
    private var executeBlock: (suspend ApiOperationContext.(ExternalType) -> Unit)? = null

    /** Sets permissions for the set. */
    fun permissions(permissions: Permissions) {
        if (permissionsConfig != null) {
            error(getExceptionMessageMultipleDefines("permissions"))
        }

        if (preconditionsConfig != null || valuePreconditionsConfig != null || warningConfig != null || executeBlock != null) {
            error(getExceptionMessageWrongOrder("permissions"))
        }

        permissionsConfig = permissions
    }

    /** Sets permissions for the set. */
    fun permissions(permission: String) {
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

    /**
     * Defines a precondition check that must pass for the set to be executed, with a string
     * resource description.
     */
    fun preconditions(
        @StringRes description: Int,
        lambda: ApiOperationContext.() -> ApiPreconditions
    ) {
        setPreconditions(PreconditionsConfig(description, lambda))
    }

    /**
     * Defines a precondition check that must pass for the set to be executed, with a string
     * description.
     */
    fun preconditions(description: String, lambda: ApiOperationContext.() -> ApiPreconditions) {
        setPreconditions(PreconditionsConfig(description, lambda))
    }

    private fun setPreconditions(config: PreconditionsConfig) {
        if (preconditionsConfig != null) {
            error(getExceptionMessageMultipleDefines("preconditions"))
        }

        if (valuePreconditionsConfig != null || warningConfig != null || executeBlock != null) {
            error(getExceptionMessageWrongOrder("preconditions"))
        }

        preconditionsConfig = config
    }

    /**
     * Defines a value precondition check that must pass for the set to be executed, with a string
     * resource description.
     */
    fun valuePreconditions(
        @StringRes description: Int,
        lambda: suspend ApiOperationContext.(ExternalType) -> ApiPreconditions
    ) {
        setValuePreconditions(ValuePreconditionsConfig(description, lambda))
    }

    /**
     * Defines a value precondition check that must pass for the set to be executed, with a string
     * description.
     */
    fun valuePreconditions(
        description: String,
        lambda: suspend ApiOperationContext.(ExternalType) -> ApiPreconditions
    ) {
        setValuePreconditions(ValuePreconditionsConfig(description, lambda))
    }

    private fun setValuePreconditions(config: ValuePreconditionsConfig<ExternalType>) {
        if (valuePreconditionsConfig != null) {
            error(getExceptionMessageMultipleDefines("valuePreconditions"))
        }

        if (warningConfig != null || executeBlock != null) {
            error(getExceptionMessageWrongOrder("valuePreconditions"))
        }

        valuePreconditionsConfig = config
    }

    /** Defines a warning to be triggered before setting the value. */
    fun warning(lambda: WarningConfigBuilder<ExternalType>.() -> Unit) {
        if (warningConfig != null) {
            error(getExceptionMessageMultipleDefines("warning"))
        }

        if (executeBlock != null) {
            error(getExceptionMessageWrongOrder("warning"))
        }

        val builder = WarningConfigBuilder<ExternalType>()
        builder.lambda()
        warningConfig = builder.build()
    }

    /** Declare the execute block of the set. */
    fun execute(lambda: suspend ApiOperationContext.(InternalType) -> Unit) {
        if (executeBlock != null) {
            error(getExceptionMessageMultipleDefines("execute"))
        }

        executeBlock = { value -> lambda(type.convertExternalToInternal(value)) }
    }

    internal fun build(): SetConfig<ExternalType> {
        return SetConfig(
            permissions = permissionsConfig,
            preconditions = preconditionsConfig,
            valuePreconditions = valuePreconditionsConfig,
            warning = warningConfig,
            execute = executeBlock ?: error("Set 'execute' block is required")
        )
    }
}

/**
 * Returns true if all flag checks should be skipped for catalyst preferences.
 *
 * This allows the flag checks to be skipped for testing purposes.
 *
 * This should never be used in production.
 */
fun shouldSkipFlagCheck(context: Context): Boolean {
    return AppUtils.isDebuggable() && Settings.Global.getInt(
        context.contentResolver,
        "com.android.settings.SKIP_CATALYST_FLAG_CHECKS",
        0
    ) == 1
}

/** Configuration builder for an [ApiPreference]. */
@ApiPreferenceDsl
class ApiPreferenceConfigBuilder<InternalType : Any, ExternalType : Any>(
    val key: String,
    @StringRes val purpose: Int,
    val type: ApiType<InternalType, ExternalType>,
    val appliesTo: PreferenceTarget,
    val screenPermissions: Permissions?,
    val screenPreconditions: PreconditionsConfig?,
    val getScreenParameterSchema: () -> KeyParametersSchema?,
    val getScreenParameters: () -> ValidatedKeyParameters?
) {
    private var flagConfig: FlagConfig? = null
    private var sensitivityLevelValue: Int? = null
    private var permissionsConfig: Permissions? = null
    private var preconditionsConfig: PreconditionsConfig? = null
    private var tagsList: List<String>? = null
    private var getConfig: GetConfig<ExternalType>? = null
    private var setConfig: SetConfig<ExternalType>? = null
    private val valueType: Class<ExternalType> = type.getType()

    /**
     * Build the [FlagConfig] from the given block.
     */
    fun flag(lambda: () -> Boolean) {
        if (flagConfig != null) {
            error(getExceptionMessageMultipleDefines("flag"))
        }

        if (permissionsConfig != null || preconditionsConfig != null || tagsList != null || getConfig != null || setConfig != null) {
            error(getExceptionMessageWrongOrder("flag"))
        }

        flagConfig = FlagConfig {
            if (shouldSkipFlagCheck(context)) {
                return@FlagConfig true
            }
            lambda()
        }
    }

    /**
     * Sets the sensitivity level for this preference.
     */
    fun sensitivityLevel(level: @SensitivityLevel Int) {
        if (sensitivityLevelValue != null) {
            error(getExceptionMessageMultipleDefines("sensitivityLevel"))
        }

        sensitivityLevelValue = level
    }

    /**
     * Sets tags.
     */
    fun tags(vararg tags: String) {
        if (tagsList != null) {
            error(getExceptionMessageMultipleDefines("tags"))
        }

        if (getConfig != null || setConfig != null) {
            error(getExceptionMessageWrongOrder("tags"))
        }

        tagsList = tags.toList()
    }

    /**
     * Build the [Permissions] from the given permissions.
     */
    fun permissions(permissions: Permissions) {
        if (permissionsConfig != null) {
            error(getExceptionMessageMultipleDefines("permissions"))
        }

        if (preconditionsConfig != null || getConfig != null || setConfig != null) {
            error(getExceptionMessageWrongOrder("permissions"))
        }

        permissionsConfig = permissions
    }

    /** Sets permissions. */
    fun permissions(permission: String) {
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

    /**
     * Build the [PreconditionsConfig] from the given block, with a string resource description.
     */
    fun preconditions(
        @StringRes description: Int,
        lambda: ApiOperationContext.() -> ApiPreconditions
    ) {
        setPreconditions(PreconditionsConfig(description = description, check = lambda))
    }

    /**
     * Build the [PreconditionsConfig] from the given block, with a string description.
     */
    fun preconditions(description: String, lambda: ApiOperationContext.() -> ApiPreconditions) {
        setPreconditions(PreconditionsConfig(description = description, check = lambda))
    }

    private fun setPreconditions(config: PreconditionsConfig) {
        if (preconditionsConfig != null) {
            error(getExceptionMessageMultipleDefines("preconditions"))
        }

        if (getConfig != null || setConfig != null) {
            error(getExceptionMessageWrongOrder("preconditions"))
        }

        preconditionsConfig = config
    }

    /**
     * Build the [GetConfig] from the given [GetConfigBuilder] block.
     */
    fun get(lambda: GetConfigBuilder<InternalType, ExternalType>.() -> Unit) {
        if (getConfig != null) {
            error(getExceptionMessageMultipleDefines("get"))
        }

        if (setConfig != null) {
            error(getExceptionMessageWrongOrder("get"))
        }

        val builder = GetConfigBuilder<InternalType, ExternalType>(type)
        builder.lambda()
        getConfig = builder.build()
    }

    /**
     * Build the [SetConfig] from the given [SetConfigBuilder] block.
     */
    fun set(lambda: SetConfigBuilder<InternalType, ExternalType>.() -> Unit) {
        if (setConfig != null) {
            error(getExceptionMessageMultipleDefines("set"))
        }

        val builder = SetConfigBuilder<InternalType, ExternalType>(type)
        builder.lambda()
        setConfig = builder.build()
    }

    /** Create an instance of [ApiPreference] from its configuration. */
    fun build() = object : ApiPreference<InternalType, ExternalType>(flagConfig, appliesTo) {
        override val sensitivityLevel: Int =
            this@ApiPreferenceConfigBuilder.sensitivityLevelValue ?: super.sensitivityLevel
        override val screenPermissions = this@ApiPreferenceConfigBuilder.screenPermissions
        override val screenPreconditions = this@ApiPreferenceConfigBuilder.screenPreconditions
        override val permissions: Permissions? = permissionsConfig
        override val preconditions: PreconditionsConfig? = preconditionsConfig
        override fun tags(context: Context): Array<String> =
            (tagsList?.toTypedArray() ?: emptyArray()) + "api-first"

        override val get: GetConfig<ExternalType> = getConfig ?: error("'get' block is required")
        override val set: SetConfig<ExternalType>? = setConfig
        override val supportsWrite: Boolean = setConfig != null
        override val type: ApiType<InternalType, ExternalType> = this@ApiPreferenceConfigBuilder.type
        override val valueType: Class<ExternalType> = this@ApiPreferenceConfigBuilder.valueType
        override val key: String = this@ApiPreferenceConfigBuilder.key
        override val purpose: Int = this@ApiPreferenceConfigBuilder.purpose
        override val getScreenParameters: () -> ValidatedKeyParameters? =
            this@ApiPreferenceConfigBuilder.getScreenParameters
        override val getParametersSchema: () -> KeyParametersSchema? = this@ApiPreferenceConfigBuilder.getScreenParameterSchema
        override val getParameters: () -> ValidatedKeyParameters? = this@ApiPreferenceConfigBuilder.getScreenParameters
    }
}