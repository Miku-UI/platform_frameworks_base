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
import android.app.admin.PolicyValueTransport
import android.app.admin.metadata.IntegerPolicyMetadata
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.server.devicepolicy.IPermissionChecker
import com.google.common.truth.Truth.assertThat
import kotlin.test.assertFailsWith
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

@RunWith(AndroidJUnit4::class)
class IntegerPolicyHandlerTest {

    val mockDelegate: PolicyHandler.Delegate =
        mock<PolicyHandler.Delegate> {
            on { getDpcType(any()) } doReturn NOT_A_DPC
            on { getPermissionChecker() } doReturn mock<IPermissionChecker> {}
        }

    private fun createHandler(
        metadata: IntegerPolicyMetadata = IntegerPolicy.metadata
    ): PolicyHandler<Int> =
        PolicyHandler<Int>(IntegerPolicy.key, metadata, IntegerPolicy.definition, mockDelegate)

    @Test
    fun setPolicyUnchecked_inRangeValue_succeeds() {
        val handler = createHandler()

        handler.setPolicyUnchecked(anyCaller, anyScope, PolicyValueTransport.integerField(0))
        handler.setPolicyUnchecked(anyCaller, anyScope, PolicyValueTransport.integerField(50))
    }

    @Test
    fun setPolicyUnchecked_boundaryValues_succeeds() {
        val handler = createHandler()

        handler.setPolicyUnchecked(
            anyCaller,
            anyScope,
            PolicyValueTransport.integerField(IntegerPolicy.MIN),
        )
        handler.setPolicyUnchecked(
            anyCaller,
            anyScope,
            PolicyValueTransport.integerField(IntegerPolicy.MAX),
        )
    }

    @Test
    fun setPolicyUnchecked_belowRange_throws() {
        val handler = createHandler()

        val exception =
            assertFailsWith<IllegalArgumentException> {
                handler.setPolicyUnchecked(
                    anyCaller,
                    anyScope,
                    PolicyValueTransport.integerField(IntegerPolicy.MIN - 1),
                )
            }
        assertThat(exception).hasMessageThat().contains("is not in the range")
    }

    @Test
    fun setPolicyUnchecked_aboveRange_throws() {
        val handler = createHandler()

        val exception =
            assertFailsWith<IllegalArgumentException> {
                handler.setPolicyUnchecked(
                    anyCaller,
                    anyScope,
                    PolicyValueTransport.integerField(IntegerPolicy.MAX + 1),
                )
            }
        assertThat(exception).hasMessageThat().contains("is not in the range")
    }

    @Test
    fun setPolicyUnchecked_noLimits_succeeds() {
        val noLimitMetadata =
            IntegerPolicy.metadata.copy(minValue = Int.MIN_VALUE, maxValue = Int.MAX_VALUE)
        val handler = createHandler(noLimitMetadata)

        handler.setPolicyUnchecked(
            anyCaller,
            anyScope,
            PolicyValueTransport.integerField(Int.MIN_VALUE),
        )
        handler.setPolicyUnchecked(
            anyCaller,
            anyScope,
            PolicyValueTransport.integerField(Int.MAX_VALUE),
        )
    }
}
