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

package com.android.server.pm

import android.app.role.RoleManager
import android.app.supervision.SupervisionManager
import android.content.pm.Flags
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.platform.test.flag.junit.SetFlagsRule
import com.android.server.testutils.any
import com.android.server.testutils.eq
import com.android.server.testutils.whenever
import com.google.common.truth.Truth.assertThat
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.core.IsNot.not
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.Mock
import org.mockito.MockitoAnnotations

/**
 * Run as {@code atest
 * FrameworksMockingServicesTests:com.android.server.pm.ProtectedPackagesMockedTest}
 */
@RunWith(JUnit4::class)
open class ProtectedPackagesMockedTest {

    @Rule @JvmField val rule = MockSystemRule()

    @get:Rule val setFlagsRule: SetFlagsRule = SetFlagsRule()

    @Mock lateinit var roleManager: RoleManager

    @Mock lateinit var supervisionManager: SupervisionManager

    lateinit var protectedPackages: ProtectedPackages

    @Before
    @Throws(Exception::class)
    fun setup() {
        MockitoAnnotations.openMocks(this)
        rule.system().stageNominalSystemState()
        whenever(roleManager.getRoleHoldersAsUser(eq(RoleManager.ROLE_SYSTEM_SUPERVISION), any()))
            .thenReturn(listOf(SYSTEM_SUPERVISION_PKG))
        whenever(roleManager.getRoleHoldersAsUser(eq(RoleManager.ROLE_SUPERVISION), any()))
            .thenReturn(listOf(SUPERVISION_PKG))
        whenever(rule.mocks().context.getSystemService(RoleManager::class.java))
            .thenReturn(roleManager)
        whenever(supervisionManager.isSupervisionEnabledForUser(any())).thenReturn(true)
        whenever(rule.mocks().context.getSystemService(SupervisionManager::class.java))
            .thenReturn(supervisionManager)
        protectedPackages = ProtectedPackages(rule.mocks().context)
    }

    @Test
    @Throws(Exception::class)
    @EnableFlags(Flags.FLAG_PROTECT_SUPERVISION_PACKAGES)
    fun testIsPackageProtected_systemSupevisionPackage_flagEnabled_returnsTrue() {
        val stateProtected =
            protectedPackages.isPackageStateProtected(TEST_USER_ID, SYSTEM_SUPERVISION_PKG)
        val dataProtected =
            protectedPackages.isPackageDataProtected(TEST_USER_ID, SYSTEM_SUPERVISION_PKG)

        assertThat(stateProtected).isTrue()
        assertThat(dataProtected).isTrue()
    }

    @Test
    @Throws(Exception::class)
    @EnableFlags(Flags.FLAG_PROTECT_SUPERVISION_PACKAGES)
    fun testIsPackageProtected_supevisionPackage_flagEnabled_returnsTrue() {
        val stateProtected =
            protectedPackages.isPackageStateProtected(TEST_USER_ID, SUPERVISION_PKG)
        val dataProtected = protectedPackages.isPackageDataProtected(TEST_USER_ID, SUPERVISION_PKG)

        assertThat(stateProtected).isTrue()
        assertThat(dataProtected).isTrue()
    }

    @Test
    @Throws(Exception::class)
    @EnableFlags(Flags.FLAG_PROTECT_SUPERVISION_PACKAGES)
    fun testIsPackageProtected_regularPackage_flagEnabled_returnsFalse() {
        val stateProtected = protectedPackages.isPackageStateProtected(TEST_USER_ID, TEST_PKG)
        val dataProtected = protectedPackages.isPackageDataProtected(TEST_USER_ID, TEST_PKG)

        assertThat(stateProtected).isFalse()
        assertThat(dataProtected).isFalse()
    }

    @Test
    @Throws(Exception::class)
    @EnableFlags(Flags.FLAG_PROTECT_SUPERVISION_PACKAGES)
    fun testIsPackageProtected_supervisionDisabled_flagEnabled_returnsFalse() {
        whenever(supervisionManager.isSupervisionEnabledForUser(any())).thenReturn(false)

        val stateProtected =
            protectedPackages.isPackageStateProtected(TEST_USER_ID, SUPERVISION_PKG)
        val dataProtected = protectedPackages.isPackageDataProtected(TEST_USER_ID, SUPERVISION_PKG)

        assertThat(stateProtected).isFalse()
        assertThat(dataProtected).isFalse()
    }

    @Test
    @Throws(Exception::class)
    @EnableFlags(Flags.FLAG_PROTECT_SUPERVISION_PACKAGES)
    /* This case should not happen. Is is covered here for completeness. */
    fun testIsPackageProtected_missingRoleManager_flagEnabled_returnsFalse() {
        whenever(rule.mocks().context.getSystemService(RoleManager::class.java)).thenReturn(null)

        val stateProtected =
            protectedPackages.isPackageStateProtected(TEST_USER_ID, SUPERVISION_PKG)
        val dataProtected = protectedPackages.isPackageDataProtected(TEST_USER_ID, SUPERVISION_PKG)

        assertThat(stateProtected).isFalse()
        assertThat(dataProtected).isFalse()
    }

    @Test
    @Throws(Exception::class)
    @EnableFlags(Flags.FLAG_PROTECT_SUPERVISION_PACKAGES)
    /* This case should not happen. Is is covered here for completeness. */
    fun testIsPackageProtected_missingSupervisionManager_flagEnabled_returnsFalse() {
        whenever(rule.mocks().context.getSystemService(SupervisionManager::class.java))
            .thenReturn(null)

        val stateProtected =
            protectedPackages.isPackageStateProtected(TEST_USER_ID, SUPERVISION_PKG)
        val dataProtected = protectedPackages.isPackageDataProtected(TEST_USER_ID, SUPERVISION_PKG)

        assertThat(stateProtected).isFalse()
        assertThat(dataProtected).isFalse()
    }

    @Test
    @Throws(Exception::class)
    @DisableFlags(Flags.FLAG_PROTECT_SUPERVISION_PACKAGES)
    fun testIsPackageProtected_supevisionPackage_flagDisabled_returnsFalse() {
        val stateProtected =
            protectedPackages.isPackageStateProtected(TEST_USER_ID, SUPERVISION_PKG)
        val dataProtected = protectedPackages.isPackageDataProtected(TEST_USER_ID, SUPERVISION_PKG)

        assertThat(stateProtected).isFalse()
        assertThat(dataProtected).isFalse()
    }

    private companion object {
        const val SYSTEM_SUPERVISION_PKG = "com.android.system.supervision"
        const val SUPERVISION_PKG = "com.android.supervision"
        const val TEST_PKG = "com.android.stub"
        const val TEST_USER_ID = 0
    }
}
