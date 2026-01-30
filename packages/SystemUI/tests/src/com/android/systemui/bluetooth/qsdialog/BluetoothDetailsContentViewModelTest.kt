/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.systemui.bluetooth.qsdialog

import android.bluetooth.BluetoothAdapter
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.platform.test.flag.junit.FlagsParameterization
import android.testing.TestableLooper
import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import androidx.test.filters.SmallTest
import com.android.settingslib.bluetooth.LocalBluetoothManager
import com.android.settingslib.flags.Flags
import com.android.systemui.Flags.FLAG_QS_TILE_DETAILED_VIEW
import com.android.systemui.SysuiTestCase
import com.android.systemui.animation.DialogTransitionAnimator
import com.android.systemui.animation.Expandable
import com.android.systemui.bluetooth.ui.viewModel.BluetoothDetailsContentViewModel
import com.android.systemui.flags.EnableSceneContainer
import com.android.systemui.kosmos.testDispatcher
import com.android.systemui.kosmos.testScope
import com.android.systemui.qs.flags.QsDetailedView
import com.android.systemui.statusbar.phone.SystemUIDialog
import com.android.systemui.testKosmos
import com.android.systemui.util.FakeSharedPreferences
import com.android.systemui.util.concurrency.FakeExecutor
import com.android.systemui.util.kotlin.getMutableStateFlow
import com.android.systemui.util.mockito.any
import com.android.systemui.util.time.FakeSystemClock
import com.android.systemui.volume.domain.interactor.audioModeInteractor
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.Mock
import org.mockito.junit.MockitoJUnit
import org.mockito.junit.MockitoRule
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import platform.test.runner.parameterized.ParameterizedAndroidJunit4
import platform.test.runner.parameterized.Parameters

@SmallTest
@RunWith(ParameterizedAndroidJunit4::class)
@TestableLooper.RunWithLooper(setAsMainLooper = true)
@EnableFlags(Flags.FLAG_BLUETOOTH_QS_TILE_DIALOG_AUTO_ON_TOGGLE)
class BluetoothDetailsContentViewModelTest(flags: FlagsParameterization) : SysuiTestCase() {

    init {
        mSetFlagsRule.setFlagsParameterization(flags)
    }

    @get:Rule val mockitoRule: MockitoRule = MockitoJUnit.rule()
    private val kosmos = testKosmos()
    private val fakeSystemClock = FakeSystemClock()
    private val backgroundExecutor = FakeExecutor(fakeSystemClock)

    private lateinit var bluetoothDetailsContentViewModel: BluetoothDetailsContentViewModel

    @Mock private lateinit var bluetoothDeviceMetadataInteractor: BluetoothDeviceMetadataInteractor

    @Mock private lateinit var deviceItemInteractor: DeviceItemInteractor

    @Mock private lateinit var deviceItemActionInteractor: DeviceItemActionInteractor

    @Mock private lateinit var mDialogTransitionAnimator: DialogTransitionAnimator

    @Mock private lateinit var bluetoothAdapter: BluetoothAdapter

    @Mock private lateinit var localBluetoothManager: LocalBluetoothManager

    @Mock private lateinit var bluetoothTileDialogLogger: BluetoothTileDialogLogger

    @Mock
    private lateinit var mBluetoothTileDialogDelegateFactory: BluetoothTileDialogDelegate.Factory

    @Mock private lateinit var bluetoothTileDialogDelegate: BluetoothTileDialogDelegate

    @Mock
    private lateinit var bluetoothDetailsContentManagerFactory:
        BluetoothDetailsContentManager.Factory

    @Mock private lateinit var bluetoothDetailsContentManager: BluetoothDetailsContentManager

    @Mock private lateinit var sysuiDialog: SystemUIDialog
    @Mock private lateinit var expandable: Expandable
    @Mock private lateinit var controller: DialogTransitionAnimator.Controller
    @Mock private lateinit var mockView: View

    private val sharedPreferences = FakeSharedPreferences()

    private lateinit var dispatcher: CoroutineDispatcher
    private lateinit var testScope: TestScope

    @Before
    fun setUp() {
        dispatcher = kosmos.testDispatcher
        testScope = kosmos.testScope
        // TODO(b/364515243): use real object instead of mock
        whenever(kosmos.deviceItemInteractor.deviceItemUpdate).thenReturn(MutableSharedFlow())
        bluetoothDetailsContentViewModel =
            BluetoothDetailsContentViewModel(
                deviceItemInteractor,
                deviceItemActionInteractor,
                BluetoothStateInteractor(
                    localBluetoothManager,
                    bluetoothTileDialogLogger,
                    testScope.backgroundScope,
                    dispatcher,
                ),
                // TODO(b/316822488): Create FakeBluetoothAutoOnInteractor.
                BluetoothAutoOnInteractor(
                    BluetoothAutoOnRepository(
                        localBluetoothManager,
                        bluetoothAdapter,
                        testScope.backgroundScope,
                        dispatcher,
                    )
                ),
                kosmos.audioSharingInteractor,
                kosmos.audioModeInteractor,
                kosmos.audioSharingButtonViewModelFactory,
                bluetoothDeviceMetadataInteractor,
                mDialogTransitionAnimator,
                bluetoothTileDialogLogger,
                testScope.backgroundScope,
                dispatcher,
                dispatcher,
                sharedPreferences,
                mBluetoothTileDialogDelegateFactory,
                bluetoothDetailsContentManagerFactory,
            )
        whenever(deviceItemInteractor.deviceItemUpdate).thenReturn(MutableSharedFlow())
        whenever(deviceItemInteractor.deviceItemUpdateRequest)
            .thenReturn(MutableStateFlow(Unit).asStateFlow())
        whenever(deviceItemInteractor.showSeeAllUpdate).thenReturn(getMutableStateFlow(false))
        whenever(bluetoothDeviceMetadataInteractor.metadataUpdate).thenReturn(MutableSharedFlow())
        whenever(mBluetoothTileDialogDelegateFactory.create(any(), anyInt(), any()))
            .thenReturn(bluetoothTileDialogDelegate)
        whenever(bluetoothTileDialogDelegate.createDialog()).thenReturn(sysuiDialog)
        whenever(bluetoothTileDialogDelegate.contentManager)
            .thenReturn(bluetoothDetailsContentManager)
        whenever(bluetoothDetailsContentManagerFactory.create(any(), anyInt(), anyBoolean(), any()))
            .thenReturn(bluetoothDetailsContentManager)
        whenever(sysuiDialog.context).thenReturn(mContext)
        whenever<Any?>(sysuiDialog.requireViewById(anyInt())).thenReturn(mockView)
        whenever(bluetoothDetailsContentManager.bluetoothStateToggle)
            .thenReturn(getMutableStateFlow(false))
        whenever(bluetoothDetailsContentManager.deviceItemClick)
            .thenReturn(getMutableStateFlow(null))
        whenever(bluetoothDetailsContentManager.contentHeight).thenReturn(getMutableStateFlow(0))
        whenever(bluetoothDetailsContentManager.bluetoothAutoOnToggle)
            .thenReturn(getMutableStateFlow(false))
        whenever(expandable.dialogTransitionController(any())).thenReturn(controller)
        whenever(mockView.context).thenReturn(mContext)
    }

    @Test
    @DisableFlags(QsDetailedView.FLAG_NAME)
    fun testShowDialog_noAnimation() {
        testScope.runTest {
            bluetoothDetailsContentViewModel.showDialog(null)
            runCurrent()

            verify(mDialogTransitionAnimator, never()).show(any(), any(), any())
        }
    }

    @Test
    @DisableFlags(QsDetailedView.FLAG_NAME)
    fun testShowDialog_animated() {
        testScope.runTest {
            bluetoothDetailsContentViewModel.showDialog(expandable)
            runCurrent()

            verify(mDialogTransitionAnimator).show(any(), any(), anyBoolean())
        }
    }

    @Test
    @EnableSceneContainer
    @EnableFlags(FLAG_QS_TILE_DETAILED_VIEW)
    fun testBindDetailsView() {
        testScope.runTest {
            bluetoothDetailsContentViewModel.bindDetailsView(mockView)
            runCurrent()

            verify(bluetoothDetailsContentManager).bind(eq(mockView), eq(null), any(), any())
            verify(bluetoothDetailsContentManager).start()
        }
    }

    @Test
    @DisableFlags(QsDetailedView.FLAG_NAME)
    fun testShowDialog_animated_callInBackgroundThread() {
        testScope.runTest {
            backgroundExecutor.execute {
                bluetoothDetailsContentViewModel.showDialog(expandable)
                runCurrent()

                verify(mDialogTransitionAnimator).show(any(), any(), anyBoolean())
            }
        }
    }

    @Test
    @EnableSceneContainer
    @EnableFlags(FLAG_QS_TILE_DETAILED_VIEW)
    fun testBindDetailsView_callInBackgroundThread() {
        testScope.runTest {
            backgroundExecutor.execute {
                bluetoothDetailsContentViewModel.bindDetailsView(mockView)
                runCurrent()

                verify(bluetoothDetailsContentManager).bind(eq(mockView), eq(null), any(), any())
                verify(bluetoothDetailsContentManager).start()
            }
        }
    }

    @Test
    @DisableFlags(QsDetailedView.FLAG_NAME)
    fun testShowDialog_fetchDeviceItem() {
        testScope.runTest {
            bluetoothDetailsContentViewModel.showDialog(null)
            runCurrent()

            verify(deviceItemInteractor).deviceItemUpdate
        }
    }

    @Test
    @EnableSceneContainer
    @EnableFlags(FLAG_QS_TILE_DETAILED_VIEW)
    fun testBindDetailsView_fetchDeviceItem() {
        testScope.runTest {
            bluetoothDetailsContentViewModel.bindDetailsView(mockView)
            runCurrent()

            verify(deviceItemInteractor).deviceItemUpdate
        }
    }

    @Test
    fun testBuildUiProperties_bluetoothOn_shouldHideAutoOn() {
        testScope.runTest {
            val actual =
                BluetoothDetailsContentViewModel.UiProperties.build(
                    isBluetoothEnabled = true,
                    isAutoOnToggleFeatureAvailable = true,
                )
            assertThat(actual.autoOnToggleVisibility).isEqualTo(GONE)
        }
    }

    @Test
    fun testBuildUiProperties_bluetoothOff_shouldShowAutoOn() {
        testScope.runTest {
            val actual =
                BluetoothDetailsContentViewModel.UiProperties.build(
                    isBluetoothEnabled = false,
                    isAutoOnToggleFeatureAvailable = true,
                )
            assertThat(actual.autoOnToggleVisibility).isEqualTo(VISIBLE)
        }
    }

    @Test
    fun testBuildUiProperties_bluetoothOff_autoOnFeatureUnavailable_shouldHideAutoOn() {
        testScope.runTest {
            val actual =
                BluetoothDetailsContentViewModel.UiProperties.build(
                    isBluetoothEnabled = false,
                    isAutoOnToggleFeatureAvailable = false,
                )
            assertThat(actual.autoOnToggleVisibility).isEqualTo(GONE)
        }
    }

    @Test
    fun testIsAutoOnToggleFeatureAvailable_returnTrue() {
        testScope.runTest {
            whenever(bluetoothAdapter.isAutoOnSupported).thenReturn(true)

            val actual = bluetoothDetailsContentViewModel.isAutoOnToggleFeatureAvailable()
            assertThat(actual).isTrue()
        }
    }

    @Test
    fun testIsAutoOnToggleFeatureAvailable_returnFalse() {
        testScope.runTest {
            whenever(bluetoothAdapter.isAutoOnSupported).thenReturn(false)

            val actual = bluetoothDetailsContentViewModel.isAutoOnToggleFeatureAvailable()
            assertThat(actual).isFalse()
        }
    }

    @Test
    @DisableFlags(QsDetailedView.FLAG_NAME)
    fun testUpdateTitleAndSubtitle() {
        testScope.runTest {
            assertThat(bluetoothDetailsContentViewModel.title).isEqualTo("")
            assertThat(bluetoothDetailsContentViewModel.subTitle).isEqualTo("")

            bluetoothDetailsContentViewModel.showDialog(expandable)
            runCurrent()

            assertThat(bluetoothDetailsContentViewModel.title).isEqualTo("Bluetooth")
            assertThat(bluetoothDetailsContentViewModel.subTitle).isEqualTo("Bluetooth is off")
        }
    }

    companion object {
        @JvmStatic
        @Parameters(name = "{0}")
        fun flagsParameterization() =
            FlagsParameterization.allCombinationsOf(QsDetailedView.FLAG_NAME)
    }
}
