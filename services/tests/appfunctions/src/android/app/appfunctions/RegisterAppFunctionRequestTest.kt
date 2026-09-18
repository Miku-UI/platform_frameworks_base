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
package android.app.appfunctions

import android.os.CancellationSignal
import android.os.OutcomeReceiver
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.util.concurrent.Executor

@RunWith(JUnit4::class)
class RegisterAppFunctionRequestTest {

    private val executor1: Executor = Executor { it.run() }
    private val executor2: Executor = Executor { it.run() }

    private val appFunction1 = object : AppFunction {
        override fun onExecuteAppFunction(
            p0: ExecuteAppFunctionRequest,
            p1: CancellationSignal,
            p2: OutcomeReceiver<ExecuteAppFunctionResponse?, AppFunctionException?>
        ) {}
    }

    private val appFunction2 = object : AppFunction {
        override fun onExecuteAppFunction(
            p0: ExecuteAppFunctionRequest,
            p1: CancellationSignal,
            p2: OutcomeReceiver<ExecuteAppFunctionResponse?, AppFunctionException?>
        ) {}
    }

    @Test
    fun testEquals() {
        val request1 = RegisterAppFunctionRequest("id1", executor1, appFunction1)
        val request2 = RegisterAppFunctionRequest("id1", executor1, appFunction1)
        val requestDifferentId = RegisterAppFunctionRequest("id2", executor1, appFunction1)
        val requestDifferentExecutor = RegisterAppFunctionRequest("id1", executor2, appFunction1)
        val requestDifferentCallback = RegisterAppFunctionRequest("id1", executor1, appFunction2)

        // Test for self equality
        assertThat(request1).isEqualTo(request1)
        // Test for equality with an identical object
        assertThat(request1).isEqualTo(request2)
        // Test for inequality with different properties
        assertThat(request1).isNotEqualTo(requestDifferentId)
        assertThat(request1).isNotEqualTo(requestDifferentExecutor)
        assertThat(request1).isNotEqualTo(requestDifferentCallback)
        // Test for inequality with null and different object types
        assertThat(request1).isNotEqualTo(null)
        assertThat(request1).isNotEqualTo("a string")
    }

    @Test
    fun testHashCode() {
        val request1 = RegisterAppFunctionRequest("id1", executor1, appFunction1)
        val request2 = RegisterAppFunctionRequest("id1", executor1, appFunction1)
        val requestDifferentId = RegisterAppFunctionRequest("id2", executor1, appFunction1)
        val requestDifferentExecutor = RegisterAppFunctionRequest("id1", executor2, appFunction1)
        val requestDifferentCallback = RegisterAppFunctionRequest("id1", executor1, appFunction2)

        assertThat(request1.hashCode()).isEqualTo(request2.hashCode())

        assertThat(request1.hashCode()).isNotEqualTo(requestDifferentId.hashCode())
        assertThat(request1.hashCode()).isNotEqualTo(requestDifferentExecutor.hashCode())
        assertThat(request1.hashCode()).isNotEqualTo(requestDifferentCallback.hashCode())
    }
}
