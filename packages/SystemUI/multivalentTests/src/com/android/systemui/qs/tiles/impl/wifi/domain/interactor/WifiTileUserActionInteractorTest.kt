/*
 * Copyright (C) 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.qs.tiles.impl.wifi.domain.interactor

import android.os.UserHandle
import android.provider.Settings
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.animation.Expandable
import com.android.systemui.kosmos.testScope
import com.android.systemui.qs.tiles.base.domain.actions.QSTileIntentUserInputHandlerSubject
import com.android.systemui.qs.tiles.base.domain.actions.qsTileIntentUserInputHandler
import com.android.systemui.qs.tiles.base.domain.model.QSTileInputTestKtx
import com.android.systemui.qs.tiles.dialog.InternetDialogManager
import com.android.systemui.qs.tiles.impl.wifi.domain.model.WifiTileModel
import com.android.systemui.statusbar.connectivity.AccessPointController
import com.android.systemui.statusbar.pipeline.shared.ui.model.WifiToggleState
import com.android.systemui.statusbar.pipeline.wifi.data.repository.WifiRepository
import com.android.systemui.statusbar.pipeline.wifi.ui.model.WifiTileIconModel
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@SmallTest
@RunWith(AndroidJUnit4::class)
class WifiTileUserActionInteractorTest : SysuiTestCase() {
    private val kosmos = testKosmos()
    private val testScope = kosmos.testScope
    private val intentHandler = kosmos.qsTileIntentUserInputHandler

    @Mock private lateinit var internetDialogManager: InternetDialogManager
    @Mock private lateinit var accessPointController: AccessPointController
    @Mock private lateinit var wifiRepository: WifiRepository
    @Mock private lateinit var expandable: Expandable

    private lateinit var underTest: WifiTileUserActionInteractor

    private val isWifiEnabledState = MutableStateFlow(true)
    private val wifiToggleState = MutableStateFlow(WifiToggleState.Normal)

    private val testModel = WifiTileModel.Active(WifiTileIconModel(0), "ssid")

    @Before
    fun setUp() {
        MockitoAnnotations.initMocks(this)

        whenever(wifiRepository.isWifiEnabled).thenReturn(isWifiEnabledState)
        whenever(wifiRepository.wifiToggleState).thenReturn(wifiToggleState)

        underTest =
            WifiTileUserActionInteractor(
                testScope.coroutineContext,
                internetDialogManager,
                accessPointController,
                wifiRepository,
                intentHandler,
            )
    }

    @Test
    fun handleLongClick_opensWifiSettings() =
        testScope.runTest {
            underTest.handleInput(QSTileInputTestKtx.longClick(testModel))

            QSTileIntentUserInputHandlerSubject.assertThat(intentHandler).handledOneIntentInput {
                assertThat(it.intent.action).isEqualTo(Settings.ACTION_WIFI_SETTINGS)
            }
        }

    @Test
    fun handleClick_showsInternetDialog() =
        testScope.runTest {
            whenever(accessPointController.canConfigMobileData()).thenReturn(true)
            whenever(accessPointController.canConfigWifi()).thenReturn(true)

            underTest.handleInput(QSTileInputTestKtx.click(testModel, UserHandle.of(0), expandable))

            verify(internetDialogManager)
                .create(
                    aboveStatusBar = true,
                    canConfigMobileData = true,
                    canConfigWifi = true,
                    expandable = expandable,
                )
        }

    @Test
    fun handleSecondaryClick_whenDisabled_enablesWifi() =
        testScope.runTest {
            isWifiEnabledState.value = false
            wifiToggleState.value = WifiToggleState.Normal

            underTest.handleInput(QSTileInputTestKtx.toggleClick(testModel))

            verify(wifiRepository).enableWifi()
        }

    @Test
    fun handleSecondaryClick_whenEnabledAndNotConnected_scansForWifi() =
        testScope.runTest {
            isWifiEnabledState.value = true
            wifiToggleState.value = WifiToggleState.Normal
            whenever(wifiRepository.isWifiConnectedWithValidSsid()).thenReturn(false)

            underTest.handleInput(QSTileInputTestKtx.toggleClick(testModel))

            verify(wifiRepository).scanForWifi()
        }

    @Test
    fun handleSecondaryClick_whenEnabledAndConnected_pausesWifi() =
        testScope.runTest {
            isWifiEnabledState.value = true
            wifiToggleState.value = WifiToggleState.Normal
            whenever(wifiRepository.isWifiConnectedWithValidSsid()).thenReturn(true)

            underTest.handleInput(QSTileInputTestKtx.toggleClick(testModel))

            verify(wifiRepository).pauseWifi()
        }

    @Test
    fun handleSecondaryClick_whenPausing_scansForWifi() =
        testScope.runTest {
            wifiToggleState.value = WifiToggleState.Pausing

            underTest.handleInput(QSTileInputTestKtx.toggleClick(testModel))

            verify(wifiRepository).scanForWifi()
        }

    @Test
    fun handleSecondaryClick_whenScanning_pausesWifi() =
        testScope.runTest {
            wifiToggleState.value = WifiToggleState.Scanning

            underTest.handleInput(QSTileInputTestKtx.toggleClick(testModel))

            verify(wifiRepository).pauseWifi()
        }
}
