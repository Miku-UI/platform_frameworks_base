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

package com.android.server.devicepolicy.handlers

import android.app.admin.DevicePolicyManager.NOT_A_DPC
import android.app.admin.ListOfStringPolicyValue
import android.app.admin.PolicyIdentifier
import android.app.admin.PolicyValueTransport
import android.app.admin.metadata.ListPolicyMetadata
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.server.devicepolicy.IPermissionChecker
import com.android.server.devicepolicy.PolicyDefinition
import com.google.common.truth.Truth.assertThat
import kotlin.test.assertFailsWith
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify

@RunWith(AndroidJUnit4::class)
class ListOfStringPolicyHandlerTest {

    val mockDelegate: PolicyHandler.Delegate =
        mock<PolicyHandler.Delegate> {
            on { getDpcType(any()) } doReturn NOT_A_DPC
            on { getPermissionChecker() } doReturn mock<IPermissionChecker>()
        }

    fun createHandler(
        key: PolicyIdentifier<List<String>> = ListOfStringPolicy.id,
        metadata: ListPolicyMetadata<String> = ListOfStringPolicy.metadata,
        definition: PolicyDefinition<List<String>> = ListOfStringPolicy.definition,
        delegate: PolicyHandler.Delegate = this.mockDelegate,
    ) = PolicyHandler<List<String>>(key, metadata, definition, delegate)

    @Test
    fun setPolicyUnchecked_emptyListDisallowed_shouldRejectEmptyList() {
        val metadata = ListOfStringPolicy.metadata.copy(emptyListAllowed = false)
        val handler = createHandler(metadata = metadata, delegate = mockDelegate)

        val error =
            assertFailsWith<IllegalArgumentException> {
                handler.setPolicyUnchecked(
                    anyCaller,
                    anyScope,
                    PolicyValueTransport.listOfStringField(listOf()),
                )
            }

        assertThat(error.message).contains("Empty list is not allowed")
        verify(mockDelegate, never()).storePolicy<List<String>>(any(), any(), any(), any())
    }

    @Test
    fun setPolicyUnchecked_emptyListAllowed_shouldAllowEmptyList() {
        val metadata = ListOfStringPolicy.metadata.copy(emptyListAllowed = true)
        val handler = createHandler(metadata = metadata, delegate = mockDelegate)

        handler.setPolicyUnchecked(
            anyCaller,
            anyScope,
            PolicyValueTransport.listOfStringField(listOf()),
        )
        val expectedValue = ListOfStringPolicyValue(listOf())
        verify(mockDelegate, times(1)).storePolicy(any(), any(), any(), eq(expectedValue))

        verify(mockDelegate, never()).clearPolicy<List<String>>(any(), any(), any())
    }

    @Test
    fun setPolicyUnchecked_shouldHandleNull() {
        val handler = createHandler(delegate = mockDelegate)

        handler.setPolicyUnchecked(anyCaller, anyScope, null)

        verify(mockDelegate).clearPolicy<List<String>>(any(), any(), any())
        verify(mockDelegate, never()).storePolicy<List<String>>(any(), any(), any(), any())
    }

    @Test
    fun setPolicyUnchecked_shouldHandleValidValues() {
        val listOfStrings = listOf("test_value_1", "test_value_2", "test_value_3")
        val handler = createHandler(delegate = mockDelegate)

        handler.setPolicyUnchecked(
            anyCaller,
            anyScope,
            PolicyValueTransport.listOfStringField(listOfStrings),
        )

        val expectedValue = ListOfStringPolicyValue(listOfStrings)
        verify(mockDelegate, times(1)).storePolicy(any(), any(), any(), eq(expectedValue))

        verify(mockDelegate, never()).clearPolicy<List<String>>(any(), any(), any())
    }

    @Test
    fun setPolicyUnchecked_wrongValueType_shouldReject() {
        val handler = createHandler(delegate = mockDelegate)

        assertFailsWith<IllegalArgumentException> {
            handler.setPolicyUnchecked(
                anyCaller,
                anyScope,
                PolicyValueTransport.stringField("wrongType"),
            )
        }

        verify(mockDelegate, never()).storePolicy<List<String>>(any(), any(), any(), any())
        verify(mockDelegate, never()).clearPolicy<List<String>>(any(), any(), any())
    }
}
