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

package com.android.systemui.ambientcue.ui.utils

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.ambientcue.ui.viewmodel.ActionType
import com.android.systemui.ambientcue.ui.viewmodel.ActionViewModel
import com.android.systemui.ambientcue.ui.viewmodel.IconViewModel
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock

@RunWith(AndroidJUnit4::class)
@SmallTest
class FilterUtilsTest : SysuiTestCase() {
    private val kosmos = testKosmos()

    private lateinit var calendarAction1: ActionViewModel
    private lateinit var calendarAction2: ActionViewModel
    private lateinit var mapsAction: ActionViewModel

    @Before
    fun setUp() {
        calendarAction1 =
            ActionViewModel(
                icon = IconViewModel(mock(), mock(), "calendar_icon", 0),
                label = "Sunday Morning",
                attribution = null,
                onClick = {},
                onLongClick = {},
                actionType = ActionType.MA,
            )
        calendarAction2 =
            ActionViewModel(
                icon = IconViewModel(mock(), mock(), "calendar_icon", 0),
                label = "Sunday Evening",
                attribution = null,
                onClick = {},
                onLongClick = {},
                actionType = ActionType.MA,
            )
        mapsAction =
            ActionViewModel(
                icon = IconViewModel(mock(), mock(), "map_icon", 0),
                label = "Philz Coffee San Carlos",
                onClick = {},
                onLongClick = {},
                actionType = ActionType.MA,
            )
    }

    @Test
    fun filterActions_noRepeatedAction_returnOriginalActions() {
        val filterActions = FilterUtils.filterActions(listOf(calendarAction1, mapsAction))

        assertThat(filterActions.size).isEqualTo(2)
        assertThat(filterActions).contains(calendarAction1)
        assertThat(filterActions).contains(mapsAction)
    }

    @Test
    fun filterActions_repeatedCalendarAction_filterCalendarAction() {
        val filterActions = FilterUtils.filterActions(listOf(calendarAction1, calendarAction2))

        assertThat(filterActions.size).isEqualTo(1)
        assertThat(filterActions[0].label).isEqualTo("Sunday Morning")
        assertThat(filterActions[0].icon.repeatCount).isEqualTo(1)
    }
}
