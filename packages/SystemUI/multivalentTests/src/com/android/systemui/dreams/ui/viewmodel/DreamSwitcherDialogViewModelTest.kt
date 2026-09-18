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

package com.android.systemui.dreams.ui.viewmodel

import android.content.ComponentName
import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.dreams.data.repository.dreamRepository
import com.android.systemui.dreams.data.repository.fake
import com.android.systemui.dreams.domain.interactor.dreamInteractor
import com.android.systemui.dreams.shared.model.DreamAppModel
import com.android.systemui.dreams.shared.model.DreamItemModel
import com.android.systemui.dreams.shared.model.DreamPlaylistModel
import com.android.systemui.dreams.ui.model.DreamItemUiModel
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.testDispatcher
import com.android.systemui.kosmos.testScope
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.lifecycle.activateIn
import com.android.systemui.plugins.ActivityStartOptions
import com.android.systemui.plugins.activityStarter
import com.android.systemui.settings.userTracker
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Job
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.KArgumentCaptor
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.verify

@SmallTest
@RunWith(AndroidJUnit4::class)
class DreamSwitcherDialogViewModelTest : SysuiTestCase() {

    private val kosmos = testKosmos().useUnconfinedTestDispatcher()

    private lateinit var activatableJob: Job

    private val Kosmos.underTest: DreamSwitcherDialogViewModel by
        Kosmos.Fixture {
            DreamSwitcherDialogViewModel(
                context,
                dreamInteractor,
                userTracker,
                testDispatcher,
                activityStarter,
                dreamDialogController,
            )
        }

    @Before
    fun setUp() {
        activatableJob = with(kosmos) { underTest.activateIn(testScope) }
        onTeardown { activatableJob.cancel() }
        // Set the dialog state to showing by default
        kosmos.dreamDialogController.fake.showDialog()
    }

    @Test
    fun onDreamSelected_setsActiveDream() =
        kosmos.runTest {
            setDreamPlaylist(TEST_DREAM_1, TEST_DREAM_2, activeIndex = 0)

            // Assert that the first dream is selected initially.
            assertThat(underTest.dreamItems).hasSize(2)
            assertThat(underTest.dreamItems.activeIndex).isEqualTo(0)

            // The second dream is selected by the user.
            underTest.onDreamSelected(underTest.dreamItems[1])

            // Verify the ViewModel state is correctly updated.
            assertThat(underTest.dreamItems.activeIndex).isEqualTo(1)
        }

    @Test
    fun onEditDreamClicked_withSettingsActivity_startsActivity() =
        kosmos.runTest {
            setDreamPlaylist(TEST_DREAM_1, activeIndex = 0)
            assertThat(underTest.dreamItems).hasSize(1)

            // User clicks the edit button for the first dream.
            underTest.onEditDreamClicked(underTest.dreamItems[0])

            val activityStartOptionsCaptor: KArgumentCaptor<ActivityStartOptions> = argumentCaptor()

            verify(activityStarter)
                .startActivityDismissingKeyguard(activityStartOptionsCaptor.capture())

            val intent = activityStartOptionsCaptor.firstValue.intent
            assertThat(intent.component).isEqualTo(TEST_DREAM_1_SETTINGS_ACTIVITY)
        }

    @Test
    fun onEditDreamClicked_withoutSettingsActivity_startsApp() =
        kosmos.runTest {
            setDreamPlaylist(TEST_DREAM_2, activeIndex = 0)
            assertThat(underTest.dreamItems).hasSize(1)

            // User clicks to edit the dream, but the dream has no associated settings activity.
            underTest.onEditDreamClicked(underTest.dreamItems[0])

            val activityStartOptionsCaptor: KArgumentCaptor<ActivityStartOptions> = argumentCaptor()

            verify(activityStarter)
                .startActivityDismissingKeyguard(activityStartOptionsCaptor.capture())

            val intent = activityStartOptionsCaptor.firstValue.intent
            assertThat(intent.component).isEqualTo(TEST_DREAM_2_LAUNCH_INTENT.component)
        }

    @Test
    fun onEditDreamClicked_dismissesSwitcher() =
        kosmos.runTest {
            setDreamPlaylist(TEST_DREAM_1, activeIndex = 0)
            assertThat(underTest.dreamItems).hasSize(1)

            underTest.onEditDreamClicked(underTest.dreamItems[0])

            assertThat(dreamDialogController.fake.dialogShowing).isFalse()
        }

    private fun Kosmos.setDreamPlaylist(
        vararg dreams: DreamItemModel,
        activeIndex: Int = 0,
    ): DreamPlaylistModel {
        val playlist = DreamPlaylistModel(dreams.toList(), activeIndex)
        dreamRepository.fake.setDreamState(userTracker.userHandle, playlist)
        return playlist
    }

    private companion object {
        val TEST_DREAM_1_SETTINGS_ACTIVITY = ComponentName("pkg", "settings")
        val TEST_DREAM_1 =
            DreamItemModel(
                componentName = ComponentName("pkg", "cls"),
                settingsActivity = TEST_DREAM_1_SETTINGS_ACTIVITY,
                title = "title1",
                description = "desc1",
            )

        val TEST_DREAM_2_LAUNCH_INTENT =
            Intent().apply { component = ComponentName("pkg", "dummyapp") }
        val TEST_DREAM_2 =
            DreamItemModel(
                componentName = ComponentName("pkg", "cls2"),
                appInfo = DreamAppModel("appName", TEST_DREAM_2_LAUNCH_INTENT),
                title = "title2",
            )
    }
}

private val List<DreamItemUiModel>.activeIndex: Int
    get() = indexOfFirst { it.active }
