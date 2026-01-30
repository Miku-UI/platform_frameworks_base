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

package com.android.server.devicepolicy

import android.app.admin.DevicePolicyManager
import android.app.admin.IntegerPolicyValue
import android.app.admin.PackageSetPolicyValue
import android.app.admin.PolicyUpdateResult
import android.app.admin.PolicyValue
import android.app.usage.UsageStatsManagerInternal
import android.content.ComponentName
import android.content.pm.PackageManagerInternal
import android.os.UserHandle
import android.os.UserManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.android.internal.util.test.BroadcastInterceptingContext
import com.android.role.RoleManagerLocal
import com.android.server.LocalManagerRegistry
import com.android.server.LocalServices
import com.android.server.pm.UserManagerInternal
import com.google.common.truth.Truth.assertThat
import java.util.List
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class DevicePolicyEngineTest {
    private val context =
        BroadcastInterceptingContext(InstrumentationRegistry.getInstrumentation().targetContext)

    private val deviceAdminServiceController = mock<DeviceAdminServiceController>()
    private val userManager = mock<UserManager>()
    private val userManagerInternal = mock<UserManagerInternal>()
    private val packageManager = mock<PackageManagerInternal>()
    private val usageStatsManagerInternal = mock<UsageStatsManagerInternal>()
    private val policyPathProvider = mock<PolicyPathProvider>()

    private val lock = Any()
    private lateinit var devicePolicyEngine: DevicePolicyEngine

    @Before
    fun setUp() {
        resetPolicyFolder()

        LocalServices.removeServiceForTest(UserManager::class.java)
        LocalServices.addService(UserManager::class.java, userManager)

        LocalServices.removeServiceForTest(UserManagerInternal::class.java)
        LocalServices.addService(UserManagerInternal::class.java, userManagerInternal)

        LocalServices.removeServiceForTest(PackageManagerInternal::class.java)
        LocalServices.addService(PackageManagerInternal::class.java, packageManager)

        LocalServices.removeServiceForTest(UsageStatsManagerInternal::class.java)
        LocalServices.addService(UsageStatsManagerInternal::class.java, usageStatsManagerInternal)

        resetDevicePolicyEngine()
    }

    @After
    fun tearDown() {
        LocalServices.removeServiceForTest(UserManager::class.java)
    }

    private fun resetPolicyFolder() {
        whenever(policyPathProvider.getDataSystemDirectory()).thenReturn(tmpDir.newFolder())
    }

    private fun resetDevicePolicyEngine() {
        devicePolicyEngine =
            DevicePolicyEngine(context, deviceAdminServiceController, lock, policyPathProvider)
        devicePolicyEngine.load()
    }

    // Helper functions for test setup.

    private fun <T> ensurePolicyIsSetLocally(
        policyDefinition: PolicyDefinition<T>,
        value: PolicyValue<T>,
        userId: Int = SYSTEM_USER_ID,
        enforcingAdmin: EnforcingAdmin = DEVICE_OWNER_ADMIN,
    ) {
        val result =
            devicePolicyEngine.setLocalPolicy(
                policyDefinition,
                enforcingAdmin,
                value,
                SYSTEM_USER_ID,
            )
        assertThat(result.get()).isEqualTo(POLICY_SET)
    }

    private fun <T> ensurePolicyIsSetGlobally(
        policyDefinition: PolicyDefinition<T>,
        value: PolicyValue<T>,
        enforcingAdmin: EnforcingAdmin = DEVICE_OWNER_ADMIN,
    ) {
        val result = devicePolicyEngine.setGlobalPolicy(policyDefinition, enforcingAdmin, value)
        assertThat(result.get()).isEqualTo(POLICY_SET)
    }

    private fun <T> ensurePolicyIsRemovedLocally(policyDefinition: PolicyDefinition<T>) {
        val result =
            devicePolicyEngine.removeLocalPolicy(
                policyDefinition,
                DEVICE_OWNER_ADMIN,
                SYSTEM_USER_ID,
            )
        assertThat(result.get()).isEqualTo(POLICY_CLEARED)
    }

    @Test
    fun setAndGetLocalPolicy_returnsCorrectPolicy() {
        ensurePolicyIsSetLocally(PASSWORD_COMPLEXITY_POLICY, HIGH_PASSWORD_COMPLEXITY)

        val resolvedPolicy =
            devicePolicyEngine.getResolvedPolicy(PASSWORD_COMPLEXITY_POLICY, SYSTEM_USER_ID)

        assertThat(resolvedPolicy).isEqualTo(HIGH_PASSWORD_COMPLEXITY.value)
    }

    @Test
    fun setAndGetGlobalPolicy_returnsCorrectPolicy() {
        ensurePolicyIsSetGlobally(AUTO_TIME_ZONE_POLICY, AUTO_TIME_ZONE_ENABLED)

        val resolvedPolicy =
            devicePolicyEngine.getResolvedPolicy(AUTO_TIME_ZONE_POLICY, SYSTEM_USER_ID)

        assertThat(resolvedPolicy).isEqualTo(AUTO_TIME_ZONE_ENABLED.value)
    }

    @Test
    fun removeLocalPolicy_removesPolicyAndResolvesToNull() {
        ensurePolicyIsSetLocally(PASSWORD_COMPLEXITY_POLICY, HIGH_PASSWORD_COMPLEXITY)
        ensurePolicyIsRemovedLocally(PASSWORD_COMPLEXITY_POLICY)

        val resolvedPolicy =
            devicePolicyEngine.getResolvedPolicy(PASSWORD_COMPLEXITY_POLICY, SYSTEM_USER_ID)

        assertThat(resolvedPolicy).isNull()
    }

    @Test
    fun removeLocalPoliciesForSystemEntities_removesOnlySpecifiedSystemEntitiesPolicies() {
        ensurePolicyIsSetLocally(
            USER_CONTROLLED_DISABLED_PACKAGES_POLICY,
            PACKAGE_SET_POLICY_VALUE_1,
            SYSTEM_USER_ID,
            DEVICE_OWNER_ADMIN,
        )
        ensurePolicyIsSetLocally(
            USER_CONTROLLED_DISABLED_PACKAGES_POLICY,
            PACKAGE_SET_POLICY_VALUE_2,
            SYSTEM_USER_ID,
            SYSTEM_ADMIN,
        )

        devicePolicyEngine.removeLocalPoliciesForSystemEntities(
            SYSTEM_USER_ID,
            // Specifically passing in a list type that will throw NPE if its #contains() method is
            // called with a null argument.
            List.of(SYSTEM_ADMIN.systemEntity!!),
        )

        val resolvedPolicy =
            devicePolicyEngine.getResolvedPolicy(
                USER_CONTROLLED_DISABLED_PACKAGES_POLICY,
                SYSTEM_USER_ID,
            )

        // Only the policy set by the device owner admin remains.
        assertThat(resolvedPolicy).isEqualTo(PACKAGE_SET_POLICY_VALUE_1.value)
    }

    @Test
    fun setLocalPackageSetPolicy_multipleEnforcingAdmins_resolvesToSetUnion() {
        ensurePolicyIsSetLocally(
            USER_CONTROLLED_DISABLED_PACKAGES_POLICY,
            PACKAGE_SET_POLICY_VALUE_1,
            SYSTEM_USER_ID,
            DEVICE_OWNER_ADMIN,
        )
        ensurePolicyIsSetLocally(
            USER_CONTROLLED_DISABLED_PACKAGES_POLICY,
            PACKAGE_SET_POLICY_VALUE_2,
            SYSTEM_USER_ID,
            SYSTEM_ADMIN,
        )

        val resolvedPolicy =
            devicePolicyEngine.getResolvedPolicy(
                USER_CONTROLLED_DISABLED_PACKAGES_POLICY,
                SYSTEM_USER_ID,
            )

        assertThat(resolvedPolicy)
            .isEqualTo(PACKAGE_SET_POLICY_VALUE_1.value.union(PACKAGE_SET_POLICY_VALUE_2.value))
    }

    @Test
    fun setGlobalAndThenLocalPolicy_localOverridesGlobal() {
        ensurePolicyIsSetGlobally(
            USER_CONTROLLED_DISABLED_PACKAGES_POLICY,
            PACKAGE_SET_POLICY_VALUE_1,
        )
        ensurePolicyIsSetLocally(
            USER_CONTROLLED_DISABLED_PACKAGES_POLICY,
            PACKAGE_SET_POLICY_VALUE_2,
        )

        val resolvedPolicy =
            devicePolicyEngine.getResolvedPolicy(
                USER_CONTROLLED_DISABLED_PACKAGES_POLICY,
                SYSTEM_USER_ID,
            )

        assertThat(resolvedPolicy).isEqualTo(PACKAGE_SET_POLICY_VALUE_2.value)
    }

    @Test
    fun setPackageSetPolicyLocallyThenGlobally_noSubset_returnsError() {
        ensurePolicyIsSetLocally(
            USER_CONTROLLED_DISABLED_PACKAGES_POLICY,
            PACKAGE_SET_POLICY_VALUE_1,
        )

        val result =
            devicePolicyEngine.setGlobalPolicy(
                USER_CONTROLLED_DISABLED_PACKAGES_POLICY,
                DEVICE_OWNER_ADMIN,
                PACKAGE_SET_POLICY_VALUE_2,
            )
        // The local policy takes precedence over the global policy, and the global policy is not a
        // subset of the local one, so we get an error.
        assertThat(result.get()).isEqualTo(ERROR_CONFLICTING_ADMIN_POLICY)
    }

    @Test
    fun setPackageSetPolicyLocallyThenGlobally_subset_returnsSuccess() {
        ensurePolicyIsSetLocally(
            USER_CONTROLLED_DISABLED_PACKAGES_POLICY,
            PACKAGE_SET_POLICY_VALUE_1,
        )

        val result =
            devicePolicyEngine.setGlobalPolicy(
                USER_CONTROLLED_DISABLED_PACKAGES_POLICY,
                DEVICE_OWNER_ADMIN,
                PACKAGE_SET_POLICY_VALUE_1_SUBSET,
            )
        // The local policy takes precedence over the global policy, but the global policy is a
        // subset of the local one.
        assertThat(result.get()).isEqualTo(POLICY_SET)
    }

    @Test
    fun setGlobalPolicy_withLocalOnlyPolicy_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException::class.java) {
            val result =
                devicePolicyEngine.setGlobalPolicy(
                    PASSWORD_COMPLEXITY_POLICY,
                    DEVICE_OWNER_ADMIN,
                    HIGH_PASSWORD_COMPLEXITY,
                )
        }
    }

    @Test
    fun setLocalPolicy_withGlobalOnlyPolicy_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException::class.java) {
            val result =
                devicePolicyEngine.setLocalPolicy(
                    AUTO_TIME_ZONE_POLICY,
                    DEVICE_OWNER_ADMIN,
                    AUTO_TIME_ZONE_ENABLED,
                    SYSTEM_USER_ID,
                )
        }
    }

    @Test
    fun setLocalPolicy_restartDevicePolicyEngine_policyIsStillSet() {
        ensurePolicyIsSetLocally(PASSWORD_COMPLEXITY_POLICY, HIGH_PASSWORD_COMPLEXITY)
        resetDevicePolicyEngine()

        val resolvedPolicy =
            devicePolicyEngine.getResolvedPolicy(PASSWORD_COMPLEXITY_POLICY, SYSTEM_USER_ID)

        assertThat(resolvedPolicy).isEqualTo(HIGH_PASSWORD_COMPLEXITY.value)
    }

    @Test
    fun setLocalPolicy_restartDevicePolicyEngine_andRemovePolicyData_policyIsRemoved() {
        ensurePolicyIsSetLocally(PASSWORD_COMPLEXITY_POLICY, HIGH_PASSWORD_COMPLEXITY)
        resetPolicyFolder()
        resetDevicePolicyEngine()

        val resolvedPolicy =
            devicePolicyEngine.getResolvedPolicy(PASSWORD_COMPLEXITY_POLICY, SYSTEM_USER_ID)

        assertThat(resolvedPolicy).isNull()
    }

    @Test
    fun getEnforcingAdminsForResolvedPolicy_oneAdminSetsPolicy_singleEnforcingAdmin() {
        ensurePolicyIsSetLocally(
            PASSWORD_COMPLEXITY_POLICY,
            HIGH_PASSWORD_COMPLEXITY,
            SYSTEM_USER_ID,
            DEVICE_OWNER_ADMIN
        )

        val enforcingAdmins =
            devicePolicyEngine.getEnforcingAdminsForResolvedPolicy(
                PASSWORD_COMPLEXITY_POLICY,
                SYSTEM_USER_ID
            )

        assertThat(enforcingAdmins).containsExactly(DEVICE_OWNER_ADMIN)
    }

    @Test
    fun getEnforcingAdminsForResolvedPolicy_multipleAdminsSetPolicy_singleEnforcingAdminForResolvedValue() {
        ensurePolicyIsSetLocally(
            PASSWORD_COMPLEXITY_POLICY,
            LOW_PASSWORD_COMPLEXITY,
            SYSTEM_USER_ID,
            SYSTEM_ADMIN
        )
        // Only this policy value set by this admin will take effect because of the resolution mechanism.
        ensurePolicyIsSetLocally(
            PASSWORD_COMPLEXITY_POLICY,
            HIGH_PASSWORD_COMPLEXITY,
            SYSTEM_USER_ID,
            DEVICE_OWNER_ADMIN
        )

        val enforcingAdmins =
            devicePolicyEngine.getEnforcingAdminsForResolvedPolicy(
                PASSWORD_COMPLEXITY_POLICY,
                SYSTEM_USER_ID
            )

        assertThat(enforcingAdmins).containsExactly(DEVICE_OWNER_ADMIN)
    }

    @Test
    fun getEnforcingAdminsForResolvedPolicy_multipleAdminsSetPolicy_multipleEnforcingAdminsForResolvedValue() {
        ensurePolicyIsSetLocally(
            PASSWORD_COMPLEXITY_POLICY,
            HIGH_PASSWORD_COMPLEXITY,
            SYSTEM_USER_ID,
            DEVICE_OWNER_ADMIN
        )
        ensurePolicyIsSetLocally(
            PASSWORD_COMPLEXITY_POLICY,
            HIGH_PASSWORD_COMPLEXITY,
            SYSTEM_USER_ID,
            SYSTEM_ADMIN
        )

        val enforcingAdmins =
            devicePolicyEngine.getEnforcingAdminsForResolvedPolicy(
                PASSWORD_COMPLEXITY_POLICY,
                SYSTEM_USER_ID
            )

        assertThat(enforcingAdmins).containsExactly(DEVICE_OWNER_ADMIN, SYSTEM_ADMIN)
    }

    @Test
    fun getEnforcingAdminsForResolvedPolicy_multipleAdminsSetPolicyLocallyAndGlobally_multipleEnforcingAdminsForResolvedValue() {
        ensurePolicyIsSetLocally(
            USER_CONTROLLED_DISABLED_PACKAGES_POLICY,
            PACKAGE_SET_POLICY_VALUE_1,
            SYSTEM_USER_ID,
            DEVICE_OWNER_ADMIN
        )
        ensurePolicyIsSetGlobally(
            USER_CONTROLLED_DISABLED_PACKAGES_POLICY,
            PACKAGE_SET_POLICY_VALUE_2,
            SYSTEM_ADMIN
        )

        val enforcingAdmins =
            devicePolicyEngine.getEnforcingAdminsForResolvedPolicy(
                USER_CONTROLLED_DISABLED_PACKAGES_POLICY,
                SYSTEM_USER_ID
            )

        assertThat(enforcingAdmins).containsExactly(DEVICE_OWNER_ADMIN, SYSTEM_ADMIN)
    }

    @Test
    fun getEnforcingAdminsForResolvedPolicy_multipleAdminsSetPolicyLocallyTwiceAndGlobally_multipleEnforcingAdminsForResolvedValue() {
        ensurePolicyIsSetLocally(
            USER_CONTROLLED_DISABLED_PACKAGES_POLICY,
            PACKAGE_SET_POLICY_VALUE_1,
            SYSTEM_USER_ID,
            DEVICE_OWNER_ADMIN
        )
        ensurePolicyIsSetLocally(
            USER_CONTROLLED_DISABLED_PACKAGES_POLICY,
            PACKAGE_SET_POLICY_VALUE_1_SUBSET,
            SYSTEM_USER_ID,
            DEVICE_OWNER_ADMIN
        )
        ensurePolicyIsSetGlobally(
            USER_CONTROLLED_DISABLED_PACKAGES_POLICY,
            PACKAGE_SET_POLICY_VALUE_2,
            SYSTEM_ADMIN
        )

        val enforcingAdmins =
            devicePolicyEngine.getEnforcingAdminsForResolvedPolicy(
                USER_CONTROLLED_DISABLED_PACKAGES_POLICY,
                SYSTEM_USER_ID
            )

        assertThat(enforcingAdmins).containsExactly(DEVICE_OWNER_ADMIN, SYSTEM_ADMIN)
    }

    @Test
    fun getEnforcingAdminsForResolvedPolicy_unsetPolicy_emptySet() {
        val enforcingAdmins =
            devicePolicyEngine.getEnforcingAdminsForResolvedPolicy(
                PASSWORD_COMPLEXITY_POLICY,
                SYSTEM_USER_ID
            )

        assertThat(enforcingAdmins).isEmpty()
    }

    companion object {
        private const val POLICY_SET = PolicyUpdateResult.RESULT_POLICY_SET
        private const val FAILURE_UNKNOWN = PolicyUpdateResult.RESULT_FAILURE_UNKNOWN
        private const val POLICY_CLEARED = PolicyUpdateResult.RESULT_POLICY_CLEARED
        private const val ERROR_CONFLICTING_ADMIN_POLICY =
            PolicyUpdateResult.RESULT_FAILURE_CONFLICTING_ADMIN_POLICY

        private val PASSWORD_COMPLEXITY_POLICY = PolicyDefinition.PASSWORD_COMPLEXITY
        private val AUTO_TIME_ZONE_POLICY = PolicyDefinition.AUTO_TIME_ZONE
        private val USER_CONTROLLED_DISABLED_PACKAGES_POLICY =
            PolicyDefinition.USER_CONTROLLED_DISABLED_PACKAGES

        private val HIGH_PASSWORD_COMPLEXITY =
            IntegerPolicyValue(DevicePolicyManager.PASSWORD_COMPLEXITY_HIGH)
        private val LOW_PASSWORD_COMPLEXITY =
            IntegerPolicyValue(DevicePolicyManager.PASSWORD_COMPLEXITY_LOW)
        private val AUTO_TIME_ZONE_ENABLED =
            IntegerPolicyValue(DevicePolicyManager.AUTO_TIME_ZONE_ENABLED)
        private val PACKAGE_SET_POLICY_VALUE_1 =
            PackageSetPolicyValue(setOf("com.example.package1", "com.example.package2"))
        private val PACKAGE_SET_POLICY_VALUE_1_SUBSET =
            PackageSetPolicyValue(setOf("com.example.package1"))
        private val PACKAGE_SET_POLICY_VALUE_2 =
            PackageSetPolicyValue(setOf("com.example.package2", "com.example.package3"))

        private const val SYSTEM_USER_ID = UserHandle.USER_SYSTEM
        private val SYSTEM_ADMIN = EnforcingAdmin.createSystemEnforcingAdmin("system_entity")
        private val DEVICE_OWNER_ADMIN =
            EnforcingAdmin.createEnterpriseEnforcingAdmin(
                ComponentName("packagename", "classname"),
                SYSTEM_USER_ID,
            )

        @ClassRule @JvmField val tmpDir = TemporaryFolder()

        @BeforeClass
        @JvmStatic
        fun setUpClass() {
            // TODO(b/420373209): Remove this once we have a better way to mock RoleManagerLocal.
            if (LocalManagerRegistry.getManager(RoleManagerLocal::class.java) == null) {
                LocalManagerRegistry.addManager(
                    RoleManagerLocal::class.java,
                    mock<RoleManagerLocal>(),
                )
            }
        }
    }
}
