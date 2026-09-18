/*
 * Copyright (C) 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law of agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.wm.shell.desktopmode

import android.testing.AndroidTestingRunner
import androidx.test.filters.SmallTest
import com.android.wm.shell.ShellTestCase
import com.android.wm.shell.shared.R
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Test class for [DesktopAnimationConfiguration] Build/Install/Run: atest
 * WMShellUnitTests:DesktopAnimationConfigurationTest
 */
@SmallTest
@RunWith(AndroidTestingRunner::class)
class DesktopAnimationConfigurationTest : ShellTestCase() {

    private lateinit var desktopAnimationConfiguration: DesktopAnimationConfiguration

    @Test
    fun testToDesktopAnimationDurationMs() {
        val expectedDuration =
            mContext.resources.getInteger(R.integer.to_desktop_animation_duration_ms)

        desktopAnimationConfiguration = DesktopAnimationConfiguration(mContext)

        assertThat(desktopAnimationConfiguration.toDesktopAnimationDurationMs)
            .isEqualTo(expectedDuration)
    }
}
