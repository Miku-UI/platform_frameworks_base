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

package com.android.systemui.qs.panels.ui.viewmodel

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.common.ui.icons.Settings
import com.android.systemui.flags.DisableSceneContainer
import com.android.systemui.flags.EnableSceneContainer
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.plugins.activityStarter
import com.android.systemui.res.R
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify

@SmallTest
@RunWith(AndroidJUnit4::class)
class EditTopBarActionsViewModelTest : SysuiTestCase() {
    private val kosmos = testKosmos().useUnconfinedTestDispatcher()
    private val Kosmos.underTest by Kosmos.Fixture { editTopBarActionsViewModelFactory.create() }

    @Test
    @DisableSceneContainer
    fun sceneContainerDisabled_baseActionsEmpty() =
        kosmos.runTest { assertThat(underTest.actions).isEmpty() }

    @Test
    @EnableSceneContainer
    fun sceneContainerEnabled_baseActionsHasSettings() =
        kosmos.runTest {
            val settingsAction = underTest.actions.single()

            assertThat(settingsAction.icon).isEqualTo(Settings)
            assertThat(settingsAction.labelId).isEqualTo(R.string.qs_edit_settings)

            settingsAction.onClick()
            val intentCaptor = argumentCaptor<Intent>()
            verify(activityStarter).startActivity(intentCaptor.capture(), eq(true))
            assertThat(intentCaptor.lastValue.action)
                .isEqualTo("com.android.settings.SHADE_SETTINGS")
        }
}
