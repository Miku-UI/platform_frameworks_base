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

import android.app.appfunctions.AppFunctionActivityId
import android.app.appfunctions.AppFunctionActivityState
import android.app.appfunctions.AppFunctionException
import android.app.appfunctions.AppFunctionName
import android.app.appfunctions.ExecuteAppFunctionRequest
import android.app.appfunctions.ExecuteAppFunctionResponse
import android.app.appfunctions.IAppFunctionExecutor
import android.app.appfunctions.IExecuteAppFunctionCallback
import android.app.appfunctions.SafeOneTimeExecuteAppFunctionCallback
import android.os.IBinder
import android.os.Binder
import android.os.ICancellationSignal
import android.os.RemoteException
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import android.util.ArraySet
import com.google.common.util.concurrent.MoreExecutors
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.anyInt
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever
import org.mockito.kotlin.inOrder

@RunWith(AndroidJUnit4::class)
class DynamicAppFunctionRegistryTest {
    private lateinit var registry: DynamicAppFunctionRegistry
    private lateinit var mMockOnRegistrationStateChangedListener:
        OnRegistrationStateChangedListener

    private val globalScope: List<RegistrationScopeId> = listOf(RegistrationScopeId(null))

    private val batchedGlobalScope: List<RegistrationScopeId> =
        listOf(RegistrationScopeId(null), RegistrationScopeId(null))

    @Before
    fun setUp() {
        mMockOnRegistrationStateChangedListener =
            mock<OnRegistrationStateChangedListener>()
        registry = DynamicAppFunctionRegistry(
            MoreExecutors.directExecutor(),
            mMockOnRegistrationStateChangedListener)
    }

    @Test
    fun createRegistrationId_equalsPackageAndFunction() {
        val name = AppFunctionName(TEST_PACKAGE, TEST_FUNCTION)
        val id1 =
            AppFunctionRegistrationId(name, RegistrationScopeId(null))
        val id2 =
            AppFunctionRegistrationId(name, RegistrationScopeId(null))

        assertThat(id1).isEqualTo(id2)
        assertThat(id1.hashCode()).isEqualTo(id2.hashCode())
    }

    @Test
    fun createRegistrationId_notEqualConstructorArguments_notEqualAndNotEqualHashCode() {
        val globalScopeId = RegistrationScopeId(null)
        val activityScopeId = RegistrationScopeId(mock())
        val id1 =
            AppFunctionRegistrationId(
                AppFunctionName(TEST_PACKAGE, TEST_FUNCTION),
                globalScopeId,
            )
        val id2 =
            AppFunctionRegistrationId(
                AppFunctionName(TEST_PACKAGE, TEST_FUNCTION2),
                globalScopeId,
            )
        val id3 =
            AppFunctionRegistrationId(
                AppFunctionName(TEST_PACKAGE2, TEST_FUNCTION),
                globalScopeId,
            )
        val id4 =
            AppFunctionRegistrationId(
                AppFunctionName(TEST_PACKAGE, TEST_FUNCTION),
                activityScopeId,
            )

        assertThat(id1).isNotEqualTo(id2)
        assertThat(id1.hashCode()).isNotEqualTo(id2.hashCode())

        assertThat(id1).isNotEqualTo(id3)
        assertThat(id1.hashCode()).isNotEqualTo(id3.hashCode())

        assertThat(id1).isNotEqualTo(id4)
        assertThat(id1.hashCode()).isNotEqualTo(id4.hashCode())
    }

    @Test
    fun register_success() {
        registry.registerAppFunctions(
            TEST_PACKAGE,
            listOf(TEST_FUNCTION),
            createExecutorMock(),
            globalScope,
        )

        assertThat(registry.hasRegistrations(TEST_PACKAGE, TEST_FUNCTION)).isTrue()
    }

    @Test
    fun register_alreadyRegistered_throwsException() {
        registry.registerAppFunctions(
            TEST_PACKAGE,
            listOf(TEST_FUNCTION),
            createExecutorMock(),
            globalScope,
        )

        assertThrows(IllegalStateException::class.java) {
            registry.registerAppFunctions(
                TEST_PACKAGE,
                listOf(TEST_FUNCTION),
                createExecutorMock(),
                globalScope,
            )
        }
    }

    @Test
    fun register_batchRegistrationIsAtomic_failsIfOneIsAlreadyRegistered() {
        // Pre-register one function
        registry.registerAppFunctions(
            TEST_PACKAGE,
            listOf(TEST_FUNCTION),
            createExecutorMock(),
            globalScope,
        )

        // Attempt to register the same function again in a batch with a new one
        assertThrows(IllegalStateException::class.java) {
            registry.registerAppFunctions(
                TEST_PACKAGE,
                listOf(TEST_FUNCTION, TEST_FUNCTION2),
                createExecutorMock(),
                batchedGlobalScope,
            )
        }

        // Verify that the new function was NOT registered, proving the operation was atomic
        assertThat(registry.hasRegistrations(TEST_PACKAGE, TEST_FUNCTION2)).isFalse()
    }

    @Test
    fun register_afterUnregistration_succeeds() {
        val executor = createExecutorMock()
        registry.registerAppFunctions(TEST_PACKAGE, listOf(TEST_FUNCTION), executor, globalScope)
        registry.unregisterAppFunctions(TEST_PACKAGE, listOf(TEST_FUNCTION), executor)

        registry.registerAppFunctions(TEST_PACKAGE, listOf(TEST_FUNCTION), executor, globalScope)
        assertThat(registry.hasRegistrations(TEST_PACKAGE, TEST_FUNCTION)).isTrue()
    }

    @Test
    fun register_sameFunctionIdAndDifferentPackages_registersBoth() {
        val executor1 = createExecutorMock()
        val executor2 = createExecutorMock()

        registry.registerAppFunctions(TEST_PACKAGE, listOf(TEST_FUNCTION), executor1, globalScope)
        registry.registerAppFunctions(TEST_PACKAGE2, listOf(TEST_FUNCTION), executor2, globalScope)

        assertThat(registry.hasRegistrations(TEST_PACKAGE, TEST_FUNCTION)).isTrue()
        assertThat(registry.hasRegistrations(TEST_PACKAGE2, TEST_FUNCTION)).isTrue()
    }

    @Test
    fun register_deathListenerAttached() {
        val binder = mock<IBinder>()
        val executor = createExecutorMock(binder)
        registry.registerAppFunctions(TEST_PACKAGE, listOf(TEST_FUNCTION), executor, globalScope)
        verify(binder).linkToDeath(any(), anyInt())
    }

    @Test
    fun register_sameFunctionWithDifferentToken_success() {
        val activityExecutor = createExecutorMock()
        val mockActivitySourceId = listOf(RegistrationScopeId(mock()))

        val mockActivityToken: IBinder = mock()
        registry.registerAppFunctions(
            TEST_PACKAGE,
            listOf(TEST_FUNCTION),
            activityExecutor,
            mockActivitySourceId,
        )

        val mockActivitySourceId2 = listOf(RegistrationScopeId(mock()))
        registry.registerAppFunctions(
            TEST_PACKAGE,
            listOf(TEST_FUNCTION),
            activityExecutor,
            mockActivitySourceId2,
        )
    }

    @Test
    fun register_alreadyRegisteredWithTheSameActivityToken_throwsException() {
        val activityExecutor = createExecutorMock()
        val mockActivitySourceId = listOf(RegistrationScopeId(mock()))

        val mockActivityToken: IBinder = mock()
        registry.registerAppFunctions(
            TEST_PACKAGE,
            listOf(TEST_FUNCTION),
            activityExecutor,
            mockActivitySourceId,
        )
        assertThrows(IllegalStateException::class.java) {
            registry.registerAppFunctions(
                TEST_PACKAGE,
                listOf(TEST_FUNCTION),
                activityExecutor,
                mockActivitySourceId,
            )
        }
    }

    @Test
    fun register_withActivityToken_countsRegistration() {
        val activityExecutor = createExecutorMock()
        val activitySourceId = listOf(RegistrationScopeId(mock()))
        registry.registerAppFunctions(
            TEST_PACKAGE,
            listOf(TEST_FUNCTION),
            activityExecutor,
            globalScope,
        )
        registry.registerAppFunctions(
            TEST_PACKAGE,
            listOf(TEST_FUNCTION),
            activityExecutor,
            activitySourceId,
        )

        // Unregister the activity-scoped function
        registry.unregisterAppFunctions(
            TEST_PACKAGE,
            listOf(TEST_FUNCTION),
            activityExecutor,
        )

        // Verify the global function is still registered
        assertThat(registry.hasRegistrations(TEST_PACKAGE, TEST_FUNCTION)).isTrue()
    }

    @Test
    fun unregister_oneOfTwoRegistrationsForSameName_callsOnRegistrationChanged() {
        val executor1 = createExecutorMock()
        val executor2 = createExecutorMock()

        val activityId1 = AppFunctionActivityId(Binder())
        registry.registerAppFunctions(
            TEST_PACKAGE,
            listOf(TEST_FUNCTION),
            executor1,
            listOf(RegistrationScopeId(activityId1)),
        )
        val activityId2 = AppFunctionActivityId(Binder())
        registry.registerAppFunctions(
            TEST_PACKAGE,
            listOf(TEST_FUNCTION),
            executor2,
            listOf(RegistrationScopeId(activityId2)),
        )

        registry.unregisterAppFunctions(
            TEST_PACKAGE,
            listOf(TEST_FUNCTION),
            executor1,
        )

        val captor = argumentCaptor<Set<AppFunctionName>>()
        verify(mMockOnRegistrationStateChangedListener, times(3))
            .onRegistrationChanged(captor.capture())
        assertThat(captor.thirdValue)
            .containsExactly(AppFunctionName(TEST_PACKAGE, TEST_FUNCTION))
    }

    @Test
    fun unregister_activityScopedRegistration_succeeds() {
        val activityExecutor = createExecutorMock()

        val mockActivitySourceId = listOf(RegistrationScopeId(mock()))
        registry.registerAppFunctions(
            TEST_PACKAGE,
            listOf(TEST_FUNCTION),
            activityExecutor,
            mockActivitySourceId,
        )

        registry.unregisterAppFunctions(
            TEST_PACKAGE,
            listOf(TEST_FUNCTION),
            activityExecutor,
        )

        assertThat(registry.hasRegistrations(TEST_PACKAGE, TEST_FUNCTION)).isFalse()
    }

    @Test
    fun unregister_notRegistered_noOp() {
        registry.unregisterAppFunctions(
            TEST_PACKAGE,
            listOf(TEST_FUNCTION),
            createExecutorMock(),
        )

        assertThat(registry.hasRegistrations(TEST_PACKAGE, TEST_FUNCTION)).isFalse()
    }

    @Test
    fun unregister_wrongExecutor_noOp() {
        val executorA = createExecutorMock()
        val executorB = createExecutorMock()
        registry.registerAppFunctions(TEST_PACKAGE, listOf(TEST_FUNCTION), executorA, globalScope)
        assertThat(registry.hasRegistrations(TEST_PACKAGE, TEST_FUNCTION)).isTrue()

        registry.unregisterAppFunctions(TEST_PACKAGE, listOf(TEST_FUNCTION), executorB)

        assertThat(registry.hasRegistrations(TEST_PACKAGE, TEST_FUNCTION)).isTrue()
    }

    @Test
    fun unregister_doesNotAffectAnotherWithSameExecutor() {
        val executor = createExecutorMock()
        registry.registerAppFunctions(
            TEST_PACKAGE,
            listOf(TEST_FUNCTION, TEST_FUNCTION2),
            executor,
            batchedGlobalScope,
        )
        assertThat(registry.hasRegistrations(TEST_PACKAGE, TEST_FUNCTION)).isTrue()
        assertThat(registry.hasRegistrations(TEST_PACKAGE, TEST_FUNCTION2)).isTrue()

        registry.unregisterAppFunctions(TEST_PACKAGE, listOf(TEST_FUNCTION), executor)

        assertThat(registry.hasRegistrations(TEST_PACKAGE, TEST_FUNCTION)).isFalse()
        assertThat(registry.hasRegistrations(TEST_PACKAGE, TEST_FUNCTION2)).isTrue()
    }

    @Test
    fun unregister_nonExistentFunction_isHarmless() {
        val executor = createExecutorMock()
        registry.registerAppFunctions(TEST_PACKAGE, listOf(TEST_FUNCTION), executor, globalScope)

        registry.unregisterAppFunctions(TEST_PACKAGE, listOf(TEST_FUNCTION2), executor)

        assertThat(registry.hasRegistrations(TEST_PACKAGE, TEST_FUNCTION)).isTrue()
    }

    @Test
    fun registerUnregister_multipleThreadsRegisterAndUnregister() {
        val numThreads = 10
        val numOperations = 100
        val executorService = Executors.newFixedThreadPool(numThreads)
        val startLatch = CountDownLatch(1)
        val endLatch = CountDownLatch(numThreads)

        for (i in 0 until numThreads) {
            executorService.submit {
                startLatch.await() // Wait for all threads to be ready
                val executor = createExecutorMock()
                for (j in 0 until numOperations) {
                    val functionName = "function_" + i + "_" + j
                    try {
                        registry.registerAppFunctions(
                            TEST_PACKAGE,
                            listOf(functionName),
                            executor,
                            globalScope,
                        )
                        assertThat(registry.hasRegistrations(TEST_PACKAGE, functionName))
                            .isTrue()
                        registry.unregisterAppFunctions(
                            TEST_PACKAGE,
                            listOf(functionName),
                            executor,
                        )
                        assertThat(registry.hasRegistrations(TEST_PACKAGE, functionName))
                            .isFalse()
                    } catch (e: Exception) {
                        // Fail the test if any exception occurs
                        throw e
                    }
                }
                endLatch.countDown()
            }
        }

        startLatch.countDown() // Start all threads
        endLatch.await() // Wait for all threads to finish
        executorService.shutdown()
    }

    @Test
    fun onCallbackDied_unregistersRegistration() {
        val binder = mock<IBinder>()
        val executor = createExecutorMock(binder)
        registry.registerAppFunctions(TEST_PACKAGE, listOf(TEST_FUNCTION), executor, globalScope)

        val deathRecipientCaptor = argumentCaptor<IBinder.DeathRecipient>()
        verify(binder).linkToDeath(deathRecipientCaptor.capture(), anyInt())
        assertThat(registry.hasRegistrations(TEST_PACKAGE, TEST_FUNCTION)).isTrue()
        deathRecipientCaptor.lastValue.binderDied(binder)
        assertThat(registry.hasRegistrations(TEST_PACKAGE, TEST_FUNCTION)).isFalse()

        val captor = argumentCaptor<Set<AppFunctionName>>()
        verify(mMockOnRegistrationStateChangedListener, times(2))
            .onRegistrationChanged(captor.capture())
        // First from registration, second from process death.
        assertThat(captor.firstValue)
            .containsExactly(AppFunctionName(TEST_PACKAGE, TEST_FUNCTION))
        assertThat(captor.secondValue)
            .containsExactly(AppFunctionName(TEST_PACKAGE, TEST_FUNCTION))
        verifyNoMoreInteractions(mMockOnRegistrationStateChangedListener)
    }

    @Test
    fun onCallbackDied_oneOfTwoRegistrationsForSameNameDied_callsOnRegistrationChanged() {
        val binder1 = mock<IBinder>()
        val executor1 = createExecutorMock(binder1)
        val binder2 = mock<IBinder>()
        val executor2 = createExecutorMock(binder2)

        val activityId1 = AppFunctionActivityId(Binder())
        registry.registerAppFunctions(
            TEST_PACKAGE,
            listOf(TEST_FUNCTION),
            executor1,
            listOf(RegistrationScopeId(activityId1)),
        )
        val activityId2 = AppFunctionActivityId(Binder())
        registry.registerAppFunctions(
            TEST_PACKAGE,
            listOf(TEST_FUNCTION),
            executor2,
            listOf(RegistrationScopeId(activityId2)),
        )

        val deathRecipientCaptor = argumentCaptor<IBinder.DeathRecipient>()
        verify(binder1).linkToDeath(deathRecipientCaptor.capture(), anyInt())
        deathRecipientCaptor.firstValue.binderDied(binder1)

        val captor = argumentCaptor<Set<AppFunctionName>>()
        verify(mMockOnRegistrationStateChangedListener, times(3))
            .onRegistrationChanged(captor.capture())
        assertThat(captor.thirdValue)
            .containsExactly(AppFunctionName(TEST_PACKAGE, TEST_FUNCTION))
    }

    @Test
    fun onCallbackDied_unregistersAllRelatedRegistrations() {
        val binder = mock<IBinder>()
        val executor = createExecutorMock(binder)
        registry.registerAppFunctions(
            TEST_PACKAGE,
            listOf(TEST_FUNCTION, TEST_FUNCTION2),
            executor,
            batchedGlobalScope,
        )

        val deathRecipientCaptor = argumentCaptor<IBinder.DeathRecipient>()
        verify(binder).linkToDeath(deathRecipientCaptor.capture(), anyInt())
        deathRecipientCaptor.lastValue.binderDied(binder)
        assertThat(registry.hasRegistrations(TEST_PACKAGE, TEST_FUNCTION)).isFalse()
        assertThat(registry.hasRegistrations(TEST_PACKAGE, TEST_FUNCTION2)).isFalse()

        val captor = argumentCaptor<Set<AppFunctionName>>()
        verify(mMockOnRegistrationStateChangedListener, times(2))
            .onRegistrationChanged(captor.capture())
        val expectedFunctions =
            setOf(
                AppFunctionName(TEST_PACKAGE, TEST_FUNCTION),
                AppFunctionName(TEST_PACKAGE, TEST_FUNCTION2),
            )
        // First from registration, second from process death.
        assertThat(captor.firstValue).isEqualTo(expectedFunctions)
        assertThat(captor.secondValue).isEqualTo(expectedFunctions)
        verifyNoMoreInteractions(mMockOnRegistrationStateChangedListener)
    }

    @Test
    fun onCallbackDied_forUnregisteredExecutor_noOp() {
        val binder = mock<IBinder>()
        val executor = createExecutorMock(binder)
        registry.registerAppFunctions(TEST_PACKAGE, listOf(TEST_FUNCTION), executor, globalScope)
        registry.unregisterAppFunctions(TEST_PACKAGE, listOf(TEST_FUNCTION), executor)

        val deathRecipientCaptor = argumentCaptor<IBinder.DeathRecipient>()
        verify(binder).linkToDeath(deathRecipientCaptor.capture(), anyInt())
        deathRecipientCaptor.lastValue.binderDied(binder)
        assertThat(registry.hasRegistrations(TEST_PACKAGE, TEST_FUNCTION)).isFalse()

        val captor = argumentCaptor<Set<AppFunctionName>>()
        verify(mMockOnRegistrationStateChangedListener, times(2))
            .onRegistrationChanged(captor.capture())
        val expectedFunctions =
            setOf(AppFunctionName(TEST_PACKAGE, TEST_FUNCTION))
        // First from registration, second from process death.
        assertThat(captor.firstValue).isEqualTo(expectedFunctions)
        assertThat(captor.secondValue).isEqualTo(expectedFunctions)
        // Process death should not trigger another callback.
        verifyNoMoreInteractions(mMockOnRegistrationStateChangedListener)
    }

    @Test
    fun isRegistered_withScope_globalFunction_isRegistered() {
        registry.registerAppFunctions(
            TEST_PACKAGE,
            listOf(TEST_FUNCTION),
            createExecutorMock(),
            globalScope,
        )

        assertThat(
                registry.isRegistered(TEST_PACKAGE, TEST_FUNCTION, RegistrationScopeId.GLOBAL_SCOPE)
            )
            .isTrue()
    }

    @Test
    fun isRegistered_withScope_globalFunction_notRegistered() {
        assertThat(
                registry.isRegistered(TEST_PACKAGE, TEST_FUNCTION, RegistrationScopeId.GLOBAL_SCOPE)
            )
            .isFalse()
    }

    @Test
    fun isRegistered_withScope_activityFunction_isRegistered() {
        val activityId = AppFunctionActivityId(Binder())
        val scopeId = RegistrationScopeId(activityId)
        registry.registerAppFunctions(
            TEST_PACKAGE,
            listOf(TEST_FUNCTION),
            createExecutorMock(),
            listOf(scopeId),
        )

        assertThat(registry.isRegistered(TEST_PACKAGE, TEST_FUNCTION, scopeId)).isTrue()
    }

    @Test
    fun isRegistered_withScope_activityFunction_wrongScope_returnsFalse() {
        val activityId1 = AppFunctionActivityId(Binder())
        val scopeId1 = RegistrationScopeId(activityId1)
        registry.registerAppFunctions(
            TEST_PACKAGE,
            listOf(TEST_FUNCTION),
            createExecutorMock(),
            listOf(scopeId1),
        )

        val activityId2 = AppFunctionActivityId(Binder())
        val scopeId2 = RegistrationScopeId(activityId2)
        assertThat(registry.isRegistered(TEST_PACKAGE, TEST_FUNCTION, scopeId2)).isFalse()
    }

    @Test
    fun isRegistered_withScope_registeredAsGlobal_checkWithActivityScope_returnsFalse() {
        registry.registerAppFunctions(
            TEST_PACKAGE,
            listOf(TEST_FUNCTION),
            createExecutorMock(),
            globalScope,
        )

        val activityId = AppFunctionActivityId(Binder())
        val scopeId = RegistrationScopeId(activityId)
        assertThat(registry.isRegistered(TEST_PACKAGE, TEST_FUNCTION, scopeId)).isFalse()
    }

    @Test
    fun isRegistered_withScope_registeredAsActivity_checkWithGlobalScope_returnsFalse() {
        val activityId = AppFunctionActivityId(Binder())
        val scopeId = RegistrationScopeId(activityId)
        registry.registerAppFunctions(
            TEST_PACKAGE,
            listOf(TEST_FUNCTION),
            createExecutorMock(),
            listOf(scopeId),
        )

        assertThat(
                registry.isRegistered(TEST_PACKAGE, TEST_FUNCTION, RegistrationScopeId.GLOBAL_SCOPE)
            )
            .isFalse()
    }

    @Test
    fun concurrency_onCallbackDiedAndUnregister_isSafe() {
        val binder = mock<IBinder>()
        val executor = createExecutorMock(binder)
        registry.registerAppFunctions(
            TEST_PACKAGE,
            listOf(TEST_FUNCTION, TEST_FUNCTION2),
            executor,
            batchedGlobalScope,
        )

        val deathRecipientCaptor = argumentCaptor<IBinder.DeathRecipient>()
        verify(binder).linkToDeath(deathRecipientCaptor.capture(), anyInt())
        val deathRecipient = deathRecipientCaptor.lastValue

        val executorService = Executors.newFixedThreadPool(2)
        val latch = CountDownLatch(2)

        executorService.submit {
            try {
                deathRecipient.binderDied(binder)
            } finally {
                latch.countDown()
            }
        }

        executorService.submit {
            try {
                registry.unregisterAppFunctions(
                    TEST_PACKAGE,
                    listOf(TEST_FUNCTION),
                    executor,
                )
            } finally {
                latch.countDown()
            }
        }

        latch.await(5, TimeUnit.SECONDS)
        executorService.shutdown()

        assertThat(registry.hasRegistrations(TEST_PACKAGE, TEST_FUNCTION)).isFalse()
        assertThat(registry.hasRegistrations(TEST_PACKAGE, TEST_FUNCTION2)).isFalse()

        val captor = argumentCaptor<Set<AppFunctionName>>()
        verify(mMockOnRegistrationStateChangedListener, times(3)).onRegistrationChanged(captor.capture())
        assertThat(captor.firstValue)
            .contains(AppFunctionName(TEST_PACKAGE, TEST_FUNCTION2))
        // No guarantee that process death or unregister is called first, so only
        // verify that the listener is called twice after registration.
        verifyNoMoreInteractions(mMockOnRegistrationStateChangedListener)
    }

    @Test
    fun isAppFunctionRegistered_notRegistered_returnsFalse() {
        assertThat(registry.hasRegistrations(TEST_PACKAGE, TEST_FUNCTION)).isFalse()
    }

    @Test
    fun executeAppFunction_success_invokesExecutor() {
        val executor = createExecutorMock()
        registry.registerAppFunctions(TEST_PACKAGE, listOf(TEST_FUNCTION), executor, globalScope)

        val request = createMockExecutionRequest(TEST_PACKAGE, TEST_FUNCTION)
        val safeCallback = mock<SafeOneTimeExecuteAppFunctionCallback>()
        val executionCallback = mock<IExecuteAppFunctionCallback>()
        whenever<IExecuteAppFunctionCallback>(safeCallback.wrapToExecutionCallback())
            .thenReturn(executionCallback)
        val cancellationSignal = mock<ICancellationSignal>()

        registry.executeAppFunction(request, safeCallback, cancellationSignal)

        verify(executor).execute(any(), any(), eq(executionCallback))
    }

    @Test
    fun executeAppFunction_functionNotFound_callsOnError() {
        registry.registerAppFunctions(
            TEST_PACKAGE,
            listOf(TEST_FUNCTION),
            createExecutorMock(),
            globalScope,
        )
        val request = createMockExecutionRequest(TEST_PACKAGE, TEST_FUNCTION2)
        val safeCallback = mock<SafeOneTimeExecuteAppFunctionCallback>()
        val cancellationSignal = mock<ICancellationSignal>()

        registry.executeAppFunction(request, safeCallback, cancellationSignal)

        val exceptionCaptor = argumentCaptor<AppFunctionException>()
        verify(safeCallback).onError(exceptionCaptor.capture())
        assertThat(exceptionCaptor.firstValue.errorCode)
            .isEqualTo(AppFunctionException.ERROR_DISABLED)
    }

    @Test
    fun executeAppFunction_executorThrowsRemoteException_callsOnError() {
        val executor = createExecutorMock()
        whenever(executor.execute(any(), any(), any())).doThrow(RemoteException("Test"))
        registry.registerAppFunctions(TEST_PACKAGE, listOf(TEST_FUNCTION), executor, globalScope)

        val request = createMockExecutionRequest(TEST_PACKAGE, TEST_FUNCTION)
        val safeCallback = mock<SafeOneTimeExecuteAppFunctionCallback>()
        val executeCallback = mock<IExecuteAppFunctionCallback>()
        whenever(safeCallback.wrapToExecutionCallback()).thenReturn(executeCallback)
        val cancellationSignal = mock<ICancellationSignal>()
        val exceptionCaptor = argumentCaptor<AppFunctionException>()

        registry.executeAppFunction(request, safeCallback, cancellationSignal)

        verify(safeCallback).onError(exceptionCaptor.capture())
        assertThat(exceptionCaptor.firstValue.errorCode)
            .isEqualTo(AppFunctionException.ERROR_APP_UNKNOWN_ERROR)
    }

    @Test
    fun executeAppFunction_attachesDeathListener() {
        val binder = mock<IBinder>()
        val executor = createExecutorMock(binder)
        registry.registerAppFunctions(TEST_PACKAGE, listOf(TEST_FUNCTION), executor, globalScope)

        val request = createMockExecutionRequest(TEST_PACKAGE, TEST_FUNCTION)
        val safeCallback = mock<SafeOneTimeExecuteAppFunctionCallback>()
        val executionCallback = mock<IExecuteAppFunctionCallback>()
        whenever(safeCallback.wrapToExecutionCallback()).thenReturn(executionCallback)
        val cancellationSignal = mock<ICancellationSignal>()

        registry.executeAppFunction(request, safeCallback, cancellationSignal)

        verify(safeCallback).attachOnDeathListener(binder)
    }

    @Test
    fun executeAppFunction_binderAlreadyDied_reportsError() {
        val binder = mock<IBinder>()
        val executor = createExecutorMock(binder)
        registry.registerAppFunctions(TEST_PACKAGE, listOf(TEST_FUNCTION), executor, globalScope)

        val request = createMockExecutionRequest(TEST_PACKAGE, TEST_FUNCTION)
        val safeCallback = mock<SafeOneTimeExecuteAppFunctionCallback>()
        whenever(safeCallback.wrapToExecutionCallback())
            .thenReturn(mock<IExecuteAppFunctionCallback>())
        whenever(safeCallback.attachOnDeathListener(binder)).doThrow(RemoteException("Binder died"))
        registry.executeAppFunction(request, safeCallback, mock<ICancellationSignal>())

        val exceptionCaptor = argumentCaptor<AppFunctionException>()
        verify(safeCallback).onError(exceptionCaptor.capture())
        assertThat(exceptionCaptor.firstValue.errorCode)
            .isEqualTo(AppFunctionException.ERROR_APP_UNKNOWN_ERROR)
    }

    @Test
    fun executeAppFunction_onSuccess_unlinksExecutionDeathListener() {
        val binder = mock<IBinder>()
        val executor = createExecutorMock(binder)
        registry.registerAppFunctions(TEST_PACKAGE, listOf(TEST_FUNCTION), executor, globalScope)

        val request = createMockExecutionRequest(TEST_PACKAGE, TEST_FUNCTION)
        val safeCallback =
            SafeOneTimeExecuteAppFunctionCallback(mock<IExecuteAppFunctionCallback>())

        registry.executeAppFunction(request, safeCallback, mock<ICancellationSignal>())

        val deathRecipientCaptor = argumentCaptor<IBinder.DeathRecipient>()
        verify(binder, times(2)).linkToDeath(deathRecipientCaptor.capture(), eq(0))

        val executorCallbackCaptor = argumentCaptor<IExecuteAppFunctionCallback>()
        verify(executor).execute(any(), any(), executorCallbackCaptor.capture())

        val response = mock<ExecuteAppFunctionResponse>()
        executorCallbackCaptor.firstValue.onSuccess(response)

        // The first is registration listener which shouldn't be unlinked
        verify(binder, never()).unlinkToDeath(deathRecipientCaptor.firstValue, 0)

        // Second is execution listener should be unlinked
        verify(binder).unlinkToDeath(deathRecipientCaptor.secondValue, 0)
    }

    @Test
    fun executeAppFunction_onError_unlinksExecutionDeathListener() {
        val binder = mock<IBinder>()
        val executor = createExecutorMock(binder)
        registry.registerAppFunctions(TEST_PACKAGE, listOf(TEST_FUNCTION), executor, globalScope)

        val request = createMockExecutionRequest(TEST_PACKAGE, TEST_FUNCTION)
        val safeCallback =
            SafeOneTimeExecuteAppFunctionCallback(mock<IExecuteAppFunctionCallback>())

        registry.executeAppFunction(request, safeCallback, mock<ICancellationSignal>())

        val deathRecipientCaptor = argumentCaptor<IBinder.DeathRecipient>()
        verify(binder, times(2)).linkToDeath(deathRecipientCaptor.capture(), eq(0))

        val executorCallbackCaptor = argumentCaptor<IExecuteAppFunctionCallback>()
        verify(executor).execute(any(), any(), executorCallbackCaptor.capture())

        val error = AppFunctionException(AppFunctionException.ERROR_APP_UNKNOWN_ERROR, "Test error")
        executorCallbackCaptor.firstValue.onError(error)

        // The first is registration listener which shouldn't be unlinked
        verify(binder, never()).unlinkToDeath(deathRecipientCaptor.firstValue, 0)

        // Second is execution listener should be unlinked
        verify(binder).unlinkToDeath(deathRecipientCaptor.secondValue, 0)
    }

    @Test
    fun getAppFunctionActivityStates_returnsCorrectAssociations() {
        val executor = createExecutorMock()
        val activityId1 = AppFunctionActivityId(Binder())
        val activityId2 = AppFunctionActivityId(Binder())
        val activityId3 = AppFunctionActivityId(Binder())

        registry.registerAppFunctions(
            TEST_PACKAGE,
            listOf(TEST_FUNCTION),
            executor,
            listOf(RegistrationScopeId(activityId1)),
        )

        registry.registerAppFunctions(
            TEST_PACKAGE,
            listOf(TEST_FUNCTION2),
            executor,
            listOf(RegistrationScopeId(activityId2)),
        )

        val states =
            registry.getAppFunctionActivityStates(
                listOf(activityId1, activityId2, activityId3))

        // ActivityId3 is skipped because it is not registered.
        assertThat(states).hasSize(2)
        assertThat(states)
            .containsExactly(
                AppFunctionActivityState(
                    activityId1,
                    ArraySet(
                        listOf(AppFunctionName(TEST_PACKAGE, TEST_FUNCTION))),
                ),
                AppFunctionActivityState(
                    activityId2,
                    ArraySet(
                        listOf(AppFunctionName(TEST_PACKAGE, TEST_FUNCTION2))),
                ),
            )
    }

    @Test
    fun getRegisteredActivityIds_functionNotRegistered_returnsNull() {
        val functionName = AppFunctionName(TEST_PACKAGE, TEST_FUNCTION)

        val result = registry.getRegisteredActivityIds(functionName)

        assertThat(result).isNull()
    }

    @Test
    fun getRegisteredActivityIds_functionRegisteredWithGlobalScope_returnsNull() {
        // Verifies that getRegisteredActivityIds returns null for a globally registered function.
        val functionName = AppFunctionName(TEST_PACKAGE, TEST_FUNCTION)
        registry.registerAppFunctions(
            TEST_PACKAGE,
            listOf(TEST_FUNCTION),
            createExecutorMock(),
            globalScope,
        )

        val result = registry.getRegisteredActivityIds(functionName)

        assertThat(result).isNull()
    }

    @Test
    fun getRegisteredActivityIds_functionRegisteredWithActivityScope_returnsActivityIds() {
        // Verifies that getRegisteredActivityIds returns the correct set of activity IDs.
        val executor = createExecutorMock()
        val functionName = AppFunctionName(TEST_PACKAGE, TEST_FUNCTION)
        val activityId1 = AppFunctionActivityId(Binder())
        val activityId2 = AppFunctionActivityId(Binder())

        // Register the same function for two different activities
        registry.registerAppFunctions(
            TEST_PACKAGE,
            listOf(TEST_FUNCTION),
            executor,
            listOf(RegistrationScopeId(activityId1)),
        )
        registry.registerAppFunctions(
            TEST_PACKAGE,
            listOf(TEST_FUNCTION),
            executor,
            listOf(RegistrationScopeId(activityId2)),
        )

        val result = registry.getRegisteredActivityIds(functionName)

        assertThat(result).containsExactly(activityId1, activityId2)
    }

    private fun createExecutorMock(binder: IBinder = mock()): IAppFunctionExecutor {
        val executor = mock<IAppFunctionExecutor>()
        whenever(executor.asBinder()).thenReturn(binder)
        return executor
    }

    private fun createMockExecutionRequest(
        packageName: String,
        functionId: String,
    ): ExecuteAppFunctionRequest {
        return ExecuteAppFunctionRequest.Builder(packageName, functionId)
            .build()
    }

    private companion object {
        const val TEST_PACKAGE = "com.example.app"
        const val TEST_PACKAGE2 = "com.example.other"

        const val TEST_FUNCTION = "myFunction"
        const val TEST_FUNCTION2 = "myFunctionOther"
    }
}
