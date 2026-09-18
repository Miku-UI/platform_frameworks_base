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

package com.android.systemui.statusbar.notification.collection.coordinator

import android.app.NotificationChannel.NEWS_ID
import android.app.NotificationManager.IMPORTANCE_MAX
import android.testing.TestableLooper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.statusbar.notification.collection.buildNotificationEntry
import com.android.systemui.statusbar.notification.collection.render.NodeController
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.MockitoAnnotations

@SmallTest
@RunWith(AndroidJUnit4::class)
@TestableLooper.RunWithLooper
class HighlightsCoordinatorTest : SysuiTestCase() {
    private val kosmos = testKosmos()
    @Mock
    private lateinit var highlightsController: NodeController

    private lateinit var coordinator: HighlightsCoordinator

    @Before
    fun setUp() {
        MockitoAnnotations.initMocks(this)
        coordinator =
            HighlightsCoordinator(
                highlightsController,
            )
    }

    @Test
    fun sectioner_yes() {
        val entry = kosmos.buildNotificationEntry {
            updateRanking {
                it.setImportance(IMPORTANCE_MAX)
            }
        }
        assertThat(coordinator.highlightsSectioner.isInSection(entry)).isTrue()
    }

    @Test
    fun sectioner_no() {
        val entry = kosmos.buildNotificationEntry()
        assertThat(coordinator.highlightsSectioner.isInSection(entry)).isFalse()
    }
}