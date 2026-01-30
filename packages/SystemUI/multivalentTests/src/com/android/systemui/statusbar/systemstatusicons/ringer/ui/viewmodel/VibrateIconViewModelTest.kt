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

package com.android.systemui.statusbar.systemstatusicons.ringer.ui.viewmodel

import android.content.testableContext
import android.media.AudioManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.settingslib.volume.shared.model.RingerMode
import com.android.systemui.SysuiTestCase
import com.android.systemui.common.shared.model.ContentDescription
import com.android.systemui.common.shared.model.Icon
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.testScope
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.lifecycle.activateIn
import com.android.systemui.res.R
import com.android.systemui.testKosmos
import com.android.systemui.volume.data.repository.fakeAudioRepository
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class VibrateIconViewModelTest : SysuiTestCase() {

    private val kosmos = testKosmos().useUnconfinedTestDispatcher()
    private val underTest =
        kosmos.vibrateIconViewModelFactory.create(kosmos.testableContext).apply {
            activateIn(kosmos.testScope)
        }

    @Test
    fun visible_isFalse_byDefault() = kosmos.runTest { assertThat(underTest.visible).isFalse() }

    @Test
    fun visible_ringerModeIsVibrate_isTrue() =
        kosmos.runTest {
            fakeAudioRepository.setRingerMode(RingerMode(AudioManager.RINGER_MODE_VIBRATE))

            assertThat(underTest.visible).isTrue()
        }

    @Test
    fun visible_ringerModeIsNormal_isFalse() =
        kosmos.runTest {
            fakeAudioRepository.setRingerMode(RingerMode(AudioManager.RINGER_MODE_NORMAL))

            assertThat(underTest.visible).isFalse()
        }

    @Test
    fun visible_ringerModeIsSilent_isFalse() =
        kosmos.runTest {
            fakeAudioRepository.setRingerMode(RingerMode(AudioManager.RINGER_MODE_SILENT))

            assertThat(underTest.visible).isFalse()
        }

    @Test
    fun visible_ringerModeChanges_flips() =
        kosmos.runTest {
            fakeAudioRepository.setRingerMode(RingerMode(AudioManager.RINGER_MODE_NORMAL))
            assertThat(underTest.visible).isFalse()

            fakeAudioRepository.setRingerMode(RingerMode(AudioManager.RINGER_MODE_VIBRATE))

            assertThat(underTest.visible).isTrue()

            fakeAudioRepository.setRingerMode(RingerMode(AudioManager.RINGER_MODE_NORMAL))

            assertThat(underTest.visible).isFalse()
        }

    @Test
    fun icon_visible_isCorrect() =
        kosmos.runTest {
            fakeAudioRepository.setRingerMode(RingerMode(AudioManager.RINGER_MODE_VIBRATE))

            val expected =
                Icon.Resource(
                    R.drawable.ic_volume_ringer_vibrate,
                    ContentDescription.Resource(R.string.accessibility_ringer_vibrate),
                )
            assertThat(underTest.icon).isEqualTo(expected)
        }

    @Test fun icon_notVisible_isNull() = kosmos.runTest { assertThat(underTest.icon).isNull() }
}
