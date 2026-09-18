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

package com.android.systemui.statusbar.systemstatusicons.domain.interactor

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
class ExternalSystemStatusIconInteractorTest : SysuiTestCase() {
    private val kosmos = testKosmosNew()

    private val Kosmos.underTest by Kosmos.Fixture { kosmos.externalSystemStatusIconInteractor }

    @Test
    fun icons_matchesRepo() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.icons)

            val icon1 = createStatusBarIcon(iconId = 1, contentDescription = "test1")
            getCommandQueueCallback().setIcon("testSlot1", icon1)

            assertThat(latest).containsExactly(ExternalIconModel("testSlot1", icon1))

            val icon2 = createStatusBarIcon(iconId = 2, contentDescription = "test2")
            getCommandQueueCallback().setIcon("testSlot2", icon2)

            assertThat(latest)
                .containsExactly(
                    ExternalIconModel("testSlot1", icon1),
                    ExternalIconModel("testSlot2", icon2),
                )

            val icon2222 = createStatusBarIcon(iconId = 2222, contentDescription = "test2222")
            getCommandQueueCallback().setIcon("testSlot2", icon2222)

            assertThat(latest)
                .containsExactly(
                    ExternalIconModel("testSlot1", icon1),
                    ExternalIconModel("testSlot2", icon2222),
                )
        }

    @Test
    fun icons_directionReversed() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.icons)

            val icon1 = createStatusBarIcon(iconId = 1, contentDescription = "test1")
            getCommandQueueCallback().setIcon("testSlot1", icon1)
            val icon2 = createStatusBarIcon(iconId = 2, contentDescription = "test2")
            getCommandQueueCallback().setIcon("testSlot2", icon2)
            val icon3 = createStatusBarIcon(iconId = 3, contentDescription = "test3")
            getCommandQueueCallback().setIcon("testSlot3", icon3)

            // The most recent icon should be first.
            assertThat(latest)
                .containsExactly(
                    ExternalIconModel("testSlot3", icon3),
                    ExternalIconModel("testSlot2", icon2),
                    ExternalIconModel("testSlot1", icon1),
                )
                .inOrder()
        }
}
