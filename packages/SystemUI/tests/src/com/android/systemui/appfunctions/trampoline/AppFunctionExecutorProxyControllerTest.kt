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

import android.app.PendingIntent
import android.app.appfunctions.AppFunctionException
import android.app.appfunctions.AppFunctionManager
import android.app.appfunctions.ExecuteAppFunctionRequest
import android.app.appfunctions.ExecuteAppFunctionResponse
import android.content.Intent
import android.os.Bundle
import android.os.OutcomeReceiver
import android.testing.TestableLooper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.appfunctions.AppFunctionProxyConstants.EXTRA_EXECUTE_APP_FUNCTION_REQUEST
import com.android.systemui.appfunctions.AppFunctionProxyConstants.EXTRA_LAUNCH_PENDING_INTENT_FROM_RESPONSE
import com.google.common.util.concurrent.MoreExecutors
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@SmallTest
@RunWith(AndroidJUnit4::class)
@TestableLooper.RunWithLooper
class AppFunctionExecutorProxyControllerTest : SysuiTestCase() {

    private val mockAppFunctionManager: AppFunctionManager = mock()
    private val mockExecuteAppFunctionRequest: ExecuteAppFunctionRequest = mock()
    private val mockExecuteAppFunctionResponse: ExecuteAppFunctionResponse = mock()
    private val mockPendingIntent: PendingIntent = mock()
    private val directExecutor = MoreExecutors.directExecutor()

    private lateinit var controller: AppFunctionExecutorProxyController

    @Before
    fun setUp() {
        mContext.addMockSystemService(AppFunctionManager::class.java, mockAppFunctionManager)

        controller = AppFunctionExecutorProxyController(mContext, directExecutor)
    }

    @Test
    fun parseAppFuncAndExecute_missingExecuteAppFunctionRequest_doesNotExecuteAppFunction() {
        controller.parseAppFuncAndExecute(Intent())

        verify(mockAppFunctionManager, never()).executeAppFunction(any(), any(), any(), any())
    }

    @Test
    fun parseAppFuncAndExecute_packedExecuteAppFunctionRequest_executesAppFunction() {
        whenever(
                mockAppFunctionManager.executeAppFunction(
                    eq(mockExecuteAppFunctionRequest),
                    any(),
                    any(),
                    any(),
                )
            )
            .then {
                // do nothing, simply intercept the execute request to ensure no platform errors.
            }

        controller.parseAppFuncAndExecute(
            Intent().apply {
                putExtra(EXTRA_EXECUTE_APP_FUNCTION_REQUEST, mockExecuteAppFunctionRequest)
                putExtra(EXTRA_LAUNCH_PENDING_INTENT_FROM_RESPONSE, false)
            }
        )

        verify(mockAppFunctionManager, times(1)).executeAppFunction(any(), any(), any(), any())
    }

    @Test
    fun parseAppFuncAndExecute_packedAppFunctionReturningPendingIntent_sendPendingIntent() {
        var callback: OutcomeReceiver<ExecuteAppFunctionResponse, AppFunctionException>? = null
        whenever(
                mockAppFunctionManager.executeAppFunction(
                    eq(mockExecuteAppFunctionRequest),
                    any(),
                    any(),
                    any(),
                )
            )
            .then { invocation ->
                callback =
                    invocation.getArgument<
                        OutcomeReceiver<ExecuteAppFunctionResponse, AppFunctionException>
                    >(
                        3
                    )
                whenever(mockExecuteAppFunctionResponse.extras)
                    .thenReturn(
                        Bundle().apply {
                            putParcelable(
                                "property/" + ExecuteAppFunctionResponse.PROPERTY_RETURN_VALUE,
                                mockPendingIntent,
                            )
                        }
                    )
                callback.onResult(mockExecuteAppFunctionResponse)
            }

        controller.parseAppFuncAndExecute(
            Intent().apply {
                putExtra(EXTRA_EXECUTE_APP_FUNCTION_REQUEST, mockExecuteAppFunctionRequest)
                putExtra(EXTRA_LAUNCH_PENDING_INTENT_FROM_RESPONSE, true)
            }
        )

        verify(mockAppFunctionManager, times(1)).executeAppFunction(any(), any(), any(), any())
        verify(mockPendingIntent, times(1)).send(any<Bundle>())
    }

    @Test
    fun parseAppFuncAndExecute_packedAppFunctionReturningPendingIntent_notCalledIfExtraUnspecified() {
        var callback: OutcomeReceiver<ExecuteAppFunctionResponse, AppFunctionException>? = null
        whenever(
                mockAppFunctionManager.executeAppFunction(
                    eq(mockExecuteAppFunctionRequest),
                    any(),
                    any(),
                    any(),
                )
            )
            .then { invocation ->
                callback =
                    invocation.getArgument<
                        OutcomeReceiver<ExecuteAppFunctionResponse, AppFunctionException>
                    >(
                        3
                    )
                whenever(mockExecuteAppFunctionResponse.extras)
                    .thenReturn(
                        Bundle().apply {
                            putParcelable(
                                "property/" + ExecuteAppFunctionResponse.PROPERTY_RETURN_VALUE,
                                mockPendingIntent,
                            )
                        }
                    )
                callback.onResult(mockExecuteAppFunctionResponse)
            }

        controller.parseAppFuncAndExecute(
            Intent().apply {
                putExtra(EXTRA_EXECUTE_APP_FUNCTION_REQUEST, mockExecuteAppFunctionRequest)
            }
        )

        verify(mockAppFunctionManager, times(1)).executeAppFunction(any(), any(), any(), any())
        verify(mockPendingIntent, never()).send(any<Bundle>())
    }
}
