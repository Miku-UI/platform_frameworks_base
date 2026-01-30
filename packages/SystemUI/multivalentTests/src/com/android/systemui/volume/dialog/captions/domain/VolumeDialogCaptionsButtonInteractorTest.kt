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

package com.android.systemui.volume.dialog.captions.domain

import android.platform.test.annotations.EnableFlags
import android.testing.TestableLooper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.Flags
import com.android.systemui.SysuiTestCase
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.runTest
import com.android.systemui.testKosmosNew
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Tests for [VolumeDialogCaptionsButtonInteractor]. */
@SmallTest
@RunWith(AndroidJUnit4::class)
@TestableLooper.RunWithLooper
class VolumeDialogCaptionsButtonInteractorTest : SysuiTestCase() {
    private val kosmos = testKosmosNew()
    private lateinit var underTest: VolumeDialogCaptionsButtonInteractor

    @Before
    fun setUp() {
        underTest = kosmos.volumeDialogCaptionsButtonInteractor
    }

    @Test
    @EnableFlags(Flags.FLAG_CAPTIONS_TOGGLE_IN_VOLUME_DIALOG_V1)
    fun onButtonClicked_verifySetIsSystemAudioCaptioningEnabled() =
        kosmos.runTest {
            val current = underTest.isEnabled.value
            val latest by collectLastValue(underTest.isEnabled)

            underTest.onButtonClicked()

            assertThat(latest).isEqualTo(!current)
        }
}
