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
import android.app.admin.IntegerPolicyValue
import android.app.admin.PolicyIdentifier
import android.app.admin.PolicyValueTransport
import android.app.admin.metadata.EnumPolicyMetadata
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
class EnumPolicyHandlerTest {

    val mockDelegate: PolicyHandler.Delegate =
        mock<PolicyHandler.Delegate> {
            on { getDpcType(any()) } doReturn NOT_A_DPC
            on { getPermissionChecker() } doReturn mock<IPermissionChecker>()
        }

    fun createHandler(
        key: PolicyIdentifier<Int> = EnumPolicy.key,
        metadata: EnumPolicyMetadata = EnumPolicy.metadata,
        definition: PolicyDefinition<Int> = EnumPolicy.definition,
        delegate: PolicyHandler.Delegate = this.mockDelegate,
    ) = PolicyHandler<Int>(key, metadata, definition, delegate)

    @Test
    fun setPolicyUnchecked_shouldAcceptValidValues() {
        val enumValues = setOf(123, 456, 789)
        val metadata = EnumPolicy.metadata.copy(allowedValues = enumValues)
        val handler = createHandler(metadata = metadata, delegate = mockDelegate)

        for (enumValue in enumValues) {
            handler.setPolicyUnchecked(
                anyCaller,
                anyScope,
                PolicyValueTransport.integerField(enumValue),
            )

            val expectedValue = IntegerPolicyValue(enumValue)
            verify(mockDelegate, times(1)).storePolicy(any(), any(), any(), eq(expectedValue))
        }

        verify(mockDelegate, never()).clearPolicy<Int>(any(), any(), any())
    }

    @Test
    fun setPolicyUnchecked_shouldAcceptNull() {
        val handler = createHandler(delegate = mockDelegate)

        handler.setPolicyUnchecked(anyCaller, anyScope, null)

        verify(mockDelegate).clearPolicy<Int>(any(), any(), any())
        verify(mockDelegate, never()).storePolicy<Int>(any(), any(), any(), any())
    }

    @Test
    fun setPolicyUnchecked_shouldRejectValuesOutOfRange() {
        val validEnumValues = setOf(555, 666)
        val metadata = EnumPolicy.metadata.copy(allowedValues = validEnumValues)
        val handler = createHandler(metadata = metadata)

        val invalidEnumValues = setOf(0, 1, 554, 556, 665, 667, 1000)

        for (enumValue in invalidEnumValues) {
            assertFailsWith<IllegalArgumentException> {
                handler.setPolicyUnchecked(
                    anyCaller,
                    anyScope,
                    PolicyValueTransport.integerField(enumValue),
                )
            }
        }
    }

    @Test
    fun setPolicyUnchecked_shouldRejectValueOfWrongType() {
        val handler = createHandler()

        val exception =
            assertFailsWith<IllegalArgumentException> {
                handler.setPolicyUnchecked(
                    anyCaller,
                    anyScope,
                    PolicyValueTransport.booleanField(true),
                )
            }

        assertThat(exception.message).contains("is not an integer")
    }
}
