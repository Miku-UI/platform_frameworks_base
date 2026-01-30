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

package com.android.systemui.display.domain.interactor

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.biometrics.data.repository.FakeDisplayStateRepository
import com.android.systemui.coroutines.collectLastValue
import com.android.systemui.display.data.repository.FakeDisplayRepository
import com.android.systemui.display.shared.model.DisplayRotation
import com.android.systemui.unfold.compat.ScreenSizeFoldProvider
import com.android.systemui.unfold.updates.FoldProvider
import com.android.systemui.util.concurrency.FakeExecutor
import com.android.systemui.util.mockito.any
import com.android.systemui.util.mockito.withArgCaptor
import com.android.systemui.util.time.FakeSystemClock
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.junit.MockitoJUnit

@SmallTest
@RunWith(AndroidJUnit4::class)
class DisplayStateInteractorImplTest : SysuiTestCase() {

    @JvmField @Rule var mockitoRule = MockitoJUnit.rule()

    private val fakeExecutor = FakeExecutor(FakeSystemClock())
    private val testScope = TestScope(StandardTestDispatcher())
    private lateinit var displayStateRepository: FakeDisplayStateRepository
    private lateinit var displayRepository: FakeDisplayRepository

    @Mock private lateinit var screenSizeFoldProvider: ScreenSizeFoldProvider
    private lateinit var interactor: DisplayStateInteractorImpl

    @Before
    fun setup() {
        displayStateRepository = FakeDisplayStateRepository()
        displayRepository = FakeDisplayRepository()
        interactor =
            DisplayStateInteractorImpl(
                testScope.backgroundScope,
                mContext,
                fakeExecutor,
                displayStateRepository,
                displayRepository,
            )
        interactor.setScreenSizeFoldProvider(screenSizeFoldProvider)
    }

    @Test
    fun isInRearDisplayModeChanges() =
        testScope.runTest {
            val isInRearDisplayMode by collectLastValue(interactor.isInRearDisplayMode)

            displayStateRepository.setIsInRearDisplayMode(false)
            assertThat(isInRearDisplayMode).isFalse()

            displayStateRepository.setIsInRearDisplayMode(true)
            assertThat(isInRearDisplayMode).isTrue()
        }

    @Test
    fun currentRotationChanges() =
        testScope.runTest {
            val currentRotation by collectLastValue(interactor.currentRotation)

            displayStateRepository.setCurrentRotation(DisplayRotation.ROTATION_180)
            assertThat(currentRotation).isEqualTo(DisplayRotation.ROTATION_180)

            displayStateRepository.setCurrentRotation(DisplayRotation.ROTATION_90)
            assertThat(currentRotation).isEqualTo(DisplayRotation.ROTATION_90)
        }

    @Test
    fun isFoldedChanges() =
        testScope.runTest {
            val isFolded by collectLastValue(interactor.isFolded)
            runCurrent()
            val callback = screenSizeFoldProvider.captureCallback()

            callback.onFoldUpdated(isFolded = true)
            assertThat(isFolded).isTrue()

            callback.onFoldUpdated(isFolded = false)
            assertThat(isFolded).isFalse()
        }

    @Test
    fun isDefaultDisplayOffChanges() =
        testScope.runTest {
            val isDefaultDisplayOff by collectLastValue(interactor.isDefaultDisplayOff)

            displayRepository.setDefaultDisplayOff(true)
            assertThat(isDefaultDisplayOff).isTrue()

            displayRepository.setDefaultDisplayOff(false)
            assertThat(isDefaultDisplayOff).isFalse()
        }
}

private fun FoldProvider.captureCallback() =
    withArgCaptor<FoldProvider.FoldCallback> {
        verify(this@captureCallback).registerCallback(capture(), any())
    }
