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

package com.android.settingslib.graph

import android.app.Application
import android.content.Context
import android.os.Bundle
import android.os.SystemClock
import androidx.annotation.IntDef
import com.android.settingslib.graph.proto.PreferenceValueProto
import com.android.settingslib.ipc.ApiDescriptor
import com.android.settingslib.ipc.ApiHandler
import com.android.settingslib.ipc.ApiPermissionChecker
import com.android.settingslib.ipc.IntMessageCodec
import com.android.settingslib.ipc.MessageCodec
import com.android.settingslib.metadata.CatalystFlagProviderFactory
import com.android.settingslib.metadata.IntRangeValuePreference
import com.android.settingslib.metadata.PersistentPreference
import com.android.settingslib.metadata.PreferenceAvailabilityProvider
import com.android.settingslib.metadata.PreferenceCoordinate
import com.android.settingslib.metadata.PreferenceMetadata
import com.android.settingslib.metadata.PreferenceRemoteOpMetricsLogger
import com.android.settingslib.metadata.PreferenceRestrictionProvider
import com.android.settingslib.metadata.PreferenceScreenRegistry
import com.android.settingslib.metadata.ReadWritePermit
import com.android.settingslib.metadata.KeyParameters
import com.android.settingslib.metadata.PreferenceScreenMetadata import com.android.settingslib.metadata.isExposable
import com.android.settingslib.metadata.preferencesapi.ApiPreference
import com.android.settingslib.metadata.preferencesapi.preconditions.Disallowed
import com.android.settingslib.metadata.toMap
import com.android.settingslib.metadata.usePreferenceHierarchyScope
import kotlinx.coroutines.runBlocking

/** Request to set preference value. */
class PreferenceSetterRequest : PreferenceCoordinate {
    val value: PreferenceValueProto

    @Deprecated("This constructor will be removed once the catalyst framework stops passing the arguments as a bundle. Use the other constructor instead.")
    constructor(
        screenKey: String,
        args: Bundle?,
        key: String,
        value: PreferenceValueProto,
    ) : super(screenKey, args, key) {
        this.value = value
    }

    constructor(
        screenKey: String,
        keyParameters: KeyParameters?,
        key: String,
        value: PreferenceValueProto,
    ) : super(screenKey, keyParameters, key) {
        this.value = value
    }
}

/** Result of preference setter request. */
@IntDef(
    PreferenceSetterResult.OK,
    PreferenceSetterResult.UNSUPPORTED,
    PreferenceSetterResult.DISABLED,
    PreferenceSetterResult.RESTRICTED,
    PreferenceSetterResult.UNAVAILABLE,
    PreferenceSetterResult.REQUIRE_APP_PERMISSION,
    PreferenceSetterResult.REQUIRE_USER_AGREEMENT,
    PreferenceSetterResult.DISALLOW,
    PreferenceSetterResult.INVALID_REQUEST,
    PreferenceSetterResult.INTERNAL_ERROR,
)
@Retention(AnnotationRetention.SOURCE)
annotation class PreferenceSetterResult {
    companion object {
        /** Set preference value successfully. */
        const val OK = 0
        /** Set preference value is unsupported on the preference. */
        const val UNSUPPORTED = 1
        /** Preference is disabled and cannot set preference value. */
        const val DISABLED = 2
        /** Preference is restricted by managed configuration and cannot set preference value. */
        const val RESTRICTED = 3
        /** Preference is unavailable and cannot set preference value. */
        const val UNAVAILABLE = 4
        /** Require (runtime/special) app permission from user explicitly. */
        const val REQUIRE_APP_PERMISSION = 5
        /** Require explicit user agreement (e.g. terms of service). */
        const val REQUIRE_USER_AGREEMENT = 6
        /** Disallow to set preference value (e.g. uid not allowed). */
        const val DISALLOW = 7
        /** Request is invalid. */
        const val INVALID_REQUEST = 8
        /** Internal error happened when persist preference value. */
        const val INTERNAL_ERROR = 9
    }
}

/** Response of the setter API. */
class PreferenceSetterResponse(
    var failureReason: String? = null,
    @PreferenceSetterResult var errorCode: Int
)

/** Preference setter API descriptor. */
class PreferenceSetterApiDescriptor(override val id: Int) :
    ApiDescriptor<PreferenceSetterRequest, Int> {

    override val requestCodec = PreferenceSetterRequestCodec()

    override val responseCodec = IntMessageCodec()
}

/** Preference setter API implementation. */
class PreferenceSetterApiHandler(
    override val id: Int,
    private val permissionChecker: ApiPermissionChecker<PreferenceSetterRequest>,
    private val metricsLogger: PreferenceRemoteOpMetricsLogger? = null,
) : ApiHandler<PreferenceSetterRequest, PreferenceSetterResponse> {

    override fun hasPermission(
        application: Application,
        callingPid: Int,
        callingUid: Int,
        request: PreferenceSetterRequest,
    ) = permissionChecker.hasPermission(application, callingPid, callingUid, request)

    override suspend fun invoke(
        application: Application,
        callingPid: Int,
        callingUid: Int,
        request: PreferenceSetterRequest,
    ): PreferenceSetterResponse {
        val elapsedRealtime = SystemClock.elapsedRealtime()
        fun notFound(): PreferenceSetterResponse {
            metricsLogger?.logSetterApi(
                application,
                callingUid,
                request,
                null,
                null,
                PreferenceSetterResult.UNSUPPORTED,
                SystemClock.elapsedRealtime() - elapsedRealtime,
            )
            return PreferenceSetterResult.UNSUPPORTED.toPreferenceSetterResponse()
        }
        val screenMetadata =
            PreferenceScreenRegistry.create(application, request) ?: return notFound()
        if(!screenMetadata.isExposable(application)) return notFound()
        val key = request.key
        val metadata =
            usePreferenceHierarchyScope {
                screenMetadata.getPreferenceHierarchy(application, this).findAsync(key)
            } ?: return notFound()

        fun <T> PreferenceMetadata.checkWritePermit(value: T): PreferenceSetterResponse {
            @Suppress("UNCHECKED_CAST") val preference = (this as PersistentPreference<T>)
            return when (preference.evalWritePermit(application, value, callingPid, callingUid)) {
                ReadWritePermit.ALLOW -> PreferenceSetterResult.OK.toPreferenceSetterResponse()
                ReadWritePermit.DISALLOW -> {
                    val preconditions = if (preference is ApiPreference<* ,*>) {
                        runBlocking {
                            preference.evaluatePreconditions(
                                application,
                                preference.set?.preconditions
                            )
                        }
                    } else {
                        null
                    }

                    PreferenceSetterResult.DISALLOW.toPreferenceSetterResponse(
                        failureReason = if (preconditions is Disallowed) {
                            preconditions.getReason(application)
                        } else if (!isEnabled(application)) {
                            getEnabledDescription()
                        } else if (this is PreferenceAvailabilityProvider) {
                            if (!isAvailable(application)) {
                                availabilityDescription
                            } else {
                                null
                            }
                        } else {
                            null
                        }
                    )
                }
                ReadWritePermit.REQUIRE_APP_PERMISSION ->
                    PreferenceSetterResult.REQUIRE_APP_PERMISSION.toPreferenceSetterResponse()
                ReadWritePermit.REQUIRE_USER_AGREEMENT ->
                    PreferenceSetterResult.REQUIRE_USER_AGREEMENT.toPreferenceSetterResponse()
                else -> PreferenceSetterResult.INTERNAL_ERROR.toPreferenceSetterResponse()
            }
        }

        fun invoke(): PreferenceSetterResponse {
            if (!metadata.isExposable(application) || metadata is PreferenceScreenMetadata)
                return notFound()
             if (metadata !is PersistentPreference<*>) return PreferenceSetterResult.UNSUPPORTED.toPreferenceSetterResponse()
            if (!metadata.isEnabled(application)) return PreferenceSetterResult.DISABLED.toPreferenceSetterResponse(
                failureReason = metadata.getEnabledDescription()
            )
            if (metadata is PreferenceRestrictionProvider && metadata.isRestricted(application)) {
                return PreferenceSetterResult.RESTRICTED.toPreferenceSetterResponse()
            }
            if (metadata is PreferenceAvailabilityProvider && !metadata.isAvailable(application)) {
                return PreferenceSetterResult.UNAVAILABLE.toPreferenceSetterResponse(failureReason = metadata.availabilityDescription)
            }

            val storage = metadata.storage(application)
            val value = request.value
            try {
                if (value.hasBooleanValue()) {
                    if (metadata.valueType != Boolean::class.javaObjectType &&
                        metadata.valueType != Boolean::class.javaPrimitiveType
                    ) {
                        return PreferenceSetterResult.INVALID_REQUEST.toPreferenceSetterResponse()
                    }
                    val booleanValue = value.booleanValue
                    val resultCode = metadata.checkWritePermit(booleanValue)
                    if (resultCode.errorCode != PreferenceSetterResult.OK) return resultCode
                    storage.setBoolean(key, booleanValue)
                    return PreferenceSetterResult.OK.toPreferenceSetterResponse()
                } else if (value.hasIntValue()) {
                    val intValue = value.intValue
                    val resultCode = metadata.checkWritePermit(intValue)
                    if (resultCode.errorCode != PreferenceSetterResult.OK) return resultCode
                    if (
                        metadata is IntRangeValuePreference &&
                            !metadata.isValidValue(application, intValue)
                    ) {
                        return PreferenceSetterResult.INVALID_REQUEST.toPreferenceSetterResponse()
                    }
                    storage.setInt(key, intValue)
                    return PreferenceSetterResult.OK.toPreferenceSetterResponse()
                } else if (value.hasFloatValue()) {
                    val floatValue = value.floatValue
                    val resultCode = metadata.checkWritePermit(floatValue)
                    if (resultCode.errorCode != PreferenceSetterResult.OK) return resultCode
                    storage.setFloat(key, floatValue)
                    return PreferenceSetterResult.OK.toPreferenceSetterResponse()
                } else if (value.hasStringValue()) {
                    if (metadata.valueType != String::class.javaObjectType &&
                        metadata.valueType != String::class.javaPrimitiveType
                    ){
                        return PreferenceSetterResult.INVALID_REQUEST.toPreferenceSetterResponse()
                    }
                    val stringValue = value.stringValue
                    val resultCode = metadata.checkWritePermit(stringValue)
                    if (resultCode.errorCode != PreferenceSetterResult.OK) return resultCode
                    storage.setString(key, stringValue)
                    return PreferenceSetterResult.OK.toPreferenceSetterResponse()
                }
            } catch (e: Exception) {
                return PreferenceSetterResult.INTERNAL_ERROR.toPreferenceSetterResponse(
                    failureReason = e.message
                )
            }
            return PreferenceSetterResult.INVALID_REQUEST.toPreferenceSetterResponse()
        }

        val result = invoke()
        metricsLogger?.logSetterApi(
            application,
            callingUid,
            request,
            screenMetadata,
            metadata,
            result.errorCode,
            SystemClock.elapsedRealtime() - elapsedRealtime,
        )
        return result
    }

    override val requestCodec = PreferenceSetterRequestCodec()

    override val responseCodec = PreferenceSetterResponseCodec()
}

/** Evaluates the write permit of a persistent preference. */
fun <T> PersistentPreference<T>.evalWritePermit(
    context: Context,
    value: T?,
    callingPid: Int,
    callingUid: Int,
): Int =
    evalWritePermit(context, callingPid, callingUid)
        ?: getWritePermit(context, value, callingPid, callingUid)

/** Message codec for [PreferenceSetterRequest]. */
class PreferenceSetterRequestCodec : MessageCodec<PreferenceSetterRequest> {
    override fun encode(data: PreferenceSetterRequest) =
        Bundle(3).apply {
            putString(SCREEN_KEY, data.screenKey)
            if (CatalystFlagProviderFactory.catalystUseKeyParameters()) {
                putBundle(KEY_PARAMETERS, data.keyParameters?.toBundle())
            } else {
                putBundle(ARGS, data.args)
            }
            putString(KEY, data.key)
            putByteArray(null, data.value.toByteArray())
        }

    override fun decode(data: Bundle): PreferenceSetterRequest {
        val screenKey = data.getString(SCREEN_KEY)!!
        val key = data.getString(KEY)!!
        val value = PreferenceValueProto.parseFrom(data.getByteArray(null)!!)

        return if (CatalystFlagProviderFactory.catalystUseKeyParameters()) {
            PreferenceSetterRequest(
                screenKey,
                data.getBundle(KEY_PARAMETERS)?.let { KeyParameters(it.toMap()) },
                key,
                value
            )
        } else {
            PreferenceSetterRequest(
                screenKey,
                data.getBundle(ARGS),
                key,
                value
            )
        }
    }

    companion object {
        private const val SCREEN_KEY = "s"
        private const val KEY = "k"
        private const val ARGS = "a"
        private const val KEY_PARAMETERS = "p"
    }
}

fun Int.toPreferenceSetterResponse(
    failureReason: String? = null,
): PreferenceSetterResponse {
    return when (this) {
        PreferenceSetterResult.OK -> PreferenceSetterResponse(errorCode = PreferenceSetterResult.OK)
        PreferenceSetterResult.UNSUPPORTED -> PreferenceSetterResponse(
            failureReason = "Set preference value is unsupported on the preference",
            errorCode = PreferenceSetterResult.UNSUPPORTED
        )
        PreferenceSetterResult.DISABLED -> PreferenceSetterResponse(
            failureReason = failureReason?.let {
                "Failing preconditions: $it"
            } ?: "Preference is disabled and cannot set preference value",
            errorCode = PreferenceSetterResult.DISABLED
        )
        PreferenceSetterResult.RESTRICTED -> PreferenceSetterResponse(
            failureReason = "Preference is restricted by managed configuration and cannot set preference value",
            errorCode = PreferenceSetterResult.RESTRICTED
        )
        PreferenceSetterResult.UNAVAILABLE -> PreferenceSetterResponse(
            failureReason = failureReason?.let {
                "Failing preconditions: $it"
            } ?: "Preference is unavailable and cannot set preference value",
            errorCode = PreferenceSetterResult.UNAVAILABLE
        )
        PreferenceSetterResult.REQUIRE_APP_PERMISSION -> PreferenceSetterResponse(
            failureReason = "Require (runtime/special) app permission from user explicitly",
            errorCode = PreferenceSetterResult.REQUIRE_APP_PERMISSION
        )
        PreferenceSetterResult.REQUIRE_USER_AGREEMENT -> PreferenceSetterResponse(
            failureReason = "Require explicit user agreement (e.g. terms of service)",
            errorCode = PreferenceSetterResult.REQUIRE_USER_AGREEMENT
        )
        PreferenceSetterResult.DISALLOW -> PreferenceSetterResponse(
            failureReason = failureReason?.let {
                "Failing preconditions: $it"
            } ?: "Failed preconditions",
            errorCode = PreferenceSetterResult.DISALLOW
        )
        PreferenceSetterResult.INVALID_REQUEST -> PreferenceSetterResponse(
            failureReason = "Request is invalid.",
            errorCode = PreferenceSetterResult.INVALID_REQUEST
        )

        // everything else is considered internal error
        else -> PreferenceSetterResponse(
            failureReason = failureReason ?: "Internal error",
            errorCode = PreferenceSetterResult.INTERNAL_ERROR
        )
    }
}

/** Message codec for [PreferenceSetterResponse]. */
class PreferenceSetterResponseCodec : MessageCodec<PreferenceSetterResponse> {
    override fun encode(data: PreferenceSetterResponse) =
        Bundle(3).apply {
            putInt(ERROR_CODE, data.errorCode)
            putString(EXCEPTION_MESSAGE, data.failureReason)
        }

    override fun decode(data: Bundle) =
        PreferenceSetterResponse(
            errorCode = data.getInt(ERROR_CODE),
            failureReason = data.getString(EXCEPTION_MESSAGE),
        )

    companion object {
        private const val ERROR_CODE = "ec"
        private const val EXCEPTION_MESSAGE = "em"
    }
}
