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

package com.android.systemui.accessibility.hearingaid

import android.content.Context
import android.view.View.GONE
import android.view.View.VISIBLE
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.settingslib.bluetooth.HearingAidInfo.DeviceSide.SIDE_LEFT
import com.android.settingslib.bluetooth.HearingAidInfo.DeviceSide.SIDE_RIGHT
import com.android.settingslib.bluetooth.hearingdevices.ui.ExpandableControlUi.Companion.SIDE_UNIFIED
import com.android.settingslib.bluetooth.hearingdevices.ui.PresetSpinner
import com.android.settingslib.bluetooth.hearingdevices.ui.PresetUi
import com.android.systemui.SysuiTestCase
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock

/** Tests for [PresetLayout]. */
@RunWith(AndroidJUnit4::class)
@SmallTest
class PresetLayoutTest : SysuiTestCase() {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val mockPresetUiListener = mock<PresetUi.PresetUiListener>()
    private lateinit var presetLayout: PresetLayout

    @Before
    fun setUp() {
        presetLayout = PresetLayout(context)
        presetLayout.setListener(mockPresetUiListener)
    }

    @Test
    fun setupControls_assertControlsNotNull() {
        presetLayout.setupControls(setOf(SIDE_LEFT, SIDE_RIGHT))

        val controls: Map<Int, PresetSpinner> = presetLayout.getControls()
        assertThat(controls[SIDE_UNIFIED]).isNotNull()
        assertThat(controls[SIDE_LEFT]).isNotNull()
        assertThat(controls[SIDE_RIGHT]).isNotNull()
    }

    @Test
    fun setControlExpanded_assertControlUiCorrect() {
        presetLayout.setupControls(setOf(SIDE_LEFT, SIDE_RIGHT))

        presetLayout.setControlExpanded(true)
        assertControlUiCorrect()

        presetLayout.setControlExpanded(false)
        assertControlUiCorrect()
    }

    private fun assertControlUiCorrect() {
        val expanded: Boolean = presetLayout.isControlExpanded()
        val controls: Map<Int, PresetSpinner> = presetLayout.getControls()
        if (expanded) {
            assertThat(controls[SIDE_UNIFIED]).isNotNull()
            assertThat(controls[SIDE_UNIFIED]!!.visibility).isEqualTo(GONE)
            assertThat(controls[SIDE_LEFT]).isNotNull()
            assertThat(controls[SIDE_LEFT]!!.visibility).isEqualTo(VISIBLE)
            assertThat(controls[SIDE_RIGHT]).isNotNull()
            assertThat(controls[SIDE_RIGHT]!!.visibility).isEqualTo(VISIBLE)
        } else {
            assertThat(controls[SIDE_UNIFIED]).isNotNull()
            assertThat(controls[SIDE_UNIFIED]!!.visibility).isEqualTo(VISIBLE)
            assertThat(controls[SIDE_LEFT]).isNotNull()
            assertThat(controls[SIDE_LEFT]!!.visibility).isEqualTo(GONE)
            assertThat(controls[SIDE_RIGHT]).isNotNull()
            assertThat(controls[SIDE_RIGHT]!!.visibility).isEqualTo(GONE)
        }
    }
}
