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

package com.android.wm.shell.shared.bubbles

import android.app.ActivityManager
import android.content.Context
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.testing.AndroidTestingRunner
import android.view.Display.DEFAULT_DISPLAY
import androidx.test.filters.SmallTest
import com.android.wm.shell.Flags
import com.android.wm.shell.ShellTestCase
import com.android.wm.shell.shared.desktopmode.FakeDesktopState
import com.google.common.truth.Truth.assertThat
import kotlin.test.Test
import org.junit.Before
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@SmallTest
@RunWith(AndroidTestingRunner::class)
class BubbleFeatureConfigTest : ShellTestCase() {

    private val context = mock<Context>()
    private val activityManager = mock<ActivityManager>()
    private val desktopState = FakeDesktopState()
    private lateinit var bubbleFeatureConfig: BubbleFeatureConfig

    @Before
    fun setup() {
        whenever(context.getSystemService(Context.ACTIVITY_SERVICE)).thenReturn(activityManager)
        whenever(activityManager.isLowRamDevice).thenReturn(false)

        bubbleFeatureConfig = BubbleFeatureConfigImpl(context, desktopState)
    }

    @EnableFlags(Flags.FLAG_ENABLE_CREATE_ANY_BUBBLE)
    @Test
    fun areAppBubblesSupported_lowRam() {
        whenever(activityManager.isLowRamDevice).thenReturn(true)
        assertThat(bubbleFeatureConfig.areAppBubblesSupported()).isFalse()
    }

    @DisableFlags(Flags.FLAG_ENABLE_CREATE_ANY_BUBBLE)
    @Test
    fun areAppBubblesSupported_noFlag() {
        assertThat(bubbleFeatureConfig.areAppBubblesSupported()).isFalse()
    }

    @EnableFlags(Flags.FLAG_ENABLE_CREATE_ANY_BUBBLE)
    @Test
    fun areAppBubblesSupported() {
        whenever(activityManager.isLowRamDevice).thenReturn(false)
        assertThat(bubbleFeatureConfig.areAppBubblesSupported()).isTrue()
    }

    @DisableFlags(Flags.FLAG_ENABLE_CREATE_ANY_BUBBLE)
    @EnableFlags(Flags.FLAG_DISABLE_BUBBLE_ANYTHING_DESKTOP_WINDOWING)
    @Test
    fun areAppBubblesSupported_desktopWindowingSupported_appBubblesDisabled() {
        desktopState.overrideDesktopModeSupportPerDisplay[DEFAULT_DISPLAY] = true
        assertThat(bubbleFeatureConfig.areAppBubblesSupported()).isFalse()
    }

    @EnableFlags(Flags.FLAG_ENABLE_CREATE_ANY_BUBBLE)
    @DisableFlags(Flags.FLAG_DISABLE_BUBBLE_ANYTHING_DESKTOP_WINDOWING)
    @Test
    fun areAppBubblesSupported_desktopWindowingSupported_disableAppBubbleFlagDisabled() {
        desktopState.overrideDesktopModeSupportPerDisplay[DEFAULT_DISPLAY] = true
        assertThat(bubbleFeatureConfig.areAppBubblesSupported()).isTrue()
    }

    @EnableFlags(Flags.FLAG_ENABLE_CREATE_ANY_BUBBLE)
    @EnableFlags(Flags.FLAG_DISABLE_BUBBLE_ANYTHING_DESKTOP_WINDOWING)
    @Test
    fun areAppBubblesSupported_desktopWindowingEnabled() {
        desktopState.overrideDesktopModeSupportPerDisplay[DEFAULT_DISPLAY] = true
        assertThat(bubbleFeatureConfig.areAppBubblesSupported()).isFalse()
    }

    @EnableFlags(Flags.FLAG_ENABLE_CREATE_ANY_BUBBLE)
    @EnableFlags(Flags.FLAG_DISABLE_BUBBLE_ANYTHING_DESKTOP_WINDOWING)
    @Test
    fun areAppBubblesSupported_desktopWindowingDisabled() {
        desktopState.overrideDesktopModeSupportPerDisplay[DEFAULT_DISPLAY] = false
        assertThat(bubbleFeatureConfig.areAppBubblesSupported()).isTrue()
    }

    @DisableFlags(Flags.FLAG_DISABLE_BUBBLE_SCRIM_LARGE_SCREENS)
    @Test
    fun isScrimEnabled_flagDisabled_desktopSupported_returnsTrue() {
        desktopState.overrideDesktopModeSupportPerDisplay[DEFAULT_DISPLAY] = true
        assertThat(bubbleFeatureConfig.isScrimEnabled(DEFAULT_DISPLAY)).isTrue()
    }

    @DisableFlags(Flags.FLAG_DISABLE_BUBBLE_SCRIM_LARGE_SCREENS)
    @Test
    fun isScrimEnabled_flagDisabled_desktopNotSupported_returnsTrue() {
        desktopState.overrideDesktopModeSupportPerDisplay[DEFAULT_DISPLAY] = false
        assertThat(bubbleFeatureConfig.isScrimEnabled(DEFAULT_DISPLAY)).isTrue()
    }

    @EnableFlags(Flags.FLAG_DISABLE_BUBBLE_SCRIM_LARGE_SCREENS)
    @Test
    fun isScrimEnabled_flagEnabled_desktopSupported_returnsFalse() {
        desktopState.overrideDesktopModeSupportPerDisplay[DEFAULT_DISPLAY] = true
        assertThat(bubbleFeatureConfig.isScrimEnabled(DEFAULT_DISPLAY)).isFalse()
    }

    @EnableFlags(Flags.FLAG_DISABLE_BUBBLE_SCRIM_LARGE_SCREENS)
    @Test
    fun isScrimEnabled_flagEnabled_desktopNotSupported_returnsTrue() {
        desktopState.overrideDesktopModeSupportPerDisplay[DEFAULT_DISPLAY] = false
        assertThat(bubbleFeatureConfig.isScrimEnabled(DEFAULT_DISPLAY)).isTrue()
    }
}
