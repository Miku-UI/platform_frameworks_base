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

package com.android.systemui.accessibility.domain.interactor

import android.view.accessibility.accessibilityManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.accessibility.SystemActions
import com.android.systemui.flags.DisableSceneContainer
import com.android.systemui.flags.EnableSceneContainer
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.runTest
import com.android.systemui.scene.SceneHelper.setDeviceEntered
import com.android.systemui.scene.domain.interactor.sceneInteractor
import com.android.systemui.scene.shared.model.Scenes
import com.android.systemui.shade.shadeTestUtil
import com.android.systemui.testKosmosNew
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.reset
import org.mockito.kotlin.verify

@SmallTest
@RunWith(AndroidJUnit4::class)
class SystemActionsInteractorTest : SysuiTestCase() {
    private val kosmos = testKosmosNew()

    private val Kosmos.underTest by Kosmos.Fixture { systemActionsInteractor }

    @Before
    fun setUp() {
        with(kosmos) {
            underTest.start()
            reset(accessibilityManager)
        }
    }

    @Test
    @EnableSceneContainer
    fun shadeOverHome_registersAction() =
        kosmos.runTest {
            setDeviceEntered()
            shadeTestUtil.setShadeAndQsExpansion(shadeExpansion = 1f, qsExpansion = 0f)

            verify(accessibilityManager)
                .registerSystemAction(
                    any(),
                    eq(SystemActions.SYSTEM_ACTION_ID_ACCESSIBILITY_DISMISS_NOTIFICATION_SHADE),
                )
        }

    @Test
    @EnableSceneContainer
    fun qsOverHome_registersAction() =
        kosmos.runTest {
            setDeviceEntered()
            shadeTestUtil.setShadeAndQsExpansion(shadeExpansion = 0f, qsExpansion = 1f)

            verify(accessibilityManager)
                .registerSystemAction(
                    any(),
                    eq(SystemActions.SYSTEM_ACTION_ID_ACCESSIBILITY_DISMISS_NOTIFICATION_SHADE),
                )
        }

    @Test
    @EnableSceneContainer
    fun shadeOverLockscreen_unregistersAction() =
        kosmos.runTest {
            setDeviceEntered()
            shadeTestUtil.setShadeAndQsExpansion(shadeExpansion = 1f, qsExpansion = 0f)
            verify(accessibilityManager)
                .registerSystemAction(
                    any(),
                    eq(SystemActions.SYSTEM_ACTION_ID_ACCESSIBILITY_DISMISS_NOTIFICATION_SHADE),
                )

            sceneInteractor.snapToScene(Scenes.Lockscreen, "SystemActionsInteractorTest")

            verify(accessibilityManager)
                .unregisterSystemAction(
                    SystemActions.SYSTEM_ACTION_ID_ACCESSIBILITY_DISMISS_NOTIFICATION_SHADE
                )
        }

    @Test
    @EnableSceneContainer
    fun noShadeOnHome_unregistersAction() =
        kosmos.runTest {
            setDeviceEntered()
            shadeTestUtil.setShadeAndQsExpansion(shadeExpansion = 1f, qsExpansion = 0f)
            verify(accessibilityManager)
                .registerSystemAction(
                    any(),
                    eq(SystemActions.SYSTEM_ACTION_ID_ACCESSIBILITY_DISMISS_NOTIFICATION_SHADE),
                )

            shadeTestUtil.setShadeAndQsExpansion(shadeExpansion = 0f, qsExpansion = 0f)

            verify(accessibilityManager)
                .unregisterSystemAction(
                    SystemActions.SYSTEM_ACTION_ID_ACCESSIBILITY_DISMISS_NOTIFICATION_SHADE
                )
        }

    @Test
    @DisableSceneContainer
    fun sceneDisabled_shadeOverHome_doesNotRegister() =
        kosmos.runTest {
            setDeviceEntered()
            shadeTestUtil.setShadeAndQsExpansion(shadeExpansion = 1f, qsExpansion = 0f)

            verify(accessibilityManager, never())
                .registerSystemAction(
                    any(),
                    eq(SystemActions.SYSTEM_ACTION_ID_ACCESSIBILITY_DISMISS_NOTIFICATION_SHADE),
                )
        }
}
