/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.systemui.notetask

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.testDispatcher
import com.android.systemui.res.R
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.junit.MockitoJUnit
import org.mockito.junit.MockitoRule

/** atest SystemUITests:LockscreenNoteTakingAvailabilityTest */
@SmallTest
@RunWith(AndroidJUnit4::class)
class LockscreenNoteTakingAvailabilityTest : SysuiTestCase() {

    @get:Rule val mockito: MockitoRule = MockitoJUnit.rule()

    private val kosmos = testKosmos()
    private lateinit var underTest: LockscreenNoteTakingAvailability

    @Before
    fun setUp() {
        underTest = LockscreenNoteTakingAvailability(mContext, kosmos.testDispatcher)
    }

    private fun setLegacyUnconsentedLockscreenNoteTakingConfig(supported: Boolean) {
        mContext.orCreateTestableResources.addOverride(
            R.bool.config_supportLegacyUnconsentedLockScreenNoteTaking,
            supported,
        )
    }

    @Test
    fun isLockscreenNoteTakingEnabled_configTrue_returnsTrue() =
        kosmos.runTest {
            setLegacyUnconsentedLockscreenNoteTakingConfig(true)

            assertThat(underTest.isLockscreenNoteTakingEnabled()).isTrue()
        }

    @Test
    fun isLockscreenNoteTakingEnabled_configFalse_returnsFalse() =
        kosmos.runTest {
            setLegacyUnconsentedLockscreenNoteTakingConfig(false)

            assertThat(underTest.isLockscreenNoteTakingEnabled()).isFalse()
        }

    @Test
    fun shouldShowNotesInLockscreenShortcutPicker_configTrue_returnsTrue() =
        kosmos.runTest {
            setLegacyUnconsentedLockscreenNoteTakingConfig(true)

            assertThat(underTest.shouldShowNotesInLockscreenShortcutPicker()).isTrue()
        }

    @Test
    fun shouldShowNotesInLockscreenShortcutPicker_configFalse_returnsFalse() =
        kosmos.runTest {
            setLegacyUnconsentedLockscreenNoteTakingConfig(false)

            assertThat(underTest.shouldShowNotesInLockscreenShortcutPicker()).isFalse()
        }
}
