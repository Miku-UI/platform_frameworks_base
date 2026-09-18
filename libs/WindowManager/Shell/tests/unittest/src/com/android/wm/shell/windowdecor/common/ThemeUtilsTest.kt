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
package com.android.wm.shell.windowdecor.common

import android.app.ActivityManager.RunningTaskInfo
import android.app.ActivityManager.TaskDescription
import android.content.res.Configuration
import android.content.res.Configuration.UI_MODE_NIGHT_YES
import android.graphics.Color
import android.testing.AndroidTestingRunner
import androidx.test.filters.SmallTest
import com.android.wm.shell.ShellTestCase
import com.android.wm.shell.TestRunningTaskInfoBuilder
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests for [ThemeUtils].
 *
 * Build/Install/Run: `atest WMShellUnitTests:ThemeUtilsTest`
 */
@SmallTest
@RunWith(AndroidTestingRunner::class)
class ThemeUtilsTest : ShellTestCase() {

    private val COLOR_LIGHT = Color.WHITE
    private val COLOR_DARK = Color.BLACK
    private val configuration = Configuration().apply { uiMode = UI_MODE_NIGHT_YES }
    private val systemTheme = Theme.DARK

    private lateinit var decorThemeUtil: DecorThemeUtil

    @Before
    fun setUp() {
        decorThemeUtil = DecorThemeUtil.Factory().create(mContext)
        mContext.getOrCreateTestableResources().overrideConfiguration(configuration)
    }

    @Test
    fun getAppTheme_darkTaskBackground_returnsDarkTheme() {
        val taskInfo = createTaskWithBgColor(COLOR_DARK)
        assertThat(decorThemeUtil.getAppTheme(taskInfo).isDark()).isTrue()
    }

    @Test
    fun getAppTheme_lightTaskBackground_returnsLightTheme() {
        val taskInfo = createTaskWithBgColor(COLOR_LIGHT)
        assertThat(decorThemeUtil.getAppTheme(taskInfo).isLight()).isTrue()
    }

    @Test
    fun getAppTheme_transparentTaskBg_returnsSystemTheme() {
        setSystemTheme(isDark = true)
        val taskInfo = createTaskWithBgColor(Color.TRANSPARENT)
        assertThat(decorThemeUtil.getAppTheme(taskInfo).isDark()).isTrue()
    }

    private fun setSystemTheme(isDark: Boolean) {
        configuration.uiMode =
            if (isDark) {
                Configuration.UI_MODE_NIGHT_YES
            } else {
                Configuration.UI_MODE_NIGHT_NO
            }
    }

    private fun createTaskWithBgColor(bgColor: Int): RunningTaskInfo =
        TestRunningTaskInfoBuilder("TestTask")
            .setTaskDescriptionBuilder(TaskDescription.Builder().setBackgroundColor(bgColor))
            .build()
}
