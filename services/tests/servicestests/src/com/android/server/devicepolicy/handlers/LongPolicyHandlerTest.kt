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

package com.android.server.devicepolicy.handlers

import android.app.admin.DevicePolicyManager.NOT_A_DPC
import android.app.admin.LongPolicyValue
import android.app.admin.PolicyIdentifier
import android.app.admin.PolicyValueTransport
import android.app.admin.metadata.LongPolicyMetadata
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.server.devicepolicy.IPermissionChecker
import com.android.server.devicepolicy.PolicyDefinition
import com.google.common.truth.Truth.assertThat
import kotlin.test.assertFailsWith
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

@RunWith(AndroidJUnit4::class)
class LongPolicyHandlerTest {

    val mockDelegate: PolicyHandler.Delegate =
        mock<PolicyHandler.Delegate> {
            on { getDpcType(any()) } doReturn NOT_A_DPC
            on { getPermissionChecker() } doReturn mock<IPermissionChecker> {}
        }

    fun createHandler(
        key: PolicyIdentifier<Long> = LongPolicy.key,
        metadata: LongPolicyMetadata = LongPolicy.metadata,
        definition: PolicyDefinition<Long> = LongPolicy.definition,
        delegate: PolicyHandler.Delegate = this.mockDelegate,
    ) = PolicyHandler<Long>(key, metadata, definition, delegate)

    @Test
    fun setPolicyUnchecked_shouldAcceptNull() {
        val definition = LongPolicy.definition
        val handler = createHandler(definition = definition)

        handler.setPolicyUnchecked(anyCaller, anyScope, null)

        verify(mockDelegate).clearPolicy(anyCaller, definition, anyScope)
    }

    @Test
    fun setPolicyUnchecked_shouldRejectValueOfWrongType() {
        val handler = createHandler()

        val exception =
            assertFailsWith<IllegalArgumentException> {
                handler.setPolicyUnchecked(
                    anyCaller,
                    anyScope,
                    PolicyValueTransport.stringField("not a long"),
                )
            }

        assertThat(exception.message).contains("is not a long")
    }

    @Test
    fun setPolicyUnchecked_shouldAcceptValuesWithinRange() {
        val handler = createHandler()

        handler.setPolicyUnchecked(anyCaller, anyScope, PolicyValueTransport.longField(15L))

        verify(mockDelegate)
            .storePolicy(anyCaller, LongPolicy.definition, anyScope, LongPolicyValue(15L))
    }

    @Test
    fun setPolicyUnchecked_shouldAcceptMinAndMaxValues() {
        val handler = createHandler()

        handler.setPolicyUnchecked(anyCaller, anyScope, PolicyValueTransport.longField(10L))
        handler.setPolicyUnchecked(anyCaller, anyScope, PolicyValueTransport.longField(20L))

        verify(mockDelegate)
            .storePolicy(anyCaller, LongPolicy.definition, anyScope, LongPolicyValue(10L))
        verify(mockDelegate)
            .storePolicy(anyCaller, LongPolicy.definition, anyScope, LongPolicyValue(20L))
    }

    @Test
    fun setPolicyUnchecked_shouldRejectValueTooSmall() {
        val handler = createHandler()

        val exception =
            assertFailsWith<IllegalArgumentException> {
                handler.setPolicyUnchecked(anyCaller, anyScope, PolicyValueTransport.longField(9L))
            }
        assertThat(exception.message).contains("Value 9 is not within range [10, 20]")
    }

    @Test
    fun setPolicyUnchecked_shouldRejectValueTooLarge() {
        val handler = createHandler()

        val exception =
            assertFailsWith<IllegalArgumentException> {
                handler.setPolicyUnchecked(anyCaller, anyScope, PolicyValueTransport.longField(21L))
            }
        assertThat(exception.message).contains("Value 21 is not within range [10, 20]")
    }
}
