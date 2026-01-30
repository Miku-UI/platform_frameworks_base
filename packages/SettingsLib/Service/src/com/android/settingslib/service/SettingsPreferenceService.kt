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

package com.android.settingslib.service

import android.Manifest.permission.WRITE_SYSTEM_PREFERENCES
import android.os.Binder
import android.os.Build
import android.os.OutcomeReceiver
import android.service.settings.preferences.GetValueRequest
import android.service.settings.preferences.GetValueResult
import android.service.settings.preferences.MetadataRequest
import android.service.settings.preferences.MetadataResult
import android.service.settings.preferences.SetValueRequest
import android.service.settings.preferences.SetValueResult
import android.util.Log
import androidx.annotation.RequiresApi
import com.android.settingslib.graph.GetPreferenceGraphApiHandler
import com.android.settingslib.graph.GetPreferenceGraphRequest
import com.android.settingslib.graph.PreferenceGetterApiHandler
import com.android.settingslib.graph.PreferenceGetterFlags
import com.android.settingslib.graph.PreferenceSetterApiHandler
import com.android.settingslib.ipc.ApiPermissionChecker
import com.android.settingslib.ipc.AppOpApiPermissionChecker
import com.android.settingslib.metadata.PreferenceRemoteOpMetricsLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@RequiresApi(Build.VERSION_CODES.BAKLAVA)
class SettingsPreferenceService
@JvmOverloads
constructor(
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    metricsLogger: PreferenceRemoteOpMetricsLogger? = null,
) : android.service.settings.preferences.SettingsPreferenceService() {

    private val getApiHandler: PreferenceGetterApiHandler
    private val setApiHandler: PreferenceSetterApiHandler
    private val graphApi: GetPreferenceGraphApiHandler

    init {
        // SettingsPreferenceService specifies READ_SYSTEM_PREFERENCES permission in
        // AndroidManifest.xml
        getApiHandler =
            PreferenceGetterApiHandler(1, ApiPermissionChecker.alwaysAllow(), metricsLogger)
        setApiHandler =
            PreferenceSetterApiHandler(
                2,
                AppOpApiPermissionChecker(OPSTR_WRITE_SYSTEM_PREFERENCES, WRITE_SYSTEM_PREFERENCES),
                metricsLogger,
            )
        graphApi =
            GetPreferenceGraphApiHandler(3, ApiPermissionChecker.alwaysAllow(), metricsLogger)
    }

    override fun onGetAllPreferenceMetadata(
        request: MetadataRequest,
        callback: OutcomeReceiver<MetadataResult, Exception>,
    ) {
        // MUST get pid/uid in binder thread
        val callingPid = Binder.getCallingPid()
        val callingUid = Binder.getCallingUid()
        Log.i(TAG, "GetAllPreferenceMetadata pid=$callingPid uid=$callingUid")
        scope.launch {
            try {
                val graphProto =
                    graphApi.invoke(
                        application,
                        callingPid,
                        callingUid,
                        GetPreferenceGraphRequest(flags = PreferenceGetterFlags.METADATA),
                    )
                val result =
                    transformCatalystGetMetadataResponse(this@SettingsPreferenceService, graphProto)
                callback.onResult(result)
            } catch (e: RuntimeException) {
                Log.e(TAG, "GetAllPreferenceMetadata pid=$callingPid uid=$callingUid", e)
                callback.onError(e)
            }
        }
    }

    override fun onGetPreferenceValue(
        request: GetValueRequest,
        callback: OutcomeReceiver<GetValueResult, Exception>,
    ) {
        // MUST get pid/uid in binder thread
        val callingPid = Binder.getCallingPid()
        val callingUid = Binder.getCallingUid()
        Log.i(TAG, "GetPreferenceValue pid=$callingPid uid=$callingUid")
        scope.launch {
            try {
                val apiRequest = transformFrameworkGetValueRequest(request)
                val response = getApiHandler.invoke(application, callingPid, callingUid, apiRequest)
                val result =
                    transformCatalystGetValueResponse(
                        this@SettingsPreferenceService,
                        request,
                        response,
                    )
                if (result == null) {
                    callback.onError(IllegalStateException("No response"))
                } else {
                    callback.onResult(result)
                }
            } catch (e: RuntimeException) {
                Log.e(TAG, "GetPreferenceValue pid=$callingPid uid=$callingUid", e)
                callback.onError(e)
            }
        }
    }

    override fun onSetPreferenceValue(
        request: SetValueRequest,
        callback: OutcomeReceiver<SetValueResult, Exception>,
    ) {
        // MUST get pid/uid in binder thread
        val callingPid = Binder.getCallingPid()
        val callingUid = Binder.getCallingUid()
        Log.i(TAG, "SetPreferenceValue pid=$callingPid uid=$callingUid")
        scope.launch {
            try {
                val apiRequest = transformFrameworkSetValueRequest(request)
                if (apiRequest == null) {
                    callback.onResult(
                        SetValueResult.Builder(SetValueResult.RESULT_INVALID_REQUEST).build()
                    )
                } else {
                    val response =
                        setApiHandler.invoke(application, callingPid, callingUid, apiRequest)
                    callback.onResult(transformCatalystSetValueResponse(response))
                }
            } catch (e: RuntimeException) {
                Log.e(TAG, "SetPreferenceValue pid=$callingPid uid=$callingUid", e)
                callback.onError(e)
            }
        }
    }

    companion object {
        private const val TAG = "PrefServiceForGraph"
        // AppOpsManager.OPSTR_WRITE_SYSTEM_PREFERENCES is hidden
        private const val OPSTR_WRITE_SYSTEM_PREFERENCES = "android:write_system_preferences"
    }
}
