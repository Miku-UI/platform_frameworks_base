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

package com.android.systemui.shade.domain.interactor

import android.platform.test.annotations.EnableFlags
import android.view.Display.TYPE_EXTERNAL
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.Flags.FLAG_DUAL_SHADE
import com.android.systemui.SysuiTestCase
import com.android.systemui.display.data.repository.display
import com.android.systemui.display.data.repository.displayRepository
import com.android.systemui.flags.EnableSceneContainer
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.runTest
import com.android.systemui.scene.domain.interactor.sceneInteractor
import com.android.systemui.scene.shared.model.Overlays
import com.android.systemui.shade.data.repository.fakeShadeDisplaysRepository
import com.android.systemui.shade.data.repository.statusBarTouchShadeDisplayPolicy
import com.android.systemui.testKosmosNew
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class DisplayAwareShadeElementToggleInteractorTest : SysuiTestCase() {
    private val kosmos = testKosmosNew()

    @Test
    @EnableFlags(FLAG_DUAL_SHADE)
    fun toggleShadeElement_whenDisplayMatches_QS_shouldNotSetExpansionIntent() =
        kosmos.runTest {
            enableDualShade()
            fakeShadeDisplaysRepository.setDisplayId(2)

            displayAwareShadeElementToggleInteractor.toggleShadeElement(qsElement, displayId = 2)

            assertThat(statusBarTouchShadeDisplayPolicy.consumeExpansionIntent()).isNull()
        }

    @Test
    fun toggleShadeElement_whenDisplayDiffers_QS_setsExpansionIntent() =
        kosmos.runTest {
            val displayId by collectLastValue(statusBarTouchShadeDisplayPolicy.displayId)
            displayRepository.addDisplays(display(id = 3, type = TYPE_EXTERNAL))
            fakeShadeDisplaysRepository.setDisplayId(2)

            displayAwareShadeElementToggleInteractor.toggleShadeElement(qsElement, displayId = 3)

            assertThat(statusBarTouchShadeDisplayPolicy.consumeExpansionIntent())
                .isEqualTo(qsElement)
            assertThat(displayId).isEqualTo(3)
        }

    @Test
    fun toggleShadeElement_whenDisplayDiffers_Notification_setsExpansionIntent() =
        kosmos.runTest {
            val displayId by collectLastValue(statusBarTouchShadeDisplayPolicy.displayId)
            displayRepository.addDisplays(display(id = 3, type = TYPE_EXTERNAL))
            fakeShadeDisplaysRepository.setDisplayId(2)

            displayAwareShadeElementToggleInteractor.toggleShadeElement(
                notificationElement,
                displayId = 3,
            )

            assertThat(statusBarTouchShadeDisplayPolicy.consumeExpansionIntent())
                .isEqualTo(notificationElement)
            assertThat(displayId).isEqualTo(3)
        }

    @Test
    @EnableFlags(FLAG_DUAL_SHADE)
    @EnableSceneContainer
    fun toggleShadeElement_whenDisplayMatches_Notification_shouldNotSetExpansionIntent() =
        kosmos.runTest {
            enableDualShade()
            fakeShadeDisplaysRepository.setDisplayId(2)

            displayAwareShadeElementToggleInteractor.toggleShadeElement(
                notificationElement,
                displayId = 2,
            )

            assertThat(statusBarTouchShadeDisplayPolicy.consumeExpansionIntent()).isNull()
        }

    @Test
    @EnableFlags(FLAG_DUAL_SHADE)
    @EnableSceneContainer
    fun toggleShadeElement_whenDisplayMatches_QS_shouldToggleQuickSettings() =
        kosmos.runTest {
            enableDualShade()
            val currentOverlays by collectLastValue(sceneInteractor.currentOverlays)

            fakeShadeDisplaysRepository.setDisplayId(2)

            displayAwareShadeElementToggleInteractor.toggleShadeElement(qsElement, displayId = 2)

            assertThat(currentOverlays).containsExactly(Overlays.QuickSettingsShade)
        }

    @Test
    @EnableFlags(FLAG_DUAL_SHADE)
    @EnableSceneContainer
    fun toggleShadeElement_whenDisplayMatches_Notification_shouldToggleNotifications() =
        kosmos.runTest {
            enableDualShade()
            val currentOverlays by collectLastValue(sceneInteractor.currentOverlays)

            fakeShadeDisplaysRepository.setDisplayId(2)

            displayAwareShadeElementToggleInteractor.toggleShadeElement(
                notificationElement,
                displayId = 2,
            )

            assertThat(currentOverlays).containsExactly(Overlays.NotificationsShade)
        }
}
