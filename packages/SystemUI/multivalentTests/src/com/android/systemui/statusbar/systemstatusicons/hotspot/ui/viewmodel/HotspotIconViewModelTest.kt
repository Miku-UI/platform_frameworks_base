/*
 * Copyright (C) 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.statusbar.systemstatusicons.hotspot.ui.viewmodel

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
import com.android.systemui.statusbar.policy.fakeHotspotController
import com.android.systemui.statusbar.systemstatusicons.SystemStatusIconsInCompose
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
@EnableFlags(SystemStatusIconsInCompose.FLAG_NAME)
class HotspotIconViewModelTest : SysuiTestCase() {

    private val kosmos = testKosmos().useUnconfinedTestDispatcher()
    private val fakeController = kosmos.fakeHotspotController
    private val underTest =
        kosmos.hotspotIconViewModelFactory.create(context).apply { activateIn(kosmos.testScope) }

    @Test
    fun visible_isFalse_byDefault() = kosmos.runTest { assertThat(underTest.visible).isFalse() }

    @Test
    fun visible_hotspotIsEnabled_isTrue() =
        kosmos.runTest {
            fakeController.isHotspotEnabled = true

            assertThat(underTest.visible).isTrue()
        }

    @Test
    fun visible_hotspotStateChanges_flips() =
        kosmos.runTest {
            fakeController.isHotspotEnabled = false
            assertThat(underTest.visible).isFalse()

            fakeController.isHotspotEnabled = true

            assertThat(underTest.visible).isTrue()

            fakeController.isHotspotEnabled = false

            assertThat(underTest.visible).isFalse()
        }

    @Test
    fun icon_visible_isCorrect() =
        kosmos.runTest {
            fakeController.isHotspotEnabled = true

            assertThat(underTest.icon).isEqualTo(EXPECTED_HOTSPOT_ICON)
        }

    @Test fun icon_notVisible_isNull() = kosmos.runTest { assertThat(underTest.icon).isNull() }

    companion object {
        private val EXPECTED_HOTSPOT_ICON =
            Icon.Resource(
                resId = R.drawable.ic_hotspot,
                contentDescription =
                    ContentDescription.Resource(R.string.accessibility_status_bar_hotspot),
            )
    }
}
