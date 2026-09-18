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

package com.android.systemui.statusbar.systemstatusicons.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.runTest
import com.android.systemui.statusbar.getCommandQueueCallback
import com.android.systemui.statusbar.systemstatusicons.data.repository.ExternalSystemStatusIconRepositoryHelper.createStatusBarIcon
import com.android.systemui.statusbar.systemstatusicons.shared.model.ExternalIconModel
import com.android.systemui.testKosmosNew
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class ExternalSystemStatusIconRepositoryTest : SysuiTestCase() {
    private val kosmos = testKosmosNew()

    private val Kosmos.underTest by Kosmos.Fixture { externalSystemStatusIconRepository }

    @Test
    fun icons_startsEmpty() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.icons)

            assertThat(latest).isEmpty()
        }

    @Test
    fun icons_setIconWithNullSlot_empty() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.icons)

            getCommandQueueCallback().setIcon(null, createStatusBarIcon())

            assertThat(latest).isEmpty()
        }

    @Test
    fun icons_setIconWithNullIcon_empty() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.icons)

            getCommandQueueCallback().setIcon("slot", null)

            assertThat(latest).isEmpty()
        }

    @Test
    fun icons_setIcon_hasIcon() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.icons)

            val icon = createStatusBarIcon()
            getCommandQueueCallback().setIcon("testSlot", icon)

            assertThat(latest).containsExactly(ExternalIconModel("testSlot", icon))
        }

    @Test
    fun icons_setIcon_updatesSameSlot() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.icons)

            val icon1 = createStatusBarIcon(iconId = 1, contentDescription = "test1")
            getCommandQueueCallback().setIcon("testSlot", icon1)
            val icon2 = createStatusBarIcon(iconId = 2, contentDescription = "test2")
            getCommandQueueCallback().setIcon("testSlot", icon2)

            assertThat(latest).containsExactly(ExternalIconModel("testSlot", icon2))
        }

    @Test
    fun icons_multipleIcons_inOrderBasedOnArrivalTime() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.icons)

            val icon1 = createStatusBarIcon(iconId = 1, contentDescription = "test1")
            getCommandQueueCallback().setIcon("testSlot1", icon1)
            val icon2 = createStatusBarIcon(iconId = 2, contentDescription = "test2")
            getCommandQueueCallback().setIcon("testSlot2", icon2)
            val icon3 = createStatusBarIcon(iconId = 3, contentDescription = "test3")
            getCommandQueueCallback().setIcon("testSlot3", icon3)

            assertThat(latest)
                .containsExactly(
                    ExternalIconModel("testSlot1", icon1),
                    ExternalIconModel("testSlot2", icon2),
                    ExternalIconModel("testSlot3", icon3),
                )
                .inOrder()
        }

    @Test
    fun icons_multipleIcons_withUpdate_orderMaintained() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.icons)

            val icon1 = createStatusBarIcon(iconId = 1, contentDescription = "test1")
            getCommandQueueCallback().setIcon("testSlot1", icon1)
            val icon2 = createStatusBarIcon(iconId = 2, contentDescription = "test2")
            getCommandQueueCallback().setIcon("testSlot2", icon2)
            val icon3 = createStatusBarIcon(iconId = 3, contentDescription = "test3")
            getCommandQueueCallback().setIcon("testSlot3", icon3)

            assertThat(latest)
                .containsExactly(
                    ExternalIconModel("testSlot1", icon1),
                    ExternalIconModel("testSlot2", icon2),
                    ExternalIconModel("testSlot3", icon3),
                )
                .inOrder()

            // WHEN icon2 is updated
            val icon2222 = createStatusBarIcon(iconId = 2222, contentDescription = "test2222")
            getCommandQueueCallback().setIcon("testSlot2", icon2222)

            // THEN the icon is updated, and it's still in between icon1 and icon3
            assertThat(latest)
                .containsExactly(
                    ExternalIconModel("testSlot1", icon1),
                    ExternalIconModel("testSlot2", icon2222),
                    ExternalIconModel("testSlot3", icon3),
                )
                .inOrder()
        }

    @Test
    fun icons_removeIcon_viaRemoveMethod() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.icons)

            val icon = createStatusBarIcon()
            getCommandQueueCallback().setIcon("testSlot", icon)

            assertThat(latest).containsExactly(ExternalIconModel("testSlot", icon))

            getCommandQueueCallback().removeIcon("testSlot")

            assertThat(latest).isEmpty()
        }

    @Test
    fun icons_removeIcon_viaSetMethod() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.icons)

            val icon = createStatusBarIcon()
            getCommandQueueCallback().setIcon("testSlot", icon)

            assertThat(latest).containsExactly(ExternalIconModel("testSlot", icon))

            getCommandQueueCallback().setIcon("testSlot", null)

            assertThat(latest).isEmpty()
        }
}
