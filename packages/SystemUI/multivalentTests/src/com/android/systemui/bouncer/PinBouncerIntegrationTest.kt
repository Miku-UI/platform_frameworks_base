/*
 * Copyright 2026 The Android Open Source Project
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

package com.android.systemui.bouncer

import android.testing.TestableLooper.RunWithLooper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.accessibility.data.repository.accessibilityRepository
import com.android.systemui.authentication.data.repository.FakeAuthenticationRepository
import com.android.systemui.authentication.data.repository.FakeAuthenticationRepository.Companion.LOCKOUT_DURATION_SECONDS
import com.android.systemui.bouncer.ui.viewmodel.PinBouncerViewModel
import com.android.systemui.bouncer.ui.viewmodel.bouncerMessageViewModel
import com.android.systemui.bouncer.ui.viewmodel.bouncerOverlayContentViewModel
import com.android.systemui.flags.EnableSceneContainer
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.testScope
import com.android.systemui.lifecycle.activateIn
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Integration test cases for the Bouncer. */
@OptIn(ExperimentalCoroutinesApi::class)
@SmallTest
@RunWith(AndroidJUnit4::class)
@RunWithLooper
@EnableSceneContainer
class PinBouncerIntegrationTest : SysuiTestCase() {

    private val kosmos = testKosmos()

    @Before
    fun setUp(): Unit =
        kosmos.run {
            // Set this or else there will be no delay between emission and clearing.
            accessibilityRepository.setRecommendedTimeout(2.seconds)
            bouncerOverlayContentViewModel.activateIn(testScope)
        }

    @Test
    fun enterDuplicateWrongPinTwice_showsMessage() =
        kosmos.runTest {
            val message by collectLastValue(bouncerMessageViewModel.message)

            enterPin(FakeAuthenticationRepository.WRONG_PIN)
            enterPin(FakeAuthenticationRepository.WRONG_PIN)

            assertThat(message?.text).isEqualTo("Enter PIN")
            assertThat(message?.secondaryText).isEqualTo("Already tried that PIN. Try another.")
        }

    @Test
    fun enterFiveWrongPins_showsLockoutMessageAndDisablesInput() =
        kosmos.runTest {
            val message by collectLastValue(bouncerMessageViewModel.message)
            val isInputEnabled by
                collectLastValue(
                    bouncerOverlayContentViewModel.authMethodViewModel!!.isInputEnabled
                )
            assertThat(isInputEnabled).isTrue()

            repeat(FakeAuthenticationRepository.MAX_FAILED_AUTH_TRIES_BEFORE_LOCKOUT) { times ->
                enterPin(FakeAuthenticationRepository.WRONG_PIN + times)
            }

            assertThat(message?.text).isEqualTo("Try again in $LOCKOUT_DURATION_SECONDS seconds")
            assertThat(message?.secondaryText).contains("Too many attempts with incorrect PIN")
            assertThat(message?.secondaryText)
                .contains(
                    resString(com.android.internal.R.string.config_lockscreenLockoutShortlink)
                )
            assertThat(isInputEnabled).isFalse()
        }

    /** Enters the given PIN in the bouncer UI, defaulting to the correct one. */
    private fun Kosmos.enterPin(pin: List<Int> = FakeAuthenticationRepository.DEFAULT_PIN) {
        (bouncerOverlayContentViewModel.authMethodViewModel as PinBouncerViewModel).apply {
            onBackspaceButtonLongPressed()
            pin.forEach(::onPinButtonClicked)
            onAuthenticateButtonClicked()
        }
    }

    private fun resString(msgResId: Int): String = context.resources.getString(msgResId)

    private companion object {
        const val TAG = "PinBouncerIntegrationTest"
    }
}
