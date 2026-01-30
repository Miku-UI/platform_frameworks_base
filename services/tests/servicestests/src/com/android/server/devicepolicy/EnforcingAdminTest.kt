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

import android.app.admin.DeviceAdminAuthority
import android.app.admin.DpcAuthority
import android.app.admin.RoleAuthority
import android.app.admin.SystemAuthority
import android.content.ComponentName
import android.os.UserHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.role.RoleManagerLocal
import com.android.server.LocalManagerRegistry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.BeforeClass
import org.junit.runner.RunWith
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class EnforcingAdminTest {

    @Test
    fun createEnforcingAdmin() {
        val enforcingAdmin = EnforcingAdmin.createEnforcingAdmin(PACKAGE_NAME, SYSTEM_USER_ID)

        assertEquals(SYSTEM_USER_ID, enforcingAdmin.userId)
        assertEquals(PACKAGE_NAME, enforcingAdmin.packageName)
    }

    @Test
    fun createEnterpriseEnforcingAdmin() {
        val enforcingAdmin =
            EnforcingAdmin.createEnterpriseEnforcingAdmin(COMPONENT_NAME, SYSTEM_USER_ID)

        assertEquals(SYSTEM_USER_ID, enforcingAdmin.userId)
        assertEquals(PACKAGE_NAME, enforcingAdmin.packageName)
        assertEquals(COMPONENT_NAME, enforcingAdmin.componentName)
        assertTrue(enforcingAdmin.hasAuthority(EnforcingAdmin.DPC_AUTHORITY))
    }

    @Test
    fun createDeviceAdminEnforcingAdmin() {
        val enforcingAdmin =
            EnforcingAdmin.createDeviceAdminEnforcingAdmin(COMPONENT_NAME, SYSTEM_USER_ID)

        assertEquals(SYSTEM_USER_ID, enforcingAdmin.userId)
        assertEquals(PACKAGE_NAME, enforcingAdmin.packageName)
        assertEquals(COMPONENT_NAME, enforcingAdmin.componentName)
        assertTrue(enforcingAdmin.hasAuthority(EnforcingAdmin.DEVICE_ADMIN_AUTHORITY))
    }

    @Test
    fun createSystemEnforcingAdmin() {
        val enforcingAdmin = EnforcingAdmin.createSystemEnforcingAdmin(SYSTEM_ENTITY)

        assertEquals(SYSTEM_USER_ID, enforcingAdmin.userId)
        assertTrue(enforcingAdmin.isSystemAuthority)
    }

    @Test
    fun createEnforcingAdmin_fromParcelable_dpcAuthority() {
        val parcelableAdmin =
            android.app.admin.EnforcingAdmin(
                PACKAGE_NAME,
                DpcAuthority.DPC_AUTHORITY,
                SYSTEM_USER_HANDLE,
                COMPONENT_NAME,
            )

        val enforcingAdmin = EnforcingAdmin.createEnforcingAdmin(parcelableAdmin)

        assertEquals(SYSTEM_USER_ID, enforcingAdmin.userId)
        assertEquals(PACKAGE_NAME, enforcingAdmin.packageName)
        assertEquals(COMPONENT_NAME, enforcingAdmin.componentName)
        assertTrue(enforcingAdmin.hasAuthority(EnforcingAdmin.DPC_AUTHORITY))
    }

    @Test
    fun createEnforcingAdmin_fromParcelable_deviceAdminAuthority() {
        val parcelableAdmin =
            android.app.admin.EnforcingAdmin(
                PACKAGE_NAME,
                DeviceAdminAuthority.DEVICE_ADMIN_AUTHORITY,
                SYSTEM_USER_HANDLE,
                COMPONENT_NAME,
            )

        val enforcingAdmin = EnforcingAdmin.createEnforcingAdmin(parcelableAdmin)

        assertEquals(SYSTEM_USER_ID, enforcingAdmin.userId)
        assertEquals(PACKAGE_NAME, enforcingAdmin.packageName)
        assertEquals(COMPONENT_NAME, enforcingAdmin.componentName)
        assertTrue(enforcingAdmin.hasAuthority(EnforcingAdmin.DEVICE_ADMIN_AUTHORITY))
    }

    @Test
    fun createEnforcingAdmin_fromParcelable_roleAuthority() {
        val parcelableAdmin =
            android.app.admin.EnforcingAdmin(
                PACKAGE_NAME,
                RoleAuthority(setOf(ROLE_AUTHORITY)),
                SYSTEM_USER_HANDLE,
                COMPONENT_NAME,
            )

        val enforcingAdmin = EnforcingAdmin.createEnforcingAdmin(parcelableAdmin)

        assertEquals(SYSTEM_USER_ID, enforcingAdmin.userId)
        assertEquals(PACKAGE_NAME, enforcingAdmin.packageName)
        assertEquals(COMPONENT_NAME, enforcingAdmin.componentName)
        assertTrue(enforcingAdmin.hasAuthority(ROLE_AUTHORITY))
    }

    @Test
    fun createEnforcingAdmin_fromParcelable_systemAuthority() {
        val parcelableAdmin =
            android.app.admin.EnforcingAdmin(
                PACKAGE_NAME,
                SystemAuthority(SYSTEM_ENTITY),
                SYSTEM_USER_HANDLE,
                COMPONENT_NAME,
            )

        val enforcingAdmin = EnforcingAdmin.createEnforcingAdmin(parcelableAdmin)

        assertEquals(SYSTEM_USER_ID, enforcingAdmin.userId)
        assertTrue(enforcingAdmin.hasAuthority(SYSTEM_AUTHORITY_NAME))
        assertTrue(enforcingAdmin.isSystemAuthority)
    }

    @Test
    fun getParcelableAdmin_dpcAuthority() {
        val enforcingAdmin =
            EnforcingAdmin.createEnterpriseEnforcingAdmin(COMPONENT_NAME, SYSTEM_USER_ID)

        val parcelableAdmin = enforcingAdmin.parcelableAdmin

        assertEquals(SYSTEM_USER_HANDLE, parcelableAdmin.userHandle)
        assertEquals(PACKAGE_NAME, parcelableAdmin.packageName)
        assertEquals(COMPONENT_NAME, parcelableAdmin.componentName)
        assertEquals(parcelableAdmin.authority, DpcAuthority.DPC_AUTHORITY)
    }

    @Test
    fun getParcelableAdmin_deviceAdminAuthority() {
        val enforcingAdmin =
            EnforcingAdmin.createDeviceAdminEnforcingAdmin(COMPONENT_NAME, SYSTEM_USER_ID)

        val parcelableAdmin = enforcingAdmin.parcelableAdmin

        assertEquals(SYSTEM_USER_HANDLE, parcelableAdmin.userHandle)
        assertEquals(PACKAGE_NAME, parcelableAdmin.packageName)
        assertEquals(COMPONENT_NAME, parcelableAdmin.componentName)
        assertEquals(parcelableAdmin.authority, DeviceAdminAuthority.DEVICE_ADMIN_AUTHORITY)
    }

    @Test
    fun getParcelableAdmin_roleAuthority() {
        whenever(roleManagerLocal.getRolesAndHolders(SYSTEM_USER_ID)) doReturn
            mapOf(ROLE_AUTHORITY to setOf(PACKAGE_NAME))
        val enforcingAdmin = EnforcingAdmin.createEnforcingAdmin(PACKAGE_NAME, SYSTEM_USER_ID)
        enforcingAdmin.reloadRoleAuthorities()

        val parcelableAdmin = enforcingAdmin.parcelableAdmin

        assertEquals(SYSTEM_USER_HANDLE, parcelableAdmin.userHandle)
        assertEquals(PACKAGE_NAME, parcelableAdmin.packageName)
        assertEquals(RoleAuthority(setOf(ROLE_AUTHORITY)), parcelableAdmin.authority)
    }

    @Test
    fun getParcelableAdmin_systemAuthority() {
        val enforcingAdmin = EnforcingAdmin.createSystemEnforcingAdmin(SYSTEM_ENTITY)

        val parcelableAdmin = enforcingAdmin.parcelableAdmin

        assertEquals(SYSTEM_USER_HANDLE, parcelableAdmin.userHandle)
        assertEquals(SYSTEM_AUTHORITY, parcelableAdmin.authority)
        assertEquals(SYSTEM_ENTITY, (parcelableAdmin.authority as SystemAuthority).systemEntity)
    }

    companion object {
        private const val PACKAGE_NAME = "package-name"
        private const val PACKAGE_CLASS = "package-name-class"
        private const val SYSTEM_USER_ID = UserHandle.USER_SYSTEM
        private val SYSTEM_USER_HANDLE = UserHandle.SYSTEM
        private val COMPONENT_NAME = ComponentName(PACKAGE_NAME, PACKAGE_CLASS)
        private const val SYSTEM_ENTITY = "android-platform"
        private const val SYSTEM_AUTHORITY_NAME =
            EnforcingAdmin.SYSTEM_AUTHORITY_PREFIX + SYSTEM_ENTITY
        private val SYSTEM_AUTHORITY = SystemAuthority(SYSTEM_ENTITY)
        private const val ROLE_AUTHORITY = "role-authority"

        private val roleManagerLocal = mock<RoleManagerLocal>()

        @BeforeClass
        @JvmStatic
        fun setUpClass() {
            // TODO(b/420373209): Remove this once we have a better way to mock RoleManagerLocal.
            if (LocalManagerRegistry.getManager(RoleManagerLocal::class.java) == null) {
                LocalManagerRegistry.addManager(RoleManagerLocal::class.java, roleManagerLocal)
            }
        }
    }
}
