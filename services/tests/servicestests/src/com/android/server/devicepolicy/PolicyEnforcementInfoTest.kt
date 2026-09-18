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

import android.app.admin.DpcAuthority
import android.app.admin.PolicyEnforcementInfo
import android.app.admin.RoleAuthority
import android.app.admin.SystemAuthority
import android.app.role.RoleManager
import android.app.supervision.SupervisionManager
import android.content.ComponentName
import android.os.UserHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlin.test.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PolicyEnforcementInfoTest {

    @Test
    fun getAllAdmins_sortsAdmins_supervisionAdminDpcAdminOtherAdmin() {
        val policyEnforcementInfo =
            PolicyEnforcementInfo(listOf(DPC_ADMIN, SYSTEM_ADMIN, SUPERVISION_ROLE_ADMIN))
        assertThat(policyEnforcementInfo.allAdmins)
            .containsExactly(SUPERVISION_ROLE_ADMIN, DPC_ADMIN, SYSTEM_ADMIN)
            .inOrder()
    }

    @Test
    fun getAllAdmins_sortsAdmins_dpcAdminOtherAdmin() {
        val policyEnforcementInfo = PolicyEnforcementInfo(listOf(SYSTEM_ADMIN, DPC_ADMIN))
        assertThat(policyEnforcementInfo.allAdmins)
            .containsExactly(DPC_ADMIN, SYSTEM_ADMIN)
            .inOrder()
    }

    @Test
    fun isOnlyEnforcedBySystem_returnsFalse() {
        val policyEnforcementInfo =
            PolicyEnforcementInfo(listOf(SYSTEM_ADMIN, SUPERVISION_ROLE_ADMIN, DPC_ADMIN))
        assertThat(policyEnforcementInfo.isOnlyEnforcedBySystem).isFalse()
    }

    @Test
    fun isOnlyEnforcedBySystem_returnsTrue() {
        val policyEnforcementInfo = PolicyEnforcementInfo(listOf(SYSTEM_ADMIN))
        assertThat(policyEnforcementInfo.isOnlyEnforcedBySystem).isTrue()
    }

    @Test
    fun isOnlyEnforcedBySystem_emptyAdminList_returnsFalse() {
      val policyEnforcementInfo = PolicyEnforcementInfo(emptyList())
      assertThat(policyEnforcementInfo.isOnlyEnforcedBySystem).isFalse()
    }

    @Test
    fun isEnforcedBySystem_returnsTrue() {
      val policyEnforcementInfo =
        PolicyEnforcementInfo(listOf(SYSTEM_ADMIN, SUPERVISION_ROLE_ADMIN, DPC_ADMIN))
      assertThat(policyEnforcementInfo.isEnforcedBySystem).isTrue()
    }

    @Test
    fun isEnforcedBySystem_noSystemAdmin_returnsFalse() {
        val policyEnforcementInfo = PolicyEnforcementInfo(listOf(SUPERVISION_ROLE_ADMIN, DPC_ADMIN))
        assertThat(policyEnforcementInfo.isEnforcedBySystem).isFalse()
    }

    @Test
    fun isEnforcedBySystem_emptyAdminList_returnsFalse() {
      val policyEnforcementInfo = PolicyEnforcementInfo(emptyList())
      assertThat(policyEnforcementInfo.isEnforcedBySystem).isFalse()
    }

    @Test
    fun isEnforced_returnsTrue() {
        val policyEnforcementInfo = PolicyEnforcementInfo(listOf(DPC_ADMIN))
        assertThat(policyEnforcementInfo.isEnforced).isTrue()
    }

    @Test
    fun isEnforced_emptyAdminList_returnsFalse() {
        val policyEnforcementInfo = PolicyEnforcementInfo(emptyList())
        assertThat(policyEnforcementInfo.isEnforced).isFalse()
    }

    @Test
    fun getMostImportantEnforcingAdmin_returnsSupervisionRoleAdmin() {
        val policyEnforcementInfo =
            PolicyEnforcementInfo(listOf(SUPERVISION_ROLE_ADMIN, DPC_ADMIN, SYSTEM_ADMIN))
        assertThat(policyEnforcementInfo.mostImportantEnforcingAdmin).isEqualTo(SUPERVISION_ROLE_ADMIN)
    }

    @Test
    fun getMostImportantEnforcingAdmin_returnsSupervisionSystemAdmin() {
        val policyEnforcementInfo =
            PolicyEnforcementInfo(listOf(SUPERVISION_SYSTEM_ADMIN, DPC_ADMIN, SYSTEM_ADMIN))
        assertThat(policyEnforcementInfo.mostImportantEnforcingAdmin).isEqualTo(SUPERVISION_SYSTEM_ADMIN)
    }

    @Test
    fun getMostImportantEnforcingAdmin_returnsDpcAdmin() {
        val policyEnforcementInfo = PolicyEnforcementInfo(listOf(SYSTEM_ADMIN, DPC_ADMIN))
        assertThat(policyEnforcementInfo.mostImportantEnforcingAdmin).isEqualTo(DPC_ADMIN)
    }

    companion object {
        private const val PACKAGE_NAME = "package-name"
        private const val PACKAGE_CLASS = "package-name-class"
        private const val SYSTEM_ENTITY = "android-platform"
        private val SYSTEM_USER_HANDLE = UserHandle.SYSTEM
        private val COMPONENT_NAME = ComponentName(PACKAGE_NAME, PACKAGE_CLASS)
        val SUPERVISION_ROLE_ADMIN =
            android.app.admin.EnforcingAdmin(
                PACKAGE_NAME,
                RoleAuthority(setOf(RoleManager.ROLE_SYSTEM_SUPERVISION)),
                SYSTEM_USER_HANDLE,
                COMPONENT_NAME,
            )
        val SUPERVISION_SYSTEM_ADMIN =
            android.app.admin.EnforcingAdmin(
                PACKAGE_NAME,
                SystemAuthority(SupervisionManager.SUPERVISION_SYSTEM_ENTITY),
                SYSTEM_USER_HANDLE,
                null,
            )
        val DPC_ADMIN =
            android.app.admin.EnforcingAdmin(
                PACKAGE_NAME,
                DpcAuthority.DPC_AUTHORITY,
                SYSTEM_USER_HANDLE,
                COMPONENT_NAME,
            )
        val SYSTEM_ADMIN =
            android.app.admin.EnforcingAdmin(
                PACKAGE_NAME,
                SystemAuthority(SYSTEM_ENTITY),
                SYSTEM_USER_HANDLE,
                null,
            )
    }
}
