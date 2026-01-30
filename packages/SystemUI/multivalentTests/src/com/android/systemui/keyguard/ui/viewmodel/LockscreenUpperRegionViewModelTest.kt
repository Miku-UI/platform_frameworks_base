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

package com.android.systemui.keyguard.ui.viewmodel

import android.platform.test.flag.junit.FlagsParameterization
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.common.ui.data.repository.fakeConfigurationRepository
import com.android.systemui.flags.EnableSceneContainer
import com.android.systemui.flags.andSceneContainer
import com.android.systemui.keyguard.data.repository.fakeKeyguardClockRepository
import com.android.systemui.keyguard.domain.interactor.keyguardClockInteractor
import com.android.systemui.keyguard.shared.model.ClockSize
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.runCurrent
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.testScope
import com.android.systemui.lifecycle.activateIn
import com.android.systemui.res.R
import com.android.systemui.shade.data.repository.shadeConfigRepository
import com.android.systemui.shade.data.repository.shadeRepository
import com.android.systemui.shade.domain.interactor.enableDualShade
import com.android.systemui.shade.domain.interactor.enableSingleShade
import com.android.systemui.shade.domain.interactor.enableSplitShade
import com.android.systemui.shade.domain.interactor.shadeModeInteractor
import com.android.systemui.shade.shared.model.ShadeMode
import com.android.systemui.statusbar.notification.data.model.activeNotificationModel
import com.android.systemui.statusbar.notification.data.repository.ActiveNotificationsStore
import com.android.systemui.statusbar.notification.data.repository.activeNotificationListRepository
import com.android.systemui.statusbar.notification.domain.interactor.activeNotificationsInteractor
import com.android.systemui.testKosmos
import com.android.systemui.unfold.fakeUnfoldTransitionProgressProvider
import com.android.systemui.util.mockito.mock
import com.google.common.truth.Truth.assertThat
import java.util.Locale
import kotlinx.coroutines.Job
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import platform.test.runner.parameterized.ParameterizedAndroidJunit4
import platform.test.runner.parameterized.Parameters

@SmallTest
@RunWith(ParameterizedAndroidJunit4::class)
class LockscreenUpperRegionViewModelTest(flags: FlagsParameterization) : SysuiTestCase() {

    private val kosmos: Kosmos = testKosmos()
    private val Kosmos.underTest by
        Kosmos.Fixture { lockscreenUpperRegionViewModelFactory.create() }
    private val activationJob = Job()

    companion object {
        @JvmStatic
        @Parameters(name = "{0}")
        fun getParams(): List<FlagsParameterization> {
            return FlagsParameterization.allCombinationsOf().andSceneContainer()
        }
    }

    init {
        mSetFlagsRule.setFlagsParameterization(flags)
    }

    @Before
    fun setup() {
        with(kosmos) {
            enableSingleShade()
            underTest.activateIn(testScope, activationJob)
        }
    }

    @Test
    @EnableSceneContainer
    fun isNotificationsVisible_hasNotifications_true() =
        kosmos.runTest {
            setupState(hasNotifications = true)
            assertThat(underTest.isNotificationStackActive).isTrue()
        }

    @Test
    @EnableSceneContainer
    fun isNotificationsVisible_hasNoNotifications_false() =
        kosmos.runTest {
            setupState(hasNotifications = false)
            assertThat(underTest.isNotificationStackActive).isFalse()
        }

    @Test
    @EnableSceneContainer
    fun unfoldTranslations() =
        kosmos.runTest {
            val maxTranslation = prepareConfiguration()

            val unfoldProvider = fakeUnfoldTransitionProgressProvider
            unfoldProvider.onTransitionStarted()
            runCurrent()
            assertThat(underTest.unfoldTranslations.start).isZero()
            assertThat(underTest.unfoldTranslations.end).isZero()

            repeat(10) { repetition ->
                val transitionProgress = 0.1f * (repetition + 1)
                unfoldProvider.onTransitionProgress(transitionProgress)
                runCurrent()
                assertThat(underTest.unfoldTranslations.start)
                    .isEqualTo((1 - transitionProgress) * maxTranslation)
                assertThat(underTest.unfoldTranslations.end)
                    .isEqualTo(-(1 - transitionProgress) * maxTranslation)
            }

            unfoldProvider.onTransitionFinishing()
            runCurrent()
            assertThat(underTest.unfoldTranslations.start).isZero()
            assertThat(underTest.unfoldTranslations.end).isZero()

            unfoldProvider.onTransitionFinished()
            runCurrent()
            assertThat(underTest.unfoldTranslations.start).isZero()
            assertThat(underTest.unfoldTranslations.end).isZero()
        }

    private fun Kosmos.setupState(
        shadeMode: ShadeMode = ShadeMode.Single,
        clockSize: ClockSize = ClockSize.SMALL,
        hasNotifications: Boolean = false,
        shadeLayoutWide: Boolean? = null,
    ) {
        val isFullWidthShade by collectLastValue(shadeConfigRepository.isFullWidthShade)
        val legacyUseSplitShade by collectLastValue(shadeRepository.legacyUseSplitShade)
        val collectedClockSize by collectLastValue(keyguardClockInteractor.clockSize)
        val collectedShadeMode by collectLastValue(shadeModeInteractor.shadeMode)
        val areAnyNotificationsPresent by
            collectLastValue(kosmos.activeNotificationsInteractor.areAnyNotificationsPresent)
        when (shadeMode) {
            ShadeMode.Dual -> enableDualShade(wideLayout = shadeLayoutWide)
            ShadeMode.Single -> enableSingleShade()
            ShadeMode.Split -> enableSplitShade()
        }
        fakeKeyguardClockRepository.setClockSize(clockSize)
        kosmos.activeNotificationListRepository.activeNotifications.value =
            ActiveNotificationsStore.Builder()
                .apply {
                    if (hasNotifications) {
                        addIndividualNotif(
                            activeNotificationModel(
                                key = "notif",
                                aodIcon = mock(),
                                groupKey = "testGroup",
                            )
                        )
                    }
                }
                .build()
        runCurrent()
        if (shadeLayoutWide != null) {
            assertThat(isFullWidthShade).isEqualTo(!shadeLayoutWide)
            assertThat(legacyUseSplitShade).isEqualTo(shadeLayoutWide)
        }
        assertThat(collectedShadeMode).isEqualTo(shadeMode)
        assertThat(collectedClockSize).isEqualTo(clockSize)
        assertThat(areAnyNotificationsPresent).isEqualTo(hasNotifications)
    }

    private fun Kosmos.prepareConfiguration(): Int {
        val configuration = context.resources.configuration
        configuration.setLayoutDirection(Locale.US)
        fakeConfigurationRepository.onConfigurationChange(configuration)
        val maxTranslation = 10
        fakeConfigurationRepository.setDimensionPixelSize(
            R.dimen.notification_side_paddings,
            maxTranslation,
        )
        return maxTranslation
    }
}
