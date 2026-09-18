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

package com.android.systemui.statusbar.quickactions.av.ui.viewmodel

import android.graphics.drawable.ShapeDrawable
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.testScope
import com.android.systemui.lifecycle.activateIn
import com.android.systemui.statusbar.quickactions.av.domain.interactor.AvControlsChipInteractor
import com.android.systemui.statusbar.quickactions.av.shared.model.AvControlsChipModel
import com.android.systemui.statusbar.quickactions.av.shared.model.Sensor
import com.android.systemui.statusbar.quickactions.av.shared.model.SensorAccess
import com.android.systemui.testKosmosNew
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class SensorActivityViewModelTest : SysuiTestCase() {
    private val kosmos = testKosmosNew()
    private val Kosmos.underTest by
        Kosmos.Fixture {
            val res =
                SensorActivityViewModel(
                    avControlsChipInteractor = interactor,
                    setCurrentPage = { currentPage = it },
                )
            res.activateIn(testScope)
            res
        }
    private val interactor = FakeInteractor()
    private var currentPage: PageType? = null

    @Test
    fun returnToMainPage_setsPageToMain() =
        kosmos.runTest {
            underTest.returnToMainPage()
            assertThat(currentPage).isEqualTo(PageType.MAIN)
        }

    @Test
    fun enterDedicatedPage_setsPageToSensorActivity() =
        kosmos.runTest {
            underTest.enterDedicatedPage()
            assertThat(currentPage).isEqualTo(PageType.SENSOR_ACTIVITY)
        }

    @Test
    fun closeApp_callsInteractor() =
        kosmos.runTest {
            underTest.closeApp("com.example.app")
            assertThat(interactor.closedApps).contains("com.example.app")
        }

    @Test
    fun sensorAccessList_updatesFromInteractor() =
        kosmos.runTest {
            assertThat(underTest.showSensorAccessSection).isFalse()
            assertThat(underTest.activeAppsSensorSectionSummary).isNull()
            assertThat(underTest.activeAppsSensorSectionSupportText).isNull()
            assertThat(underTest.activeAppsIconDrawable).isNull()
            assertThat(underTest.appDetails).isEmpty()

            val icon = ShapeDrawable()
            val accessList =
                listOf(SensorAccess("com.example.app", "Example App", Sensor.CAMERA, icon))
            interactor.model.value = AvControlsChipModel(sensorAccessList = accessList)

            assertThat(underTest.showSensorAccessSection).isTrue()
            assertThat(underTest.activeAppsSensorSectionSummary)
                .isEqualTo(SensorAccessSummary.Simple("Example App"))
            assertThat(underTest.activeAppsSensorSectionSupportText)
                .isEqualTo(com.android.systemui.res.R.string.privacy_chip_camera_in_use)
            assertThat(underTest.activeAppsIconDrawable).isEqualTo(icon)
            assertThat(underTest.appDetails)
                .containsExactly(
                    AppDetailUiState(
                        icon = icon,
                        appName = "Example App",
                        packageName = "com.example.app",
                        sensorUsages = listOf(Sensor.CAMERA),
                    )
                )
        }

    @Test
    fun activeAppsIconDrawable_isNull_whenMultipleAppsHaveIcons() =
        kosmos.runTest {
            // GIVEN a sensor access list with two apps that have icons
            val icon1 = ShapeDrawable()
            val icon2 = ShapeDrawable()
            val accessList =
                listOf(
                    SensorAccess("com.example.app", "Example App", Sensor.CAMERA, icon1),
                    SensorAccess("com.example.app2", "Example App 2", Sensor.MICROPHONE, icon2)
                )

            // WHEN the interactor model is updated
            interactor.model.value = AvControlsChipModel(sensorAccessList = accessList)

            // THEN the activeAppsIconDrawable is null
            assertThat(underTest.activeAppsIconDrawable).isNull()
        }

    private class FakeInteractor : AvControlsChipInteractor {
        override val model = MutableStateFlow(AvControlsChipModel())
        override val isShowingAvChip = MutableStateFlow(false)
        override val cameraBlocked = MutableStateFlow(false)
        override val microphoneBlocked = MutableStateFlow(false)
        val closedApps = mutableListOf<String>()

        override fun setCameraBlocked(value: Boolean) {
            cameraBlocked.value = value
        }

        override fun setMicrophoneBlocked(value: Boolean) {
            microphoneBlocked.value = value
        }

        override fun closeApp(packageName: String) {
            closedApps.add(packageName)
        }

        override fun manageApp(packageName: String) {}

        override fun openPrivacyDashboard() {}
    }
}
