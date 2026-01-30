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

package com.android.systemui.actioncorner.data.repository

import android.provider.Settings.Secure.ACTION_CORNER_ACTION_HOME
import android.provider.Settings.Secure.ACTION_CORNER_ACTION_LOCKSCREEN
import android.provider.Settings.Secure.ACTION_CORNER_ACTION_NOTIFICATIONS
import android.provider.Settings.Secure.ACTION_CORNER_ACTION_OVERVIEW
import android.provider.Settings.Secure.ACTION_CORNER_ACTION_QUICK_SETTINGS
import android.provider.Settings.Secure.ACTION_CORNER_BOTTOM_LEFT_ACTION
import android.provider.Settings.Secure.ACTION_CORNER_BOTTOM_RIGHT_ACTION
import android.provider.Settings.Secure.ACTION_CORNER_TOP_LEFT_ACTION
import android.provider.Settings.Secure.ACTION_CORNER_TOP_RIGHT_ACTION
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.actioncorner.data.model.ActionType.HOME
import com.android.systemui.actioncorner.data.model.ActionType.LOCKSCREEN
import com.android.systemui.actioncorner.data.model.ActionType.NONE
import com.android.systemui.actioncorner.data.model.ActionType.NOTIFICATIONS
import com.android.systemui.actioncorner.data.model.ActionType.OVERVIEW
import com.android.systemui.actioncorner.data.model.ActionType.QUICK_SETTINGS
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.Kosmos.Fixture
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.testDispatcher
import com.android.systemui.kosmos.testScope
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.testKosmos
import com.android.systemui.util.settings.data.repository.userAwareSecureSettingsRepository
import com.google.common.truth.Truth.assertThat
import kotlin.test.Test
import org.junit.Rule
import org.junit.runner.RunWith
import org.mockito.junit.MockitoJUnit
import org.mockito.junit.MockitoRule

@SmallTest
@RunWith(AndroidJUnit4::class)
@kotlinx.coroutines.ExperimentalCoroutinesApi
class ActionCornerSettingRepositoryTest : SysuiTestCase() {
    @get:Rule val mockitoRule: MockitoRule = MockitoJUnit.rule()
    private val kosmos = testKosmos().useUnconfinedTestDispatcher()
    private val settingsRepository = kosmos.userAwareSecureSettingsRepository
    private val Kosmos.underTest by Fixture {
        ActionCornerSettingRepository(settingsRepository, testScope.backgroundScope, testDispatcher)
    }

    @Test
    fun allCornersDefaultToNoneAction() =
        kosmos.runTest {
            val cornerActions =
                listOf(
                    underTest.topLeftCornerAction,
                    underTest.topRightCornerAction,
                    underTest.bottomLeftCornerAction,
                    underTest.bottomRightCornerAction,
                )

            cornerActions.forEach {
                val model by collectLastValue(it)
                assertThat(model).isEqualTo(NONE)
            }
        }

    @Test
    fun testNotificationsActionOnTopLeftCorner() =
        kosmos.runTest {
            settingsRepository.setInt(
                ACTION_CORNER_TOP_LEFT_ACTION,
                ACTION_CORNER_ACTION_NOTIFICATIONS,
            )
            val model by collectLastValue(underTest.topLeftCornerAction)
            assertThat(model).isEqualTo(NOTIFICATIONS)
        }

    @Test
    fun testQuickSettingsActionOnTopRightCorner() =
        kosmos.runTest {
            settingsRepository.setInt(
                ACTION_CORNER_TOP_RIGHT_ACTION,
                ACTION_CORNER_ACTION_QUICK_SETTINGS,
            )
            val model by collectLastValue(underTest.topRightCornerAction)
            assertThat(model).isEqualTo(QUICK_SETTINGS)
        }

    @Test
    fun testOverviewActionOnBottomLeftCorner() =
        kosmos.runTest {
            settingsRepository.setInt(
                ACTION_CORNER_BOTTOM_LEFT_ACTION,
                ACTION_CORNER_ACTION_OVERVIEW,
            )
            val model by collectLastValue(underTest.bottomLeftCornerAction)
            assertThat(model).isEqualTo(OVERVIEW)
        }

    @Test
    fun testHomeActionOnBottomRightCorner() =
        kosmos.runTest {
            settingsRepository.setInt(ACTION_CORNER_BOTTOM_RIGHT_ACTION, ACTION_CORNER_ACTION_HOME)
            val model by collectLastValue(underTest.bottomRightCornerAction)
            assertThat(model).isEqualTo(HOME)
        }

    @Test
    fun testLockscreenActionOnBottomRightCorner() =
        kosmos.runTest {
            settingsRepository.setInt(
                ACTION_CORNER_BOTTOM_RIGHT_ACTION,
                ACTION_CORNER_ACTION_LOCKSCREEN,
            )
            val model by collectLastValue(underTest.bottomRightCornerAction)
            assertThat(model).isEqualTo(LOCKSCREEN)
        }
}
