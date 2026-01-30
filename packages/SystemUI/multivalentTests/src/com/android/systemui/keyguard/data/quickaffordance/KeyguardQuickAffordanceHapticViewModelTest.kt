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

package com.android.systemui.keyguard.data.quickaffordance

import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.Flags
import com.android.systemui.SysuiTestCase
import com.android.systemui.haptics.FakeVibratorHelper
import com.android.systemui.haptics.msdl.fakeMSDLPlayer
import com.android.systemui.haptics.vibratorHelper
import com.android.systemui.keyguard.domain.interactor.keyguardQuickAffordanceHapticViewModelFactory
import com.android.systemui.keyguard.ui.binder.KeyguardBottomAreaVibrations
import com.android.systemui.kosmos.testScope
import com.android.systemui.testKosmos
import com.google.android.msdl.data.model.MSDLToken
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class KeyguardQuickAffordanceHapticViewModelTest : SysuiTestCase() {

    private val kosmos = testKosmos()
    private val testScope = kosmos.testScope
    private val vibratorHelper = kosmos.vibratorHelper as FakeVibratorHelper
    private val msdlPlayer = kosmos.fakeMSDLPlayer

    private val underTest = kosmos.keyguardQuickAffordanceHapticViewModelFactory.create()

    @DisableFlags(Flags.FLAG_MSDL_FEEDBACK)
    @Test
    fun onQuickAffordanceClick_playsShadeEffect() =
        testScope.runTest {
            underTest.onQuickAffordanceClick()

            assertThat(vibratorHelper.hasVibratedWithEffects(KeyguardBottomAreaVibrations.Shake))
                .isTrue()
            assertThat(msdlPlayer.tokensPlayed.isEmpty()).isTrue()
        }

    @EnableFlags(Flags.FLAG_MSDL_FEEDBACK)
    @Test
    fun onQuickAffordanceClick_playsFailureToken() =
        testScope.runTest {
            underTest.onQuickAffordanceClick()

            assertThat(msdlPlayer.latestTokenPlayed).isEqualTo(MSDLToken.FAILURE)
            assertThat(vibratorHelper.totalVibrations).isEqualTo(0)
        }

    @EnableFlags(Flags.FLAG_MSDL_FEEDBACK)
    @Test
    fun onUpdateActivatedHistory_withoutLongPress_whenToggling_doesNotPlayHaptics() =
        testScope.runTest {
            // GIVEN that the isActivated state toggles without a long-press called
            assertThat(underTest.longPressed).isFalse()
            underTest.updateActivatedHistory(false)
            underTest.updateActivatedHistory(true)

            // THEN no haptics play
            assertThat(msdlPlayer.tokensPlayed.isEmpty()).isTrue()
            assertThat(vibratorHelper.totalVibrations).isEqualTo(0)
        }

    @EnableFlags(Flags.FLAG_MSDL_FEEDBACK)
    @Test
    fun onUpdateActivatedHistory_togglesToActivated_playsMSDLSwitchOnToken() =
        testScope.runTest {
            // GIVEN that an affordance toggles from deactivated to activated
            toggleQuickAffordance(on = true)

            // THEN the switch on token plays
            assertThat(msdlPlayer.latestTokenPlayed).isEqualTo(MSDLToken.SWITCH_ON)
            assertThat(vibratorHelper.totalVibrations).isEqualTo(0)
        }

    @EnableFlags(Flags.FLAG_MSDL_FEEDBACK)
    @Test
    fun onUpdateActivatedHistory_togglesToDeactivated_playsMSDLSwitchOffToken() =
        testScope.runTest {
            // GIVEN that an affordance toggles from activated to deactivated
            toggleQuickAffordance(on = false)

            // THEN the switch off token plays
            assertThat(msdlPlayer.latestTokenPlayed).isEqualTo(MSDLToken.SWITCH_OFF)
            assertThat(vibratorHelper.totalVibrations).isEqualTo(0)
        }

    @DisableFlags(Flags.FLAG_MSDL_FEEDBACK)
    @Test
    fun onUpdateActivatedHistory_togglesToActivated__playsActivatedEffect() =
        testScope.runTest {
            // GIVEN that an affordance toggles from deactivated to activated
            toggleQuickAffordance(on = true)

            // THEN the activated effect plays
            assertThat(
                    vibratorHelper.hasVibratedWithEffects(KeyguardBottomAreaVibrations.Activated)
                )
                .isTrue()
            assertThat(msdlPlayer.tokensPlayed.isEmpty()).isTrue()
        }

    @DisableFlags(Flags.FLAG_MSDL_FEEDBACK)
    @Test
    fun onUpdateActivatedHistory_togglesToDeactivated_playsDeactivatedEffect() =
        testScope.runTest {
            // GIVEN that an affordance toggles from activated to deactivated
            toggleQuickAffordance(on = false)

            // THEN the deactivated effect plays
            assertThat(
                    vibratorHelper.hasVibratedWithEffects(KeyguardBottomAreaVibrations.Deactivated)
                )
                .isTrue()
            assertThat(msdlPlayer.tokensPlayed.isEmpty()).isTrue()
        }

    private fun toggleQuickAffordance(on: Boolean) {
        underTest.onQuickAffordanceLongPress(isActivated = false)
        underTest.updateActivatedHistory(!on)
        underTest.onQuickAffordanceLongPress(isActivated = true)
        underTest.updateActivatedHistory(on)
    }
}
