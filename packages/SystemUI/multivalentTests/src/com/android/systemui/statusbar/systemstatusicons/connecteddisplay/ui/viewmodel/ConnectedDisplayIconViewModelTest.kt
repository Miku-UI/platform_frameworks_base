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

package com.android.systemui.statusbar.systemstatusicons.connecteddisplay.ui.viewmodel

import android.content.testableContext
import android.platform.test.annotations.EnableFlags
import android.view.Display
import android.view.Display.FLAG_SECURE
import android.view.Display.TYPE_EXTERNAL
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.common.shared.model.ContentDescription
import com.android.systemui.common.shared.model.Icon
import com.android.systemui.display.data.repository.display
import com.android.systemui.display.data.repository.displayRepository
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.testScope
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.lifecycle.activateIn
import com.android.systemui.res.R
import com.android.systemui.statusbar.systemstatusicons.SystemStatusIconsInCompose
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
@EnableFlags(SystemStatusIconsInCompose.FLAG_NAME)
class ConnectedDisplayIconViewModelTest : SysuiTestCase() {

    private val kosmos = testKosmos().useUnconfinedTestDispatcher()

    private val underTest =
        kosmos.connectedDisplayIconViewModelFactory.create(kosmos.testableContext).apply {
            activateIn(kosmos.testScope)
        }

    @Before
    fun setUp() = runBlocking { kosmos.displayRepository.removeDisplay(Display.DEFAULT_DISPLAY) }

    @Test
    fun icon_visible_isCorrect() =
        kosmos.runTest {
            displayRepository.addDisplay(display(type = TYPE_EXTERNAL, id = 1))

            assertThat(underTest.icon).isEqualTo(expectedConnectedDisplayIcon)
        }

    @Test fun icon_notVisible_isNull() = kosmos.runTest { assertThat(underTest.icon).isNull() }

    @Test
    fun visible_isFalse_byDefault() = kosmos.runTest { assertThat(underTest.visible).isFalse() }

    @Test
    fun visible_externalDisplayIsConnected_isTrue() =
        kosmos.runTest {
            displayRepository.addDisplay(display(type = TYPE_EXTERNAL, id = 1))

            assertThat(underTest.visible).isTrue()
        }

    @Test
    fun visible_secureExternalDisplayIsConnected_isTrue() =
        kosmos.runTest {
            displayRepository.addDisplay(display(type = TYPE_EXTERNAL, flags = FLAG_SECURE, id = 1))

            assertThat(underTest.visible).isTrue()
        }

    @Test
    fun visible_displayConnectionChanges_flips() =
        kosmos.runTest {
            assertThat(underTest.visible).isFalse()

            displayRepository.addDisplay(display(type = TYPE_EXTERNAL, id = 1))
            assertThat(underTest.visible).isTrue()

            displayRepository.removeDisplay(1)
            assertThat(underTest.visible).isFalse()
        }

    companion object {
        private val expectedConnectedDisplayIcon =
            Icon.Resource(
                resId = R.drawable.stat_sys_connected_display,
                contentDescription =
                    ContentDescription.Resource(R.string.connected_display_icon_desc),
            )
    }
}
