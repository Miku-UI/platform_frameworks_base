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

package com.android.systemui.keyguard.ui.viewmodel

import android.graphics.Rect
import android.platform.test.annotations.EnableFlags
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.Flags
import com.android.systemui.SysuiTestCase
import com.android.systemui.biometrics.data.repository.fingerprintPropertyRepository
import com.android.systemui.coroutines.collectLastValue
import com.android.systemui.haptics.msdl.fakeMSDLPlayer
import com.android.systemui.keyguard.data.repository.deviceEntryFingerprintAuthRepository
import com.android.systemui.keyguard.domain.interactor.keyguardTouchHandlingInteractor
import com.android.systemui.kosmos.testScope
import com.android.systemui.testKosmos
import com.google.android.msdl.data.model.MSDLToken
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any

@SmallTest
@RunWith(AndroidJUnit4::class)
class KeyguardTouchHandlingViewModelTest : SysuiTestCase() {

    private val kosmos = testKosmos()
    private val testScope = kosmos.testScope
    private val msdlPlayer = kosmos.fakeMSDLPlayer
    private lateinit var underTest: KeyguardTouchHandlingViewModel

    @Before
    fun setUp() {
        underTest = kosmos.keyguardTouchHandlingViewModel
    }

    @Test
    fun udfpsAccessibilityOverlayBounds_isNull_whenNotListeningForUdfps() =
        testScope.runTest {
            val accessibilityOverlayBoundsWhenListeningForUdfps by
                collectLastValue(underTest.accessibilityOverlayBoundsWhenListeningForUdfps)
            setUdfpsListeningState(false)
            assertThat(accessibilityOverlayBoundsWhenListeningForUdfps).isNull()
        }

    @Test
    fun updatesUdfpsAccessibilityOverlayBoundsWhenListeningForUdfps() =
        testScope.runTest {
            val accessibilityOverlayBoundsWhenListeningForUdfps by
                collectLastValue(underTest.accessibilityOverlayBoundsWhenListeningForUdfps)
            setUdfpsListeningState(true)
            assertThat(accessibilityOverlayBoundsWhenListeningForUdfps)
                .isEqualTo(Rect(0, 1000, 1000, 2000))
        }

    @EnableFlags(Flags.FLAG_MSDL_FEEDBACK)
    @Test
    fun onLongPress_playsLongPressHapticToken() =
        testScope.runTest {
            underTest.onLongPress(any())

            assertThat(msdlPlayer.latestTokenPlayed).isEqualTo(MSDLToken.LONG_PRESS)
        }

    private fun setUdfpsListeningState(isListening: Boolean) {
        if (isListening) {
            kosmos.fingerprintPropertyRepository.supportsUdfps()
            kosmos.keyguardTouchHandlingInteractor.setUdfpsAccessibilityOverlayBounds(
                Rect(0, 1000, 1000, 2000)
            )
        } else {
            kosmos.keyguardTouchHandlingInteractor.setUdfpsAccessibilityOverlayBounds(null)
        }
        kosmos.deviceEntryFingerprintAuthRepository.setIsRunning(isListening)
    }
}
