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

package com.android.systemui.statusbar.pipeline.mobile.ui.viewmodel

import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.settingslib.R.drawable.ic_sat_mobiledata
import com.android.settingslib.mobile.MobileMappings
import com.android.settingslib.mobile.TelephonyIcons.G
import com.android.settingslib.mobile.TelephonyIcons.THREE_G
import com.android.settingslib.mobile.TelephonyIcons.UNKNOWN
import com.android.systemui.SysuiTestCase
import com.android.systemui.common.shared.model.ContentDescription
import com.android.systemui.common.shared.model.Icon
import com.android.systemui.coroutines.collectLastValue
import com.android.systemui.flags.FakeFeatureFlagsClassic
import com.android.systemui.flags.Flags
import com.android.systemui.log.table.logcatTableLogBuffer
import com.android.systemui.res.R
import com.android.systemui.statusbar.connectivity.MobileIconCarrierIdOverridesFake
import com.android.systemui.statusbar.core.NewStatusBarIcons
import com.android.systemui.statusbar.pipeline.airplane.data.repository.AirplaneModeRepository
import com.android.systemui.statusbar.pipeline.airplane.data.repository.airplaneModeRepository
import com.android.systemui.statusbar.pipeline.airplane.domain.interactor.AirplaneModeInteractor
import com.android.systemui.statusbar.pipeline.airplane.domain.interactor.airplaneModeInteractor
import com.android.systemui.statusbar.pipeline.mobile.NewSatelliteIcon
import com.android.systemui.statusbar.pipeline.mobile.data.model.DataConnectionState
import com.android.systemui.statusbar.pipeline.mobile.data.model.NetworkNameModel
import com.android.systemui.statusbar.pipeline.mobile.data.repository.FakeMobileConnectionRepository
import com.android.systemui.statusbar.pipeline.mobile.data.repository.FakeMobileConnectionRepository.Companion.DEFAULT_NETWORK_NAME
import com.android.systemui.statusbar.pipeline.mobile.data.repository.FakeMobileConnectionsRepository
import com.android.systemui.statusbar.pipeline.mobile.domain.interactor.MobileIconInteractorImpl
import com.android.systemui.statusbar.pipeline.mobile.domain.interactor.MobileIconsInteractorImpl
import com.android.systemui.statusbar.pipeline.mobile.domain.model.SignalIconModel
import com.android.systemui.statusbar.pipeline.mobile.ui.model.MobileContentDescription
import com.android.systemui.statusbar.pipeline.mobile.util.FakeMobileMappingsProxy
import com.android.systemui.statusbar.pipeline.shared.ConnectivityConstants
import com.android.systemui.statusbar.pipeline.shared.data.model.ConnectivitySlot
import com.android.systemui.statusbar.pipeline.shared.data.model.DataActivityModel
import com.android.systemui.statusbar.pipeline.shared.data.repository.FakeConnectivityRepository
import com.android.systemui.statusbar.pipeline.shared.data.repository.connectivityRepository
import com.android.systemui.statusbar.pipeline.shared.data.repository.fake
import com.android.systemui.statusbar.policy.data.repository.FakeUserSetupRepository
import com.android.systemui.statusbar.systemstatusicons.flags.DisableSystemStatusIconsInCompose
import com.android.systemui.statusbar.systemstatusicons.flags.EnableSystemStatusIconsInCompose
import com.android.systemui.testKosmos
import com.android.systemui.util.CarrierConfigTracker
import com.android.systemui.util.mockito.whenever
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.MockitoAnnotations

@Suppress("EXPERIMENTAL_IS_NOT_ENABLED")
@SmallTest
@RunWith(AndroidJUnit4::class)
class MobileIconViewModelTest : SysuiTestCase() {
    private val kosmos = testKosmos()

    private var connectivityRepository = FakeConnectivityRepository()

    private lateinit var underTest: MobileIconViewModel
    private lateinit var interactor: MobileIconInteractorImpl
    private lateinit var iconsInteractor: MobileIconsInteractorImpl
    private lateinit var repository: FakeMobileConnectionRepository
    private lateinit var connectionsRepository: FakeMobileConnectionsRepository
    private lateinit var airplaneModeRepository: AirplaneModeRepository
    private lateinit var airplaneModeInteractor: AirplaneModeInteractor
    @Mock private lateinit var constants: ConnectivityConstants
    private val tableLogBuffer = logcatTableLogBuffer(kosmos, "MobileIconViewModelTest")
    @Mock private lateinit var carrierConfigTracker: CarrierConfigTracker

    private val flags =
        FakeFeatureFlagsClassic().also {
            it.set(Flags.FILTER_PROVISIONING_NETWORK_SUBSCRIPTIONS, true)
        }
    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    @Before
    fun setUp() {
        MockitoAnnotations.initMocks(this)
        whenever(constants.hasDataCapabilities).thenReturn(true)

        connectionsRepository =
            FakeMobileConnectionsRepository(FakeMobileMappingsProxy(), tableLogBuffer)

        repository =
            FakeMobileConnectionRepository(SUB_1_ID, tableLogBuffer).apply {
                setNetworkTypeKey(connectionsRepository.GSM_KEY)
                isInService.value = true
                dataConnectionState.value = DataConnectionState.Connected
                dataEnabled.value = true
            }
        connectionsRepository.activeMobileDataRepository.value = repository
        connectionsRepository.mobileIsDefault.value = true

        airplaneModeInteractor = kosmos.airplaneModeInteractor
        airplaneModeRepository = kosmos.airplaneModeRepository
        connectivityRepository = kosmos.connectivityRepository.fake

        iconsInteractor =
            MobileIconsInteractorImpl(
                connectionsRepository,
                carrierConfigTracker,
                tableLogBuffer,
                connectivityRepository,
                FakeUserSetupRepository(),
                testScope.backgroundScope,
                context,
                flags,
            )

        interactor =
            MobileIconInteractorImpl(
                testScope.backgroundScope,
                iconsInteractor.activeDataConnectionHasDataEnabled,
                iconsInteractor.alwaysShowDataRatIcon,
                iconsInteractor.alwaysUseCdmaLevel,
                iconsInteractor.isSingleCarrier,
                iconsInteractor.mobileIsDefault,
                iconsInteractor.defaultMobileIconMapping,
                iconsInteractor.defaultMobileIconGroup,
                iconsInteractor.isDefaultConnectionFailed,
                iconsInteractor.isForceHidden,
                repository,
                context,
                MobileIconCarrierIdOverridesFake(),
            )
        createAndSetViewModel()
    }

    @Test
    fun isVisible_notDataCapable_alwaysFalse() =
        testScope.runTest {
            // Create a new view model here so the constants are properly read
            whenever(constants.hasDataCapabilities).thenReturn(false)
            createAndSetViewModel()

            var latest: Boolean? = null
            val job = underTest.isVisible.onEach { latest = it }.launchIn(this)

            assertThat(latest).isFalse()

            job.cancel()
        }

    @Test
    fun isVisible_notAirplane_notForceHidden_true() =
        testScope.runTest {
            var latest: Boolean? = null
            val job = underTest.isVisible.onEach { latest = it }.launchIn(this)

            airplaneModeRepository.setIsAirplaneMode(false)

            assertThat(latest).isTrue()

            job.cancel()
        }

    @Test
    fun isVisible_airplaneAndNotAllowed_false() =
        testScope.runTest {
            var latest: Boolean? = null
            val job = underTest.isVisible.onEach { latest = it }.launchIn(this)

            airplaneModeRepository.setIsAirplaneMode(true)
            repository.isAllowedDuringAirplaneMode.value = false
            connectivityRepository.setForceHiddenIcons(setOf())

            assertThat(latest).isFalse()

            job.cancel()
        }

    /** Regression test for b/291993542. */
    @Test
    fun isVisible_airplaneButAllowed_true() =
        testScope.runTest {
            var latest: Boolean? = null
            val job = underTest.isVisible.onEach { latest = it }.launchIn(this)

            airplaneModeRepository.setIsAirplaneMode(true)
            repository.isAllowedDuringAirplaneMode.value = true
            connectivityRepository.setForceHiddenIcons(setOf())

            assertThat(latest).isTrue()

            job.cancel()
        }

    @Test
    fun isVisible_forceHidden_false() =
        testScope.runTest {
            var latest: Boolean? = null
            val job = underTest.isVisible.onEach { latest = it }.launchIn(this)

            airplaneModeRepository.setIsAirplaneMode(false)
            connectivityRepository.setForceHiddenIcons(setOf(ConnectivitySlot.MOBILE))

            assertThat(latest).isFalse()

            job.cancel()
        }

    @Test
    fun isVisible_respondsToUpdates() =
        testScope.runTest {
            var latest: Boolean? = null
            val job = underTest.isVisible.onEach { latest = it }.launchIn(this)

            airplaneModeRepository.setIsAirplaneMode(false)
            connectivityRepository.setForceHiddenIcons(setOf())

            assertThat(latest).isTrue()

            airplaneModeRepository.setIsAirplaneMode(true)
            assertThat(latest).isFalse()

            repository.isAllowedDuringAirplaneMode.value = true
            assertThat(latest).isTrue()

            connectivityRepository.setForceHiddenIcons(setOf(ConnectivitySlot.MOBILE))
            assertThat(latest).isFalse()

            job.cancel()
        }

    @Test
    fun isVisible_satellite_respectsAirplaneMode() =
        testScope.runTest {
            val latest by collectLastValue(underTest.isVisible)

            repository.isNonTerrestrial.value = true
            airplaneModeInteractor.setIsAirplaneMode(false)

            assertThat(latest).isTrue()

            airplaneModeInteractor.setIsAirplaneMode(true)

            assertThat(latest).isFalse()
        }

    @Test
    fun contentDescription_notInService_usesNoPhone() =
        testScope.runTest {
            val latest by collectLastValue(underTest.contentDescription)

            repository.isInService.value = false

            assertThat(latest as MobileContentDescription.Cellular)
                .isEqualTo(MobileContentDescription.Cellular(DEFAULT_NETWORK_NAME, NO_SIGNAL))
        }

    @Test
    fun contentDescription_includesNetworkName() =
        testScope.runTest {
            val latest by collectLastValue(underTest.contentDescription)

            repository.isInService.value = true
            repository.networkName.value = NetworkNameModel.SubscriptionDerived("Test Network Name")
            repository.numberOfLevels.value = 5
            repository.setAllLevels(3)

            assertThat(latest as MobileContentDescription.Cellular)
                .isEqualTo(MobileContentDescription.Cellular("Test Network Name", THREE_BARS))
        }

    @Test
    fun contentDescription_inService_usesLevel() =
        testScope.runTest {
            val latest by collectLastValue(underTest.contentDescription)

            repository.setAllLevels(2)

            assertThat(latest as MobileContentDescription.Cellular)
                .isEqualTo(MobileContentDescription.Cellular(DEFAULT_NETWORK_NAME, TWO_BARS))

            repository.setAllLevels(0)

            assertThat(latest as MobileContentDescription.Cellular)
                .isEqualTo(MobileContentDescription.Cellular(DEFAULT_NETWORK_NAME, NO_SIGNAL))
        }

    @Test
    fun contentDescription_nonInflated_invalidLevelUsesNoSignalText() =
        testScope.runTest {
            val latest by collectLastValue(underTest.contentDescription)

            repository.inflateSignalStrength.value = false
            repository.setAllLevels(-1)

            assertThat(latest as MobileContentDescription.Cellular)
                .isEqualTo(MobileContentDescription.Cellular(DEFAULT_NETWORK_NAME, NO_SIGNAL))

            repository.setAllLevels(100)

            assertThat(latest as MobileContentDescription.Cellular)
                .isEqualTo(MobileContentDescription.Cellular(DEFAULT_NETWORK_NAME, NO_SIGNAL))
        }

    @Test
    fun contentDescription_nonInflated_levelStrings() =
        testScope.runTest {
            val latest by collectLastValue(underTest.contentDescription)

            repository.inflateSignalStrength.value = false
            repository.setAllLevels(0)

            assertThat(latest as MobileContentDescription.Cellular)
                .isEqualTo(MobileContentDescription.Cellular(DEFAULT_NETWORK_NAME, NO_SIGNAL))

            repository.setAllLevels(1)

            assertThat(latest as MobileContentDescription.Cellular)
                .isEqualTo(MobileContentDescription.Cellular(DEFAULT_NETWORK_NAME, ONE_BAR))

            repository.setAllLevels(2)

            assertThat(latest as MobileContentDescription.Cellular)
                .isEqualTo(MobileContentDescription.Cellular(DEFAULT_NETWORK_NAME, TWO_BARS))

            repository.setAllLevels(3)

            assertThat(latest as MobileContentDescription.Cellular)
                .isEqualTo(MobileContentDescription.Cellular(DEFAULT_NETWORK_NAME, THREE_BARS))

            repository.setAllLevels(4)

            assertThat(latest as MobileContentDescription.Cellular)
                .isEqualTo(MobileContentDescription.Cellular(DEFAULT_NETWORK_NAME, FULL_BARS))
        }

    @Test
    fun contentDescription_inflated_invalidLevelUsesNoSignalText() =
        testScope.runTest {
            val latest by collectLastValue(underTest.contentDescription)

            repository.inflateSignalStrength.value = true
            repository.numberOfLevels.value = 6

            repository.setAllLevels(-2)

            assertThat(latest as MobileContentDescription.Cellular)
                .isEqualTo(MobileContentDescription.Cellular(DEFAULT_NETWORK_NAME, NO_SIGNAL))

            repository.setAllLevels(100)

            assertThat(latest as MobileContentDescription.Cellular)
                .isEqualTo(MobileContentDescription.Cellular(DEFAULT_NETWORK_NAME, NO_SIGNAL))
        }

    @Test
    fun contentDescription_inflated_levelStrings() =
        testScope.runTest {
            val latest by collectLastValue(underTest.contentDescription)

            repository.inflateSignalStrength.value = true
            repository.numberOfLevels.value = 6

            // Note that the _repo_ level is 1 lower than the reported level through the interactor

            repository.setAllLevels(0)

            assertThat(latest as MobileContentDescription.Cellular)
                .isEqualTo(MobileContentDescription.Cellular(DEFAULT_NETWORK_NAME, ONE_BAR))

            repository.setAllLevels(1)

            assertThat(latest as MobileContentDescription.Cellular)
                .isEqualTo(MobileContentDescription.Cellular(DEFAULT_NETWORK_NAME, TWO_BARS))

            repository.setAllLevels(2)

            assertThat(latest as MobileContentDescription.Cellular)
                .isEqualTo(MobileContentDescription.Cellular(DEFAULT_NETWORK_NAME, THREE_BARS))

            repository.setAllLevels(3)

            assertThat(latest as MobileContentDescription.Cellular)
                .isEqualTo(MobileContentDescription.Cellular(DEFAULT_NETWORK_NAME, FOUR_BARS))

            repository.setAllLevels(4)

            assertThat(latest as MobileContentDescription.Cellular)
                .isEqualTo(MobileContentDescription.Cellular(DEFAULT_NETWORK_NAME, FULL_BARS))
        }

    @Test
    fun contentDescription_nonInflated_testABunchOfLevelsForNull() =
        testScope.runTest {
            val latest by collectLastValue(underTest.contentDescription)

            repository.inflateSignalStrength.value = false
            repository.numberOfLevels.value = 5

            // -1 and 5 are out of the bounds for non-inflated content descriptions
            for (i in -1..5) {
                repository.setAllLevels(i)
                when (i) {
                    -1,
                    5 ->
                        assertWithMessage("Level $i is expected to be 'no signal'")
                            .that((latest as MobileContentDescription.Cellular).levelDescriptionRes)
                            .isEqualTo(NO_SIGNAL)

                    else ->
                        assertWithMessage("Level $i is expected not to be null")
                            .that(latest)
                            .isNotNull()
                }
            }
        }

    @Test
    fun contentDescription_inflated_testABunchOfLevelsForNull() =
        testScope.runTest {
            val latest by collectLastValue(underTest.contentDescription)
            repository.inflateSignalStrength.value = true
            repository.numberOfLevels.value = 6
            // -1 and 6 are out of the bounds for inflated content descriptions
            // Note that the interactor adds 1 to the reported level, hence the -2 to 5 range
            for (i in -2..5) {
                repository.setAllLevels(i)
                when (i) {
                    -2,
                    5 ->
                        assertWithMessage("Level $i is expected to be 'no signal'")
                            .that((latest as MobileContentDescription.Cellular).levelDescriptionRes)
                            .isEqualTo(NO_SIGNAL)

                    else ->
                        assertWithMessage("Level $i is not expected to be null")
                            .that(latest)
                            .isNotNull()
                }
            }
        }

    @Test
    fun networkType_dataEnabled_groupIsRepresented() =
        testScope.runTest {
            val expected =
                Icon.Resource(
                    THREE_G.dataType,
                    ContentDescription.Resource(THREE_G.dataContentDescription),
                )
            connectionsRepository.mobileIsDefault.value = true
            repository.setNetworkTypeKey(connectionsRepository.GSM_KEY)

            var latest: Icon? = null
            val job = underTest.networkTypeIcon.onEach { latest = it }.launchIn(this)

            assertThat(latest).isEqualTo(expected)

            job.cancel()
        }

    @Test
    fun networkType_null_whenDisabled() =
        testScope.runTest {
            repository.setNetworkTypeKey(connectionsRepository.GSM_KEY)
            repository.setDataEnabled(false)
            connectionsRepository.mobileIsDefault.value = true
            var latest: Icon? = null
            val job = underTest.networkTypeIcon.onEach { latest = it }.launchIn(this)

            assertThat(latest).isNull()

            job.cancel()
        }

    @Test
    fun networkType_null_whenCarrierNetworkChangeActive() =
        testScope.runTest {
            repository.setNetworkTypeKey(connectionsRepository.GSM_KEY)
            repository.carrierNetworkChangeActive.value = true
            connectionsRepository.mobileIsDefault.value = true
            var latest: Icon? = null
            val job = underTest.networkTypeIcon.onEach { latest = it }.launchIn(this)

            assertThat(latest).isNull()

            job.cancel()
        }

    @Test
    fun networkTypeIcon_notNull_whenEnabled() =
        testScope.runTest {
            val expected =
                Icon.Resource(
                    THREE_G.dataType,
                    ContentDescription.Resource(THREE_G.dataContentDescription),
                )
            repository.setNetworkTypeKey(connectionsRepository.GSM_KEY)
            repository.setDataEnabled(true)
            repository.dataConnectionState.value = DataConnectionState.Connected
            connectionsRepository.mobileIsDefault.value = true
            var latest: Icon? = null
            val job = underTest.networkTypeIcon.onEach { latest = it }.launchIn(this)

            assertThat(latest).isEqualTo(expected)

            job.cancel()
        }

    @Test
    fun networkType_nullWhenDataDisconnects() =
        testScope.runTest {
            val initial =
                Icon.Resource(
                    THREE_G.dataType,
                    ContentDescription.Resource(THREE_G.dataContentDescription),
                )

            repository.setNetworkTypeKey(connectionsRepository.GSM_KEY)
            var latest: Icon? = null
            val job = underTest.networkTypeIcon.onEach { latest = it }.launchIn(this)

            assertThat(latest).isEqualTo(initial)

            repository.dataConnectionState.value = DataConnectionState.Disconnected

            assertThat(latest).isNull()

            job.cancel()
        }

    @Test
    fun networkType_null_changeToDisabled() =
        testScope.runTest {
            val expected =
                Icon.Resource(
                    THREE_G.dataType,
                    ContentDescription.Resource(THREE_G.dataContentDescription),
                )
            repository.dataEnabled.value = true
            var latest: Icon? = null
            val job = underTest.networkTypeIcon.onEach { latest = it }.launchIn(this)

            assertThat(latest).isEqualTo(expected)

            repository.dataEnabled.value = false

            assertThat(latest).isNull()

            job.cancel()
        }

    @Test
    fun networkType_alwaysShow_shownEvenWhenDisabled() =
        testScope.runTest {
            repository.dataEnabled.value = false

            connectionsRepository.defaultDataSubRatConfig.value =
                MobileMappings.Config().also { it.alwaysShowDataRatIcon = true }

            var latest: Icon? = null
            val job = underTest.networkTypeIcon.onEach { latest = it }.launchIn(this)

            val expected =
                Icon.Resource(
                    THREE_G.dataType,
                    ContentDescription.Resource(THREE_G.dataContentDescription),
                )
            assertThat(latest).isEqualTo(expected)

            job.cancel()
        }

    @Test
    fun networkType_alwaysShow_shownEvenWhenDisconnected() =
        testScope.runTest {
            repository.setNetworkTypeKey(connectionsRepository.GSM_KEY)
            repository.dataConnectionState.value = DataConnectionState.Disconnected

            connectionsRepository.defaultDataSubRatConfig.value =
                MobileMappings.Config().also { it.alwaysShowDataRatIcon = true }

            var latest: Icon? = null
            val job = underTest.networkTypeIcon.onEach { latest = it }.launchIn(this)

            val expected =
                Icon.Resource(
                    THREE_G.dataType,
                    ContentDescription.Resource(THREE_G.dataContentDescription),
                )
            assertThat(latest).isEqualTo(expected)

            job.cancel()
        }

    @Test
    fun networkType_alwaysShow_shownEvenWhenFailedConnection() =
        testScope.runTest {
            repository.setNetworkTypeKey(connectionsRepository.GSM_KEY)
            connectionsRepository.mobileIsDefault.value = true
            connectionsRepository.defaultDataSubRatConfig.value =
                MobileMappings.Config().also { it.alwaysShowDataRatIcon = true }

            var latest: Icon? = null
            val job = underTest.networkTypeIcon.onEach { latest = it }.launchIn(this)

            val expected =
                Icon.Resource(
                    THREE_G.dataType,
                    ContentDescription.Resource(THREE_G.dataContentDescription),
                )
            assertThat(latest).isEqualTo(expected)

            job.cancel()
        }

    @Test
    fun networkType_alwaysShow_usesDefaultIconWhenInvalid() =
        testScope.runTest {
            // The UNKNOWN icon group doesn't have a valid data type icon ID, and the logic from the
            // old pipeline was to use the default icon group if the map doesn't exist
            repository.setNetworkTypeKey(UNKNOWN.name)
            connectionsRepository.defaultDataSubRatConfig.value =
                MobileMappings.Config().also { it.alwaysShowDataRatIcon = true }

            var latest: Icon? = null
            val job = underTest.networkTypeIcon.onEach { latest = it }.launchIn(this)

            val expected =
                Icon.Resource(
                    connectionsRepository.defaultMobileIconGroup.value.dataType,
                    ContentDescription.Resource(G.dataContentDescription),
                )

            assertThat(latest).isEqualTo(expected)

            job.cancel()
        }

    @Test
    fun networkType_alwaysShow_shownWhenNotDefault() =
        testScope.runTest {
            repository.setNetworkTypeKey(connectionsRepository.GSM_KEY)
            connectionsRepository.mobileIsDefault.value = false
            connectionsRepository.defaultDataSubRatConfig.value =
                MobileMappings.Config().also { it.alwaysShowDataRatIcon = true }

            var latest: Icon? = null
            val job = underTest.networkTypeIcon.onEach { latest = it }.launchIn(this)

            val expected =
                Icon.Resource(
                    THREE_G.dataType,
                    ContentDescription.Resource(THREE_G.dataContentDescription),
                )
            assertThat(latest).isEqualTo(expected)

            job.cancel()
        }

    @Test
    fun networkType_notShownWhenNotDefault() =
        testScope.runTest {
            repository.setNetworkTypeKey(connectionsRepository.GSM_KEY)
            repository.dataConnectionState.value = DataConnectionState.Connected
            connectionsRepository.mobileIsDefault.value = false

            var latest: Icon? = null
            val job = underTest.networkTypeIcon.onEach { latest = it }.launchIn(this)

            assertThat(latest).isNull()

            job.cancel()
        }

    @Test
    fun roaming() =
        testScope.runTest {
            repository.setAllRoaming(true)

            var latest: Boolean? = null
            val job = underTest.roaming.onEach { latest = it }.launchIn(this)

            assertThat(latest).isTrue()

            repository.setAllRoaming(false)

            assertThat(latest).isFalse()

            job.cancel()
        }

    @Test
    fun dataActivity_nullWhenConfigIsOff() =
        testScope.runTest {
            // Create a new view model here so the constants are properly read
            whenever(constants.shouldShowActivityConfig).thenReturn(false)
            createAndSetViewModel()

            var inVisible: Boolean? = null
            val inJob = underTest.activityInVisible.onEach { inVisible = it }.launchIn(this)

            var outVisible: Boolean? = null
            val outJob = underTest.activityInVisible.onEach { outVisible = it }.launchIn(this)

            var containerVisible: Boolean? = null
            val containerJob =
                underTest.activityInVisible.onEach { containerVisible = it }.launchIn(this)

            repository.dataActivityDirection.value =
                DataActivityModel(hasActivityIn = true, hasActivityOut = true)

            assertThat(inVisible).isFalse()
            assertThat(outVisible).isFalse()
            assertThat(containerVisible).isFalse()

            inJob.cancel()
            outJob.cancel()
            containerJob.cancel()
        }

    @Test
    @DisableSystemStatusIconsInCompose
    fun dataActivity_configOn_testIndicators_iconsInComposeFlagOff() =
        testScope.runTest {
            // Create a new view model here so the constants are properly read
            whenever(constants.shouldShowActivityConfig).thenReturn(true)
            createAndSetViewModel()

            var inVisible: Boolean? = null
            val inJob = underTest.activityInVisible.onEach { inVisible = it }.launchIn(this)

            var outVisible: Boolean? = null
            val outJob = underTest.activityOutVisible.onEach { outVisible = it }.launchIn(this)

            var containerVisible: Boolean? = null
            val containerJob =
                underTest.activityContainerVisible.onEach { containerVisible = it }.launchIn(this)

            repository.dataActivityDirection.value =
                DataActivityModel(hasActivityIn = true, hasActivityOut = false)

            yield()

            assertThat(inVisible).isTrue()
            assertThat(outVisible).isFalse()
            assertThat(containerVisible).isTrue()

            repository.dataActivityDirection.value =
                DataActivityModel(hasActivityIn = false, hasActivityOut = true)

            assertThat(inVisible).isFalse()
            assertThat(outVisible).isTrue()
            assertThat(containerVisible).isTrue()

            repository.dataActivityDirection.value =
                DataActivityModel(hasActivityIn = false, hasActivityOut = false)

            assertThat(inVisible).isFalse()
            assertThat(outVisible).isFalse()
            assertThat(containerVisible).isFalse()

            inJob.cancel()
            outJob.cancel()
            containerJob.cancel()
        }

    @Test
    @EnableSystemStatusIconsInCompose
    fun dataActivity_configOn_testIndicators_iconsInComposeFlagOn() =
        testScope.runTest {
            // Create a new view model here so the constants are properly read
            whenever(constants.shouldShowActivityConfig).thenReturn(true)
            createAndSetViewModel()

            var inVisible: Boolean? = null
            val inJob = underTest.activityInVisible.onEach { inVisible = it }.launchIn(this)

            var outVisible: Boolean? = null
            val outJob = underTest.activityOutVisible.onEach { outVisible = it }.launchIn(this)

            var containerVisible: Boolean? = null
            val containerJob =
                underTest.activityContainerVisible.onEach { containerVisible = it }.launchIn(this)

            repository.dataActivityDirection.value =
                DataActivityModel(hasActivityIn = true, hasActivityOut = false)

            yield()

            assertThat(inVisible).isTrue()
            assertThat(outVisible).isFalse()
            assertThat(containerVisible).isTrue()

            repository.dataActivityDirection.value =
                DataActivityModel(hasActivityIn = false, hasActivityOut = true)

            assertThat(inVisible).isFalse()
            assertThat(outVisible).isTrue()
            assertThat(containerVisible).isTrue()

            repository.dataActivityDirection.value =
                DataActivityModel(hasActivityIn = false, hasActivityOut = false)

            assertThat(inVisible).isFalse()
            assertThat(outVisible).isFalse()
            assertThat(containerVisible).isTrue()

            inJob.cancel()
            outJob.cancel()
            containerJob.cancel()
        }

    @Test
    fun netTypeBackground_nullWhenNoPrioritizedCapabilities() =
        testScope.runTest {
            createAndSetViewModel()

            val latest by collectLastValue(underTest.networkTypeBackground)

            repository.hasPrioritizedNetworkCapabilities.value = false

            assertThat(latest).isNull()
        }

    @Test
    @EnableFlags(NewStatusBarIcons.FLAG_NAME)
    fun netTypeBackground_notNullWhenPrioritizedCapabilities_newIcons() =
        testScope.runTest {
            createAndSetViewModel()

            val latest by collectLastValue(underTest.networkTypeBackground)

            repository.hasPrioritizedNetworkCapabilities.value = true

            assertThat(latest)
                .isEqualTo(Icon.Resource(R.drawable.mobile_network_type_background_updated, null))
        }

    @Test
    @DisableFlags(NewStatusBarIcons.FLAG_NAME)
    fun netTypeBackground_notNullWhenPrioritizedCapabilities_oldIcons() =
        testScope.runTest {
            createAndSetViewModel()

            val latest by collectLastValue(underTest.networkTypeBackground)

            repository.hasPrioritizedNetworkCapabilities.value = true

            assertThat(latest)
                .isEqualTo(Icon.Resource(R.drawable.mobile_network_type_background, null))
        }

    @Test
    @DisableFlags(NewSatelliteIcon.FLAG_NAME)
    fun nonTerrestrial_defaultProperties() =
        testScope.runTest {
            repository.isNonTerrestrial.value = true

            val roaming by collectLastValue(underTest.roaming)
            val networkTypeIcon by collectLastValue(underTest.networkTypeIcon)
            val networkTypeBackground by collectLastValue(underTest.networkTypeBackground)
            val activityInVisible by collectLastValue(underTest.activityInVisible)
            val activityOutVisible by collectLastValue(underTest.activityOutVisible)
            val activityContainerVisible by collectLastValue(underTest.activityContainerVisible)

            assertThat(roaming).isFalse()
            assertThat(networkTypeIcon).isNull()
            assertThat(networkTypeBackground).isNull()
            assertThat(activityInVisible).isFalse()
            assertThat(activityOutVisible).isFalse()
            assertThat(activityContainerVisible).isFalse()
        }

    @Test
    @EnableFlags(NewSatelliteIcon.FLAG_NAME)
    fun nonTerrestrial_defaultProperties_newSatelliteIconEnabled() =
        testScope.runTest {
            repository.isNonTerrestrial.value = true

            val roaming by collectLastValue(underTest.roaming)
            val networkTypeIcon by collectLastValue(underTest.networkTypeIcon)
            val networkTypeBackground by collectLastValue(underTest.networkTypeBackground)
            val activityInVisible by collectLastValue(underTest.activityInVisible)
            val activityOutVisible by collectLastValue(underTest.activityOutVisible)
            val activityContainerVisible by collectLastValue(underTest.activityContainerVisible)

            assertThat(roaming).isFalse()
            assertThat(networkTypeIcon)
                .isEqualTo(
                    Icon.Resource(
                        ic_sat_mobiledata,
                        ContentDescription.Resource(
                            R.string.accessibility_status_bar_satellite_symbol
                        ),
                    )
                )
            assertThat(networkTypeBackground).isNull()
            assertThat(activityInVisible).isFalse()
            assertThat(activityOutVisible).isFalse()
            assertThat(activityContainerVisible).isFalse()
        }

    @Test
    @DisableFlags(NewSatelliteIcon.FLAG_NAME)
    fun nonTerrestrial_ignoresDefaultProperties() =
        testScope.runTest {
            repository.isNonTerrestrial.value = true

            val roaming by collectLastValue(underTest.roaming)
            val networkTypeIcon by collectLastValue(underTest.networkTypeIcon)
            val networkTypeBackground by collectLastValue(underTest.networkTypeBackground)
            val activityInVisible by collectLastValue(underTest.activityInVisible)
            val activityOutVisible by collectLastValue(underTest.activityOutVisible)
            val activityContainerVisible by collectLastValue(underTest.activityContainerVisible)

            repository.setAllRoaming(true)
            repository.setNetworkTypeKey(connectionsRepository.LTE_KEY)
            // sets the background on cellular
            repository.hasPrioritizedNetworkCapabilities.value = true
            repository.dataActivityDirection.value =
                DataActivityModel(hasActivityIn = true, hasActivityOut = true)

            assertThat(roaming).isFalse()
            assertThat(networkTypeIcon).isNull()
            assertThat(networkTypeBackground).isNull()
            assertThat(activityInVisible).isFalse()
            assertThat(activityOutVisible).isFalse()
            assertThat(activityContainerVisible).isFalse()
        }

    @Test
    @EnableFlags(NewSatelliteIcon.FLAG_NAME)
    fun nonTerrestrial_ignoresDefaultProperties_newSatelliteIconEnabled() =
        testScope.runTest {
            repository.isNonTerrestrial.value = true

            val roaming by collectLastValue(underTest.roaming)
            val networkTypeIcon by collectLastValue(underTest.networkTypeIcon)
            val networkTypeBackground by collectLastValue(underTest.networkTypeBackground)
            val activityInVisible by collectLastValue(underTest.activityInVisible)
            val activityOutVisible by collectLastValue(underTest.activityOutVisible)
            val activityContainerVisible by collectLastValue(underTest.activityContainerVisible)

            repository.setAllRoaming(true)
            repository.setNetworkTypeKey(connectionsRepository.LTE_KEY)
            // sets the background on cellular
            repository.hasPrioritizedNetworkCapabilities.value = true
            repository.dataActivityDirection.value =
                DataActivityModel(hasActivityIn = true, hasActivityOut = true)

            assertThat(roaming).isFalse()
            assertThat(networkTypeIcon)
                .isEqualTo(
                    Icon.Resource(
                        ic_sat_mobiledata,
                        ContentDescription.Resource(
                            R.string.accessibility_status_bar_satellite_symbol
                        ),
                    )
                )
            assertThat(networkTypeBackground).isNull()
            assertThat(activityInVisible).isFalse()
            assertThat(activityOutVisible).isFalse()
            assertThat(activityContainerVisible).isFalse()
        }

    @Test
    @DisableFlags(NewSatelliteIcon.FLAG_NAME)
    fun nonTerrestrial_usesSatelliteIcon_flagOn() =
        testScope.runTest {
            repository.isNonTerrestrial.value = true
            repository.satelliteLevel.value = 0

            val latest by
                collectLastValue(underTest.icon.filterIsInstance(SignalIconModel.Satellite::class))

            // Level 0 -> no connection
            assertThat(latest).isNotNull()
            assertThat(latest!!.icon.resId).isEqualTo(R.drawable.ic_satellite_connected_0)

            // 1-2 -> 1 bar
            repository.satelliteLevel.value = 1
            assertThat(latest!!.icon.resId).isEqualTo(R.drawable.ic_satellite_connected_1)

            repository.satelliteLevel.value = 2
            assertThat(latest!!.icon.resId).isEqualTo(R.drawable.ic_satellite_connected_1)

            // 3-4 -> 2 bars
            repository.satelliteLevel.value = 3
            assertThat(latest!!.icon.resId).isEqualTo(R.drawable.ic_satellite_connected_2)

            repository.satelliteLevel.value = 4
            assertThat(latest!!.icon.resId).isEqualTo(R.drawable.ic_satellite_connected_2)
        }

    @Test
    @DisableFlags(NewSatelliteIcon.FLAG_NAME)
    fun satelliteIcon_ignoresInflateSignalStrength_flagOn() =
        testScope.runTest {
            // Note that this is the exact same test as above, but with inflateSignalStrength set to
            // true we note that the level is unaffected by inflation
            repository.inflateSignalStrength.value = true
            repository.isNonTerrestrial.value = true
            repository.satelliteLevel.value = 0

            val latest by
                collectLastValue(underTest.icon.filterIsInstance(SignalIconModel.Satellite::class))

            // Level 0 -> no connection
            assertThat(latest).isNotNull()
            assertThat(latest!!.icon.resId).isEqualTo(R.drawable.ic_satellite_connected_0)

            // 1-2 -> 1 bar
            repository.satelliteLevel.value = 1
            assertThat(latest!!.icon.resId).isEqualTo(R.drawable.ic_satellite_connected_1)

            repository.satelliteLevel.value = 2
            assertThat(latest!!.icon.resId).isEqualTo(R.drawable.ic_satellite_connected_1)

            // 3-4 -> 2 bars
            repository.satelliteLevel.value = 3
            assertThat(latest!!.icon.resId).isEqualTo(R.drawable.ic_satellite_connected_2)

            repository.satelliteLevel.value = 4
            assertThat(latest!!.icon.resId).isEqualTo(R.drawable.ic_satellite_connected_2)
        }

    @Test
    @EnableFlags(NewSatelliteIcon.FLAG_NAME)
    fun nonTerrestrial_contentDescription_fromSatelliteLevel() =
        testScope.runTest {
            repository.isNonTerrestrial.value = true
            val contentDescription by collectLastValue(underTest.contentDescription)

            repository.satelliteLevel.value = 0
            assertThat(
                    (contentDescription as MobileContentDescription.SatelliteContentDescription)
                        .resId
                )
                .isEqualTo(R.string.accessibility_status_bar_satellite_no_connection)

            repository.satelliteLevel.value = 2
            assertThat(
                    (contentDescription as MobileContentDescription.SatelliteContentDescription)
                        .resId
                )
                .isEqualTo(R.string.accessibility_status_bar_satellite_poor_connection)

            repository.satelliteLevel.value = 4
            assertThat(
                    (contentDescription as MobileContentDescription.SatelliteContentDescription)
                        .resId
                )
                .isEqualTo(R.string.accessibility_status_bar_satellite_good_connection)
        }

    @Test
    @EnableFlags(NewSatelliteIcon.FLAG_NAME)
    fun nonTerrestrial_contentDescription_fromSatelliteLevel_inflated() =
        testScope.runTest {
            repository.isNonTerrestrial.value = true
            repository.inflateSignalStrength.value = true
            repository.numberOfLevels.value = 6

            val contentDescription by collectLastValue(underTest.contentDescription)

            // Inflated level 0 -> shown level 1 -> reported level 0 (No connection)
            repository.satelliteLevel.value = 0
            assertThat(
                    (contentDescription as MobileContentDescription.SatelliteContentDescription)
                        .resId
                )
                .isEqualTo(R.string.accessibility_status_bar_satellite_no_connection)

            // Inflated level 2 -> shown level 3 -> reported level 2 (Poor connection)
            repository.satelliteLevel.value = 2
            assertThat(
                    (contentDescription as MobileContentDescription.SatelliteContentDescription)
                        .resId
                )
                .isEqualTo(R.string.accessibility_status_bar_satellite_poor_connection)

            // Inflated level 4 -> shown level 5 -> reported level 4 (Good connection)
            repository.satelliteLevel.value = 4
            assertThat(
                    (contentDescription as MobileContentDescription.SatelliteContentDescription)
                        .resId
                )
                .isEqualTo(R.string.accessibility_status_bar_satellite_good_connection)

            // Inflated level 5 -> shown level 6 -> reported level 5 (No connection - fallback)
            repository.satelliteLevel.value = 5
            assertThat(
                    (contentDescription as MobileContentDescription.SatelliteContentDescription)
                        .resId
                )
                .isEqualTo(R.string.accessibility_status_bar_satellite_no_connection)
        }

    @Test
    @DisableFlags(NewSatelliteIcon.FLAG_NAME)
    fun nonTerrestrial_contentDescription_fromSatelliteLevel_flagOff() =
        testScope.runTest {
            repository.isNonTerrestrial.value = true
            val contentDescription by collectLastValue(underTest.contentDescription)

            repository.satelliteLevel.value = 0
            assertThat(contentDescription).isNull()

            repository.satelliteLevel.value = 2
            assertThat(contentDescription).isNull()

            repository.satelliteLevel.value = 4
            assertThat(contentDescription).isNull()
        }

    @Test
    @EnableFlags(NewSatelliteIcon.FLAG_NAME)
    fun nonTerrestrial_usesSatelliteIcon() =
        testScope.runTest {
            val icon by collectLastValue(underTest.icon)
            repository.isNonTerrestrial.value = true

            assertThat(icon)
                .isInstanceOf(SignalIconModel.CellularTypeIconModel.SatelliteV2::class.java)
        }

    @Test
    @EnableFlags(NewSatelliteIcon.FLAG_NAME)
    fun nonTerrestrial_icon_levelChanges() =
        testScope.runTest {
            val icon by collectLastValue(underTest.icon)
            repository.isNonTerrestrial.value = true
            repository.isInService.value = true

            assertThat(icon)
                .isInstanceOf(SignalIconModel.CellularTypeIconModel.SatelliteV2::class.java)

            repository.satelliteLevel.value = 1

            assertThat(icon?.level).isEqualTo(1)

            repository.satelliteLevel.value = 3

            assertThat(icon?.level).isEqualTo(3)
        }

    @Test
    @EnableFlags(NewSatelliteIcon.FLAG_NAME)
    fun nonTerrestrial_icon_numberOfLevelsChanges() =
        testScope.runTest {
            val icon by collectLastValue(underTest.icon)
            repository.isNonTerrestrial.value = true

            assertThat(icon)
                .isInstanceOf(SignalIconModel.CellularTypeIconModel.SatelliteV2::class.java)

            repository.numberOfLevels.value = 5

            assertThat((icon as SignalIconModel.CellularTypeIconModel.SatelliteV2).numberOfLevels)
                .isEqualTo(5)

            repository.numberOfLevels.value = 4

            assertThat((icon as SignalIconModel.CellularTypeIconModel.SatelliteV2).numberOfLevels)
                .isEqualTo(4)
        }

    @Test
    @EnableFlags(NewSatelliteIcon.FLAG_NAME)
    fun nonTerrestrial_icon_inService_noCutout() =
        testScope.runTest {
            val icon by collectLastValue(underTest.icon)
            repository.isNonTerrestrial.value = true
            repository.isInService.value = true

            assertThat(icon)
                .isInstanceOf(SignalIconModel.CellularTypeIconModel.SatelliteV2::class.java)
            assertThat(
                    (icon as SignalIconModel.CellularTypeIconModel.SatelliteV2).showExclamationMark
                )
                .isFalse()
        }

    @Test
    @EnableFlags(NewSatelliteIcon.FLAG_NAME)
    fun nonTerrestrial_icon_notInService_cutout() =
        testScope.runTest {
            val icon by collectLastValue(underTest.icon)
            repository.isNonTerrestrial.value = true
            repository.isInService.value = false

            assertThat(icon)
                .isInstanceOf(SignalIconModel.CellularTypeIconModel.SatelliteV2::class.java)
            assertThat(
                    (icon as SignalIconModel.CellularTypeIconModel.SatelliteV2).showExclamationMark
                )
                .isTrue()
        }

    private fun createAndSetViewModel() {
        underTest =
            MobileIconViewModel(
                SUB_1_ID,
                interactor,
                airplaneModeInteractor,
                constants,
                testScope.backgroundScope,
            )
    }

    companion object {
        private const val SUB_1_ID = 1

        // For convenience, just define these as constants
        private val NO_SIGNAL = R.string.accessibility_no_signal
        private val ONE_BAR = R.string.accessibility_one_bar
        private val TWO_BARS = R.string.accessibility_two_bars
        private val THREE_BARS = R.string.accessibility_three_bars
        private val FOUR_BARS = R.string.accessibility_four_bars
        private val FULL_BARS = R.string.accessibility_signal_full
    }
}
