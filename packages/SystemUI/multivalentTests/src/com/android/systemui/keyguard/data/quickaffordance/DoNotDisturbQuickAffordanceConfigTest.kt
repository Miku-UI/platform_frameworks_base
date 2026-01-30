/*
 *  Copyright (C) 2022 The Android Open Source Project
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */
package com.android.systemui.keyguard.data.quickaffordance

import android.net.Uri
import android.provider.Settings
import android.provider.Settings.Secure.ZEN_DURATION_FOREVER
import android.provider.Settings.Secure.ZEN_DURATION_PROMPT
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.settingslib.notification.modes.EnableDndDialogFactory
import com.android.settingslib.notification.modes.TestModeBuilder.MANUAL_DND
import com.android.systemui.SysuiTestCase
import com.android.systemui.animation.Expandable
import com.android.systemui.common.shared.model.ContentDescription
import com.android.systemui.common.shared.model.Icon
import com.android.systemui.coroutines.collectLastValue
import com.android.systemui.keyguard.shared.quickaffordance.ActivationState
import com.android.systemui.kosmos.testScope
import com.android.systemui.res.R
import com.android.systemui.settings.UserTracker
import com.android.systemui.shared.settings.data.repository.secureSettingsRepository
import com.android.systemui.statusbar.policy.data.repository.fakeDeviceProvisioningRepository
import com.android.systemui.statusbar.policy.data.repository.fakeZenModeRepository
import com.android.systemui.statusbar.policy.domain.interactor.zenModeInteractor
import com.android.systemui.testKosmos
import com.android.systemui.util.mockito.mock
import com.android.systemui.util.mockito.whenever
import com.google.common.truth.Truth.assertThat
import java.time.Duration
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.MockitoAnnotations

@SmallTest
@RunWith(AndroidJUnit4::class)
class DoNotDisturbQuickAffordanceConfigTest : SysuiTestCase() {

    private val kosmos = testKosmos()
    private val testScope = kosmos.testScope

    private val zenModeRepository = kosmos.fakeZenModeRepository
    private val deviceProvisioningRepository = kosmos.fakeDeviceProvisioningRepository
    private val secureSettingsRepository = kosmos.secureSettingsRepository

    @Mock private lateinit var userTracker: UserTracker
    @Mock private lateinit var conditionUri: Uri
    @Mock private lateinit var mEnableDndDialogFactory: EnableDndDialogFactory

    private lateinit var underTest: DoNotDisturbQuickAffordanceConfig

    @Before
    fun setUp() {
        MockitoAnnotations.initMocks(this)

        underTest =
            DoNotDisturbQuickAffordanceConfig(
                context,
                kosmos.zenModeInteractor,
                userTracker,
                testScope.backgroundScope,
                conditionUri,
                mEnableDndDialogFactory,
            )
    }

    @Test
    fun dndNotAvailable_pickerStateHidden() =
        testScope.runTest {
            deviceProvisioningRepository.setDeviceProvisioned(false)
            runCurrent()

            val result = underTest.getPickerScreenState()
            runCurrent()

            assertEquals(
                KeyguardQuickAffordanceConfig.PickerScreenState.UnavailableOnDevice,
                result,
            )
        }

    @Test
    fun dndAvailable_pickerStateVisible() =
        testScope.runTest {
            deviceProvisioningRepository.setDeviceProvisioned(true)
            runCurrent()

            val result = underTest.getPickerScreenState()
            runCurrent()

            assertThat(result)
                .isInstanceOf(KeyguardQuickAffordanceConfig.PickerScreenState.Default::class.java)
            val defaultPickerState =
                result as KeyguardQuickAffordanceConfig.PickerScreenState.Default
            assertThat(defaultPickerState.configureIntent).isNotNull()
            assertThat(defaultPickerState.configureIntent?.action)
                .isEqualTo(Settings.ACTION_ZEN_MODE_SETTINGS)
        }

    @Test
    fun onTriggered_dndModeIsNotOff_setToOff() =
        testScope.runTest {
            val currentModes by collectLastValue(zenModeRepository.modes)

            zenModeRepository.activateMode(MANUAL_DND)
            secureSettingsRepository.setInt(Settings.Secure.ZEN_DURATION, -2)
            collectLastValue(underTest.lockScreenState)
            runCurrent()

            val result = underTest.onTriggered(null)
            runCurrent()

            val dndMode = currentModes!!.single()
            assertThat(dndMode.isActive).isFalse()
            assertEquals(KeyguardQuickAffordanceConfig.OnTriggeredResult.Handled(false), result)
        }

    @Test
    fun onTriggered_dndModeIsOff_settingFOREVER_setZenWithoutCondition() =
        testScope.runTest {
            val currentModes by collectLastValue(zenModeRepository.modes)

            secureSettingsRepository.setInt(Settings.Secure.ZEN_DURATION, ZEN_DURATION_FOREVER)
            collectLastValue(underTest.lockScreenState)
            runCurrent()

            val result = underTest.onTriggered(null)
            runCurrent()

            val dndMode = currentModes!!.single()
            assertThat(dndMode.isActive).isTrue()
            assertThat(zenModeRepository.getModeActiveDuration(dndMode.id)).isNull()
            assertEquals(KeyguardQuickAffordanceConfig.OnTriggeredResult.Handled(false), result)
        }

    @Test
    fun onTriggered_dndModeIsOff_settingNotFOREVERorPROMPT_dndWithDuration() =
        testScope.runTest {
            val currentModes by collectLastValue(zenModeRepository.modes)
            secureSettingsRepository.setInt(Settings.Secure.ZEN_DURATION, -900)
            runCurrent()

            val result = underTest.onTriggered(null)
            runCurrent()

            assertEquals(KeyguardQuickAffordanceConfig.OnTriggeredResult.Handled(false), result)
            val dndMode = currentModes!!.single()
            assertThat(dndMode.isActive).isTrue()
            assertThat(zenModeRepository.getModeActiveDuration(dndMode.id))
                .isEqualTo(Duration.ofMinutes(-900))
        }

    @Test
    fun onTriggered_dndModeIsOff_settingIsPROMPT_showDialog() =
        testScope.runTest {
            val expandable: Expandable = mock()
            secureSettingsRepository.setInt(Settings.Secure.ZEN_DURATION, ZEN_DURATION_PROMPT)
            whenever(mEnableDndDialogFactory.createDialog()).thenReturn(mock())
            collectLastValue(underTest.lockScreenState)
            runCurrent()

            val result = underTest.onTriggered(expandable)

            assertTrue(result is KeyguardQuickAffordanceConfig.OnTriggeredResult.ShowDialog)
            assertEquals(
                expandable,
                (result as KeyguardQuickAffordanceConfig.OnTriggeredResult.ShowDialog).expandable,
            )
        }

    @Test
    fun lockScreenState_dndAvailableStartsAsTrue_changeToFalse_StateIsHidden() =
        testScope.runTest {
            deviceProvisioningRepository.setDeviceProvisioned(true)
            val valueSnapshot = collectLastValue(underTest.lockScreenState)
            val secondLastValue = valueSnapshot()
            runCurrent()

            deviceProvisioningRepository.setDeviceProvisioned(false)
            runCurrent()
            val lastValue = valueSnapshot()

            assertTrue(secondLastValue is KeyguardQuickAffordanceConfig.LockScreenState.Visible)
            assertTrue(lastValue is KeyguardQuickAffordanceConfig.LockScreenState.Hidden)
        }

    @Test
    fun lockScreenState_dndModeStartsAsOff_changeToOn_StateVisible() =
        testScope.runTest {
            val lockScreenState by collectLastValue(underTest.lockScreenState)

            assertThat(lockScreenState)
                .isEqualTo(
                    KeyguardQuickAffordanceConfig.LockScreenState.Visible(
                        Icon.Resource(
                            R.drawable.qs_dnd_icon_off,
                            ContentDescription.Resource(R.string.dnd_is_off),
                        ),
                        ActivationState.Inactive,
                    )
                )

            zenModeRepository.activateMode(MANUAL_DND)
            runCurrent()

            assertThat(lockScreenState)
                .isEqualTo(
                    KeyguardQuickAffordanceConfig.LockScreenState.Visible(
                        Icon.Resource(
                            R.drawable.qs_dnd_icon_on,
                            ContentDescription.Resource(R.string.dnd_is_on),
                        ),
                        ActivationState.Active,
                    )
                )
        }
}
