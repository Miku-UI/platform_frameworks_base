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

package com.android.systemui.qs.tiles.impl.modes.domain.interactor

import android.app.AutomaticZenRule
import android.app.Flags
import android.graphics.drawable.TestStubDrawable
import android.os.UserHandle
import android.platform.test.annotations.EnableFlags
import android.platform.test.flag.junit.FlagsParameterization
import androidx.test.filters.SmallTest
import com.android.internal.R
import com.android.settingslib.notification.modes.TestModeBuilder
import com.android.systemui.SysuiTestCase
import com.android.systemui.SysuiTestableContext
import com.android.systemui.common.shared.model.ContentDescription
import com.android.systemui.common.shared.model.Icon
import com.android.systemui.coroutines.collectLastValue
import com.android.systemui.coroutines.collectValues
import com.android.systemui.flags.andSceneContainer
import com.android.systemui.keyguard.data.repository.fakeKeyguardRepository
import com.android.systemui.keyguard.shared.model.StatusBarState
import com.android.systemui.kosmos.testDispatcher
import com.android.systemui.kosmos.testScope
import com.android.systemui.qs.tiles.base.domain.model.DataUpdateTrigger
import com.android.systemui.qs.tiles.impl.modes.domain.model.ModesTileModel
import com.android.systemui.scene.shared.flag.SceneContainerFlag
import com.android.systemui.shade.domain.interactor.shadeInteractor
import com.android.systemui.shade.shadeTestUtil
import com.android.systemui.statusbar.policy.data.repository.fakeZenModeRepository
import com.android.systemui.statusbar.policy.domain.interactor.zenModeInteractor
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import java.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toCollection
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import platform.test.runner.parameterized.ParameterizedAndroidJunit4
import platform.test.runner.parameterized.Parameters

@SmallTest
@RunWith(ParameterizedAndroidJunit4::class)
@OptIn(ExperimentalCoroutinesApi::class)
class ModesTileDataInteractorTest(flags: FlagsParameterization) : SysuiTestCase() {

    init {
        mSetFlagsRule.setFlagsParameterization(flags)
    }

    private val TEST_USER = UserHandle.of(1)!!
    private val kosmos = testKosmos()
    private val testScope = kosmos.testScope
    private val dispatcher = kosmos.testDispatcher
    private val zenModeRepository = kosmos.fakeZenModeRepository
    private val keyguardRepository = kosmos.fakeKeyguardRepository
    private val shadeTestUtil by lazy { kosmos.shadeTestUtil }

    private val underTest by lazy {
        ModesTileDataInteractor(
            context,
            kosmos.zenModeInteractor,
            kosmos.shadeInteractor,
            keyguardRepository,
            dispatcher,
            testScope,
        )
    }

    @Before
    fun setUp() {
        context.orCreateTestableResources.apply {
            addOverride(MODES_DRAWABLE_ID, MODES_DRAWABLE)
            addOverride(BEDTIME_DRAWABLE_ID, BEDTIME_DRAWABLE)
            addOverride(THEATER_DRAWABLE_ID, THEATER_DRAWABLE)
        }

        val customPackageContext = SysuiTestableContext(context)
        context.prepareCreatePackageContext(CUSTOM_PACKAGE, customPackageContext)
        customPackageContext.orCreateTestableResources.apply {
            addOverride(CUSTOM_DRAWABLE_ID, CUSTOM_DRAWABLE)
        }
    }

    @Test
    fun availableWhenFlagIsOn() =
        testScope.runTest {
            val availability = underTest.availability(TEST_USER).toCollection(mutableListOf())

            assertThat(availability).containsExactly(true)
        }

    @Test
    fun isActivatedWhenModesChange() =
        testScope.runTest {
            val dataList: List<ModesTileModel> by
                collectValues(
                    underTest.tileData(TEST_USER, flowOf(DataUpdateTrigger.InitialRequest))
                )
            runCurrent()
            assertThat(dataList.map { it.isActivated }).containsExactly(false).inOrder()

            // Add active mode
            zenModeRepository.addMode(id = "One", active = true)
            runCurrent()
            assertThat(dataList.map { it.isActivated }).containsExactly(false, true).inOrder()
            assertThat(dataList.map { it.activeModes }.last().map { it.name })
                .containsExactly("Mode One")

            // Add an inactive mode: state hasn't changed, so this shouldn't cause another emission
            zenModeRepository.addMode(id = "Two", active = false)
            runCurrent()
            assertThat(dataList.map { it.isActivated }).containsExactly(false, true).inOrder()
            assertThat(dataList.map { it.activeModes }.last().map { it.name })
                .containsExactly("Mode One")

            // Add another active mode
            zenModeRepository.addMode(id = "Three", active = true)
            runCurrent()
            assertThat(dataList.map { it.isActivated }).containsExactly(false, true, true).inOrder()
            assertThat(dataList.map { it.activeModes }.last().map { it.name })
                .containsExactly("Mode One", "Mode Three")
                .inOrder()

            // Remove a mode and deactivate the other
            zenModeRepository.removeMode("One")
            runCurrent()
            zenModeRepository.deactivateMode("Three")
            runCurrent()
            assertThat(dataList.map { it.isActivated })
                .containsExactly(false, true, true, true, false)
                .inOrder()
            assertThat(dataList.map { it.activeModes }.last()).isEmpty()
        }

    @Test
    fun tileData_iconsFlagEnabled_changesIconWhenActiveModesChange() =
        testScope.runTest {
            val tileData by
                collectLastValue(
                    underTest.tileData(TEST_USER, flowOf(DataUpdateTrigger.InitialRequest))
                )

            // Tile starts with the generic Modes icon.
            runCurrent()
            assertThat(tileData?.icon).isEqualTo(MODES_ICON)
            assertThat(tileData?.icon!!.resId).isEqualTo(MODES_DRAWABLE_ID)

            // Add an inactive mode -> Still modes icon
            zenModeRepository.addMode(id = "Mode", active = false)
            runCurrent()
            assertThat(tileData?.icon).isEqualTo(MODES_ICON)
            assertThat(tileData?.icon!!.resId).isEqualTo(MODES_DRAWABLE_ID)

            // Add an active mode with a default icon: icon should be the mode icon, and the
            // iconResId is also populated, because we know it's a system icon.
            zenModeRepository.addMode(
                TestModeBuilder()
                    .setId("Bedtime with default icon")
                    .setName(BEDTIME_NAME)
                    .setType(AutomaticZenRule.TYPE_BEDTIME)
                    .setActive(true)
                    .build()
            )
            runCurrent()
            assertThat(tileData?.icon).isEqualTo(BEDTIME_ICON)
            assertThat(tileData?.icon!!.resId).isEqualTo(BEDTIME_DRAWABLE_ID)

            // Add another, less-prioritized mode that has a *custom* icon: for now, icon should
            // remain the first mode icon
            zenModeRepository.addMode(
                TestModeBuilder()
                    .setId("Driving with custom icon")
                    .setName(CUSTOM_NAME)
                    .setType(AutomaticZenRule.TYPE_DRIVING)
                    .setPackage(CUSTOM_PACKAGE)
                    .setIconResId(CUSTOM_DRAWABLE_ID)
                    .setActive(true)
                    .build()
            )
            runCurrent()
            assertThat(tileData?.icon).isEqualTo(BEDTIME_ICON)
            assertThat(tileData?.icon!!.resId).isEqualTo(BEDTIME_DRAWABLE_ID)

            // Deactivate more important mode: icon should be the less important, still active mode
            zenModeRepository.deactivateMode("Bedtime with default icon")
            runCurrent()
            assertThat(tileData?.icon).isEqualTo(CUSTOM_ICON)

            // Deactivate remaining mode: back to the default modes icon
            zenModeRepository.deactivateMode("Driving with custom icon")
            runCurrent()
            assertThat(tileData?.icon).isEqualTo(MODES_ICON)
            assertThat(tileData?.icon!!.resId).isEqualTo(MODES_DRAWABLE_ID)
        }

    @Test
    @EnableFlags(Flags.FLAG_MODES_UI_TILE_REACTIVATES_LAST)
    fun tileData_withPastManualActivation_iconOfMruManualMode() =
        testScope.runTest {
            val tileData by
                collectLastValue(
                    underTest.tileData(TEST_USER, flowOf(DataUpdateTrigger.InitialRequest))
                )

            // Tile starts with the generic Modes icon.
            runCurrent()
            assertThat(tileData?.icon).isEqualTo(MODES_ICON)

            // With modes that were never activated, and no active modes -> Still modes icon
            zenModeRepository.addMode(
                TestModeBuilder()
                    .setId("Manual Mode 1")
                    .setName(BEDTIME_NAME)
                    .setManualInvocationAllowed(true)
                    .setPackage("android")
                    .setIconResId(BEDTIME_DRAWABLE_ID)
                    .build()
            )
            zenModeRepository.addMode(
                TestModeBuilder()
                    .setId("Manual Mode 2")
                    .setName(THEATER_NAME)
                    .setManualInvocationAllowed(true)
                    .setPackage("android")
                    .setIconResId(THEATER_DRAWABLE_ID)
                    .build()
            )
            zenModeRepository.addMode(
                TestModeBuilder().setId("Manual Mode 3").setManualInvocationAllowed(true).build()
            )
            runCurrent()
            assertThat(tileData?.icon).isEqualTo(MODES_ICON)

            // With modes that were activated manually -> Icon of the last manually activated mode
            zenModeRepository.updateMode("Manual Mode 3") {
                TestModeBuilder(it).setLastManualActivation(Instant.ofEpochMilli(100)).build()
            }
            zenModeRepository.updateMode("Manual Mode 2") {
                TestModeBuilder(it).setLastManualActivation(Instant.ofEpochMilli(200)).build()
            }
            runCurrent()
            assertThat(tileData?.icon).isEqualTo(THEATER_ICON)

            // With an active mode -> the icon of the active mode, regardless of past activations
            zenModeRepository.addMode(
                TestModeBuilder()
                    .setId("Active automatic mode")
                    .setName(BEDTIME_NAME)
                    .setType(AutomaticZenRule.TYPE_BEDTIME)
                    .setActive(true)
                    .build()
            )
            runCurrent()
            assertThat(tileData?.icon).isEqualTo(BEDTIME_ICON)
        }

    @Test
    @EnableFlags(Flags.FLAG_MODES_UI_TILE_REACTIVATES_LAST)
    fun tileData_withRecentManualDeactivation_quickModeIsLastDeactivatedMode() =
        testScope.runTest {
            val tileData by
                collectLastValue(
                    underTest.tileData(TEST_USER, flowOf(DataUpdateTrigger.InitialRequest))
                )
            zenModeRepository.addMode(
                TestModeBuilder()
                    .setId("mode1")
                    .setName(BEDTIME_NAME)
                    .setManualInvocationAllowed(true)
                    .setPackage("android")
                    .setIconResId(BEDTIME_DRAWABLE_ID)
                    .build()
            )

            // Starts with DND as quick mode and icon.
            runCurrent()
            assertThat(tileData?.quickMode?.id).isEqualTo(TestModeBuilder.MANUAL_DND.id)
            assertThat(tileData?.icon).isEqualTo(MODES_ICON)

            // Active mode -> shows mode icon
            zenModeRepository.activateMode("mode1")
            runCurrent()
            assertThat(tileData?.icon).isEqualTo(BEDTIME_ICON)

            // Open shade, deactivate mode and use it as override -> quick mode is deactivated mode
            keyguardRepository.setStatusBarState(StatusBarState.SHADE)
            // TODO: b/381869885 - Here and below, replace by setShadeExpansion.
            if (SceneContainerFlag.isEnabled) {
                shadeTestUtil.setShadeExpansion(1f)
            } else {
                shadeTestUtil.setLegacyExpandedOrAwaitingInputTransfer(true)
            }
            zenModeRepository.deactivateMode("mode1")
            underTest.setQuickModeOverride(listOf("mode1"))
            runCurrent()
            assertThat(tileData?.quickMode?.id).isEqualTo("mode1")
            assertThat(tileData?.icon).isEqualTo(BEDTIME_ICON)

            // Shade closes -> Tile reverts to DND as quick mode
            if (SceneContainerFlag.isEnabled) {
                shadeTestUtil.setShadeExpansion(0f)
            } else {
                shadeTestUtil.setLegacyExpandedOrAwaitingInputTransfer(false)
            }

            runCurrent()
            assertThat(tileData?.quickMode?.id).isEqualTo(TestModeBuilder.MANUAL_DND.id)
            assertThat(tileData?.icon).isEqualTo(MODES_ICON)
        }

    @EnableFlags(Flags.FLAG_MODES_UI_TILE_REACTIVATES_LAST)
    fun tileData_withPastManualActivation_mruManualModeAsQuickMode() =
        testScope.runTest {
            val tileData by
                collectLastValue(
                    underTest.tileData(TEST_USER, flowOf(DataUpdateTrigger.InitialRequest))
                )

            // Default -> DND
            runCurrent()
            assertThat(tileData?.quickMode?.id).isEqualTo(TestModeBuilder.MANUAL_DND.id)

            // With modes that were never activated, and no active modes -> Still DND
            zenModeRepository.addMode(
                TestModeBuilder().setId("Manual Mode 1").setManualInvocationAllowed(true).build()
            )
            zenModeRepository.addMode(
                TestModeBuilder().setId("Manual Mode 2").setManualInvocationAllowed(true).build()
            )
            zenModeRepository.addMode(
                TestModeBuilder().setId("Manual Mode 3").setManualInvocationAllowed(true).build()
            )
            runCurrent()
            assertThat(tileData?.quickMode?.id).isEqualTo(TestModeBuilder.MANUAL_DND.id)

            // With modes that were activated manually -> last manually activated mode
            zenModeRepository.updateMode("Manual Mode 3") {
                TestModeBuilder(it).setLastManualActivation(Instant.ofEpochMilli(100)).build()
            }
            zenModeRepository.updateMode("Manual Mode 2") {
                TestModeBuilder(it).setLastManualActivation(Instant.ofEpochMilli(200)).build()
            }
            runCurrent()
            assertThat(tileData?.quickMode?.id).isEqualTo("Manual Mode 2")

            // Active modes have no effect -> still last manually activated mode
            zenModeRepository.addMode(
                id = "Active mode",
                type = AutomaticZenRule.TYPE_BEDTIME,
                active = true,
            )
            runCurrent()
            assertThat(tileData?.quickMode?.id).isEqualTo("Manual Mode 2")
        }

    @Test
    fun getCurrentTileModel_returnsActiveModes() = runTest {
        var tileData = underTest.getCurrentTileModel()
        assertThat(tileData.isActivated).isFalse()
        assertThat(tileData.activeModes).isEmpty()

        // Add active mode
        zenModeRepository.addMode(id = "One", active = true)
        tileData = underTest.getCurrentTileModel()
        assertThat(tileData.isActivated).isTrue()
        assertThat(tileData.activeModes.map { it.name }).containsExactly("Mode One")

        // Add an inactive mode: state hasn't changed
        zenModeRepository.addMode(id = "Two", active = false)
        tileData = underTest.getCurrentTileModel()
        assertThat(tileData.isActivated).isTrue()
        assertThat(tileData.activeModes.map { it.name }).containsExactly("Mode One")

        // Add another active mode
        zenModeRepository.addMode(id = "Three", active = true)
        tileData = underTest.getCurrentTileModel()
        assertThat(tileData.isActivated).isTrue()
        assertThat(tileData.activeModes.map { it.name })
            .containsExactly("Mode One", "Mode Three")
            .inOrder()

        // Remove a mode and deactivate the other
        zenModeRepository.removeMode("One")
        zenModeRepository.deactivateMode("Three")
        tileData = underTest.getCurrentTileModel()
        assertThat(tileData.isActivated).isFalse()
        assertThat(tileData.activeModes).isEmpty()
    }

    private companion object {
        @JvmStatic
        @Parameters(name = "{0}")
        fun getParams(): List<FlagsParameterization> {
            return FlagsParameterization.allCombinationsOf().andSceneContainer()
        }

        const val CUSTOM_PACKAGE = "com.some.mode.owner.package"

        const val MODES_DRAWABLE_ID = R.drawable.ic_zen_priority_modes
        const val CUSTOM_DRAWABLE_ID = 12345

        const val BEDTIME_DRAWABLE_ID = R.drawable.ic_zen_mode_type_bedtime
        const val THEATER_DRAWABLE_ID = R.drawable.ic_zen_mode_type_theater

        val MODES_DRAWABLE = TestStubDrawable("modes_icon")
        val BEDTIME_DRAWABLE = TestStubDrawable("bedtime")
        val THEATER_DRAWABLE = TestStubDrawable("theater")
        val CUSTOM_DRAWABLE = TestStubDrawable("custom")

        // The names are used for the icon's content description
        const val BEDTIME_NAME = "Bedtime"
        const val THEATER_NAME = "Theater"
        const val CUSTOM_NAME = "Custom"

        val MODES_ICON =
            Icon.Loaded(
                drawable = MODES_DRAWABLE,
                contentDescription = null,
                resId = MODES_DRAWABLE_ID,
            )
        val BEDTIME_ICON =
            Icon.Loaded(
                drawable = BEDTIME_DRAWABLE,
                contentDescription = ContentDescription.Loaded(BEDTIME_NAME),
                resId = BEDTIME_DRAWABLE_ID,
            )
        val THEATER_ICON =
            Icon.Loaded(
                drawable = THEATER_DRAWABLE,
                contentDescription = ContentDescription.Loaded(THEATER_NAME),
                resId = THEATER_DRAWABLE_ID,
            )
        val CUSTOM_ICON =
            Icon.Loaded(
                drawable = CUSTOM_DRAWABLE,
                contentDescription = ContentDescription.Loaded(CUSTOM_NAME),
                packageName = CUSTOM_PACKAGE,
                resId = CUSTOM_DRAWABLE_ID,
            )
    }
}
