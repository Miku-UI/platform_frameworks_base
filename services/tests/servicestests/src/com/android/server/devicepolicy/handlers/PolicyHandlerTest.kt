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

import android.app.admin.DevicePolicyManager.DEVICE_OWNER
import android.app.admin.DevicePolicyManager.FINANCED_DEVICE_OWNER
import android.app.admin.DevicePolicyManager.MANAGED_PROFILE_OWNER_OF_PERSONAL_OWNED_DEVICE
import android.app.admin.DevicePolicyManager.NOT_A_DPC
import android.app.admin.DevicePolicyManager.POLICY_SCOPE_DEVICE
import android.app.admin.DevicePolicyManager.POLICY_SCOPE_PARENT_USER
import android.app.admin.DevicePolicyManager.POLICY_SCOPE_USER
import android.app.admin.DevicePolicyManager.RESOURCE_DEVICE_WIDE
import android.app.admin.DevicePolicyManager.RESOURCE_PER_USER
import android.app.admin.IntegerPolicyValue
import android.app.admin.PolicyIdentifier
import android.app.admin.PolicyValueTransport
import android.app.admin.metadata.EnumPolicyMetadata
import android.app.admin.metadata.PolicyMetadata
import android.app.admin.metadata.ResolutionMechanismMetadata
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.server.devicepolicy.CallerIdentity
import com.android.server.devicepolicy.IPermissionChecker
import com.android.server.devicepolicy.PolicyDefinition
import com.google.common.truth.Truth.assertThat
import kotlin.test.assertFailsWith
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.stub
import org.mockito.kotlin.times
import org.mockito.kotlin.verify

// This class contains all `PolicyHandler` tests that are independent of the policy value type.
// Type specific tests (for enum vs string vs int vs ...) should go in the correct
// <Type>PolicyHandlerTest class.
@RunWith(AndroidJUnit4::class)
open class PolicyHandlerTest {

    data class EnforceArguments(val permission: String, val caller: CallerIdentity)

    private val permissionChecker =
        object : IPermissionChecker {
            val enforceCalls = mutableListOf<EnforceArguments>()
            val hasPermissionsAllowed = mutableSetOf<EnforceArguments>()

            override fun enforce(permission: String, caller: CallerIdentity) {
                enforceCalls.add(EnforceArguments(permission, caller))
            }

            override fun hasPermission(permission: String, caller: CallerIdentity) =
                hasPermissionsAllowed.contains(EnforceArguments(permission, caller))

            fun assertEnforcesPermissions(caller: CallerIdentity, vararg permission: String) {
                assertThat(enforceCalls).isEqualTo(permission.map { EnforceArguments(it, caller) })
            }
        }

    private abstract class CallCheckingHandler<T>(
        id: PolicyIdentifier<T>,
        metadata: PolicyMetadata<T>,
        definition: PolicyDefinition<T>,
        delegate: Delegate,
    ) : PolicyHandler<T>(id, metadata, definition, delegate) {

        val methodCalls = mutableListOf<String>()

        abstract fun valueConstructor(): T

        abstract fun transportConstructor(): PolicyValueTransport

        override fun convertValue(transportValue: PolicyValueTransport?): T {
            methodCalls.add("convertValue from transport")
            return valueConstructor()
        }

        override fun convertValue(value: T?): PolicyValueTransport? {
            methodCalls.add("convertValue to transport")
            return transportConstructor()
        }

        override fun checkPermissions(caller: CallerIdentity, scope: Int) {
            methodCalls.add("checkPermissions")
        }

        override fun validateValue(caller: CallerIdentity, value: T?) {
            methodCalls.add("validateValue")
        }

        override fun storePolicyValue(caller: CallerIdentity, scope: Int, value: T?) {
            methodCalls.add("storePolicyValue")
        }

        override fun getPolicyValue(caller: CallerIdentity, scope: Int): T? {
            methodCalls.add("getPolicyValue")
            return null
        }

        override fun getResolvedPerUserPolicyValue(userId: Int): T? {
            methodCalls.add("getResolvedPerUserPolicyValue")
            return null
        }

        override fun getResolvedDeviceWidePolicyValue(): T? {
            methodCalls.add("getResolvedDeviceWidePolicyValue")
            return null
        }
    }

    val mockDelegate: PolicyHandler.Delegate =
        mock<PolicyHandler.Delegate> {
            on { getDpcType(any()) } doReturn NOT_A_DPC
            on { getPermissionChecker() } doReturn permissionChecker
        }

    private val intCallCheckingHandler =
        object :
            CallCheckingHandler<Int>(
                EnumPolicy.key,
                EnumPolicy.metadata,
                EnumPolicy.definition,
                mockDelegate,
            ) {
            override fun valueConstructor() = 5

            override fun transportConstructor() = PolicyValueTransport.integerField(5)
        }

    fun createHandler(
        key: PolicyIdentifier<Int> = EnumPolicy.key,
        metadata: PolicyMetadata<Int> = EnumPolicy.metadata,
        definition: PolicyDefinition<Int> = EnumPolicy.definition,
        delegate: PolicyHandler.Delegate = this.mockDelegate,
    ) = PolicyHandler<Int>(key, metadata, definition, delegate)

    @Test
    fun setPolicyUnchecked_shouldCallMethodsInOrder() {
        val handler = intCallCheckingHandler

        handler.setPolicyUnchecked(anyCaller, anyScope, EnumPolicy.anyTransportValue)

        assertThat(intCallCheckingHandler.methodCalls)
            .isEqualTo(listOf("convertValue from transport", "validateValue", "storePolicyValue"))
    }

    @Test
    fun setPolicyUnchecked_shouldValidateAllowedScope() {
        val allAllowedScopes = setOf(POLICY_SCOPE_DEVICE, POLICY_SCOPE_PARENT_USER)
        val someDisallowedScopes = setOf(POLICY_SCOPE_USER, 111, 666)
        val metadata = EnumPolicy.metadata.copy(allowedScopes = allAllowedScopes)
        val handler = createHandler(metadata = metadata)

        // This should not throw exceptions
        for (scope in allAllowedScopes) {
            handler.setPolicyUnchecked(anyCaller, scope, EnumPolicy.anyTransportValue)
        }

        // This should throw exceptions
        for (scope in someDisallowedScopes) {
            assertFailsWith<IllegalArgumentException> {
                handler.setPolicyUnchecked(anyCaller, scope, EnumPolicy.anyTransportValue)
            }
        }
    }

    @Test
    fun setPolicyUnchecked_shouldStorePolicy() {
        val theCaller = anyCaller
        val theValue = EnumPolicy.VALUE_1
        val theKey = EnumPolicy.definition
        val handler =
            createHandler(
                metadata = EnumPolicy.metadata.copy(allowedScopes = allScopes),
                definition = theKey,
            )

        for (scope in allScopes) {
            handler.setPolicyUnchecked(
                theCaller,
                scope,
                PolicyValueTransport.integerField(theValue),
            )

            verify(mockDelegate, times(1))
                .storePolicy(theCaller, theKey, scope, IntegerPolicyValue(theValue))
        }

        // There should be no calls to `clearPolicy`
        verify(mockDelegate, never()).clearPolicy<Int>(any(), any(), any())
    }

    @Test
    fun setPolicyUnchecked_shouldClearNullPolicy() {
        val theCaller = anyCaller
        val theKey = EnumPolicy.definition
        val handler =
            createHandler(
                metadata = EnumPolicy.metadata.copy(allowedScopes = allScopes),
                delegate = mockDelegate,
                definition = theKey,
            )

        for (scope in allScopes) {
            handler.setPolicyUnchecked(theCaller, scope, null)

            verify(mockDelegate, times(1)).clearPolicy(theCaller, theKey, scope)
        }

        // There should be no calls to `storePolicy`
        verify(mockDelegate, never()).storePolicy<Int>(any(), any(), any(), any())
    }

    @Test
    fun checkPermissions_scopeUser_shouldCheckPermission() {
        val metadata =
            EnumPolicy.metadata.copy(
                allowedScopes = setOf(POLICY_SCOPE_USER),
                requiredPermission = "thePermission",
                requiredCrossUserPermission = "shouldNotBeChecked",
            )
        val handler = createHandler(metadata = metadata)
        val theCaller = anyCaller

        handler.checkPermissions(theCaller, POLICY_SCOPE_USER)

        permissionChecker.assertEnforcesPermissions(theCaller, "thePermission")
    }

    @Test
    fun checkPermissions_scopeGlobal_shouldCheckPermissionAndCrossUserPermission() {
        val metadata =
            EnumPolicy.metadata.copy(
                allowedScopes = setOf(POLICY_SCOPE_DEVICE),
                requiredPermission = "thePermission",
                requiredCrossUserPermission = "theCrossUserPermission",
            )
        val handler = createHandler(metadata = metadata)
        val theCaller = anyCaller

        handler.checkPermissions(theCaller, POLICY_SCOPE_DEVICE)

        permissionChecker.assertEnforcesPermissions(
            theCaller,
            "thePermission",
            "theCrossUserPermission",
        )
    }

    @Test
    fun checkPermissions_scopeParent_shouldCheckPermissionAndCrossUserPermission() {
        val metadata =
            EnumPolicy.metadata.copy(
                allowedScopes = setOf(POLICY_SCOPE_PARENT_USER),
                requiredPermission = "permission",
                requiredCrossUserPermission = "crossUserPermission",
            )
        val handler = createHandler(metadata = metadata)
        val theCaller = anyCaller

        handler.checkPermissions(theCaller, POLICY_SCOPE_PARENT_USER)

        permissionChecker.assertEnforcesPermissions(theCaller, "permission", "crossUserPermission")
    }

    @Test
    fun checkPermissions_acceptedDpcTypes_shouldNotCheckPermissionIfDpcTypeIsAccepted() {
        val metadata =
            EnumPolicy.metadata.copy(
                requiredPermission = "thePermissionThatShallNotBeChecked",
                allowedDpcTypes =
                    setOf(DEVICE_OWNER, MANAGED_PROFILE_OWNER_OF_PERSONAL_OWNED_DEVICE),
            )
        val handler = createHandler(metadata = metadata)

        mockDelegate.stub { on { getDpcType(any()) } doReturn DEVICE_OWNER }

        handler.checkPermissions(anyCaller, anyScope)

        assertThat(permissionChecker.enforceCalls).isEmpty()
    }

    @Test
    fun checkPermissions_acceptedDpcTypes_shouldCheckPermissionIfDpcTypeIsNotAccepted() {
        val metadata =
            EnumPolicy.metadata.copy(
                requiredPermission = "thePermissionThatShallBeChecked",
                allowedDpcTypes =
                    setOf(DEVICE_OWNER, MANAGED_PROFILE_OWNER_OF_PERSONAL_OWNED_DEVICE),
            )
        val handler = createHandler(metadata = metadata)
        val theCaller = anyCaller

        mockDelegate.stub { on { getDpcType(any()) } doReturn FINANCED_DEVICE_OWNER }

        handler.checkPermissions(theCaller, anyScope)

        permissionChecker.assertEnforcesPermissions(theCaller, "thePermissionThatShallBeChecked")
    }

    @Test
    fun checkPermissions_acceptedDpcTypes_shouldStillCheckCrossUserPermissionIfDpcTypeIsAccepted() {
        val metadata =
            EnumPolicy.metadata.copy(
                allowedScopes = setOf(POLICY_SCOPE_DEVICE),
                requiredPermission = "thePermissionThatShallNotBeChecked",
                requiredCrossUserPermission = "theCrossUserPermissionThatShallBeChecked",
                allowedDpcTypes = setOf(DEVICE_OWNER),
            )
        val handler = createHandler(metadata = metadata)
        val theCaller = anyCaller

        mockDelegate.stub { on { getDpcType(any()) } doReturn DEVICE_OWNER }

        handler.checkPermissions(theCaller, POLICY_SCOPE_DEVICE)

        permissionChecker.assertEnforcesPermissions(
            theCaller,
            "theCrossUserPermissionThatShallBeChecked",
        )
    }

    @Test
    fun getPolicyUnchecked_shouldCallMethodsInOrder() {
        intCallCheckingHandler.getPolicyUnchecked(anyCaller, anyScope)

        assertThat(intCallCheckingHandler.methodCalls)
            .isEqualTo(listOf("getPolicyValue", "convertValue to transport"))
    }

    @Test
    fun getPolicyUnchecked_shouldAcceptAllowedScopes() {
        val allAllowedScopes = setOf(POLICY_SCOPE_DEVICE, POLICY_SCOPE_PARENT_USER)
        val metadata = EnumPolicy.metadata.copy(allowedScopes = allAllowedScopes)
        val handler = createHandler(metadata = metadata)

        // This should not throw exceptions
        for (scope in allAllowedScopes) {
            handler.getPolicyUnchecked(anyCaller, scope)
        }
    }

    @Test
    fun getPolicyUnchecked_shouldRejectDisallowedScopes() {
        val allAllowedScopes = setOf(POLICY_SCOPE_DEVICE, POLICY_SCOPE_PARENT_USER)
        val someDisallowedScopes = setOf(POLICY_SCOPE_USER, 111, 666)
        val metadata = EnumPolicy.metadata.copy(allowedScopes = allAllowedScopes)
        val handler = createHandler(metadata = metadata)

        for (scope in someDisallowedScopes) {
            assertFailsWith<IllegalArgumentException> {
                handler.getPolicyUnchecked(anyCaller, scope)
            }
        }
    }

    @Test
    fun checkPermissions_missingRequiredPermission_throwsException() {
        val metadata =
            EnumPolicyMetadata(
                EnumPolicy.key,
                /* allowedScopes= */ setOf(POLICY_SCOPE_USER),
                /* affectedResource= */ RESOURCE_PER_USER,
                /* requiredPermission= */ null,
                /* requiredCrossUserPermission= */ "testCrossUserPermission",
                /* allowedDpcTypes= */ setOf(),
                /* resolutionMechanism= */ ResolutionMechanismMetadata.MostRestrictive<Int>(),
                /* allowedValues= */ setOf(EnumPolicy.VALUE_1),
            )

        val handler = createHandler(metadata = metadata)
        val theCaller = anyCaller

        val error =
            assertFailsWith<IllegalStateException> {
                handler.checkPermissions(theCaller, POLICY_SCOPE_USER)
            }

        assertThat(error).hasMessageThat().contains("no requiredPermission")
        assertThat(permissionChecker.enforceCalls).isEmpty()
    }

    @Test
    fun checkPermissions_missingRequiredCrossUserPermission_throwsException() {
        val metadata =
            EnumPolicyMetadata(
                EnumPolicy.key,
                /* allowedScopes= */ setOf(POLICY_SCOPE_DEVICE),
                /* affectedResource= */ RESOURCE_PER_USER,
                /* requiredPermission= */ "thePermission",
                /* requiredCrossUserPermission= */ null,
                /* allowedDpcTypes= */ setOf(),
                /* resolutionMechanism= */ ResolutionMechanismMetadata.MostRestrictive<Int>(),
                /* allowedValues= */ setOf(EnumPolicy.VALUE_1),
            )

        val handler = createHandler(metadata = metadata)
        val theCaller = anyCaller

        handler.checkPermissions(theCaller, POLICY_SCOPE_DEVICE)

        permissionChecker.assertEnforcesPermissions(theCaller, "thePermission")
    }

    @Test
    fun getPolicyUnchecked_getStoredPolicy() {
        val theCaller = anyCaller
        val storedValue = EnumPolicy.VALUE_1
        val theKey = EnumPolicy.definition
        val handler =
            createHandler(
                metadata = EnumPolicy.metadata.copy(allowedScopes = allScopes),
                definition = theKey,
            )

        mockDelegate.stub {
            on { getPolicySetByAdmin<Int>(any(), any(), any()) } doReturn storedValue
        }

        for (scope in allScopes) {
            val returnedValue = handler.getPolicyUnchecked(theCaller, scope)

            assertThat(returnedValue).isNotNull()
            assertThat(returnedValue?.tag).isEqualTo(PolicyValueTransport.integerField)
            assertThat(returnedValue?.getIntegerField()).isEqualTo(storedValue)
            verify(mockDelegate, times(1)).getPolicySetByAdmin(theCaller, theKey, scope)
        }
    }

    @Test
    fun getPolicyUnchecked_shouldBeAbleToHandleUnsetPolicies() {
        val theCaller = anyCaller
        val theKey = EnumPolicy.definition
        val handler =
            createHandler(
                metadata = EnumPolicy.metadata.copy(allowedScopes = allScopes),
                delegate = mockDelegate,
                definition = theKey,
            )

        mockDelegate.stub { on { getPolicySetByAdmin<Int>(any(), any(), any()) } doReturn null }

        for (scope in allScopes) {
            val returnedValue = handler.getPolicyUnchecked(theCaller, scope)

            assertThat(returnedValue).isNull()
            verify(mockDelegate, times(1)).getPolicySetByAdmin(theCaller, theKey, scope)
        }
    }

    @Test
    fun getResolvedPerUserPolicyUnchecked_onSelf_shouldReadPerUserPolicy() {
        val metadata = EnumPolicy.metadata.copy(affectedResource = RESOURCE_PER_USER)
        val handler = createHandler(metadata = metadata, definition = EnumPolicy.definition)
        val theUser = anyUid

        handler.getResolvedPerUserPolicyUnchecked(theUser)

        verify(mockDelegate).getResolvedPerUserPolicy(theUser, EnumPolicy.definition)
        verify(mockDelegate, never()).getResolvedDeviceWidePolicy(EnumPolicy.definition)
    }

    @Test
    fun getResolvedDeviceWidePolicyUnchecked_shouldReadDeviceWidePolicy() {
        val metadata = EnumPolicy.metadata.copy(affectedResource = RESOURCE_DEVICE_WIDE)
        val handler = createHandler(metadata = metadata, definition = EnumPolicy.definition)

        handler.getResolvedDeviceWidePolicyUnchecked()

        verify(mockDelegate).getResolvedDeviceWidePolicy(EnumPolicy.definition)
        verify(mockDelegate, never()).getResolvedPerUserPolicy<Int>(any(), any())
    }

    @Test
    fun getResolvedPerUserPolicyUnchecked_shouldCallAllMethodsInOrder() {
        val metadata = EnumPolicy.metadata.copy(affectedResource = RESOURCE_PER_USER)

        val intCallCheckingHandler =
            object :
                CallCheckingHandler<Int>(
                    EnumPolicy.key,
                    metadata,
                    EnumPolicy.definition,
                    mockDelegate,
                ) {
                override fun valueConstructor() = 5

                override fun transportConstructor() = PolicyValueTransport.integerField(5)
            }

        intCallCheckingHandler.getResolvedPerUserPolicyUnchecked(anyCaller.userId)

        assertThat(intCallCheckingHandler.methodCalls)
            .isEqualTo(listOf("getResolvedPerUserPolicyValue", "convertValue to transport"))
    }

    @Test
    fun getResolvedDeviceWidePolicyUnchecked_shouldCallAllMethodsInOrder() {
        val metadata = EnumPolicy.metadata.copy(affectedResource = RESOURCE_DEVICE_WIDE)

        val intCallCheckingHandler =
            object :
                CallCheckingHandler<Int>(
                    EnumPolicy.key,
                    metadata,
                    EnumPolicy.definition,
                    mockDelegate,
                ) {
                override fun valueConstructor() = 5

                override fun transportConstructor() = PolicyValueTransport.integerField(5)
            }

        intCallCheckingHandler.getResolvedDeviceWidePolicyUnchecked()

        assertThat(intCallCheckingHandler.methodCalls)
            .isEqualTo(listOf("getResolvedDeviceWidePolicyValue", "convertValue to transport"))
    }

    @Test
    fun getResolvedDeviceWidePolicyUnchecked_onPerUserPolicy_throws() {
        val metadata = EnumPolicy.metadata.copy(affectedResource = RESOURCE_PER_USER)
        val handler = createHandler(metadata = metadata)

        val exception =
            assertFailsWith<IllegalArgumentException> {
                handler.getResolvedDeviceWidePolicyUnchecked()
            }

        assertThat(exception).hasMessageThat().contains("is not device-wide")
    }

    @Test
    fun getResolvedPerUserPolicyUnchecked_onDeviceWide_throws() {
        val metadata = EnumPolicy.metadata.copy(affectedResource = RESOURCE_DEVICE_WIDE)
        val handler = createHandler(metadata = metadata)
        val theUser = anyUid

        val exception =
            assertFailsWith<IllegalArgumentException> {
                handler.getResolvedPerUserPolicyUnchecked(theUser)
            }

        assertThat(exception).hasMessageThat().contains("is not per-user")
    }

    @Test
    fun checkReadResolvedPerUserPermissions_sameUser_shouldCheckRequiredPermissionOnly() {
        val metadata =
            EnumPolicy.metadata.copy(
                affectedResource = RESOURCE_PER_USER,
                requiredPermission = "thePermission",
                requiredCrossUserPermission = "shouldNotBeChecked",
            )
        val handler = createHandler(metadata = metadata)
        val theCaller = anyCaller
        val theUser = theCaller.userId

        mockDelegate.stub { on { getDpcType(any()) } doReturn NOT_A_DPC }

        handler.checkReadResolvedPerUserPermissions(theCaller, theUser)

        permissionChecker.assertEnforcesPermissions(theCaller, "thePermission")
    }

    @Test
    fun checkReadResolvedPerUserPermissions_differentUser_shouldCheckPermissionAndCrossUserPermission() {
        val metadata =
            EnumPolicy.metadata.copy(
                affectedResource = RESOURCE_PER_USER,
                requiredPermission = "thePermission",
                requiredCrossUserPermission = "theCrossUserPermission",
            )
        val handler = createHandler(metadata = metadata)
        val theCaller = anyCaller
        val theUser = anyCaller.userId + 1

        mockDelegate.stub { on { getDpcType(any()) } doReturn NOT_A_DPC }

        handler.checkReadResolvedPerUserPermissions(theCaller, theUser)

        permissionChecker.assertEnforcesPermissions(
            theCaller,
            "thePermission",
            "theCrossUserPermission",
        )
    }

    @Test
    fun checkReadResolvedDeviceWidePermissions_shouldCheckPermissionAndCrossUserPermission() {
        val metadata =
            EnumPolicy.metadata.copy(
                affectedResource = RESOURCE_DEVICE_WIDE,
                requiredPermission = "thePermission",
                requiredCrossUserPermission = "theCrossUserPermission",
            )
        val handler = createHandler(metadata = metadata)
        val theCaller = anyCaller

        mockDelegate.stub { on { getDpcType(any()) } doReturn NOT_A_DPC }

        handler.checkReadResolvedDeviceWidePermissions(theCaller)

        permissionChecker.assertEnforcesPermissions(
            theCaller,
            "thePermission",
            "theCrossUserPermission",
        )
    }

    @Test
    fun checkReadResolvedPerUserPermissions_acceptedDpcTypes_shouldNotCheckPermissionIfDpcTypeIsAccepted() {
        val metadata =
            EnumPolicy.metadata.copy(
                requiredPermission = "thePermissionThatShallNotBeChecked",
                allowedDpcTypes =
                    setOf(DEVICE_OWNER, MANAGED_PROFILE_OWNER_OF_PERSONAL_OWNED_DEVICE),
            )
        val handler = createHandler(metadata = metadata)
        val theCaller = anyCaller
        val theUser = theCaller.userId

        mockDelegate.stub { on { getDpcType(any()) } doReturn DEVICE_OWNER }

        handler.checkReadResolvedPerUserPermissions(theCaller, theUser)

        assertThat(permissionChecker.enforceCalls).isEmpty()
    }

    @Test
    fun checkReadResolvedPerUserPermissions_acceptedDpcTypes_shouldCheckPermissionIfDpcTypeIsNotAccepted() {
        val metadata =
            EnumPolicy.metadata.copy(
                requiredPermission = "thePermissionThatShallBeChecked",
                allowedDpcTypes =
                    setOf(DEVICE_OWNER, MANAGED_PROFILE_OWNER_OF_PERSONAL_OWNED_DEVICE),
            )
        val handler = createHandler(metadata = metadata)
        val theCaller = anyCaller
        val theUser = theCaller.userId

        mockDelegate.stub { on { getDpcType(any()) } doReturn FINANCED_DEVICE_OWNER }

        handler.checkReadResolvedPerUserPermissions(theCaller, theUser)

        permissionChecker.assertEnforcesPermissions(theCaller, "thePermissionThatShallBeChecked")
    }

    @Test
    fun checkReadResolvedPerUserPermissions_acceptedDpcTypes_shouldStillCheckCrossUserPermissionIfDpcTypeIsAccepted() {
        val metadata =
            EnumPolicy.metadata.copy(
                allowedScopes = setOf(POLICY_SCOPE_DEVICE),
                requiredPermission = "thePermissionThatShallNotBeChecked",
                requiredCrossUserPermission = "theCrossUserPermissionThatShallBeChecked",
                allowedDpcTypes = setOf(DEVICE_OWNER),
            )
        val handler = createHandler(metadata = metadata)
        val theCaller = anyCaller
        val theUser = theCaller.userId + 1

        mockDelegate.stub { on { getDpcType(any()) } doReturn DEVICE_OWNER }

        handler.checkReadResolvedPerUserPermissions(theCaller, theUser)

        permissionChecker.assertEnforcesPermissions(
            theCaller,
            "theCrossUserPermissionThatShallBeChecked",
        )
    }

    @Test
    fun checkReadResolvedPerUserPermissions_withQueryPermission_shouldStillCheckCrossUserPermission() {
        val metadata =
            EnumPolicy.metadata.copy(
                allowedScopes = setOf(POLICY_SCOPE_DEVICE),
                requiredPermission = "thePermissionThatShallNotBeChecked",
                requiredCrossUserPermission = "theCrossUserPermissionThatShallBeChecked",
                allowedDpcTypes = setOf(DEVICE_OWNER),
                affectedResource = RESOURCE_PER_USER,
            )
        val handler = createHandler(metadata = metadata)
        val theCaller = anyCaller
        val theUserId = anyCaller.userId + 1

        mockDelegate.stub { on { getDpcType(any()) } doReturn NOT_A_DPC }
        permissionChecker.hasPermissionsAllowed.add(
            EnforceArguments("android.permission.QUERY_ADMIN_POLICY", theCaller)
        )

        handler.checkReadResolvedPerUserPermissions(theCaller, theUserId)

        permissionChecker.assertEnforcesPermissions(
            theCaller,
            "theCrossUserPermissionThatShallBeChecked",
        )
    }

    @Test
    fun checkReadResolvedDeviceWidePermissions_withQueryPermission_shouldStillCheckCrossUserPermission() {
        val metadata =
            EnumPolicy.metadata.copy(
                allowedScopes = setOf(POLICY_SCOPE_DEVICE),
                requiredPermission = "thePermissionThatShallNotBeChecked",
                requiredCrossUserPermission = "theCrossUserPermissionThatShallBeChecked",
                allowedDpcTypes = setOf(DEVICE_OWNER),
            )
        val handler = createHandler(metadata = metadata)
        val theCaller = anyCaller

        mockDelegate.stub { on { getDpcType(any()) } doReturn NOT_A_DPC }
        permissionChecker.hasPermissionsAllowed.add(
            EnforceArguments("android.permission.QUERY_ADMIN_POLICY", theCaller)
        )

        handler.checkReadResolvedDeviceWidePermissions(theCaller)

        permissionChecker.assertEnforcesPermissions(
            theCaller,
            "theCrossUserPermissionThatShallBeChecked",
        )
    }

    @Test
    fun checkReadResolvedDeviceWidePermissions_acceptedDpcTypes_shouldNotCheckPermissionIfDpcTypeIsAccepted() {
        val metadata =
            EnumPolicy.metadata.copy(
                requiredPermission = "thePermissionThatShallNotBeChecked",
                requiredCrossUserPermission = "theCrossUserPermissionThatShallBeChecked",
                allowedDpcTypes =
                    setOf(DEVICE_OWNER, MANAGED_PROFILE_OWNER_OF_PERSONAL_OWNED_DEVICE),
            )
        val handler = createHandler(metadata = metadata)
        val theCaller = anyCaller

        mockDelegate.stub { on { getDpcType(any()) } doReturn DEVICE_OWNER }

        handler.checkReadResolvedDeviceWidePermissions(theCaller)

        permissionChecker.assertEnforcesPermissions(
            theCaller,
            "theCrossUserPermissionThatShallBeChecked",
        )
    }

    @Test
    fun checkReadResolvedDeviceWidePermissions_acceptedDpcTypes_shouldCheckPermissionIfDpcTypeIsNotAccepted() {
        val metadata =
            EnumPolicy.metadata.copy(
                requiredPermission = "thePermissionThatShallBeChecked",
                requiredCrossUserPermission = "theCrossUserPermissionThatShallBeChecked",
                allowedDpcTypes =
                    setOf(DEVICE_OWNER, MANAGED_PROFILE_OWNER_OF_PERSONAL_OWNED_DEVICE),
            )
        val handler = createHandler(metadata = metadata)
        val theCaller = anyCaller

        mockDelegate.stub { on { getDpcType(any()) } doReturn FINANCED_DEVICE_OWNER }

        handler.checkReadResolvedDeviceWidePermissions(theCaller)

        permissionChecker.assertEnforcesPermissions(
            theCaller,
            "thePermissionThatShallBeChecked",
            "theCrossUserPermissionThatShallBeChecked",
        )
    }

    @Test
    fun checkReadResolvedDeviceWidePermissions_acceptedDpcTypes_shouldStillCheckCrossUserPermissionIfDpcTypeIsAccepted() {
        val metadata =
            EnumPolicy.metadata.copy(
                allowedScopes = setOf(POLICY_SCOPE_DEVICE),
                requiredPermission = "thePermissionThatShallNotBeChecked",
                requiredCrossUserPermission = "theCrossUserPermissionThatShallBeChecked",
                allowedDpcTypes = setOf(DEVICE_OWNER),
            )
        val handler = createHandler(metadata = metadata)
        val theCaller = anyCaller

        mockDelegate.stub { on { getDpcType(any()) } doReturn DEVICE_OWNER }

        handler.checkReadResolvedDeviceWidePermissions(theCaller)

        permissionChecker.assertEnforcesPermissions(
            theCaller,
            "theCrossUserPermissionThatShallBeChecked",
        )
    }

    @Test
    fun checkReadResolvedPerUserPermissions_missingPermission_throwsException() {
        val metadata =
            EnumPolicyMetadata(
                EnumPolicy.key,
                /* allowedScopes= */ setOf(POLICY_SCOPE_USER),
                /* affectedResource= */ RESOURCE_PER_USER,
                /* requiredPermission= */ null,
                /* requiredCrossUserPermission= */ "testCrossUserPermission",
                /* allowedDpcTypes= */ setOf(),
                /* resolutionMechanism= */ ResolutionMechanismMetadata.MostRestrictive<Int>(),
                /* allowedValues= */ setOf(EnumPolicy.VALUE_1),
            )

        val handler = createHandler(metadata = metadata)
        val theCaller = anyCaller
        val theUser = theCaller.userId

        mockDelegate.stub { on { getDpcType(any()) } doReturn NOT_A_DPC }

        val error =
            assertFailsWith<IllegalStateException> {
                handler.checkReadResolvedPerUserPermissions(theCaller, theUser)
            }

        assertThat(error).hasMessageThat().contains("no requiredPermission")
    }

    @Test
    fun checkReadResolvedDeviceWidePermissions_missingPermission_throwsException() {
        val metadata =
            EnumPolicyMetadata(
                EnumPolicy.key,
                /* allowedScopes= */ setOf(POLICY_SCOPE_USER),
                /* affectedResource= */ RESOURCE_PER_USER,
                /* requiredPermission= */ null,
                /* requiredCrossUserPermission= */ "testCrossUserPermission",
                /* allowedDpcTypes= */ setOf(),
                /* resolutionMechanism= */ ResolutionMechanismMetadata.MostRestrictive<Int>(),
                /* allowedValues= */ setOf(EnumPolicy.VALUE_1),
            )

        val handler = createHandler(metadata = metadata)
        val theCaller = anyCaller

        mockDelegate.stub { on { getDpcType(any()) } doReturn NOT_A_DPC }

        val error =
            assertFailsWith<IllegalStateException> {
                handler.checkReadResolvedDeviceWidePermissions(theCaller)
            }

        assertThat(error).hasMessageThat().contains("no requiredPermission")
    }
}
