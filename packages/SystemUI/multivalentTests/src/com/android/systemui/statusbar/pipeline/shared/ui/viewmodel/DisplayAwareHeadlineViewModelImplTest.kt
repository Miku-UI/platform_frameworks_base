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

package com.android.systemui.statusbar.pipeline.shared.ui.viewmodel

import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.headline.ui.viewmodel.HeadlineItem
import com.android.systemui.headline.ui.viewmodel.HeadlineItemContent
import com.android.systemui.headline.ui.viewmodel.HeadlineItemKey
import com.android.systemui.headline.ui.viewmodel.HeadlineViewModel.Companion.GoneScene
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.testScope
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.lifecycle.activateIn
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test

@SmallTest
class DisplayAwareHeadlineViewModelImplTest : SysuiTestCase() {

    private val kosmos = testKosmos().useUnconfinedTestDispatcher()
    private val Kosmos.underTest by
        Kosmos.Fixture { displayAwareHeadlineViewModelImpl.also { it.activateIn(testScope) } }

    @Before fun setUp() = kosmos.run { headlineItemsAdapter = fakeHeadlineItemsAdapter }

    @Test
    fun items_correctlyProvidesItems() =
        kosmos.runTest {
            // TODO(b/486143387): Assert that deleted items stay until transition is done
            fakeHeadlineItemsAdapter.headlineItems.value = emptyList()
            assertThat(underTest.items).hasSize(0)

            fakeHeadlineItemsAdapter.headlineItems.value = listOf(Item1)
            assertThat(underTest.items).hasSize(1)
            assertThat(underTest.items[0].key).isEqualTo(Item1.key)

            fakeHeadlineItemsAdapter.headlineItems.value = listOf(Item1, Item2, Item3)
            assertThat(underTest.items).hasSize(3)
            assertThat(underTest.items[0].key).isEqualTo(Item1.key)
            assertThat(underTest.items[1].key).isEqualTo(Item2.key)
            assertThat(underTest.items[2].key).isEqualTo(Item3.key)
        }

    @Test
    fun state_targetsFirstItem() =
        kosmos.runTest {
            fakeHeadlineItemsAdapter.headlineItems.value = emptyList()
            assertThat(underTest.state.currentScene).isEqualTo(GoneScene)

            fakeHeadlineItemsAdapter.headlineItems.value = listOf(Item1)
            assertThat(underTest.state.currentScene).isEqualTo(Item1.key.toSceneKey())

            fakeHeadlineItemsAdapter.headlineItems.value = listOf(Item3, Item2)
            assertThat(underTest.state.currentScene).isEqualTo(Item3.key.toSceneKey())

            fakeHeadlineItemsAdapter.headlineItems.value = emptyList()
            assertThat(underTest.state.currentScene).isEqualTo(GoneScene)
        }

    class FakeHeadlineItem(key: Int) : HeadlineItem {
        override val key: HeadlineItemKey = HeadlineItemKey(key)
        override val startContents: List<HeadlineItemContent> = emptyList()
        override val endContents: List<HeadlineItemContent> = emptyList()
    }

    private companion object {
        val Item1 = FakeHeadlineItem(1)
        val Item2 = FakeHeadlineItem(2)
        val Item3 = FakeHeadlineItem(3)
    }
}
