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

package com.android.systemui.desktop

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.wm.shell.desktopmode.api.DesktopMode
import com.google.common.truth.Truth.assertThat
import java.util.Optional
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify

@SmallTest
@RunWith(AndroidJUnit4::class)
class DesktopFirstRepositoryTest : SysuiTestCase() {

    private val desktopMode: DesktopMode = mock()

    @Test
    fun init_withDesktopModePresent_registersListener() {
        val repository = DesktopFirstRepository(Optional.of(desktopMode))

        verify(desktopMode).registerDesktopFirstListener(repository)
    }

    @Test
    fun init_withDesktopModeEmpty_doesNotRegisterListener() {
        val repository = DesktopFirstRepository(Optional.empty())

        verify(desktopMode, never()).registerDesktopFirstListener(any())
    }

    @Test
    fun isDisplayDesktopFirst_defaultState_returnsFalse() {
        val repository = DesktopFirstRepository(Optional.of(desktopMode))

        // Before any state changes, it should return false for any displayId
        assertThat(repository.isDisplayDesktopFirst(DISPLAY_ID_1)).isFalse()
        assertThat(repository.isDisplayDesktopFirst(DISPLAY_ID_2)).isFalse()
    }

    @Test
    fun onStateChanged_enableDesktopFirst_updatesState() {
        val repository = DesktopFirstRepository(Optional.of(desktopMode))

        // When state changes to true for DISPLAY_ID_1
        repository.onStateChanged(displayId = DISPLAY_ID_1, isDesktopFirstEnabled = true)

        assertThat(repository.isDisplayDesktopFirst(DISPLAY_ID_1)).isTrue()
        assertThat(repository.isDisplayDesktopFirst(DISPLAY_ID_2)).isFalse()
    }

    @Test
    fun onStateChanged_disableDesktopFirst_updatesState() {
        val repository = DesktopFirstRepository(Optional.of(desktopMode))

        repository.onStateChanged(displayId = DISPLAY_ID_1, isDesktopFirstEnabled = true)
        assertThat(repository.isDisplayDesktopFirst(DISPLAY_ID_1)).isTrue()

        // When state changes to false for DISPLAY_ID_1
        repository.onStateChanged(displayId = DISPLAY_ID_1, isDesktopFirstEnabled = false)

        // Then isDisplayDesktopFirst returns false for DISPLAY_ID_1
        assertThat(repository.isDisplayDesktopFirst(DISPLAY_ID_1)).isFalse()
    }

    @Test
    fun onStateChanged_multipleDisplays_updatesStatesIndependently() {
        val repository = DesktopFirstRepository(Optional.of(desktopMode))

        repository.onStateChanged(displayId = DISPLAY_ID_1, isDesktopFirstEnabled = true)
        repository.onStateChanged(displayId = DISPLAY_ID_2, isDesktopFirstEnabled = true)

        assertThat(repository.isDisplayDesktopFirst(DISPLAY_ID_1)).isTrue()
        assertThat(repository.isDisplayDesktopFirst(DISPLAY_ID_2)).isTrue()

        // When DISPLAY_ID_1 is disabled
        repository.onStateChanged(displayId = DISPLAY_ID_1, isDesktopFirstEnabled = false)

        // Then DISPLAY_ID_1 is false, but DISPLAY_ID_2 remains true
        assertThat(repository.isDisplayDesktopFirst(DISPLAY_ID_1)).isFalse()
        assertThat(repository.isDisplayDesktopFirst(DISPLAY_ID_2)).isTrue()
    }

    companion object {
        private const val DISPLAY_ID_1 = 0
        private const val DISPLAY_ID_2 = 1
    }
}
