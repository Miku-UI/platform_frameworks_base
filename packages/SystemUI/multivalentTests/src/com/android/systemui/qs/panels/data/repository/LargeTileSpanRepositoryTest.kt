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

package com.android.systemui.qs.panels.data.repository

import android.content.res.Configuration
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.common.ui.data.repository.fakeConfigurationRepository
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.testCase
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.res.R
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class LargeTileSpanRepositoryTest : SysuiTestCase() {
    private val kosmos = testKosmos().useUnconfinedTestDispatcher()
    private val Kosmos.underTest by Kosmos.Fixture { largeTileSpanRepository }

    @Test
    fun useExtraLargeTiles_tracksConfig() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.useExtraLargeTiles)

            val configuration = Configuration().apply { this.fontScale = 1f }
            context.orCreateTestableResources.overrideConfiguration(configuration)
            fakeConfigurationRepository.onConfigurationChange()
            assertThat(latest).isFalse()

            configuration.fontScale = 1.3f
            fakeConfigurationRepository.onConfigurationChange()
            assertThat(latest).isFalse()

            configuration.fontScale = 1.5f
            fakeConfigurationRepository.onConfigurationChange()
            assertThat(latest).isFalse()

            configuration.fontScale = 1.8f
            fakeConfigurationRepository.onConfigurationChange()
            assertThat(latest).isTrue()

            configuration.fontScale = 2f
            fakeConfigurationRepository.onConfigurationChange()
            assertThat(latest).isTrue()
        }

    @Test
    fun tileMaxWidth_tracksConfig() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.tileMaxWidth)

            setColumnsInConfig(1)
            assertThat(latest).isEqualTo(1)

            setColumnsInConfig(4)
            assertThat(latest).isEqualTo(4)

            setColumnsInConfig(8)
            assertThat(latest).isEqualTo(8)
        }

    private fun setColumnsInConfig(columns: Int) =
        with(kosmos) {
            testCase.context.orCreateTestableResources.addOverride(
                R.integer.quick_settings_infinite_grid_tile_max_width,
                columns,
            )
            fakeConfigurationRepository.onConfigurationChange()
        }
}
