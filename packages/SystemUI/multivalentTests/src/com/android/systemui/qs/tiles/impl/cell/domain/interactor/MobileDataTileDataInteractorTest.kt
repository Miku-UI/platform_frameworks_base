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

package com.android.systemui.qs.tiles.impl.cell.domain.interactor

import android.os.UserHandle
import android.platform.test.annotations.RequiresFlagsDisabled
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.CheckFlagsRule
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import android.platform.test.flag.junit.FlagsParameterization
import androidx.test.filters.SmallTest
import com.android.settingslib.graph.SignalDrawable
import com.android.systemui.Flags as AconfigFlags
import com.android.systemui.Flags.FLAG_QS_SPLIT_INTERNET_TILE
import com.android.systemui.SysuiTestCase
import com.android.systemui.common.shared.model.ContentDescription
import com.android.systemui.common.shared.model.Icon
import com.android.systemui.flags.Flags
import com.android.systemui.flags.andSceneContainer
import com.android.systemui.flags.fake
import com.android.systemui.flags.featureFlagsClassic
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.runCurrent
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.testScope
import com.android.systemui.log.table.logcatTableLogBuffer
import com.android.systemui.qs.pipeline.shared.pipelineFlagsRepository
import com.android.systemui.qs.tiles.impl.cell.domain.model.MobileDataTileIcon
import com.android.systemui.qs.tiles.impl.cell.domain.model.MobileDataTileModel
import com.android.systemui.res.R
import com.android.systemui.statusbar.pipeline.mobile.data.model.DataConnectionState
import com.android.systemui.statusbar.pipeline.mobile.data.repository.FakeMobileConnectionRepository
import com.android.systemui.statusbar.pipeline.mobile.data.repository.fake
import com.android.systemui.statusbar.pipeline.mobile.data.repository.mobileConnectionsRepository
import com.android.systemui.statusbar.pipeline.mobile.domain.interactor.MobileIconsInteractor
import com.android.systemui.statusbar.pipeline.mobile.domain.interactor.MobileIconsInteractorImpl
import com.android.systemui.statusbar.pipeline.shared.data.repository.connectivityRepository
import com.android.systemui.statusbar.policy.data.repository.userSetupRepository
import com.android.systemui.testKosmos
import com.android.systemui.util.CarrierConfigTracker
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import platform.test.runner.parameterized.ParameterizedAndroidJunit4
import platform.test.runner.parameterized.Parameters

@SmallTest
@RunWith(ParameterizedAndroidJunit4::class)
@OptIn(ExperimentalCoroutinesApi::class)
class MobileDataTileDataInteractorTest(flags: FlagsParameterization) : SysuiTestCase() {

    @get:Rule val checkFlagsRule: CheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule()

    init {
        mSetFlagsRule.setFlagsParameterization(flags)
    }

    private val kosmos = testKosmos()
    private val testScope = kosmos.testScope

    // Fakes needed for MobileIconsInteractorImpl
    private val connectivityRepository = kosmos.connectivityRepository
    private val mobileConnectionsRepository = kosmos.mobileConnectionsRepository
    private val userSetupRepo = kosmos.userSetupRepository
    private val featureFlags = kosmos.featureFlagsClassic
    private val pipelineFlagsRepository = kosmos.pipelineFlagsRepository
    private val carrierConfigTracker: CarrierConfigTracker = mock()

    // Real MobileIconsInteractor, fed by fakes
    private var mobileIconsInteractor: MobileIconsInteractor =
        MobileIconsInteractorImpl(
            mobileConnectionsRepository,
            carrierConfigTracker,
            logcatTableLogBuffer(kosmos, "MobileIconsInteractorTest"),
            connectivityRepository,
            userSetupRepo,
            testScope.backgroundScope,
            context,
            featureFlags,
        )

    private var underTest: MobileDataTileDataInteractor =
        MobileDataTileDataInteractor(context, mobileIconsInteractor, pipelineFlagsRepository)

    @Before
    fun setUp() {
        featureFlags.fake.set(Flags.FILTER_PROVISIONING_NETWORK_SUBSCRIPTIONS, true)
    }

    @Test
    fun tileData_noActiveSim_emitsInactiveModel() =
        kosmos.runTest {
            val tileData by collectLastValue(underTest.tileData())
            mobileConnectionsRepository.fake.setActiveMobileDataSubscriptionId(-1)
            runCurrent()

            val expectedModel =
                MobileDataTileModel(
                    isSimActive = false,
                    isEnabled = false,
                    icon =
                        MobileDataTileIcon.ResourceIcon(
                            Icon.Resource(
                                com.android.settingslib.R.drawable.ic_mobile_4_4_bar,
                                ContentDescription.Loaded(
                                    context.getString(R.string.quick_settings_cellular_detail_title)
                                ),
                            )
                        ),
                )
            assertThat(tileData).isEqualTo(expectedModel)
        }

    @Test
    fun tileData_activeSim_dataDisabled_emitsOffIcon() =
        kosmos.runTest {
            val tileData by collectLastValue(underTest.tileData())
            val mobileConnectionRepo =
                FakeMobileConnectionRepository(SUB_ID, logcatTableLogBuffer(kosmos))
            mobileConnectionsRepository.fake.setMobileConnectionRepositoryMap(
                mapOf(SUB_ID to mobileConnectionRepo)
            )
            mobileConnectionsRepository.fake.activeMobileDataSubscriptionId.value = SUB_ID
            mobileConnectionRepo.dataConnectionState.value = DataConnectionState.Connected

            // Set data to disabled
            mobileConnectionRepo.dataEnabled.value = false
            runCurrent()

            assertThat(tileData?.isSimActive).isTrue()
            assertThat(tileData?.isEnabled).isFalse()
            assertThat(tileData?.icon)
                .isEqualTo(
                    MobileDataTileIcon.ResourceIcon(
                        Icon.Resource(
                            R.drawable.ic_signal_mobile_data_off,
                            ContentDescription.Loaded(
                                context.getString(R.string.quick_settings_cellular_detail_title)
                            ),
                        )
                    )
                )
        }

    @Test
    fun tileData_activeSim_dataEnabled_emitsCellularSignalIcon() =
        kosmos.runTest {
            val tileData by collectLastValue(underTest.tileData())
            val mobileConnectionRepo =
                FakeMobileConnectionRepository(SUB_ID, logcatTableLogBuffer(kosmos))
            mobileConnectionsRepository.fake.setMobileConnectionRepositoryMap(
                mapOf(SUB_ID to mobileConnectionRepo)
            )
            mobileConnectionsRepository.fake.setActiveMobileDataSubscriptionId(SUB_ID)
            mobileConnectionRepo.dataConnectionState.value = DataConnectionState.Connected
            mobileConnectionRepo.numberOfLevels.value = 4
            mobileConnectionRepo.dataEnabled.value = true

            // Update the signal level in the fake repo
            mobileConnectionRepo.setAllLevels(0)
            runCurrent()

            val expectedState = SignalDrawable.getState(0, 4, true)
            assertThat(tileData?.isSimActive).isTrue()
            assertThat(tileData?.isEnabled).isTrue()
            assertThat(tileData?.icon).isEqualTo(MobileDataTileIcon.SignalIcon(expectedState))
        }

    @Test
    @RequiresFlagsEnabled(FLAG_QS_SPLIT_INTERNET_TILE)
    fun availability_flagEnabled_isTrue() =
        kosmos.runTest {
            assertThat(AconfigFlags.qsSplitInternetTile()).isTrue()

            val availability by collectLastValue(underTest.availability(USER))
            assertThat(availability).isTrue()
        }

    @Test
    @RequiresFlagsDisabled(FLAG_QS_SPLIT_INTERNET_TILE)
    fun availability_flagDisabled_isFalse() =
        kosmos.runTest {
            assertThat(AconfigFlags.qsSplitInternetTile()).isFalse()
            val availability by collectLastValue(underTest.availability(USER))
            assertThat(availability).isFalse()
        }

    private companion object {
        const val SUB_ID = 1
        private val USER = UserHandle.of(0)

        @JvmStatic
        @Parameters(name = "{0}")
        fun getParams(): List<FlagsParameterization> {
            return FlagsParameterization.allCombinationsOf().andSceneContainer()
        }
    }
}
