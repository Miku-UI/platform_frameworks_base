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

package com.android.systemui.screencapture.record.smallscreen.domain.interactor

import android.hardware.display.defaultDisplay
import android.platform.test.annotations.EnableFlags
import android.view.Display
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.media.projection.flags.Flags
import com.android.systemui.SysuiTestCase
import com.android.systemui.common.shared.model.Text
import com.android.systemui.display.data.repository.display
import com.android.systemui.display.data.repository.displayRepository
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.runTest
import com.android.systemui.mediaprojection.appselector.data.TestRecentTaskFactory
import com.android.systemui.res.R
import com.android.systemui.screencapture.common.data.repository.fakeScreenCaptureRecentTaskRepository
import com.android.systemui.screencapture.record.smallscreen.shared.model.RecordDetailsTargetModel
import com.android.systemui.testKosmosNew
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class RecordDetailsTargetInteractorTest : SysuiTestCase() {

    private val kosmos: Kosmos = testKosmosNew()

    private val underTest: RecordDetailsTargetInteractor by lazy {
        kosmos.recordDetailsTargetInteractor
    }

    @Test
    fun noConnectedDisplays_returnsDefaultOptions() =
        kosmos.runTest {
            val model by collectLastValue(underTest.model)
            val recentTasks by collectLastValue(underTest.recentTasks)
            setUpDefaultRecentTasks()

            assertThat(model!!.items)
                .containsExactly(
                    RecordDetailsTargetModel.EntireScreen(
                        defaultDisplay,
                        Text.Resource(R.string.screen_record_entire_screen),
                    ),
                    RecordDetailsTargetModel.SingleApp(recentTasks!!.first(), "FakeLabel"),
                )
        }

    @Test
    fun noRecentTasks_returnsNoRecentsOption() =
        kosmos.runTest {
            val model by collectLastValue(underTest.model)
            fakeScreenCaptureRecentTaskRepository.setRecentTasks(emptyList())

            assertThat(model!!.items).contains(RecordDetailsTargetModel.SingleAppNoRecents)
        }

    @Test
    @EnableFlags(
        Flags.FLAG_MEDIA_PROJECTION_CONNECTED_DISPLAY,
        Flags.FLAG_MEDIA_PROJECTION_CONNECTED_DISPLAY_NO_VIRTUAL_DEVICE,
    )
    fun hasConnectedDisplays_returnsAllOptions() =
        kosmos.runTest {
            val model by collectLastValue(underTest.model)
            setUpAllPossibleDisplayTypes()
            setUpDefaultRecentTasks()

            assertThat(model!!.items)
                .containsAtLeast(
                    RecordDetailsTargetModel.EntireScreen(
                        defaultDisplay,
                        Text.Resource(R.string.screen_record_entire_screen),
                    ),
                    RecordDetailsTargetModel.EntireScreen(
                        display(id = 2, type = Display.TYPE_INTERNAL, name = "internal"),
                        Text.Loaded("internal"),
                    ),
                    RecordDetailsTargetModel.EntireScreen(
                        display(id = 3, type = Display.TYPE_EXTERNAL, name = "external"),
                        Text.Loaded("external"),
                    ),
                    RecordDetailsTargetModel.EntireScreen(
                        display(id = 4, type = Display.TYPE_WIFI, name = "wifi"),
                        Text.Loaded("wifi"),
                    ),
                    RecordDetailsTargetModel.EntireScreen(
                        display(id = 5, type = Display.TYPE_OVERLAY, name = "overlay"),
                        Text.Loaded("overlay"),
                    ),
                )
        }
}

private suspend fun Kosmos.setUpAllPossibleDisplayTypes() {
    displayRepository.addDisplays(
        defaultDisplay,
        display(id = 1, type = Display.TYPE_UNKNOWN, name = "unknown"),
        display(id = 2, type = Display.TYPE_INTERNAL, name = "internal"),
        display(id = 3, type = Display.TYPE_EXTERNAL, name = "external"),
        display(id = 4, type = Display.TYPE_WIFI, name = "wifi"),
        display(id = 5, type = Display.TYPE_OVERLAY, name = "overlay"),
        display(id = 6, type = Display.TYPE_VIRTUAL, name = "virtual"),
    )
}

private fun Kosmos.setUpDefaultRecentTasks() {
    fakeScreenCaptureRecentTaskRepository.setRecentTasks(TestRecentTaskFactory.createRecentTasks())
}
