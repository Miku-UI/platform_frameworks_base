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

import android.app.supervision.SupervisionRecoveryInfo
import android.app.supervision.flags.Flags
import android.os.PersistableBundle
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import android.util.ArraySet
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.nio.file.Files
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
@RequiresFlagsEnabled(Flags.FLAG_PERSISTENT_SUPERVISION_SETTINGS)
class SupervisionSettingsTest {
    @get:Rule val checkFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule()
    @get:Rule val mocks: MockitoRule = MockitoJUnit.rule()

    private lateinit var mSupervisionSettings: SupervisionSettings

    @Before
    fun setUp() {
        mSupervisionSettings = SupervisionSettings.getInstance()
        // Creating a temporary folder to enable access.
        mSupervisionSettings.changeDirForTesting(
            Files.createTempDirectory("tempSupervisionFolder").toFile()
        )
    }

    @Test
    fun anySupervisedUser_oneSupervisedUser_returnsTrue() {
        // Get and set user data
        val userData1 = mSupervisionSettings.getUserData(1)
        userData1.changeUserData(true, "package1", true, BUNDLE_1)

        val anySupervisedUser: Boolean = mSupervisionSettings.anySupervisedUser()

        assertThat(anySupervisedUser).isTrue()
    }

    @Test
    fun anySupervisedUser_manySupervisedUsers_returnsTrue() {
        // Get and set user data
        val userData1 = mSupervisionSettings.getUserData(1)
        userData1.changeUserData(true, "package1", true, BUNDLE_1)
        val userData2 = mSupervisionSettings.getUserData(2)
        userData2.changeUserData(true, null, false, null)
        val userData3 = mSupervisionSettings.getUserData(3)
        userData3.changeUserData(false, null, false, BUNDLE_2)
        val userData4 = mSupervisionSettings.getUserData(4)
        userData4.changeUserData(true, "package4", false, null)

        val anySupervisedUser: Boolean = mSupervisionSettings.anySupervisedUser()

        assertThat(anySupervisedUser).isTrue()
    }

    @Test
    fun anySupervisedUser_noSupervisedUser_returnsFalse() {
        // Get and set user data
        val userData0 = mSupervisionSettings.getUserData(0)
        userData0.changeUserData(false, "package1", true, BUNDLE_1)
        val userData1 = mSupervisionSettings.getUserData(1)
        userData1.changeUserData(false, "package1", true, BUNDLE_1)
        val userData2 = mSupervisionSettings.getUserData(2)
        userData2.changeUserData(false, null, false, null)
        val userData3 = mSupervisionSettings.getUserData(3)
        userData3.changeUserData(false, null, false, BUNDLE_2)
        val userData4 = mSupervisionSettings.getUserData(4)
        userData4.changeUserData(false, "package4", false, null)

        val anySupervisedUser: Boolean = mSupervisionSettings.anySupervisedUser()

        assertThat(anySupervisedUser).isFalse()
    }

    @Test
    fun removeAndGetUserData_returnsNewInstanceOfUserData() {
        // Get and set user data
        val userData = mSupervisionSettings.getUserData(1)
        userData.changeUserData(true, "package1", true, BUNDLE_1)

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
        userData1.changeUserData(true, "package1", true, BUNDLE_1)

        // Save, change and load user data
        mSupervisionSettings.saveUserData()
        userData1.changeUserData(false, null, false, BUNDLE_2)
        mSupervisionSettings.loadUserData()

        // Check if user data was loaded correctly
        mSupervisionSettings.getUserData(1).checkUserData(true, "package1", true, BUNDLE_1)
    }

    @Test
    fun saveAndLoadSupervisionUserData_oneUserNoPersistableBundle_retrievesUserDataCorrectly() {
        // Get and set user data
        val userData1 = mSupervisionSettings.getUserData(1)
        userData1.changeUserData(true, "package1", true, null)

        // Save, change and load user data
        mSupervisionSettings.saveUserData()
        userData1.changeUserData(false, null, false, BUNDLE_2)
        mSupervisionSettings.loadUserData()

        // Check if user data was loaded correctly
        mSupervisionSettings.getUserData(1).checkUserData(true, "package1", true, null)
    }

    @Test
    fun saveAndLoadSupervisionUserData_hasSupervisionRoleHolders_retrievesUserDataCorrectly() {
        // Get and set user data
        val userData1 = mSupervisionSettings.getUserData(1)
        val roleHolders = ArraySet<String>(setOf("package2", "package3", "package4"))
        userData1.changeUserData(true, "package1", true,
            null, roleHolders)

        // Save, change and load user data
        mSupervisionSettings.saveUserData()
        userData1.changeUserData(false, null, false, BUNDLE_2)
        mSupervisionSettings.loadUserData()

        // Check if user data was loaded correctly
        mSupervisionSettings.getUserData(1).checkUserData(true, "package1",
            true, null, roleHolders)
    }

    @Test
    fun saveAndLoadSupervisionUserData_manyUsers_retrievesUserDataCorrectly() {
        // Get and set user data
        val userData1 = mSupervisionSettings.getUserData(1)
        userData1.changeUserData(true, "package1", true, BUNDLE_1)
        val userData2 = mSupervisionSettings.getUserData(2)
        userData2.changeUserData(true, null, false, null)
        val userData3 = mSupervisionSettings.getUserData(3)
        userData3.changeUserData(false, null, false, BUNDLE_2)
        val userData4 = mSupervisionSettings.getUserData(4)
        userData4.changeUserData(false, "package4", false, null)

        // Save, change and load user data
        mSupervisionSettings.saveUserData()
        userData1.changeUserData(false, null, false, BUNDLE_2)
        userData2.changeUserData(true, null, false, BUNDLE_1)
        userData3.changeUserData(true, "package3", true, null)
        userData4.changeUserData(false, null, false, null)
        mSupervisionSettings.loadUserData()

        // Check if user data was loaded correctly
        mSupervisionSettings.getUserData(1).checkUserData(true, "package1", true, BUNDLE_1)
        mSupervisionSettings.getUserData(2).checkUserData(true, null, false, null)
        mSupervisionSettings.getUserData(3).checkUserData(false, null, false, BUNDLE_2)
        mSupervisionSettings.getUserData(4).checkUserData(false, "package4", false, null)
    }

    @Test
    fun saveAndGetRecoveryInfo_retrievesCorrectRecoveryInfo() {
        // Save and get recovery info
        mSupervisionSettings.saveRecoveryInfo(RECOVERY_INFO)
        val retrievedRecoveryInfo = mSupervisionSettings.getRecoveryInfo()

        // Check if recovery info was retrieved correctly
        assertThat(retrievedRecoveryInfo).isNotNull()
        assertThat(retrievedRecoveryInfo.accountType).isEqualTo(RECOVERY_INFO.accountType)
        assertThat(retrievedRecoveryInfo.accountName).isEqualTo(RECOVERY_INFO.accountName)
        assertThat(retrievedRecoveryInfo.accountData.getString("id"))
            .isEqualTo(RECOVERY_INFO.accountData.getString("id"))
        assertThat(retrievedRecoveryInfo.state).isEqualTo(RECOVERY_INFO.state)
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

        fun SupervisionUserData.changeUserData(
            enabled: Boolean,
            appPackage: String?,
            lockScreenEnabled: Boolean,
            lockScreenOptions: PersistableBundle?,
            roleHolders: ArraySet<String> = ArraySet<String>(),
        ) {
            this.supervisionEnabled = enabled
            this.supervisionAppPackage = appPackage
            this.supervisionLockScreenEnabled = lockScreenEnabled
            this.supervisionLockScreenOptions = lockScreenOptions
            this.supervisionRoleHolders = roleHolders
        }

        fun SupervisionUserData.checkUserData(
            enabled: Boolean,
            appPackage: String?,
            lockScreenEnabled: Boolean,
            lockScreenOptions: PersistableBundle?,
            roleHolders: ArraySet<String> = ArraySet<String>(),
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
            assertThat(this.supervisionRoleHolders).containsExactlyElementsIn(roleHolders)
        }
    }
}
