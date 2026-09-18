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
package com.android.server.appfunctions.dynamic

import android.app.appfunctions.AppFunctionException
import android.app.appfunctions.ExecuteAppFunctionAidlRequest
import android.app.appfunctions.ExecuteAppFunctionRequest
import android.app.appfunctions.IAppFunctionExecutor
import android.app.appfunctions.SafeOneTimeExecuteAppFunctionCallback
import android.content.pm.UserInfo
import android.os.IBinder
import android.os.ICancellationSignal
import android.os.UserHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.server.SystemService.TargetUser
import com.google.common.truth.Truth.assertThat
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class MultiUserDynamicAppFunctionRegistryTest {
    private lateinit var registry: MultiUserDynamicAppFunctionRegistry
    private val mockRegistrationListener = mock<OnRegistrationStateChangedListener>()

    private val globalScope: List<RegistrationScopeId> = listOf(RegistrationScopeId(null))

    @Before
    fun setUp() {
        registry = MultiUserDynamicAppFunctionRegistry()
        registry.onUserUnlocked(mockRegistrationListener, TARGET_USER_10)
        registry.onUserUnlocked(mockRegistrationListener, TARGET_USER_11)
    }

    @Test
    fun register_correctUser_success() {
        val executor = createExecutorMock()
        registry.registerAppFunctions(TEST_PACKAGE, listOf(TEST_FUNCTION), executor, USER_10, globalScope)

        assertThat(registry.hasRegistrations(TEST_PACKAGE, TEST_FUNCTION, USER_10)).isTrue()
    }

    @Test
    fun register_inOneUser_notVisibleInAnother() {
        val executor = createExecutorMock()
        registry.registerAppFunctions(TEST_PACKAGE, listOf(TEST_FUNCTION), executor, USER_10, globalScope)

        assertThat(registry.hasRegistrations(TEST_PACKAGE, TEST_FUNCTION, USER_11))
            .isFalse()
    }

    @Test
    fun execute_inWrongUser_fails() {
        val executor = createExecutorMock()
        registry.registerAppFunctions(TEST_PACKAGE, listOf(TEST_FUNCTION), executor, USER_10, globalScope)

        val request = createAidlRequest(TEST_PACKAGE, TEST_FUNCTION, USER_11)
        val safeCallback = mock<SafeOneTimeExecuteAppFunctionCallback>()
        val cancellationSignal = mock<ICancellationSignal>()

        registry.executeAppFunction(request, safeCallback, cancellationSignal)

        val exceptionCaptor = argumentCaptor<AppFunctionException>()
        verify(safeCallback).onError(exceptionCaptor.capture())
        assertThat(exceptionCaptor.firstValue.errorCode)
            .isEqualTo(AppFunctionException.ERROR_DISABLED)
    }

    @Test
    fun unregister_correctUser_success() {
        val executor = createExecutorMock()
        registry.registerAppFunctions(TEST_PACKAGE, listOf(TEST_FUNCTION), executor, USER_10, globalScope)
        assertThat(registry.hasRegistrations(TEST_PACKAGE, TEST_FUNCTION, USER_10)).isTrue()

        registry.unregisterAppFunctions(TEST_PACKAGE, listOf(TEST_FUNCTION), executor, USER_10)

        assertThat(registry.hasRegistrations(TEST_PACKAGE, TEST_FUNCTION, USER_10))
            .isFalse()
    }

    @Test
    fun unregister_wrongUser_isNoOp() {
        val executor = createExecutorMock()
        registry.registerAppFunctions(TEST_PACKAGE, listOf(TEST_FUNCTION), executor, USER_10, globalScope)

        registry.unregisterAppFunctions(TEST_PACKAGE, listOf(TEST_FUNCTION), executor, USER_11)

        assertThat(registry.hasRegistrations(TEST_PACKAGE, TEST_FUNCTION, USER_10)).isTrue()
    }

    @Test
    fun onUserStopped_removesRegistryAndAffectsOnlyThatUser() {
        val executor10 = createExecutorMock()
        val executor11 = createExecutorMock()
        registry.registerAppFunctions(TEST_PACKAGE, listOf(TEST_FUNCTION), executor10, USER_10, globalScope)
        registry.registerAppFunctions(TEST_PACKAGE, listOf(TEST_FUNCTION), executor11, USER_11, globalScope)

        registry.onUserStopped(TARGET_USER_10)

        assertThrows(IllegalStateException::class.java) {
            registry.hasRegistrations(TEST_PACKAGE, TEST_FUNCTION, USER_10)
        }

        assertThat(registry.hasRegistrations(TEST_PACKAGE, TEST_FUNCTION, USER_11)).isTrue()
    }

    @Test
    fun accessBeforeUnlock_throwsException() {
        val user12 = UserHandle.of(12)
        assertThrows(IllegalStateException::class.java) {
            registry.hasRegistrations(TEST_PACKAGE, TEST_FUNCTION, user12)
        }
    }

    @Test
    fun onUserUnlocked_calledTwice_isHarmless() {
        val executor = createExecutorMock()
        registry.registerAppFunctions(TEST_PACKAGE, listOf(TEST_FUNCTION), executor, USER_10, globalScope)

        registry.onUserUnlocked(mockRegistrationListener, TARGET_USER_10)

        assertThat(registry.hasRegistrations(TEST_PACKAGE, TEST_FUNCTION, USER_10)).isTrue()
    }

    @Test
    fun onUserStopped_calledTwice_isHarmless() {
        registry.onUserStopped(TARGET_USER_10)
        // Should not throw an exception
        registry.onUserStopped(TARGET_USER_10)
    }

    @Test
    fun stopAndReunlockUser_providesFreshRegistry() {
        val executor = createExecutorMock()
        registry.registerAppFunctions(TEST_PACKAGE, listOf(TEST_FUNCTION), executor, USER_10, globalScope)

        // Stop and re-unlock the user
        registry.onUserStopped(TARGET_USER_10)
        registry.onUserUnlocked(mockRegistrationListener, TARGET_USER_10)

        // Verify the old registration is gone
        assertThat(registry.hasRegistrations(TEST_PACKAGE, TEST_FUNCTION, USER_10))
            .isFalse()
    }

    @Test
    fun register_withInvalidUserAll_throwsException() {
        assertThrows(IllegalStateException::class.java) {
            registry.registerAppFunctions(
                TEST_PACKAGE,
                listOf(TEST_FUNCTION),
                createExecutorMock(),
                UserHandle.ALL,
                globalScope,
            )
        }
    }

    @Test
    fun stopUserAndRegister_concurrent_isSafe() {
        val executorService = Executors.newFixedThreadPool(2)
        val latch = CountDownLatch(2)

        // Thread 1: Stop the user
        executorService.submit {
            try {
                registry.onUserStopped(TARGET_USER_10)
            } finally {
                latch.countDown()
            }
        }

        // Thread 2: Register a function for the same user
        executorService.submit {
            try {
                registry.registerAppFunctions(
                    TEST_PACKAGE,
                    listOf("raceFunction"),
                    createExecutorMock(),
                    USER_10,
                    globalScope,
                )
            } catch (e: IllegalStateException) {
                // This is an expected, safe outcome if stop runs first
            } finally {
                latch.countDown()
            }
        }

        latch.await(5, TimeUnit.SECONDS)
        executorService.shutdown()

        assertThrows(IllegalStateException::class.java) {
            registry.hasRegistrations(TEST_PACKAGE, "contextFunction", USER_10)
        }
    }

    private fun createExecutorMock(binder: IBinder = mock()): IAppFunctionExecutor {
        val executor = mock<IAppFunctionExecutor>()
        whenever(executor.asBinder()).thenReturn(binder)
        return executor
    }

    private fun createAidlRequest(
        packageName: String,
        functionId: String,
        userHandle: UserHandle,
    ): ExecuteAppFunctionAidlRequest {
        val clientRequest = ExecuteAppFunctionRequest.Builder(packageName, functionId).build()
        return ExecuteAppFunctionAidlRequest(clientRequest, userHandle, "calling.package", 0, 0)
    }

    private companion object {
        val USER_10 = UserHandle.of(10)
        val INFO_10 = UserInfo(USER_10.identifier, "test", 0)
        val TARGET_USER_10 = TargetUser(INFO_10)

        val USER_11 = UserHandle.of(11)
        val INFO_11 = UserInfo(USER_11.identifier, "test2", 0)
        val TARGET_USER_11 = TargetUser(INFO_11)

        const val TEST_PACKAGE = "com.example.app"
        const val TEST_FUNCTION = "contextualFunction"
    }
}
