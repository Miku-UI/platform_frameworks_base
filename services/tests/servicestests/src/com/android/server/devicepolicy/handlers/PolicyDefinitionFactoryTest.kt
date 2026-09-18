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

import android.app.admin.DevicePolicyManager.POLICY_SCOPE_DEVICE
import android.app.admin.DevicePolicyManager.POLICY_SCOPE_PARENT_USER
import android.app.admin.DevicePolicyManager.POLICY_SCOPE_USER
import android.app.admin.PolicyIdentifier
import android.app.admin.metadata.PolicyMetadata
import android.app.admin.metadata.ResolutionMechanismMetadata
import com.google.common.truth.Truth.assertThat
import com.google.testing.junit.testparameterinjector.TestParameter
import com.google.testing.junit.testparameterinjector.TestParameterInjector
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.times

@RunWith(TestParameterInjector::class)
open class PolicyDefinitionFactoryTest {

    val FIRST_ENUM_VALUE = EnumPolicy.VALUE_1
    val LAST_ENUM_VALUE = EnumPolicy.VALUE_2

    fun <T> createPolicyDefinition(metadata: PolicyMetadata<T>) =
        PolicyDefinitionFactory.createPrePopulatedBuilder(metadata).build()

    enum class PolicyDefinitionGlobalFlagTestCase(
        val allowedScopes: Set<Int>,
        val expectedValue: Boolean,
    ) {
        SCOPE_DEVICE(allowedScopes = setOf(POLICY_SCOPE_DEVICE), expectedValue = true),
        SCOPE_USER(allowedScopes = setOf(POLICY_SCOPE_USER), expectedValue = false),
        SCOPE_PARENT_USER(allowedScopes = setOf(POLICY_SCOPE_PARENT_USER), expectedValue = false),
        SCOPE_DEVICE_AND_USER(
            allowedScopes = setOf(POLICY_SCOPE_DEVICE, POLICY_SCOPE_USER),
            expectedValue = false,
        ),
        SCOPE_DEVICE_AND_PARENT_USER(
            allowedScopes = setOf(POLICY_SCOPE_DEVICE, POLICY_SCOPE_PARENT_USER),
            expectedValue = false,
        ),
    }

    @Test
    fun getPolicyDefinition_globalOnlyFlag(
        @TestParameter testCase: PolicyDefinitionGlobalFlagTestCase
    ) {
        val metadata = StringPolicy.metadata.copy(allowedScopes = testCase.allowedScopes)

        val policyDefinition = createPolicyDefinition(metadata)

        assertThat(policyDefinition.isGlobalOnlyPolicy()).isEqualTo(testCase.expectedValue)
    }

    enum class PolicyDefinitionLocalFlagTestCase(
        val allowedScopes: Set<Int>,
        val expectedValue: Boolean,
    ) {
        SCOPE_DEVICE(allowedScopes = setOf(POLICY_SCOPE_DEVICE), expectedValue = false),
        SCOPE_USER(allowedScopes = setOf(POLICY_SCOPE_USER), expectedValue = true),
        SCOPE_PARENT_USER(allowedScopes = setOf(POLICY_SCOPE_PARENT_USER), expectedValue = true),
        SCOPE_DEVICE_AND_USER(
            allowedScopes = setOf(POLICY_SCOPE_DEVICE, POLICY_SCOPE_USER),
            expectedValue = false,
        ),
        SCOPE_DEVICE_AND_PARENT_USER(
            allowedScopes = setOf(POLICY_SCOPE_DEVICE, POLICY_SCOPE_PARENT_USER),
            expectedValue = false,
        ),
        SCOPE_USER_AND_PARENT_USER(
            allowedScopes = setOf(POLICY_SCOPE_USER, POLICY_SCOPE_PARENT_USER),
            expectedValue = true,
        ),
        SCOPE_ALL(allowedScopes = allScopes, expectedValue = false),
    }

    @Test
    fun getPolicyDefinition_localOnlyFlag(
        @TestParameter testCase: PolicyDefinitionLocalFlagTestCase
    ) {
        val metadata = StringPolicy.metadata.copy(allowedScopes = testCase.allowedScopes)

        val policyDefinition = createPolicyDefinition(metadata)

        assertThat(policyDefinition.isLocalOnlyPolicy()).isEqualTo(testCase.expectedValue)
    }

    @Test
    fun getPolicyDefinition_key() {

        val policyDefinition =
            createPolicyDefinition(
                StringPolicy.metadata.copy(id = PolicyIdentifier<String>("THE_POLICY_NAME"))
            )

        assertThat(policyDefinition.getPolicyKey().getIdentifier()).isEqualTo("THE_POLICY_NAME")
    }

    @Test
    fun getPolicyDefinition_resolutionMechanism_mostRestrictive() {
        val metadata =
            EnumPolicy.metadata.copy(
                resolutionMechanism =
                    ResolutionMechanismMetadata.MostRestrictive<Int>(
                        listOf(LAST_ENUM_VALUE, FIRST_ENUM_VALUE)
                    )
            )

        val policyDefinition = createPolicyDefinition(metadata)

        val expected =
            "MostRestrictive { " +
                "mMostToLeastRestrictive= " +
                "[" +
                "IntegerPolicyValue { mValue= 2 }, " +
                "IntegerPolicyValue { mValue= 1 }" +
                "] " +
                "}"
        assertThat(policyDefinition.getResolutionMechanism().toString()).isEqualTo(expected)
    }

    @Test
    fun getPolicyDefinition_resolutionMechanism_listUnionOnStringPolicy() {
        val metadata =
            ListOfStringPolicy.metadata.copy(
                resolutionMechanism = ResolutionMechanismMetadata.ListUnion<List<String>>()
            )

        val policyDefinition = createPolicyDefinition(metadata)

        val expected = "ListUnion<String> {}"
        assertThat(policyDefinition.getResolutionMechanism().toString()).isEqualTo(expected)
    }

    @Test
    fun getPolicyDefinition_resolutionMechanism_notCoexistable() {
        val metadata =
            EnumPolicy.metadata.copy(
                resolutionMechanism = ResolutionMechanismMetadata.NotCoexistable()
            )

        val policyDefinition = createPolicyDefinition(metadata)

        val expected = "LeastRecent {}"
        assertThat(policyDefinition.getResolutionMechanism().toString()).isEqualTo(expected)
    }
}
