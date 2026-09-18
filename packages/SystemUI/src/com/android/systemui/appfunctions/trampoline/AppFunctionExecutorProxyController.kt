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

package com.android.systemui.appfunctions.trampoline

import android.app.ActivityOptions
import android.app.PendingIntent
import android.app.appfunctions.AppFunctionException
import android.app.appfunctions.AppFunctionManager
import android.app.appfunctions.ExecuteAppFunctionRequest
import android.app.appfunctions.ExecuteAppFunctionResponse
import android.content.Context
import android.content.Intent
import android.os.CancellationSignal
import android.os.OutcomeReceiver
import android.util.Log
import android.util.Slog
import com.android.systemui.appfunctions.AppFunctionProxyConstants.EXTRA_EXECUTE_APP_FUNCTION_REQUEST
import com.android.systemui.appfunctions.AppFunctionProxyConstants.EXTRA_LAUNCH_PENDING_INTENT_FROM_RESPONSE
import com.android.systemui.dagger.qualifiers.Main
import java.util.concurrent.Executor
import javax.inject.Inject

class AppFunctionExecutorProxyController
@Inject
constructor(private val context: Context, @Main private val callbackExecutor: Executor) {
    private val appFunctionManager: AppFunctionManager? =
        context.getSystemService(AppFunctionManager::class.java)

    fun parseAppFuncAndExecute(intent: Intent) {
        if (appFunctionManager == null) {
            Slog.e(TAG, "AppFunctionManager unavailable, unable to fulfill request")
            return
        }

        val parcelableExecuteAppFunctionRequest: ExecuteAppFunctionRequest? =
            intent.getParcelableExtra(
                EXTRA_EXECUTE_APP_FUNCTION_REQUEST,
                ExecuteAppFunctionRequest::class.java,
            )
        val launchPendingIntent: Boolean =
            intent.getBooleanExtra(EXTRA_LAUNCH_PENDING_INTENT_FROM_RESPONSE, false)

        if (parcelableExecuteAppFunctionRequest == null) {
            Slog.e(TAG, "Unable to retrieve ExecuteAppFunctionRequest")
            return
        }

        appFunctionManager.executeAppFunction(
            parcelableExecuteAppFunctionRequest,
            callbackExecutor,
            CancellationSignal(),
            object : OutcomeReceiver<ExecuteAppFunctionResponse, AppFunctionException> {
                override fun onResult(result: ExecuteAppFunctionResponse) {
                    if (!launchPendingIntent) {
                        return
                    }

                    val resultExtras = result.extras
                    try {
                        // Needed to allow launching the pending intent if the donor does not
                        // have any associated UI.
                        val activityOptions = ActivityOptions.makeBasic()
                        activityOptions.pendingIntentBackgroundActivityStartMode =
                            ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_ALWAYS
                        // TODO: b/485536914 - Don't use Platform variant of AppFunction APIs.
                        val maybePendingIntent =
                            resultExtras.getParcelable(
                                KEY_PREFIX + ExecuteAppFunctionResponse.PROPERTY_RETURN_VALUE,
                                PendingIntent::class.java,
                            )
                        maybePendingIntent?.send(activityOptions.toBundle())
                    } catch (e: Exception) {
                        Slog.e(
                            TAG,
                            "Error in parsing and executing retrieved PendingIntent: ${Log.getStackTraceString(e)}",
                        )
                    }

                    Slog.d(TAG, "AppFunction execution via proxy complete.")
                }

                override fun onError(e: AppFunctionException) {
                    Slog.e(TAG, "Error in executing AppFunction: ${Log.getStackTraceString(e)}")
                }
            },
        )
    }

    companion object {
        private const val TAG = "AppFunctionExecutorProxy"
        private const val KEY_PREFIX = "property/"
    }
}
