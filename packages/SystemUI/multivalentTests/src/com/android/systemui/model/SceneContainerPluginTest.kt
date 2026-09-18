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

package com.android.systemui.model

import android.view.Display
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.flags.EnableSceneContainer
import com.android.systemui.scene.data.repository.Transition
import com.android.systemui.scene.data.repository.setSceneTransition
import com.android.systemui.scene.shared.model.Scenes
import com.android.systemui.scene.shared.model.fakeSceneDataSource
import com.android.systemui.shade.data.repository.fakeShadeDisplaysRepository
import com.android.systemui.shared.system.QuickStepContract.SYSUI_STATE_AWAKE
import com.android.systemui.shared.system.QuickStepContract.SYSUI_STATE_NOTIFICATION_PANEL_EXPANDED
import com.android.systemui.shared.system.QuickStepContract.SYSUI_STATE_NOTIFICATION_PANEL_VISIBLE
import com.android.systemui.shared.system.QuickStepContract.SYSUI_STATE_STATUS_BAR_KEYGUARD_SHOWING
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import kotlin.test.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
@EnableSceneContainer
class SceneContainerPluginTest : SysuiTestCase() {
    private val kosmos = testKosmos()

    private val shadeDisplayRepository = kosmos.fakeShadeDisplaysRepository
    private val sceneDataSource = kosmos.fakeSceneDataSource

    private val underTest: SceneContainerPlugin = kosmos.sceneContainerPluginImpl

    @Test
    fun flagValueOverride_differentDisplayId_falseIfShadeRelated() {
        sceneDataSource.changeScene(Scenes.Shade)

        shadeDisplayRepository.setDisplayId(1)

        assertThat(
                underTest.flagValueOverride(
                    flag = SYSUI_STATE_NOTIFICATION_PANEL_VISIBLE,
                    displayId = 2,
                )
            )
            .isFalse()
    }

    @Test
    fun flagValueOverride_differentDisplayId_notFalseIfKeyguardRelated() {
        sceneDataSource.changeScene(Scenes.Lockscreen)

        shadeDisplayRepository.setDisplayId(1)

        assertThat(
                underTest.flagValueOverride(
                    flag = SYSUI_STATE_STATUS_BAR_KEYGUARD_SHOWING,
                    displayId = 2,
                )
            )
            .isTrue()
    }

    @Test
    fun flagValueOverride_doesntOverrideAwake() {
        sceneDataSource.changeScene(Scenes.Shade)

        shadeDisplayRepository.setPendingDisplayId(1)

        assertThat(underTest.flagValueOverride(flag = SYSUI_STATE_AWAKE, displayId = 1)).isNull()
    }

    @Test
    fun flagValueOverride_sameDisplayId_returnsTrue() {
        sceneDataSource.changeScene(Scenes.Shade)

        shadeDisplayRepository.setPendingDisplayId(1)

        assertThat(
                underTest.flagValueOverride(
                    flag = SYSUI_STATE_NOTIFICATION_PANEL_VISIBLE,
                    displayId = 1,
                )
            )
            .isTrue()
    }

    @Test
    fun flagValueOverride_duringTransition_returnsNull() {
        kosmos.setSceneTransition(Transition(from = Scenes.Lockscreen, to = Scenes.Shade))

        assertThat(
                underTest.flagValueOverride(
                    flag = SYSUI_STATE_STATUS_BAR_KEYGUARD_SHOWING,
                    displayId = Display.DEFAULT_DISPLAY,
                )
            )
            .isNull()
    }

    @Test
    fun flagValueOverride_duringTransition_expanded_returnsTrue() {
        kosmos.setSceneTransition(Transition(from = Scenes.Lockscreen, to = Scenes.Shade))

        assertThat(
                underTest.flagValueOverride(
                    flag = SYSUI_STATE_NOTIFICATION_PANEL_EXPANDED,
                    displayId = Display.DEFAULT_DISPLAY,
                )
            )
            .isTrue()
    }
}
