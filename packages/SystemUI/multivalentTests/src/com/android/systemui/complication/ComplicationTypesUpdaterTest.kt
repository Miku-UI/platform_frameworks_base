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
package com.android.systemui.complication

import android.provider.Settings
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.settingslib.dream.DreamBackend
import com.android.systemui.SysuiTestCase
import com.android.systemui.condition.SelfExecutingMonitor
import com.android.systemui.dreams.dreamOverlayStateController
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.applicationCoroutineScope
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.shared.settings.data.repository.secureSettingsRepository
import com.android.systemui.testKosmos
import com.android.systemui.user.domain.interactor.selectedUserInteractor
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@SmallTest
@RunWith(AndroidJUnit4::class)
class ComplicationTypesUpdaterTest : SysuiTestCase() {
    private val kosmos = testKosmos().useUnconfinedTestDispatcher()

    private val Kosmos.dreamBackend: DreamBackend by Kosmos.Fixture { mock() }
    private val Kosmos.monitor by Kosmos.Fixture { SelfExecutingMonitor.createInstance() }

    private val Kosmos.underTest by
        Kosmos.Fixture {
            ComplicationTypesUpdater(
                dreamBackend = dreamBackend,
                dreamOverlayStateController = dreamOverlayStateController,
                secureSettingsRepository = secureSettingsRepository,
                bgScope = applicationCoroutineScope,
                selectedUserInteractor = selectedUserInteractor,
                monitor = monitor,
            )
        }

    @Before
    fun setUp() {
        whenever(kosmos.dreamBackend.getEnabledComplications(any())).thenReturn(emptySet())
    }

    @Test
    fun testPushUpdateToDreamOverlayStateControllerImmediatelyOnStart() =
        kosmos.runTest {
            // DreamOverlayStateController shouldn't be updated before start().
            assertThat(dreamOverlayStateController.availableComplicationTypes).isEqualTo(0)

            whenever(dreamBackend.getEnabledComplications(any()))
                .thenReturn(hashSetOf(DreamBackend.COMPLICATION_TYPE_TIME))

            underTest.start()

            // DreamOverlayStateController updated immediately on start().
            assertThat(dreamOverlayStateController.availableComplicationTypes)
                .isEqualTo(DreamBackend.COMPLICATION_TYPE_TIME)
        }

    @Test
    fun testPushUpdateToDreamOverlayStateControllerOnChange() =
        kosmos.runTest {
            underTest.start()

            whenever(dreamBackend.getEnabledComplications(any()))
                .thenReturn(
                    hashSetOf(
                        DreamBackend.COMPLICATION_TYPE_TIME,
                        DreamBackend.COMPLICATION_TYPE_WEATHER,
                        DreamBackend.COMPLICATION_TYPE_AIR_QUALITY,
                    )
                )

            // Update the setting to trigger any content observers
            secureSettingsRepository.setBoolean(
                name = Settings.Secure.SCREENSAVER_COMPLICATIONS_ENABLED,
                value = true,
            )

            assertThat(dreamOverlayStateController.availableComplicationTypes)
                .isEqualTo(
                    Complication.COMPLICATION_TYPE_TIME or
                        Complication.COMPLICATION_TYPE_WEATHER or
                        Complication.COMPLICATION_TYPE_AIR_QUALITY
                )
        }
}
