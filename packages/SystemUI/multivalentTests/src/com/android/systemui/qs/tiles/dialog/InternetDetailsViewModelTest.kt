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

package com.android.systemui.qs.tiles.dialog

import android.content.Intent
import android.provider.Settings
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.qs.tiles.base.domain.actions.FakeQSTileIntentUserInputHandler
import com.android.systemui.qs.tiles.base.domain.actions.QSTileIntentUserInputHandler
import com.android.systemui.qs.tiles.base.domain.actions.intentInputs
import com.android.systemui.statusbar.connectivity.AccessPointController
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@SmallTest
@RunWith(AndroidJUnit4::class)
class InternetDetailsViewModelTest : SysuiTestCase() {

    private val accessPointController: AccessPointController = mock()
    private val contentManagerFactory: InternetDetailsContentManager.Factory = mock()
    private val qsTileIntentUserActionHandler: QSTileIntentUserInputHandler =
        FakeQSTileIntentUserInputHandler()
    private val internetDetailsContentManager: InternetDetailsContentManager = mock()

    private lateinit var viewModel: InternetDetailsViewModel

    @Before
    fun setUp() {
        whenever(accessPointController.canConfigMobileData()).thenReturn(true)
        whenever(accessPointController.canConfigWifi()).thenReturn(true)
        whenever(
                contentManagerFactory.create(
                    canConfigMobileData = true,
                    canConfigWifi = true,
                    isInDialog = false,
                )
            )
            .thenReturn(internetDetailsContentManager)

        viewModel =
            InternetDetailsViewModel(
                accessPointController,
                contentManagerFactory,
                qsTileIntentUserActionHandler,
            )
    }

    @Test
    fun title_getsTitleFromContentManager() {
        val testTitle = "Test Title"
        whenever(internetDetailsContentManager.title).thenReturn(testTitle)

        assertThat(viewModel.title).isEqualTo(testTitle)
    }

    @Test
    fun subTitle_getsSubTitleFromContentManager() {
        val testSubTitle = "Test SubTitle"
        whenever(internetDetailsContentManager.subTitle).thenReturn(testSubTitle)

        assertThat(viewModel.subTitle).isEqualTo(testSubTitle)
    }

    @Test
    fun clickOnSettingsButton_startsWifiSettings() {
        viewModel.clickOnSettingsButton()

        val expectedIntent = Intent(Settings.ACTION_WIFI_SETTINGS)
        val actualIntent =
            (qsTileIntentUserActionHandler as FakeQSTileIntentUserInputHandler)
                .intentInputs
                .last()
                .intent

        assertThat(actualIntent.action).isEqualTo(expectedIntent.action)
    }
}
