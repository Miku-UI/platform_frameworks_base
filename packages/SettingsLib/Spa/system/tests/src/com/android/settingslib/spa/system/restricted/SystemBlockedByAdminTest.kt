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

package com.android.settingslib.spa.system.restricted

import android.app.admin.DevicePolicyManager
import android.app.admin.DevicePolicyResourcesManager
import android.content.Context
import android.os.UserManager
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.settingslib.spa.system.restricted.SystemBlockedByAdmin.Companion.DISABLED_BY_ADMIN_SWITCH_SUMMARY
import com.android.settingslib.spa.system.restricted.SystemBlockedByAdmin.Companion.ENABLED_BY_ADMIN_SWITCH_SUMMARY
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doNothing
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.spy
import org.mockito.kotlin.stub
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class SystemBlockedByAdminTest {
    private val mockDevicePolicyResourcesManager = mock<DevicePolicyResourcesManager>()

    private val mockDevicePolicyManager =
        mock<DevicePolicyManager> { on { resources } doReturn mockDevicePolicyResourcesManager }

    private val context: Context =
        spy(ApplicationProvider.getApplicationContext()) {
            on { getSystemService(DevicePolicyManager::class.java) } doReturn
                mockDevicePolicyManager
            doNothing().whenever(mock).startActivity(any())
        }

    @Test
    fun switchPreferenceOverridesFlow_setSummaryOnCorrectly() = runBlocking {
        mockDevicePolicyResourcesManager.stub {
            on { getString(eq(ENABLED_BY_ADMIN_SWITCH_SUMMARY), any()) } doReturn SUMMARY_ON
        }
        val systemBlockedByAdmin = SystemBlockedByAdmin(context, RESTRICTION_KEY)

        val switchPreferenceOverrides = systemBlockedByAdmin.switchPreferenceOverridesFlow.first()

        assertThat(switchPreferenceOverrides.summaryOn).isEqualTo(SUMMARY_ON)
    }

    @Test
    fun switchPreferenceOverridesFlow_setSummaryOffCorrectly() = runBlocking {
        mockDevicePolicyResourcesManager.stub {
            on { getString(eq(DISABLED_BY_ADMIN_SWITCH_SUMMARY), any()) } doReturn SUMMARY_OFF
        }
        val systemBlockedByAdmin = SystemBlockedByAdmin(context, RESTRICTION_KEY)

        val switchPreferenceOverrides = systemBlockedByAdmin.switchPreferenceOverridesFlow.first()

        assertThat(switchPreferenceOverrides.summaryOff).isEqualTo(SUMMARY_OFF)
    }

    @Test
    fun showDetails() {
        val systemBlockedByAdmin = SystemBlockedByAdmin(context, RESTRICTION_KEY)

        systemBlockedByAdmin.showDetails()

        val intent = argumentCaptor { verify(context).startActivity(capture()) }.firstValue
        assertThat(intent.action).isEqualTo(Settings.ACTION_SHOW_ADMIN_SUPPORT_DETAILS)
        assertThat(intent.getStringExtra(DevicePolicyManager.EXTRA_RESTRICTION))
            .isEqualTo(RESTRICTION_KEY)
    }

    private companion object {
        const val RESTRICTION_KEY = UserManager.DISALLOW_ADJUST_VOLUME
        const val SUMMARY_ON = "Summary on"
        const val SUMMARY_OFF = "Summary off"
    }
}
