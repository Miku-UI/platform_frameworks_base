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
import android.app.admin.PolicyIdentifier
import android.app.admin.PolicyValueTransport
import android.app.admin.StringPolicyValue
import android.app.admin.metadata.StringPolicyMetadata
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
class StringPolicyHandlerTest {

    val mockDelegate: PolicyHandler.Delegate =
        mock<PolicyHandler.Delegate> {
            on { getDpcType(any()) } doReturn NOT_A_DPC
            on { getPermissionChecker() } doReturn mock<IPermissionChecker> {}
        }

    fun createHandler(
        key: PolicyIdentifier<String> = StringPolicy.key,
        metadata: StringPolicyMetadata = StringPolicy.metadata,
        definition: PolicyDefinition<String> = StringPolicy.definition,
        delegate: PolicyHandler.Delegate = this.mockDelegate,
    ) = PolicyHandler<String>(key, metadata, definition, delegate)

    @Test
    fun setPolicyUnchecked_shouldAcceptNull() {
        val definition = StringPolicy.definition
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
                    PolicyValueTransport.booleanField(true),
                )
            }

        assertThat(exception.message).contains("is not a string")
    }

    @Test
    fun setPolicyUnchecked_shouldAcceptNonEmptyStrings() {
        val handler = createHandler()

        handler.setPolicyUnchecked(
            anyCaller,
            anyScope,
            PolicyValueTransport.stringField("It's a string"),
        )

        verify(mockDelegate)
            .storePolicy(
                anyCaller,
                StringPolicy.definition,
                anyScope,
                StringPolicyValue("It's a string"),
            )
    }

    @Test
    fun setPolicyUnchecked_shouldRejectEmptyString() {
        val metadataBlockingEmptyString = StringPolicy.metadata.copy(emptyStringAllowed = false)
        val handler = createHandler(metadata = metadataBlockingEmptyString)

        val exception =
            assertFailsWith<IllegalArgumentException> {
                handler.setPolicyUnchecked(
                    anyCaller,
                    anyScope,
                    PolicyValueTransport.stringField(""),
                )
            }
        assertThat(exception.message)
            .contains("Empty string is not allowed for policy ${StringPolicy.key}")
    }

    @Test
    fun setPolicyUnchecked_shouldAcceptEmptyString() {
        val metadataAllowingEmptyString = StringPolicy.metadata.copy(emptyStringAllowed = true)
        val handler = createHandler(metadata = metadataAllowingEmptyString)

        handler.setPolicyUnchecked(anyCaller, anyScope, PolicyValueTransport.stringField(""))

        verify(mockDelegate)
            .storePolicy(anyCaller, StringPolicy.definition, anyScope, StringPolicyValue(""))
    }

    @Test
    fun setPolicyUnchecked_unprintableCharactersAllowed_shouldAllowUnprintableCharacters() {
        val metadataAllowingUnprintableCharacters =
            StringPolicy.metadata.copy(unprintableCharactersAllowed = true)
        val handler = createHandler(metadata = metadataAllowingUnprintableCharacters)

        handler.setPolicyUnchecked(
            anyCaller,
            anyScope,
            PolicyValueTransport.stringField("test_value_1\u0000"),
        )

        verify(mockDelegate)
            .storePolicy(
                anyCaller,
                StringPolicy.definition,
                anyScope,
                StringPolicyValue("test_value_1\u0000"),
            )
    }

    @Test
    fun setPolicyUnchecked_unprintableCharactersDisallowed_shouldRejectUnprintableCharacters() {
        val metadataBlockingUnprintableCharacters =
            StringPolicy.metadata.copy(unprintableCharactersAllowed = false)
        val handler = createHandler(metadata = metadataBlockingUnprintableCharacters)

        val exception =
            assertFailsWith<IllegalArgumentException> {
                handler.setPolicyUnchecked(
                    anyCaller,
                    anyScope,
                    PolicyValueTransport.stringField("test_value_1\u0000"),
                )
            }
        assertThat(exception.message)
            .contains("Unprintable characters are not allowed for policy ${StringPolicy.key}")
    }

    @Test
    fun setPolicyUnchecked_maxLength_shouldRejectLongerStrings() {
        val metadataWithMaxLength = StringPolicy.metadata.copy(maxLength = 10)
        val handler = createHandler(metadata = metadataWithMaxLength)

        val exception =
            assertFailsWith<IllegalArgumentException> {
                handler.setPolicyUnchecked(
                    anyCaller,
                    anyScope,
                    PolicyValueTransport.stringField("a".repeat(11)),
                )
            }
        assertThat(exception.message)
            .contains("is longer than the maximum length 10 for policy ${StringPolicy.key}")
    }
}
