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

package com.android.systemui.supervision.data.repository

import android.app.admin.DevicePolicyManager
import android.app.admin.devicePolicyManager
import android.app.role.RoleManager
import android.app.role.roleManager
import android.app.supervision.SupervisionManager
import android.content.ComponentName
import android.os.UserHandle
import android.platform.test.flag.junit.SetFlagsRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.biometrics.Utils.toBitmap
import com.android.systemui.coroutines.collectLastValue
import com.android.systemui.kosmos.testDispatcher
import com.android.systemui.kosmos.testScope
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.res.R
import com.android.systemui.supervision.supervisionManager
import com.android.systemui.testKosmos
import com.android.systemui.user.data.repository.FakeUserRepository
import com.android.systemui.user.data.repository.fakeUserRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.junit.MockitoJUnit
import org.mockito.junit.MockitoRule
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever

@SmallTest
@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
class SupervisionRepositoryImplTest : SysuiTestCase() {
    @get:Rule val mockitoRule: MockitoRule = MockitoJUnit.rule()

    @get:Rule val setFlagsRule = SetFlagsRule()

    private val kosmos = testKosmos().useUnconfinedTestDispatcher()
    private val testScope = kosmos.testScope
    private val roleManager: RoleManager = kosmos.roleManager
    private val supervisionManager: SupervisionManager = kosmos.supervisionManager
    private val devicePolicyManager: DevicePolicyManager = kosmos.devicePolicyManager
    private val userRepository: FakeUserRepository = kosmos.fakeUserRepository
    private val supervisionListenerCaptor = argumentCaptor<SupervisionManager.SupervisionListener>()

    private lateinit var underTest: SupervisionRepository

    @Before
    fun setup() {
        underTest =
            SupervisionRepositoryImpl(
                roleManager = roleManager,
                supervisionManagerProvider = { supervisionManager },
                userRepository = userRepository,
                devicePolicyManager = devicePolicyManager,
                context = context,
                backgroundDispatcher = kosmos.testDispatcher,
            )
    }

    @Test
    fun isSupervisionEnabled_returnsFalse() =
        testScope.runTest {
            val currentSupervisionModel by collectLastValue(underTest.supervision)

            assertNotNull(currentSupervisionModel)
            verify(supervisionManager)
                .registerSupervisionListener(supervisionListenerCaptor.capture())
            verify(supervisionManager).isSupervisionEnabledForUser(eq(USER_ID))

            supervisionListenerCaptor.lastValue.onSupervisionDisabled(USER_ID)

            assertFalse(currentSupervisionModel!!.isSupervisionEnabled)
            verifyNoMoreInteractions(supervisionManager)
        }

    @Test
    fun isSupervisionEnabled_returnsTrue() =
        testScope.runTest {
            val currentSupervisionModel by collectLastValue(underTest.supervision)

            assertNotNull(currentSupervisionModel)
            verify(supervisionManager)
                .registerSupervisionListener(supervisionListenerCaptor.capture())
            verify(supervisionManager).isSupervisionEnabledForUser(eq(USER_ID))

            supervisionListenerCaptor.lastValue.onSupervisionEnabled(USER_ID)

            assertTrue(currentSupervisionModel!!.isSupervisionEnabled)
            verifyNoMoreInteractions(supervisionManager)
        }

    @Test
    fun getProperties_returnsGenericPinSupervisionResources() =
        testScope.runTest {
            val currentSupervisionModel by collectLastValue(underTest.supervision)

            assertNotNull(currentSupervisionModel)
            verify(supervisionManager)
                .registerSupervisionListener(supervisionListenerCaptor.capture())
            verify(supervisionManager).isSupervisionEnabledForUser(eq(USER_ID))

            whenever(
                    roleManager.getRoleHoldersAsUser(
                        eq(RoleManager.ROLE_SYSTEM_SUPERVISION),
                        eq(UserHandle.of(USER_ID)),
                    )
                )
                .thenReturn(listOf(SYSTEM_SUPERVISION_APP_PACKAGE_NAME))
            whenever(
                    roleManager.getRoleHoldersAsUser(
                        eq(RoleManager.ROLE_SUPERVISION),
                        eq(UserHandle.of(USER_ID)),
                    )
                )
                .thenReturn(listOf(SUPERVISION_APP_PACKAGE_NAME))

            supervisionListenerCaptor.lastValue.onSupervisionEnabled(USER_ID)

            val expectedLabel = context.getString(R.string.status_bar_pin_supervision)
            val expectedIcon = context.getDrawable(R.drawable.ic_pin_supervision)
            val expectedDisclaimerText =
                context.getString(R.string.monitoring_description_pin_protection)
            val expectedFooterText =
                context.getString(R.string.quick_settings_disclosure_pin_protection)
            assertEquals(expectedLabel, currentSupervisionModel!!.label)
            assertTrue(currentSupervisionModel!!.icon.toBitmap()!!.sameAs(expectedIcon.toBitmap()))
            assertEquals(expectedDisclaimerText, currentSupervisionModel!!.disclaimerText)
            assertEquals(expectedFooterText, currentSupervisionModel!!.footerText)
            verify(roleManager)
                .getRoleHoldersAsUser(eq(RoleManager.ROLE_SUPERVISION), eq(UserHandle.of(USER_ID)))
            verify(roleManager)
                .getRoleHoldersAsUser(
                    eq(RoleManager.ROLE_SYSTEM_SUPERVISION),
                    eq(UserHandle.of(USER_ID)),
                )
        }

    @Test
    fun getProperties_returnsParentalControlsResources() =
        testScope.runTest {
            val currentSupervisionModel by collectLastValue(underTest.supervision)

            assertNotNull(currentSupervisionModel)
            verify(supervisionManager)
                .registerSupervisionListener(supervisionListenerCaptor.capture())
            verify(supervisionManager).isSupervisionEnabledForUser(eq(USER_ID))

            whenever(
                    roleManager.getRoleHoldersAsUser(
                        eq(RoleManager.ROLE_SYSTEM_SUPERVISION),
                        eq(UserHandle.of(USER_ID)),
                    )
                )
                .thenReturn(listOf(SYSTEM_SUPERVISION_APP_PACKAGE_NAME))
            whenever(
                    roleManager.getRoleHoldersAsUser(
                        eq(RoleManager.ROLE_SUPERVISION),
                        eq(UserHandle.of(USER_ID)),
                    )
                )
                .thenReturn(listOf(SYSTEM_SUPERVISION_APP_PACKAGE_NAME))

            supervisionListenerCaptor.lastValue.onSupervisionEnabled(USER_ID)

            val expectedLabel = context.getString(R.string.status_bar_supervision)
            val expectedIcon = context.getDrawable(R.drawable.ic_supervision)
            val expectedDisclaimerText =
                context.getString(R.string.monitoring_description_parental_controls)
            val expectedFooterText =
                context.getString(R.string.quick_settings_disclosure_parental_controls)
            assertEquals(expectedLabel, currentSupervisionModel!!.label)
            assertTrue(currentSupervisionModel!!.icon.toBitmap()!!.sameAs(expectedIcon.toBitmap()))
            assertEquals(expectedDisclaimerText, currentSupervisionModel!!.disclaimerText)
            assertEquals(expectedFooterText, currentSupervisionModel!!.footerText)
            verify(roleManager)
                .getRoleHoldersAsUser(eq(RoleManager.ROLE_SUPERVISION), eq(UserHandle.of(USER_ID)))
            verify(roleManager)
                .getRoleHoldersAsUser(
                    eq(RoleManager.ROLE_SYSTEM_SUPERVISION),
                    eq(UserHandle.of(USER_ID)),
                )
        }

    @Test
    fun getProperties_isSupervisionProfileOwner_returnsParentalControlsResources() =
        testScope.runTest {
            val currentSupervisionModel by collectLastValue(underTest.supervision)

            assertNotNull(currentSupervisionModel)
            verify(supervisionManager)
                .registerSupervisionListener(supervisionListenerCaptor.capture())
            verify(supervisionManager).isSupervisionEnabledForUser(eq(USER_ID))

            whenever(
                    roleManager.getRoleHoldersAsUser(
                        eq(RoleManager.ROLE_SYSTEM_SUPERVISION),
                        eq(UserHandle.of(USER_ID)),
                    )
                )
                .thenReturn(listOf(SYSTEM_SUPERVISION_APP_PACKAGE_NAME))
            whenever(
                    roleManager.getRoleHoldersAsUser(
                        eq(RoleManager.ROLE_SUPERVISION),
                        eq(UserHandle.of(USER_ID)),
                    )
                )
                .thenReturn(listOf(SUPERVISION_APP_PACKAGE_NAME))
            whenever(devicePolicyManager.getProfileOwnerAsUser(eq(UserHandle.of(USER_ID))))
                .thenReturn(SUPERVISION_COMPONENT)
            context
                .getOrCreateTestableResources()
                .addOverride(
                    com.android.internal.R.string.config_defaultSupervisionProfileOwnerComponent,
                    SUPERVISION_COMPONENT_STR,
                )

            supervisionListenerCaptor.lastValue.onSupervisionEnabled(USER_ID)

            val expectedLabel = context.getString(R.string.status_bar_supervision)
            val expectedIcon = context.getDrawable(R.drawable.ic_supervision)
            val expectedDisclaimerText =
                context.getString(R.string.monitoring_description_parental_controls)
            val expectedFooterText =
                context.getString(R.string.quick_settings_disclosure_parental_controls)
            assertEquals(expectedLabel, currentSupervisionModel!!.label)
            assertTrue(currentSupervisionModel!!.icon.toBitmap()!!.sameAs(expectedIcon.toBitmap()))
            assertEquals(expectedDisclaimerText, currentSupervisionModel!!.disclaimerText)
            assertEquals(expectedFooterText, currentSupervisionModel!!.footerText)
            verify(roleManager)
                .getRoleHoldersAsUser(eq(RoleManager.ROLE_SUPERVISION), eq(UserHandle.of(USER_ID)))
            verify(roleManager)
                .getRoleHoldersAsUser(
                    eq(RoleManager.ROLE_SYSTEM_SUPERVISION),
                    eq(UserHandle.of(USER_ID)),
                )
        }

    @Test
    fun getProperties_supervisionIsDisabled_returnsNull() =
        testScope.runTest {
            val currentSupervisionModel by collectLastValue(underTest.supervision)

            assertNotNull(currentSupervisionModel)
            verify(supervisionManager)
                .registerSupervisionListener(supervisionListenerCaptor.capture())
            verify(supervisionManager).isSupervisionEnabledForUser(eq(USER_ID))

            whenever(
                    roleManager.getRoleHoldersAsUser(
                        eq(RoleManager.ROLE_SYSTEM_SUPERVISION),
                        eq(UserHandle.of(USER_ID)),
                    )
                )
                .thenReturn(listOf(SYSTEM_SUPERVISION_APP_PACKAGE_NAME))
            whenever(
                    roleManager.getRoleHoldersAsUser(
                        eq(RoleManager.ROLE_SUPERVISION),
                        eq(UserHandle.of(USER_ID)),
                    )
                )
                .thenReturn(listOf(SUPERVISION_APP_PACKAGE_NAME))

            supervisionListenerCaptor.lastValue.onSupervisionDisabled(USER_ID)

            assertNull(currentSupervisionModel!!.label)
            assertNull(currentSupervisionModel!!.icon)
            assertNull(currentSupervisionModel!!.disclaimerText)
            assertNull(currentSupervisionModel!!.footerText)
        }

    companion object {
        const val USER_ID = FakeUserRepository.DEFAULT_SELECTED_USER
        const val SYSTEM_SUPERVISION_APP_PACKAGE_NAME = "com.abc.xyz.SystemSupervisionApp"
        const val SUPERVISION_APP_PACKAGE_NAME = "com.abc.xyz.SupervisionApp"
        const val SUPERVISION_COMPONENT_STR =
            "com.google.android.gms/.kids.account.receiver.ProfileOwnerReceiver"
        val SUPERVISION_COMPONENT = ComponentName.unflattenFromString(SUPERVISION_COMPONENT_STR)
        val USER_HANDLE: UserHandle = UserHandle.of(FakeUserRepository.DEFAULT_SELECTED_USER)
    }
}
