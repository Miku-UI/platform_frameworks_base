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

package com.android.server.supervision

import android.app.supervision.PackageUsagePolicy
import android.app.supervision.Policy
import android.app.supervision.SupervisionRecoveryInfo
import android.app.supervision.flags.Flags
import android.content.Context
import android.content.res.Resources
import android.os.PersistableBundle
import android.platform.test.annotations.RequiresFlagsDisabled
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import android.platform.test.flag.junit.SetFlagsRule
import android.util.ArraySet
import android.util.Xml
import androidx.annotation.XmlRes
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.frameworks.mockingservicestests.R
import com.google.common.truth.Truth.assertThat
import java.io.DataOutputStream
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.time.Duration
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.junit.MockitoJUnit
import org.mockito.junit.MockitoRule

/**
 * Unit tests for [SupervisionSettings].
 *
 * Run with `atest SupervisionSettingsTest`.
 */
@RunWith(AndroidJUnit4::class)
class SupervisionSettingsTest {
    @get:Rule val checkFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule()
    @get:Rule val mocks: MockitoRule = MockitoJUnit.rule()
    @get:Rule val setFlagsRule = SetFlagsRule()

    private lateinit var mSupervisionSettings: SupervisionSettings
    private lateinit var mResources: Resources
    private lateinit var tempSupervisionDir: File

    @Before
    fun setUp() {
        mSupervisionSettings = SupervisionSettings.getInstance()
        mResources = ApplicationProvider.getApplicationContext<Context>().getResources()
        // Creating a temporary folder to enable access.
        tempSupervisionDir = Files.createTempDirectory("tempSupervisionFolder").toFile()
        mSupervisionSettings.changeDirForTesting(tempSupervisionDir)
    }

    @Test
    fun anySupervisedUser_oneSupervisedUser_returnsTrue() {
        // Get and set user data
        val userData1 = mSupervisionSettings.getUserData(1)
        userData1.changeUserData(true, "package1", true, BUNDLE_1, true)

        val anySupervisedUser: Boolean = mSupervisionSettings.anySupervisedUser()

        assertThat(anySupervisedUser).isTrue()
    }

    @Test
    fun anySupervisedUser_manySupervisedUsers_returnsTrue() {
        // Get and set user data
        val userData1 = mSupervisionSettings.getUserData(1)
        userData1.changeUserData(true, "package1", true, BUNDLE_1, true)
        val userData2 = mSupervisionSettings.getUserData(2)
        userData2.changeUserData(true, null, false, null, false)
        val userData3 = mSupervisionSettings.getUserData(3)
        userData3.changeUserData(false, null, false, BUNDLE_2, false)
        val userData4 = mSupervisionSettings.getUserData(4)
        userData4.changeUserData(true, "package4", false, null, false)

        val anySupervisedUser: Boolean = mSupervisionSettings.anySupervisedUser()

        assertThat(anySupervisedUser).isTrue()
    }

    @Test
    fun anySupervisedUser_noSupervisedUser_returnsFalse() {
        // Get and set user data
        val userData0 = mSupervisionSettings.getUserData(0)
        userData0.changeUserData(false, "package1", true, BUNDLE_1, true)
        val userData1 = mSupervisionSettings.getUserData(1)
        userData1.changeUserData(false, "package1", true, BUNDLE_1, true)
        val userData2 = mSupervisionSettings.getUserData(2)
        userData2.changeUserData(false, null, false, null, false)
        val userData3 = mSupervisionSettings.getUserData(3)
        userData3.changeUserData(false, null, false, BUNDLE_2, false)
        val userData4 = mSupervisionSettings.getUserData(4)
        userData4.changeUserData(false, "package4", false, null, false)

        val anySupervisedUser: Boolean = mSupervisionSettings.anySupervisedUser()

        assertThat(anySupervisedUser).isFalse()
    }

    @Test
    fun removeAndGetUserData_returnsNewInstanceOfUserData() {
        // Get and set user data
        val userData = mSupervisionSettings.getUserData(1)
        userData.changeUserData(true, "package1", true, BUNDLE_1, true)

        // Remove user data and get a new instance
        mSupervisionSettings.removeUserData(1)
        val newUserData = mSupervisionSettings.getUserData(1)

        // Check that user data is not the old instance
        assertThat(newUserData).isNotSameInstanceAs(userData)
    }

    @Test
    fun saveAndLoadSupervisionUserData_oneUser_retrievesUserDataCorrectly() {
        // Get and set user data
        val userData1 = mSupervisionSettings.getUserData(1)
        userData1.changeUserData(true, "package1", true, BUNDLE_1, true)

        // Save, change and load user data
        mSupervisionSettings.saveUserData()
        userData1.changeUserData(false, null, false, BUNDLE_2, false)
        mSupervisionSettings.loadUserData()

        // Check if user data was loaded correctly
        mSupervisionSettings.getUserData(1).checkUserData(true, "package1", true, BUNDLE_1, true)
    }

    @Test
    fun saveAndLoadSupervisionUserData_oneUserNoPersistableBundle_retrievesUserDataCorrectly() {
        // Get and set user data
        val userData1 = mSupervisionSettings.getUserData(1)
        userData1.changeUserData(true, "package1", true, null, true)

        // Save, change and load user data
        mSupervisionSettings.saveUserData()
        userData1.changeUserData(false, null, false, BUNDLE_2, false)
        mSupervisionSettings.loadUserData()

        // Check if user data was loaded correctly
        mSupervisionSettings.getUserData(1).checkUserData(true, "package1", true, null, true)
    }

    @Test
    fun saveAndLoadSupervisionUserData_hasSupervisionRoleHolders_retrievesUserDataCorrectly() {
        // Get and set user data
        val userData1 = mSupervisionSettings.getUserData(1)
        val roleHolders = ArraySet<String>(setOf("package2", "package3", "package4"))
        userData1.changeUserData(true, "package1", true, null, true, roleHolders)

        // Save, change and load user data
        mSupervisionSettings.saveUserData()
        userData1.changeUserData(false, null, false, BUNDLE_2, false)
        mSupervisionSettings.loadUserData()

        // Check if user data was loaded correctly
        mSupervisionSettings
            .getUserData(1)
            .checkUserData(true, "package1", true, null, true, roleHolders)
    }

    @Test
    fun saveAndLoadSupervisionUserData_manyUsers_retrievesUserDataCorrectly() {
        // Get and set user data
        val userData1 = mSupervisionSettings.getUserData(1)
        userData1.changeUserData(true, "package1", true, BUNDLE_1, true)
        val userData2 = mSupervisionSettings.getUserData(2)
        userData2.changeUserData(true, null, false, null, false)
        val userData3 = mSupervisionSettings.getUserData(3)
        userData3.changeUserData(false, null, false, BUNDLE_2, false)
        val userData4 = mSupervisionSettings.getUserData(4)
        userData4.changeUserData(false, "package4", false, null, false)

        // Save, change and load user data
        mSupervisionSettings.saveUserData()
        userData1.changeUserData(false, null, false, BUNDLE_2, false)
        userData2.changeUserData(true, null, false, BUNDLE_1, false)
        userData3.changeUserData(true, "package3", true, null, true)
        userData4.changeUserData(false, null, false, null, false)
        mSupervisionSettings.loadUserData()

        // Check if user data was loaded correctly
        mSupervisionSettings.getUserData(1).checkUserData(true, "package1", true, BUNDLE_1, true)
        mSupervisionSettings.getUserData(2).checkUserData(true, null, false, null, false)
        mSupervisionSettings.getUserData(3).checkUserData(false, null, false, BUNDLE_2, false)
        mSupervisionSettings.getUserData(4).checkUserData(false, "package4", false, null, false)
    }

    @Test
    fun saveAndLoadRecoveryInfo_retrievesCorrectRecoveryInfo() {
        // Save, load, and get recovery info
        mSupervisionSettings.saveRecoveryInfo(RECOVERY_INFO)
        mSupervisionSettings.loadRecoveryInfo()
        val retrievedRecoveryInfo = mSupervisionSettings.getRecoveryInfo()

        // Check if recovery info was retrieved correctly
        assertThat(retrievedRecoveryInfo).isNotNull()
        assertThat(retrievedRecoveryInfo.accountType).isEqualTo(RECOVERY_INFO.accountType)
        assertThat(retrievedRecoveryInfo.accountName).isEqualTo(RECOVERY_INFO.accountName)
        assertThat(retrievedRecoveryInfo.accountData.getString("id"))
            .isEqualTo(RECOVERY_INFO.accountData.getString("id"))
        assertThat(retrievedRecoveryInfo.state).isEqualTo(RECOVERY_INFO.state)
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_SUPERVISION_MANAGER_POLICY_APIS)
    fun saveAndGetSupervisionPolicies_storesAndRetrievesPoliciesCorrectly() {
        val policies = mutableListOf(TEST_PACKAGE_POLICY, TEST_PACKAGE_POLICY_2)

        savePoliciesAndVerifyLoadPolicies(policies)
    }

    @Test
    @RequiresFlagsEnabled(
        Flags.FLAG_ENABLE_SUPERVISION_MANAGER_POLICY_APIS,
        Flags.FLAG_ENABLE_SUPERVISION_PACKAGE_USAGE_APIS,
    )
    fun saveAndGetSupervisionPolicies_storesAndRetrievesPoliciesCorrectly_withTimeLimitPolicy() {
        val policies =
            mutableListOf(
                TEST_PACKAGE_POLICY,
                TEST_PACKAGE_POLICY_2,
                PackageUsagePolicy.Builder("test.package3", PackageUsagePolicy.TYPE_TIME_LIMIT)
                    .setTimeLimit(Duration.ofMinutes(10))
                    .build(),
            )
        savePoliciesAndVerifyLoadPolicies(policies)
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_SUPERVISION_MANAGER_POLICY_APIS)
    @RequiresFlagsDisabled(Flags.FLAG_ENABLE_SUPERVISION_PACKAGE_USAGE_APIS)
    fun loadUserData_withMixedPolicyIdentifiers_loadsKnownPoliciesCorrectly() {
        writeSupervisionSettingsFrom(R.xml.supervision_user_data_v0)

        mSupervisionSettings.loadUserData()

        val userData = mSupervisionSettings.getUserData(1)
        assertThat(userData.policies.getPolicies()).containsExactly(TEST_PACKAGE_POLICY)
    }

    @RequiresFlagsEnabled(
        Flags.FLAG_ENABLE_SUPERVISION_MANAGER_POLICY_APIS,
        Flags.FLAG_ENABLE_SUPERVISION_PACKAGE_USAGE_APIS,
    )
    fun loadUserData_withMixedPolicyIdentifiers_loadsTimeLimitPolicyCorrectly() {
        writeSupervisionSettingsFrom(R.xml.supervision_user_data_v0)

        mSupervisionSettings.loadUserData()

        val expectedTimeLimitPolicy =
            PackageUsagePolicy.Builder("test.package", PackageUsagePolicy.TYPE_TIME_LIMIT)
                .setTimeLimit(Duration.ofMinutes(10))
                .setVersion(1)
                .build()

        val userData = mSupervisionSettings.getUserData(1)
        assertThat(userData.policies.getPolicies())
            .containsExactly(listOf(expectedTimeLimitPolicy, TEST_PACKAGE_POLICY))
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_SUPERVISION_MANAGER_POLICY_APIS)
    fun loadUserData_withUnknownTag_skipsTagAndLoadsCorrectly() {
        writeSupervisionSettingsFrom(R.xml.supervision_user_data_malformed_v0)

        mSupervisionSettings.loadUserData()

        mSupervisionSettings
            .getUserData(1)
            .checkUserData(
                /* enabled= */ true,
                /* appPackage= */ "package1",
                /* lockScreenEnabled= */ true,
                /* lockScreenOptions= */ BUNDLE_1,
                /* escrowTokenRequired= */ false,
            )
    }

    @Test
    @RequiresFlagsEnabled(
        Flags.FLAG_ENABLE_SUPERVISION_MANAGER_POLICY_APIS,
        Flags.FLAG_ENABLE_SUPERVISION_PACKAGE_USAGE_APIS,
    )
    fun loadUserData_withMalformedPolicies_skipsPolicyAndLoadsCorrectly() {
        writeSupervisionSettingsFrom(R.xml.supervision_user_data_malformed_policies)
        mSupervisionSettings.loadUserData()

        assertThat(mSupervisionSettings.getUserData(1).policies.getPolicies())
            .containsExactly(TEST_PACKAGE_POLICY)
    }

    private fun writeSupervisionSettingsFrom(@XmlRes resId: Int) {
        val file = File(tempSupervisionDir, "supervision_settings.xml")
        file.outputStream().use { fileOutputStream ->
            val dataOutputStream = DataOutputStream(fileOutputStream)
            val xmlIn = mResources.getXml(resId)
            val xmlOut = Xml.newBinarySerializer()

            xmlOut.setOutput(dataOutputStream, StandardCharsets.UTF_8.name())
            Xml.copy(xmlIn, xmlOut)
            xmlOut.flush()
        }
    }

    private fun savePoliciesAndVerifyLoadPolicies(
        policies: List<Policy>,
        expectedStoredPolicies: List<Policy> = policies,
    ) {
        // set up user data with policies
        mSupervisionSettings
            .getUserData(1)
            .changeUserData(true, null, true, null, true, ArraySet<String>(), policies)

        // save user data, update with empty policies and load user data
        mSupervisionSettings.saveUserData()
        mSupervisionSettings.getUserData(1).changeUserData(false, null, false, null, false)
        mSupervisionSettings.loadUserData()

        // check if policy was loaded correctly
        mSupervisionSettings
            .getUserData(1)
            .checkUserData(true, null, true, null, true, ArraySet<String>(), expectedStoredPolicies)
    }

    private companion object {
        const val USER_ID = 100

        val BUNDLE_1 =
            PersistableBundle().apply {
                putString("id", "id")
                putInt("key1", 1)
                putBoolean("key2", true)
                putString("key3", "value")
                putInt("key4", 4)
            }
        val BUNDLE_2 =
            PersistableBundle().apply {
                putInt("key1", 10)
                putBoolean("key2", false)
                putString("key5", "value")
                putInt("key6", 6)
            }

        val RECOVERY_INFO =
            SupervisionRecoveryInfo(
                "account_name",
                "account_type",
                SupervisionRecoveryInfo.STATE_VERIFIED,
                BUNDLE_1,
            )

        private val TEST_PACKAGE_POLICY =
            PackageUsagePolicy.Builder("test.package", PackageUsagePolicy.TYPE_BLOCKED)
                .setVersion(1)
                .build()
        private val TEST_PACKAGE_POLICY_2 =
            PackageUsagePolicy.Builder("test.package2", PackageUsagePolicy.TYPE_BLOCKED)
                .setVersion(1)
                .build()

        fun SupervisionUserData.changeUserData(
            enabled: Boolean,
            appPackage: String?,
            lockScreenEnabled: Boolean,
            lockScreenOptions: PersistableBundle?,
            escrowTokenRequired: Boolean,
            roleHolders: ArraySet<String> = ArraySet<String>(),
            policies: List<Policy> = emptyList(),
        ) {
            this.supervisionEnabled = enabled
            this.supervisionAppPackage = appPackage
            this.supervisionLockScreenEnabled = lockScreenEnabled
            this.supervisionLockScreenOptions = lockScreenOptions
            this.escrowTokenRequired = escrowTokenRequired
            this.supervisionRoleHolders = roleHolders
            for (policy in policies) {
                this.policies.add(policy)
            }
        }

        fun SupervisionUserData.checkUserData(
            enabled: Boolean,
            appPackage: String?,
            lockScreenEnabled: Boolean,
            lockScreenOptions: PersistableBundle?,
            escrowTokenRequired: Boolean,
            roleHolders: ArraySet<String> = ArraySet<String>(),
            policies: List<Policy> = emptyList(),
        ) {
            assertThat(this.supervisionEnabled).isEqualTo(enabled)
            assertThat(this.supervisionAppPackage).isEqualTo(appPackage)
            assertThat(this.supervisionLockScreenEnabled).isEqualTo(lockScreenEnabled)
            if (lockScreenOptions == null) {
                assertThat(this.supervisionLockScreenOptions).isNull()
            } else {
                assertThat(this.supervisionLockScreenOptions.toString())
                    .isEqualTo(lockScreenOptions.toString())
            }
            assertThat(this.escrowTokenRequired).isEqualTo(escrowTokenRequired)
            assertThat(this.supervisionRoleHolders).containsExactlyElementsIn(roleHolders)
            assertThat(this.policies.getPolicies()).containsExactlyElementsIn(policies)
        }
    }
}
