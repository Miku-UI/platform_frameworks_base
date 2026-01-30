/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.systemui.qs.footer.domain.interactor

import android.content.Context
import android.content.Intent
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.platform.test.flag.junit.SetFlagsRule
import android.provider.Settings
import android.testing.TestableLooper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.internal.logging.nano.MetricsProto
import com.android.internal.logging.testing.FakeMetricsLogger
import com.android.internal.logging.testing.UiEventLoggerFake
import com.android.systemui.SysuiTestCase
import com.android.systemui.animation.ActivityTransitionAnimator
import com.android.systemui.animation.Expandable
import com.android.systemui.coroutines.collectLastValue
import com.android.systemui.globalactions.GlobalActionsDialogLite
import com.android.systemui.plugins.ActivityStarter
import com.android.systemui.qs.QSSecurityFooterUtils
import com.android.systemui.qs.footer.FooterActionsTestUtils
import com.android.systemui.security.data.model.SecurityModel
import com.android.systemui.security.data.repository.SecurityRepository
import com.android.systemui.statusbar.policy.DeviceProvisionedController
import com.android.systemui.supervision.data.model.SupervisionModel
import com.android.systemui.supervision.data.repository.FakeSupervisionRepository
import com.android.systemui.truth.correspondence.FakeUiEvent
import com.android.systemui.truth.correspondence.LogMaker
import com.android.systemui.util.mockito.argumentCaptor
import com.android.systemui.util.mockito.nullable
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when` as whenever
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock

@SmallTest
@RunWith(AndroidJUnit4::class)
@ExperimentalCoroutinesApi
@TestableLooper.RunWithLooper
class FooterActionsInteractorTest : SysuiTestCase() {
    @get:Rule val setFlagsRule = SetFlagsRule()
    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private lateinit var utils: FooterActionsTestUtils

    @Before
    fun setUp() {
        utils = FooterActionsTestUtils(context, TestableLooper.get(this), testScope.testScheduler)
    }

    @Test
    fun showDeviceMonitoringDialog() {
        val qsSecurityFooterUtils: QSSecurityFooterUtils = mock()
        val underTest = utils.footerActionsInteractor(qsSecurityFooterUtils = qsSecurityFooterUtils)

        val quickSettingsContext: Context = mock()

        underTest.showDeviceMonitoringDialog(quickSettingsContext, null)
        verify(qsSecurityFooterUtils).showDeviceMonitoringDialog(quickSettingsContext, null)

        val expandable: Expandable = mock()
        underTest.showDeviceMonitoringDialog(quickSettingsContext, expandable)
        verify(qsSecurityFooterUtils).showDeviceMonitoringDialog(quickSettingsContext, expandable)
    }

    @Test
    fun showPowerMenuDialog() {
        val uiEventLogger = UiEventLoggerFake()
        val underTest = utils.footerActionsInteractor(uiEventLogger = uiEventLogger)

        val globalActionsDialogLite: GlobalActionsDialogLite = mock()
        val expandable: Expandable = mock()
        underTest.showPowerMenuDialog(globalActionsDialogLite, expandable)

        // Event is logged.
        val logs = uiEventLogger.logs
        assertThat(logs)
            .comparingElementsUsing(FakeUiEvent.EVENT_ID)
            .containsExactly(GlobalActionsDialogLite.GlobalActionsEvent.GA_OPEN_QS.id)

        // Dialog is shown.
        verify(globalActionsDialogLite)
            .showOrHideDialog(
                /* keyguardShowing= */ eq(false),
                /* isDeviceProvisioned= */ eq(true),
                eq(expandable),
                anyInt(),
            )
    }

    @Test
    fun showSettings_userSetUp() {
        val activityStarter: ActivityStarter = mock()
        val deviceProvisionedController: DeviceProvisionedController = mock()
        val metricsLogger = FakeMetricsLogger()

        // User is set up.
        whenever(deviceProvisionedController.isCurrentUserSetup).thenReturn(true)

        val underTest =
            utils.footerActionsInteractor(
                activityStarter = activityStarter,
                deviceProvisionedController = deviceProvisionedController,
                metricsLogger = metricsLogger,
            )

        underTest.showSettings(mock())

        // Event is logged.
        assertThat(metricsLogger.logs.toList())
            .comparingElementsUsing(LogMaker.CATEGORY)
            .containsExactly(MetricsProto.MetricsEvent.ACTION_QS_EXPANDED_SETTINGS_LAUNCH)

        // Activity is started.
        val intentCaptor = argumentCaptor<Intent>()
        verify(activityStarter)
            .startActivity(
                intentCaptor.capture(),
                /* dismissShade= */ eq(true),
                nullable() as? ActivityTransitionAnimator.Controller,
            )
        assertThat(intentCaptor.value.action).isEqualTo(Settings.ACTION_SETTINGS)
    }

    @Test
    fun showSettings_userNotSetUp() {
        val activityStarter: ActivityStarter = mock()
        val deviceProvisionedController: DeviceProvisionedController = mock()

        // User is not set up.
        whenever(deviceProvisionedController.isCurrentUserSetup).thenReturn(false)

        val underTest =
            utils.footerActionsInteractor(
                activityStarter = activityStarter,
                deviceProvisionedController = deviceProvisionedController,
            )

        underTest.showSettings(mock())

        // We only unlock the device.
        verify(activityStarter).postQSRunnableDismissingKeyguard(any())
    }

    @Test
    @EnableFlags(android.app.supervision.flags.Flags.FLAG_ENABLE_SUPERVISION_APP_SERVICE)
    fun securityButtonConfig_flagEnabled_usesSupervisionModel() =
        testScope.runTest {
            val qsSecurityFooterUtils: QSSecurityFooterUtils = mock()
            val securityRepository: SecurityRepository = mock()
            val securityModel =
                SecurityModel(
                    isDeviceManaged = false,
                    hasWorkProfile = false,
                    isWorkProfileOn = false,
                    isProfileOwnerOfOrganizationOwnedDevice = false,
                    deviceOwnerOrganizationName = null,
                    workProfileOrganizationName = null,
                    isNetworkLoggingEnabled = false,
                    isVpnBranded = false,
                    primaryVpnName = null,
                    workProfileVpnName = null,
                    hasCACertInCurrentUser = false,
                    hasCACertInWorkProfile = false,
                    isParentalControlsEnabled = false,
                    deviceAdminIcon = null,
                )
            val securityModelFlow = MutableStateFlow(securityModel)
            whenever(securityRepository.security).thenReturn(securityModelFlow)
            val supervisionRepository = FakeSupervisionRepository()
            supervisionRepository.updateState(
                SupervisionModel(
                    isSupervisionEnabled = true,
                    label = "Test app",
                    footerText = "This device is managed",
                    disclaimerText = "Some settings are managed by Test app",
                    icon = null,
                )
            )
            val underTest =
                utils.footerActionsInteractor(
                    qsSecurityFooterUtils = qsSecurityFooterUtils,
                    supervisionRepository = supervisionRepository,
                )

            collectLastValue(underTest.securityButtonConfig)
            runCurrent()

            verify(qsSecurityFooterUtils)
                .getButtonConfig(eq(securityModel), eq(supervisionRepository.getSupervisionModel()))
        }

    @Test
    @DisableFlags(android.app.supervision.flags.Flags.FLAG_ENABLE_SUPERVISION_APP_SERVICE)
    fun securityButtonConfig_flagDisabled_doesNotUseSupervisionModel() =
        testScope.runTest {
            val qsSecurityFooterUtils: QSSecurityFooterUtils = mock()
            val securityRepository: SecurityRepository = mock()
            val securityModel =
                SecurityModel(
                    isDeviceManaged = false,
                    hasWorkProfile = false,
                    isWorkProfileOn = false,
                    isProfileOwnerOfOrganizationOwnedDevice = false,
                    deviceOwnerOrganizationName = null,
                    workProfileOrganizationName = null,
                    isNetworkLoggingEnabled = false,
                    isVpnBranded = false,
                    primaryVpnName = null,
                    workProfileVpnName = null,
                    hasCACertInCurrentUser = false,
                    hasCACertInWorkProfile = false,
                    isParentalControlsEnabled = false,
                    deviceAdminIcon = null,
                )
            val securityModelFlow = MutableStateFlow(securityModel)
            whenever(securityRepository.security).thenReturn(securityModelFlow)
            val supervisionRepository = FakeSupervisionRepository()
            supervisionRepository.updateState(
                SupervisionModel(
                    isSupervisionEnabled = true,
                    label = "Test app",
                    footerText = "This device is managed",
                    disclaimerText = "Some settings are managed by Test app",
                    icon = null,
                )
            )
            val underTest =
                utils.footerActionsInteractor(
                    qsSecurityFooterUtils = qsSecurityFooterUtils,
                    supervisionRepository = supervisionRepository,
                )

            collectLastValue(underTest.securityButtonConfig)
            runCurrent()

            verify(qsSecurityFooterUtils).getButtonConfig(eq(securityModel), eq(null))
        }
}
