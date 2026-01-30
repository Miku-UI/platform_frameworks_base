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
 * limitations under the License
 */

package com.android.settingslib.supervision

import android.app.role.RoleManager
import android.app.supervision.SupervisionManager
import android.app.supervision.flags.Flags
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.platform.test.annotations.EnableFlags
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub

/**
 * Unit tests for [SupervisionIntentProvider].
 *
 * Run with `atest SupervisionIntentProviderTest`.
 */
@RunWith(AndroidJUnit4::class)
class SupervisionIntentProviderTest {
    private val mockPackageManager = mock<PackageManager>()
    private val mockSupervisionManager = mock<SupervisionManager>()
    private val mockRoleManager = mock<RoleManager>()

    private lateinit var context: Context

    @Before
    fun setUp() {
        context =
            object : ContextWrapper(InstrumentationRegistry.getInstrumentation().context) {
                override fun getPackageManager() = mockPackageManager

                override fun getSystemService(name: String) =
                    when (name) {
                        Context.SUPERVISION_SERVICE -> mockSupervisionManager
                        Context.ROLE_SERVICE -> mockRoleManager
                        else -> super.getSystemService(name)
                    }
            }
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_SUPERVISION_SETTINGS_SCREEN)
    fun getSettingsIntent_supervisionDisabled_returnsNull() {
        mockPackageManager.stub {
            on { queryIntentActivitiesAsUser(any<Intent>(), any<Int>(), any<Int>()) } doReturn
                listOf(ResolveInfo())
        }
        mockSupervisionManager.stub { on { isSupervisionEnabled() } doReturn false }

        val intent = SupervisionIntentProvider.getSettingsIntent(context)

        assertThat(intent).isNull()
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_SUPERVISION_SETTINGS_SCREEN)
    fun getSettingsIntent_unresolvedIntent() {
        mockPackageManager.stub {
            on { queryIntentActivitiesAsUser(any<Intent>(), any<Int>(), any<Int>()) } doReturn
                emptyList<ResolveInfo>()
        }
        mockSupervisionManager.stub { on { isSupervisionEnabled() } doReturn true }

        val intent = SupervisionIntentProvider.getSettingsIntent(context)

        assertThat(intent).isNull()
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_SUPERVISION_SETTINGS_SCREEN)
    fun getSettingsIntent_resolvedIntent_defaultSettingsPackage() {
        mockPackageManager.stub {
            on { queryIntentActivitiesAsUser(any<Intent>(), any<Int>(), any<Int>()) } doReturn
                listOf(ResolveInfo())
        }
        mockSupervisionManager.stub { on { isSupervisionEnabled() } doReturn true }

        val intent = SupervisionIntentProvider.getSettingsIntent(context)

        assertThat(intent).isNotNull()
        assertThat(intent?.action).isEqualTo("android.settings.SUPERVISION_SETTINGS")
        assertThat(intent?.`package`).isEqualTo("com.android.settings")
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_SUPERVISION_SETTINGS_SCREEN)
    fun getSettingsIntent_resolvedIntent_getSettingsPackageFromPackageManager() {
        val expectedSettingsPackage = "com.android.expected_settings"
        val resolveInfo =
            ResolveInfo().apply {
                this.activityInfo =
                    ActivityInfo().apply {
                        packageName = expectedSettingsPackage
                    }
            }
        mockPackageManager.stub {
            on { queryIntentActivitiesAsUser(any<Intent>(), any<Int>(), any<Int>()) } doReturn
                listOf(ResolveInfo())
            on { queryIntentActivities(any<Intent>(), any<Int>()) } doReturn listOf(resolveInfo)
        }
        mockSupervisionManager.stub { on { isSupervisionEnabled() } doReturn true }

        val intent = SupervisionIntentProvider.getSettingsIntent(context)

        assertThat(intent).isNotNull()
        assertThat(intent?.action).isEqualTo("android.settings.SUPERVISION_SETTINGS")
        assertThat(intent?.`package`).isEqualTo(expectedSettingsPackage)
    }

    @Test
    fun getPinRecoveryIntent_nullSupervisionPackage() {
        mockRoleManager.stub {
            on { getRoleHolders(RoleManager.ROLE_SYSTEM_SUPERVISION) } doReturn emptyList()
        }
        val intent =
            SupervisionIntentProvider.getPinRecoveryIntent(
                context,
                SupervisionIntentProvider.PinRecoveryAction.SET,
            )

        assertThat(intent).isNull()
    }

    @Test
    fun getPinRecoveryIntent_unresolvedIntent() {
        mockRoleManager.stub {
            on { getRoleHolders(RoleManager.ROLE_SYSTEM_SUPERVISION) } doReturn
                listOf(SUPERVISION_APP_PACKAGE)
        }
        mockPackageManager.stub {
            on { queryIntentActivitiesAsUser(any<Intent>(), any<Int>(), any<Int>()) } doReturn
                emptyList<ResolveInfo>()
        }

        val intent =
            SupervisionIntentProvider.getPinRecoveryIntent(
                context,
                SupervisionIntentProvider.PinRecoveryAction.SET,
            )

        assertThat(intent).isNull()
    }

    @Test
    fun getConfirmSupervisionCredentialsIntent_unresolvedIntent() {
        mockPackageManager.stub {
            on { queryIntentActivitiesAsUser(any<Intent>(), any<Int>(), any<Int>()) } doReturn
                emptyList<ResolveInfo>()
        }

        val intent = SupervisionIntentProvider.getConfirmSupervisionCredentialsIntent(context)

        assertThat(intent).isNull()
    }

    @Test
    fun getConfirmSupervisionCredentialsIntent_forceConfirm_unresolvedIntent() {
        mockPackageManager.stub {
            on { queryIntentActivitiesAsUser(any<Intent>(), any<Int>(), any<Int>()) } doReturn
                emptyList<ResolveInfo>()
        }

        val intent =
            SupervisionIntentProvider.getConfirmSupervisionCredentialsIntent(
                context = context,
                forceConfirm = true,
            )

        assertThat(intent).isNull()
    }

    @Test
    fun getPinRecoveryIntent_setup_resolvedIntent() {
        mockRoleManager.stub {
            on { getRoleHolders(RoleManager.ROLE_SYSTEM_SUPERVISION) } doReturn
                listOf(SUPERVISION_APP_PACKAGE)
        }
        mockPackageManager.stub {
            on { queryIntentActivitiesAsUser(any<Intent>(), any<Int>(), any<Int>()) } doReturn
                listOf(ResolveInfo())
        }

        val intent =
            SupervisionIntentProvider.getPinRecoveryIntent(
                context,
                SupervisionIntentProvider.PinRecoveryAction.SET,
            )

        assertThat(intent).isNotNull()
        assertThat(intent?.action).isEqualTo("android.settings.supervision.action.SET_PIN_RECOVERY")
        assertThat(intent?.`package`).isEqualTo(SUPERVISION_APP_PACKAGE)
    }

    @Test
    fun getPinRecoveryIntent_verify_resolvedIntent() {
        mockRoleManager.stub {
            on { getRoleHolders(RoleManager.ROLE_SYSTEM_SUPERVISION) } doReturn
                listOf(SUPERVISION_APP_PACKAGE)
        }
        mockPackageManager.stub {
            on { queryIntentActivitiesAsUser(any<Intent>(), any<Int>(), any<Int>()) } doReturn
                listOf(ResolveInfo())
        }

        val intent =
            SupervisionIntentProvider.getPinRecoveryIntent(
                context,
                SupervisionIntentProvider.PinRecoveryAction.VERIFY,
            )

        assertThat(intent).isNotNull()
        assertThat(intent?.action)
            .isEqualTo("android.settings.supervision.action.VERIFY_PIN_RECOVERY")
        assertThat(intent?.`package`).isEqualTo(SUPERVISION_APP_PACKAGE)
    }

    @Test
    fun getPinRecoveryIntent_update_resolvedIntent() {
        mockRoleManager.stub {
            on { getRoleHolders(RoleManager.ROLE_SYSTEM_SUPERVISION) } doReturn
                listOf(SUPERVISION_APP_PACKAGE)
        }
        mockPackageManager.stub {
            on { queryIntentActivitiesAsUser(any<Intent>(), any<Int>(), any<Int>()) } doReturn
                listOf(ResolveInfo())
        }

        val intent =
            SupervisionIntentProvider.getPinRecoveryIntent(
                context,
                SupervisionIntentProvider.PinRecoveryAction.UPDATE,
            )

        assertThat(intent).isNotNull()
        assertThat(intent?.action)
            .isEqualTo("android.settings.supervision.action.UPDATE_PIN_RECOVERY")
        assertThat(intent?.`package`).isEqualTo(SUPERVISION_APP_PACKAGE)
    }

    @Test
    fun getPinRecoveryIntent_setVerified_resolvedIntent() {
        mockRoleManager.stub {
            on { getRoleHolders(RoleManager.ROLE_SYSTEM_SUPERVISION) } doReturn
                listOf(SUPERVISION_APP_PACKAGE)
        }
        mockPackageManager.stub {
            on { queryIntentActivitiesAsUser(any<Intent>(), any<Int>(), any<Int>()) } doReturn
                listOf(ResolveInfo())
        }

        val intent =
            SupervisionIntentProvider.getPinRecoveryIntent(
                context,
                SupervisionIntentProvider.PinRecoveryAction.SET_VERIFIED,
            )

        assertThat(intent).isNotNull()
        assertThat(intent?.action)
            .isEqualTo("android.settings.supervision.action.SET_VERIFIED_PIN_RECOVERY")
        assertThat(intent?.`package`).isEqualTo(SUPERVISION_APP_PACKAGE)
    }

    @Test
    fun getPinRecoveryIntent_postSetupVerify_resolvedIntent() {
        mockRoleManager.stub {
            on { getRoleHolders(RoleManager.ROLE_SYSTEM_SUPERVISION) } doReturn
                listOf(SUPERVISION_APP_PACKAGE)
        }
        mockPackageManager.stub {
            on { queryIntentActivitiesAsUser(any<Intent>(), any<Int>(), any<Int>()) } doReturn
                listOf(ResolveInfo())
        }

        val intent =
            SupervisionIntentProvider.getPinRecoveryIntent(
                context,
                SupervisionIntentProvider.PinRecoveryAction.POST_SETUP_VERIFY,
            )

        assertThat(intent).isNotNull()
        assertThat(intent?.action)
            .isEqualTo("android.settings.supervision.action.POST_SETUP_VERIFY_PIN_RECOVERY")
        assertThat(intent?.`package`).isEqualTo(SUPERVISION_APP_PACKAGE)
    }

    @Test
    fun getConfirmSupervisionCredentialsIntent_resolvedIntent() {
        mockPackageManager.stub {
            on { queryIntentActivitiesAsUser(any<Intent>(), any<Int>(), any<Int>()) } doReturn
                listOf(ResolveInfo())
        }

        val intent = SupervisionIntentProvider.getConfirmSupervisionCredentialsIntent(context)
        assertThat(intent).isNotNull()
        assertThat(intent?.action)
            .isEqualTo("android.app.supervision.action.CONFIRM_SUPERVISION_CREDENTIALS")
        assertThat(intent?.`package`).isEqualTo("com.android.settings")
    }

    @Test
    fun getConfirmSupervisionCredentialsIntent_forceConfirm_resolvedIntent() {
        mockPackageManager.stub {
            on { queryIntentActivitiesAsUser(any<Intent>(), any<Int>(), any<Int>()) } doReturn
                listOf(ResolveInfo())
        }

        val intent =
            SupervisionIntentProvider.getConfirmSupervisionCredentialsIntent(
                context = context,
                forceConfirm = true,
            )
        assertThat(intent).isNotNull()
        assertThat(intent?.action)
            .isEqualTo("android.app.supervision.action.CONFIRM_SUPERVISION_CREDENTIALS")
        assertThat(intent?.`package`).isEqualTo("com.android.settings")
        assertThat(intent?.extras?.getBoolean("force_confirmation")).isTrue()
    }

    private companion object {
        const val SUPERVISION_APP_PACKAGE = "app.supervision"
    }
}
