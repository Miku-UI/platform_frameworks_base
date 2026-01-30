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

package com.android.systemui.statusbar.policy

import android.content.res.Configuration
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.Flags
import com.android.systemui.SysuiTestCase
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class ClockTest : SysuiTestCase() {

    private lateinit var clock: Clock

    @Before
    fun setUp() {
        clock = Clock(context, null)
    }

    @Test
    @EnableFlags(Flags.FLAG_SHADE_WINDOW_GOES_AROUND)
    fun onConfigurationChanged_fontScaleChanges_paddingChanges() {
        val initialPadding = clock.paddingLeft

        val newConfig = Configuration(context.resources.configuration)
        newConfig.fontScale += 1.0f

        clock.onConfigurationChanged(newConfig)

        assertThat(clock.paddingLeft).isNotEqualTo(initialPadding)
    }

    @Test
    @EnableFlags(Flags.FLAG_SHADE_WINDOW_GOES_AROUND)
    fun onConfigurationChanged_densityChanges_paddingChanges() {
        val initialPadding = clock.paddingLeft

        val newConfig = Configuration(context.resources.configuration)
        newConfig.densityDpi += 1

        clock.onConfigurationChanged(newConfig)

        assertThat(clock.paddingLeft).isNotEqualTo(initialPadding)
    }

    @Test
    @EnableFlags(Flags.FLAG_SHADE_WINDOW_GOES_AROUND)
    fun onConfigurationChanged_nothingChanges_paddingDoesNotChange() {
        val initialPadding = clock.paddingLeft

        val newConfig = Configuration(context.resources.configuration)

        clock.onConfigurationChanged(newConfig)

        assertThat(clock.paddingLeft).isNotEqualTo(initialPadding)
    }

    @Test
    @DisableFlags(Flags.FLAG_SHADE_WINDOW_GOES_AROUND)
    fun onConfigurationChanged_densityChanges_flagOff_paddingDoesNotChange() {
        val initialPadding = clock.paddingLeft

        val newConfig = Configuration(context.resources.configuration)
        newConfig.densityDpi += 1

        clock.onConfigurationChanged(newConfig)

        assertThat(clock.paddingLeft).isEqualTo(initialPadding)
    }
}
