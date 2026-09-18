/*
 * Copyright (C) 2026 The Android Open Source Project
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
import android.app.appfunctions.AppFunctionName
import android.app.appfunctions.IAppFunctionExecutor
import android.os.Binder
import android.os.IBinder
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class DynamicAppFunctionsRegistrationStoreTest {

    private lateinit var store: DynamicAppFunctionsRegistrationStore

    @Before
    fun setUp() {
        store = DynamicAppFunctionsRegistrationStore()
    }

    @Test
    fun addRegistration_hasRegistration() {
        val name = AppFunctionName("pkg", "func")
        val scopeId = createActivityScopeId()
        val executor = createExecutorMock()

        store.putRegistration(name, scopeId, executor)

        assertThat(store.hasRegistration(name, scopeId)).isTrue()
        assertThat(store.hasFunction(name)).isTrue()
        assertThat(store.hasExecutor(executor.asBinder())).isTrue()
    }

    @Test
    fun getExecutor_returnsCorrectExecutor() {
        val name = AppFunctionName("pkg", "func")
        val scopeId = createActivityScopeId()
        val executor = createExecutorMock()

        store.putRegistration(name, scopeId, executor)

        assertThat(store.getExecutor(name, scopeId)).isEqualTo(executor)
    }

    @Test
    fun getScopes_returnsCorrectScopes() {
        val name = AppFunctionName("pkg", "func")
        val scopeId1 = createActivityScopeId()
        val scopeId2 = createActivityScopeId()
        val executor = createExecutorMock()

        store.putRegistration(name, scopeId1, executor)
        store.putRegistration(name, scopeId2, executor)

        val scopes = store.getScopes(name)
        assertThat(scopes).containsExactly(scopeId1, scopeId2)
    }

    @Test
    fun getFunctionsByScope_returnsCorrectFunctions() {
        val name1 = AppFunctionName("pkg", "func1")
        val name2 = AppFunctionName("pkg", "func2")
        val scopeId = createActivityScopeId()
        val executor = createExecutorMock()

        store.putRegistration(name1, scopeId, executor)
        store.putRegistration(name2, scopeId, executor)

        val functions = store.getFunctionsByScope(scopeId)
        assertThat(functions).containsExactly(name1, name2)
    }

    @Test
    fun getRegistrationsByExecutor_returnsCorrectRegistrations() {
        val name1 = AppFunctionName("pkg", "func1")
        val name2 = AppFunctionName("pkg", "func2")
        val scopeId = createActivityScopeId()
        val executor = createExecutorMock()

        store.putRegistration(name1, scopeId, executor)
        store.putRegistration(name2, scopeId, executor)

        val registrations = store.getRegistrationsByExecutor(executor.asBinder())
        assertThat(registrations).containsExactly(
            AppFunctionRegistrationId(name1, scopeId),
            AppFunctionRegistrationId(name2, scopeId)
        )
    }

    @Test
    fun getScopeIdByExecutor_returnsCorrectScopeId() {
        val name = AppFunctionName("pkg", "func")
        val scopeId = createActivityScopeId()
        val executor = createExecutorMock()

        store.putRegistration(name, scopeId, executor)

        assertThat(store.getScopeIdByExecutor(name, executor.asBinder())).isEqualTo(scopeId)
    }

    @Test
    fun removeRegistration_removesCorrectRegistration() {
        val name = AppFunctionName("pkg", "func")
        val scopeId = createActivityScopeId()
        val executor = createExecutorMock()

        store.putRegistration(name, scopeId, executor)
        store.removeRegistration(name, scopeId, executor.asBinder())

        assertThat(store.hasRegistration(name, scopeId)).isFalse()
        assertThat(store.hasFunction(name)).isFalse()
        assertThat(store.hasExecutor(executor.asBinder())).isFalse()
    }

    @Test
    fun removeRegistrationsForExecutor_removesAllAndReturns() {
        val name1 = AppFunctionName("pkg", "func1")
        val name2 = AppFunctionName("pkg", "func2")
        val scopeId = createActivityScopeId()
        val executor = createExecutorMock()

        store.putRegistration(name1, scopeId, executor)
        store.putRegistration(name2, scopeId, executor)

        val removed = store.removeRegistrationsForExecutor(executor.asBinder())

        assertThat(removed).containsExactly(
            AppFunctionRegistrationId(name1, scopeId),
            AppFunctionRegistrationId(name2, scopeId)
        )
        assertThat(store.hasExecutor(executor.asBinder())).isFalse()
        assertThat(store.hasFunction(name1)).isFalse()
        assertThat(store.hasFunction(name2)).isFalse()
    }

    @Test
    fun removeRegistration_doesNotRemoveOtherScopes() {
        val name = AppFunctionName("pkg", "func")
        val scopeId1 = createActivityScopeId()
        val scopeId2 = createActivityScopeId()
        val executor = createExecutorMock()

        store.putRegistration(name, scopeId1, executor)
        store.putRegistration(name, scopeId2, executor)

        store.removeRegistration(name, scopeId1, executor.asBinder())

        assertThat(store.hasRegistration(name, scopeId1)).isFalse()
        assertThat(store.hasRegistration(name, scopeId2)).isTrue()
        assertThat(store.hasFunction(name)).isTrue()
    }

    private fun createActivityScopeId(): RegistrationScopeId {
        return RegistrationScopeId(AppFunctionActivityId(Binder()))
    }

    private fun createExecutorMock(binder: IBinder = mock()): IAppFunctionExecutor {
        val executor = mock<IAppFunctionExecutor>()
        whenever(executor.asBinder()).thenReturn(binder)
        return executor
    }
}
