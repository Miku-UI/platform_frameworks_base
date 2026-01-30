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

package com.android.systemui.statusbar.systemstatusicons.datasaver.ui.viewmodel

import android.content.testableContext
import android.platform.test.annotations.EnableFlags
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.common.shared.model.ContentDescription
import com.android.systemui.common.shared.model.Icon
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.testScope
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.lifecycle.activateIn
import com.android.systemui.res.R
import com.android.systemui.statusbar.policy.fakeDataSaverController // For controlling the fake
import com.android.systemui.statusbar.systemstatusicons.SystemStatusIconsInCompose
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
@EnableFlags(SystemStatusIconsInCompose.FLAG_NAME)
class DataSaverIconViewModelTest : SysuiTestCase() {

    private val kosmos = testKosmos().useUnconfinedTestDispatcher()

    private val underTest =
        kosmos.dataSaverIconViewModelFactory.create(kosmos.testableContext).apply {
            activateIn(kosmos.testScope)
        }

    @Test
    fun visible_isFalse_byDefault() = kosmos.runTest { assertThat(underTest.visible).isFalse() }

    @Test
    fun visible_dataSaverTurnsOn_isTrue() =
        kosmos.runTest {
            fakeDataSaverController.setDataSaverEnabled(true)

            assertThat(underTest.visible).isTrue()
        }

    @Test
    fun visible_updatesWhenDataSaverStateChanges() =
        kosmos.runTest {
            assertThat(underTest.visible).isFalse()

            fakeDataSaverController.setDataSaverEnabled(true)
            assertThat(underTest.visible).isTrue()

            fakeDataSaverController.setDataSaverEnabled(false)
            assertThat(underTest.visible).isFalse()
        }

    @Test
    fun icon_visible_isCorrect() =
        kosmos.runTest {
            fakeDataSaverController.setDataSaverEnabled(true)
            assertThat(underTest.icon).isEqualTo(expectedDataSaverIcon)
        }

    @Test fun icon_notVisible_isNull() = kosmos.runTest { assertThat(underTest.icon).isNull() }

    companion object {
        private val expectedDataSaverIcon =
            Icon.Resource(
                resId = R.drawable.ic_data_saver,
                contentDescription =
                    ContentDescription.Resource(R.string.accessibility_data_saver_on),
            )
    }
}
